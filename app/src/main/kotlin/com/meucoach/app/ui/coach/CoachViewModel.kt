package com.meucoach.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.coach.CoachConsentStore
import com.meucoach.app.coach.CoachPlanSource
import com.meucoach.app.coach.CoachReviewSource
import com.meucoach.app.di.ApplicationScope
import com.meucoach.app.replan.ReplanControllerFactory
import com.meucoach.app.ui.replan.ReplanUiState
import com.meucoach.core.data.coach.CoachApi
import com.meucoach.core.data.coach.CoachKind
import com.meucoach.core.data.coach.CoachMemoryRepository
import com.meucoach.core.domain.coach.CoachMessageKind
import com.meucoach.core.domain.coach.CoachPromptBuilder
import com.meucoach.core.domain.coach.CoachResponseValidator
import com.meucoach.core.domain.coach.CoachReviewPromptBuilder
import com.meucoach.core.domain.coach.CoachValidationResult
import com.meucoach.core.domain.review.WeeklyReview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

sealed interface CoachExplanationState {
    data object Unavailable : CoachExplanationState

    data object Loading : CoachExplanationState

    data class Ready(val title: String, val body: String, val bullets: List<String>) : CoachExplanationState

    data class Fallback(val reason: String, val planRationale: String) : CoachExplanationState
}

/**
 * Narrative of the closed-week review. `Fallback` keeps the deterministic review
 * on screen: the AI only rewrites text, so an unavailable model is not an error
 * state for the athlete.
 */
sealed interface CoachReviewState {
    data object Unavailable : CoachReviewState

    data object Loading : CoachReviewState

    data class Ready(val title: String, val body: String, val bullets: List<String>) : CoachReviewState

