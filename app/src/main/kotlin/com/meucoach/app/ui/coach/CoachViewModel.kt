package com.meucoach.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.coach.CoachConsentStore
import com.meucoach.app.coach.CoachNextContext
import com.meucoach.app.coach.CoachNextSource
import com.meucoach.app.coach.CoachPlanSource
import com.meucoach.app.coach.CoachReviewSource
import com.meucoach.app.di.ApplicationScope
import com.meucoach.app.replan.ReplanControllerFactory
import com.meucoach.app.ui.replan.LessTimeChange
import com.meucoach.app.ui.replan.ReplanUiState
import com.meucoach.core.data.coach.CoachApi
import com.meucoach.core.data.coach.CoachKind
import com.meucoach.core.data.coach.CoachMemoryRepository
import com.meucoach.core.domain.coach.CoachHistory
import com.meucoach.core.domain.coach.CoachMemoryMessage
import com.meucoach.core.domain.coach.CoachMemorySummary
import com.meucoach.core.domain.coach.CoachMessageKind
import com.meucoach.core.domain.coach.CoachPromptBuilder
import com.meucoach.core.domain.coach.CoachResponseValidator
import com.meucoach.core.domain.coach.CoachReviewPromptBuilder
import com.meucoach.core.domain.coach.CoachValidationResult
import com.meucoach.core.domain.review.WeeklyReview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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

/**
 * Next relevant session for the Coach tab (PRD evolução, F1).
 *
 * `Hidden` is the no-consent state. `Ready` carries the read-only pick of
 * [CoachNextSource]: the session plus the latest persisted prescription of
 * its date (null while the day has no decision yet) and a race event on the
 * date. It never triggers a new prescription.
 */
sealed interface CoachNextUiState {
    data object Hidden : CoachNextUiState

    data object Loading : CoachNextUiState

    data object Empty : CoachNextUiState

    data class Ready(val context: CoachNextContext) : CoachNextUiState

    data class Error(val reason: String) : CoachNextUiState
}

/**
 * Read-only list of what the Coach already persisted (PRD decisions 17/25).
 *
 * `Hidden` is the no-consent state: without opt-in the tab never shows nor
 * reads the memory. `Ready` carries validated copy only; it is never a source
 * of load, pace or planning.
 */
sealed interface CoachHistoryState {
    data object Hidden : CoachHistoryState

    data object Loading : CoachHistoryState

    data object Empty : CoachHistoryState

    data class Ready(val summaries: List<CoachMemorySummary>, val messages: List<CoachMemoryMessage>) :
        CoachHistoryState

    data class Error(val reason: String) : CoachHistoryState
}

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val consent: CoachConsentStore,
    private val plans: CoachPlanSource,
    private val reviews: CoachReviewSource,
    private val nextSource: CoachNextSource,
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

    private val _history = MutableStateFlow<CoachHistoryState>(CoachHistoryState.Hidden)
    val history: StateFlow<CoachHistoryState> = _history

    private val _next = MutableStateFlow<CoachNextUiState>(CoachNextUiState.Hidden)
    val next: StateFlow<CoachNextUiState> = _next

    /** Hash of the explanation currently rendered; excluded from the archive. */
    private val currentExplainHash = MutableStateFlow<String?>(null)

    private val retryTick = MutableStateFlow(0)
    private val historyRetryTick = MutableStateFlow(0)
    private var lastHash: String? = null
    private var lastReviewHash: String? = null

    init {
        viewModelScope.launch {
            runCatching { memory.pruneExpired() }
                .onFailure { android.util.Log.d("Coach", "memory prune failed: ${it.message}") }
        }
        observeNext()
        viewModelScope.launch {
            combine(_consented, historyRetryTick) { isConsented, tick -> isConsented to tick }
                .collectLatest { (isConsented, _) ->
                    if (!isConsented) {
                        _history.value = CoachHistoryState.Hidden
                        return@collectLatest
                    }
                    combine(
                        memory.observeMessages(),
                        memory.observeSummaries(),
                        currentExplainHash,
                        _review,
                    ) { messages, summaries, explainHash, review ->
                        val visible = CoachHistory.view(
                            messages = messages,
                            summaries = summaries,
                            currentExplainHash = explainHash,
                            currentReviewWeek = review?.weekStart,
                        )
                        if (visible.isEmpty) {
                            CoachHistoryState.Empty
                        } else {
                            CoachHistoryState.Ready(
                                summaries = visible.summaries,
                                messages = visible.messages,
                            )
                        }
                    }
                        .onStart { emit(CoachHistoryState.Loading) }
                        .catch { error ->
                            android.util.Log.d("Coach", "history failed: ${error.message}")
                            emit(CoachHistoryState.Error("Não foi possível carregar o histórico."))
                        }
                        .collect { _history.value = it }
                }
        }
        viewModelScope.launch {
            combine(_consented, plans.observeCurrent(), retryTick) { isConsented, context, tick ->
                Triple(isConsented, context, tick)
            }.collect { (isConsented, context, _) ->
                val plan = context?.plan
                if (!isConsented || plan == null) {
                    lastHash = null
                    currentExplainHash.value = null
                    _explanation.value = CoachExplanationState.Unavailable
                    return@collect
                }
                if (context.inputsHash == lastHash &&
                    _explanation.value is CoachExplanationState.Ready
                ) {
                    return@collect
                }
                currentExplainHash.value = context.inputsHash
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
                            currentExplainHash.value = context.inputsHash
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

    /**
     * Next relevant session, read-only. A future day without a persisted
     * prescription surfaces with `prescription == null`; this never computes
     * or appends a prescription for a day that has not arrived (ADR 0023).
     * [retry] restarts the collection, so the error state can recover.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeNext() {
        viewModelScope.launch {
            combine(_consented, retryTick) { isConsented, _ -> isConsented }
                .flatMapLatest { isConsented ->
                    if (!isConsented) {
                        flowOf(CoachNextUiState.Hidden)
                    } else {
                        nextSource.observeNext()
                            .map { context ->
                                if (context == null) {
                                    CoachNextUiState.Empty
                                } else {
                                    CoachNextUiState.Ready(context)
                                }
                            }
                            .onStart { emit(CoachNextUiState.Loading) }
                            .catch { error ->
                                android.util.Log.d("Coach", "next failed: ${error.message}")
                                emit(CoachNextUiState.Error("Não foi possível carregar o próximo treino."))
                            }
                    }
                }
                .collect { _next.value = it }
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

    /** Erases messages and summaries only; training data and consent are untouched. */
    fun clearHistory() {
        if (!_consented.value) return
        appScope.launch {
            runCatching { memory.clearAll() }
                .onFailure {
                    android.util.Log.d("Coach", "history clear failed: ${it.message}")
                    _history.value = CoachHistoryState.Error("Não foi possível apagar o histórico.")
                }
        }
    }

    /** Removes one validated turn; the rest of the history and training data stay. */
    fun deleteHistoryMessage(id: String) = deleteHistoryEntry { memory.deleteMessage(id) }

    /** Removes one weekly summary; the conversation and training data stay. */
    fun deleteHistorySummary(weekStart: LocalDate) = deleteHistoryEntry { memory.deleteSummary(weekStart) }

    fun retryHistory() {
        historyRetryTick.value += 1
    }

    private fun deleteHistoryEntry(delete: suspend () -> Unit) {
        if (!_consented.value) return
        appScope.launch {
            runCatching { delete() }
                .onFailure {
                    android.util.Log.d("Coach", "history delete failed: ${it.message}")
                    _history.value = CoachHistoryState.Error("Não foi possível apagar este item.")
                }
        }
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

    fun replanLessTime(changes: List<LessTimeChange>) = replanController.replanLessTime(changes)

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