    data class Fallback(val reason: String) : CoachReviewState
}

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val consent: CoachConsentStore,
    private val plans: CoachPlanSource,
    private val reviews: CoachReviewSource,
    private val api: CoachApi,
    private val memory: CoachMemoryRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    replanFactory: ReplanControllerFactory,
) : ViewModel() {
    private val _consented = MutableStateFlow(consent.isConsented())
    val consented: StateFlow<Boolean> = _consented

    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed

    private val replanController = replanFactory.create(viewModelScope)

    /** Same acceptance machine as Today/Perfil; the Coach tab never duplicates it. */
    val replan: StateFlow<ReplanUiState> = replanController.uiState

    private val _explanation =
        MutableStateFlow<CoachExplanationState>(CoachExplanationState.Unavailable)
    val explanation: StateFlow<CoachExplanationState> = _explanation

    private val _review = MutableStateFlow<WeeklyReview?>(null)
    val review: StateFlow<WeeklyReview?> = _review

    private val _reviewState = MutableStateFlow<CoachReviewState>(CoachReviewState.Unavailable)
    val reviewState: StateFlow<CoachReviewState> = _reviewState

    private val retryTick = MutableStateFlow(0)
    private var lastHash: String? = null
    private var lastReviewHash: String? = null

    init {
        viewModelScope.launch {
            runCatching { memory.pruneExpired() }
                .onFailure { android.util.Log.d("Coach", "memory prune failed: ${it.message}") }
        }
        viewModelScope.launch {
            combine(_consented, plans.observeCurrent(), retryTick) { isConsented, context, tick ->
                Triple(isConsented, context, tick)
            }.collect { (isConsented, context, _) ->
                val plan = context?.plan
                if (!isConsented || plan == null) {
                    lastHash = null
                    _explanation.value = CoachExplanationState.Unavailable
                    return@collect
                }
                if (context.inputsHash == lastHash &&
                    _explanation.value is CoachExplanationState.Ready
                ) {
                    return@collect
                }
                _explanation.value = CoachExplanationState.Loading
                try {
                    val prompt = CoachPromptBuilder.build(plan, context.inputsHash)
                    val raw = api.explain(prompt, CoachKind.EXPLAIN)
                    if (!_consented.value) return@collect
                    val allowed = prompt.sessions.mapNotNull { it.durationMin }.toSet() +
                        prompt.weeklyMinutes +
                        listOfNotNull(prompt.executedMinutes)
                    when (val validated = CoachResponseValidator.validate(raw, allowed)) {
                        is CoachValidationResult.Valid -> {
                            val response = validated.response
                            recordSafely {
                                memory.recordMessage(
                                    kind = CoachMessageKind.EXPLAIN,
                                    inputsHash = context.inputsHash,
                                    title = response.title,
                                    body = response.body,
                                    bullets = response.bullets,
                                )
                            }
                            lastHash = context.inputsHash
                            android.util.Log.d("Coach", "explain ok hash=${context.inputsHash}")
                            _explanation.value = CoachExplanationState.Ready(
                                title = response.title,
                                body = response.body,
                                bullets = response.bullets,
                            )
                        }

                        is CoachValidationResult.Invalid -> {
                            android.util.Log.d("Coach", "explain rejected: ${validated.reason}")
                            _explanation.value = CoachExplanationState.Fallback(
                                reason = validated.reason,
                                planRationale = plan.rationale,
                            )
                        }
                    }
                } catch (e: IOException) {
                    if (!_consented.value) return@collect
                    android.util.Log.d("Coach", "explain io: ${e.message}")
                    _explanation.value = CoachExplanationState.Fallback(
                        reason = "Sem conexão com o Coach. Mostrando o resumo do motor.",
                        planRationale = plan.rationale,
                    )
                } catch (e: Exception) {
                    if (!_consented.value) return@collect
                    android.util.Log.d("Coach", "explain failed: ${e.message}")
                    _explanation.value = CoachExplanationState.Fallback(
                        reason = "O Coach não respondeu. Mostrando o resumo do motor.",
                        planRationale = plan.rationale,
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(_consented, reviews.observeLastClosedWeek(), retryTick) { isConsented, review, tick ->
                Triple(isConsented, review, tick)
            }.collect { (isConsented, review, _) ->
                _review.value = review
                if (!isConsented || review == null) {
                    lastReviewHash = null
                    _reviewState.value = CoachReviewState.Unavailable
                    return@collect
                }
                val prompt = CoachReviewPromptBuilder.build(review)
                if (prompt.inputsHash == lastReviewHash && _reviewState.value is CoachReviewState.Ready) {
                    return@collect
                }
                _reviewState.value = CoachReviewState.Loading
                try {
                    val raw = api.review(prompt)
                    if (!_consented.value) return@collect
                    val validated = CoachResponseValidator.validate(
                        raw = raw,
                        allowedMinutes = CoachReviewPromptBuilder.allowedMinutes(prompt),
                        allowedPercents = CoachReviewPromptBuilder.allowedPercents(prompt),
                    )
                    when (validated) {
                        is CoachValidationResult.Valid -> {
                            val response = validated.response
                            recordSafely {
                                memory.recordMessage(
                                    kind = CoachMessageKind.REVIEW,
                                    inputsHash = prompt.inputsHash,
                                    title = response.title,
                                    body = response.body,
                                    bullets = response.bullets,
                                )
                                memory.recordSummary(
                                    weekStart = review.weekStart,
                                    inputsHash = prompt.inputsHash,
                                    title = response.title,
                                    body = response.body,
                                    bullets = response.bullets,
                                )
                            }
                            lastReviewHash = prompt.inputsHash
                            android.util.Log.d("Coach", "review ok hash=${prompt.inputsHash}")
                            _reviewState.value = CoachReviewState.Ready(
                                title = response.title,
                                body = response.body,
                                bullets = response.bullets,
                            )
                        }

                        is CoachValidationResult.Invalid -> {
                            android.util.Log.d("Coach", "review rejected: ${validated.reason}")
                            _reviewState.value = CoachReviewState.Fallback(validated.reason)
                        }
                    }
                } catch (e: IOException) {
                    if (!_consented.value) return@collect
                    android.util.Log.d("Coach", "review io: ${e.message}")
                    _reviewState.value = CoachReviewState.Fallback("Sem conexão com o Coach.")
                } catch (e: Exception) {
                    if (!_consented.value) return@collect
                    android.util.Log.d("Coach", "review failed: ${e.message}")
                    _reviewState.value = CoachReviewState.Fallback("O Coach não respondeu.")
                }
            }
        }
    }

    fun grant() {
        consent.grant()
        _consented.value = true
    }

    fun revoke() {
        consent.revoke()
        _consented.value = false
        _dismissed.value = false
        // App scope: the deletion must finish even if the tab/ViewModel goes away.
        appScope.launch {
            runCatching { memory.clearAll() }
                .onFailure { android.util.Log.d("Coach", "memory clear failed: ${it.message}") }
        }
    }

    fun dismiss() {
        _dismissed.value = true
    }

    fun retry() {
        lastHash = null
        lastReviewHash = null
        retryTick.value += 1
    }

    /**
     * Quick actions on the Coach tab evaluate T3 explicitly. "Ajustar a semana"
     * is the plain request; the other two open a picker and evaluate with an
     * explicit intent (3.3-B).
     */
    fun requestReplan() = replanController.replanNow()

    fun requestMissedSession() = replanController.requestMissedSession()

    fun replanMissed(date: LocalDate) = replanController.replanMissed(date)

    fun requestLessTime() = replanController.requestLessTime()

    fun replanLessTime(date: LocalDate, minutes: Int) = replanController.replanLessTime(date, minutes)

    fun dismissReplanIntent() = replanController.dismissIntent()

    fun acceptReplan() = replanController.accept()

    fun declineReplan() = replanController.decline()

    fun suppressReplan() = replanController.suppress()

    fun dismissReplanSuggestion() = replanController.dismissSuggestion()

    fun undoReplan() = replanController.undo()

    fun consumeReplanMessage() = replanController.consumeMessage()

    /** Memory failure must never break the tab: the engine copy stays on screen. */
    private inline fun recordSafely(block: () -> Unit) {
        if (!_consented.value) return
        runCatching(block).onFailure {
            android.util.Log.d("Coach", "memory write failed: ${it.message}")
        }
    }
}
