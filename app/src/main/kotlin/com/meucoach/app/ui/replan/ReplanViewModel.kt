package com.meucoach.app.ui.replan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.application.ApplyReplan
import com.meucoach.app.application.ApplyReplanRequest
import com.meucoach.app.application.ApplyReplanResult
import com.meucoach.app.application.EvaluateReplan
import com.meucoach.app.application.EvaluateReplanResult
import com.meucoach.app.application.diffFuturePlannedSessions
import com.meucoach.app.replan.ReplanSuggestionStore
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.replan.ReplanTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Drives the replanning invitation on the Today screen.
 *
 * Automatic suggestions respect the day's suppression/decline preference; the
 * explicit "Replanejar minha semana" action always evaluates. Acceptance goes
 * through `ApplyReplan`, which revalidates version and inputs before writing.
 */
@HiltViewModel
class ReplanViewModel @Inject constructor(
    private val plans: TrainingPlanRepository,
    private val evaluateReplan: EvaluateReplan,
    private val applyReplan: ApplyReplan,
    private val store: ReplanSuggestionStore,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReplanUiState())
    val uiState: StateFlow<ReplanUiState> = mutableState.asStateFlow()

    private var pending: EvaluateReplanResult.Suggestion? = null
    private var evaluating = false
    private var queuedExplicit = false

    /** Called when Today becomes visible; never surfaces an explicit-only message. */
    fun refresh() = runEvaluation(explicit = false)

    /** Explicit T3 request: ignores suppression and reports `NoChange` reasons. */
    fun replanNow() = runEvaluation(explicit = true)

    fun accept() {
        val suggestion = pending ?: return
        if (mutableState.value.isApplying) return
        mutableState.update { it.copy(isApplying = true) }
        viewModelScope.launch {
            val today = timeProvider.currentDate()
            try {
                val result = applyReplan(
                    ApplyReplanRequest(
                        suggestion = suggestion.suggestion,
                        candidate = suggestion.candidate,
                        today = today,
                        explicit = suggestion.suggestion.trigger == ReplanTrigger.USER_REQUEST,
                    ),
                )
                when (result) {
                    is ApplyReplanResult.Applied -> {
                        pending = null
                        store.clear(today)
                        reloadVersionAndChange(today)
                        mutableState.update {
                            it.copy(
                                isApplying = false,
                                suggestion = null,
                                message = "Semana ajustada. O que já passou e o treino de hoje não mudaram.",
                            )
                        }
                    }

                    is ApplyReplanResult.Stale -> {
                        pending = null
                        reloadVersionAndChange(today)
                        when (val fresh = result.reevaluated) {
                            is EvaluateReplanResult.Suggestion -> {
                                pending = fresh
                                mutableState.update {
                                    it.copy(
                                        isApplying = false,
                                        suggestion = fresh.suggestion.toUi(fresh.diff),
                                        message = "Os dados mudaram enquanto você decidia. " +
                                            "Atualizei a sugestão; confira o que mudou.",
                                    )
                                }
                            }

                            is EvaluateReplanResult.NoChange -> mutableState.update {
                                it.copy(
                                    isApplying = false,
                                    suggestion = null,
                                    message = "Os dados mudaram e não há mais nada para ajustar nesta semana.",
                                )
                            }

                            is EvaluateReplanResult.Unavailable -> mutableState.update {
                                it.copy(
                                    isApplying = false,
                                    suggestion = null,
                                    message = fresh.reason,
                                )
                            }
                        }
                    }

                    is ApplyReplanResult.Rejected -> mutableState.update {
                        it.copy(isApplying = false, message = result.message)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        isApplying = false,
                        message = "Não foi possível ajustar a semana agora. Tente de novo em instantes.",
                    )
                }
            }
        }
    }

    /** Declines this exact proposal; a new version/input set may be offered again. */
    fun decline() {
        val suggestion = pending ?: return
        val today = timeProvider.currentDate()
        store.decline(today, suggestion.suggestion)
        pending = null
        mutableState.update { it.copy(suggestion = null, message = null) }
    }

    /** Hides automatic suggestions for the rest of the day; the explicit button still works. */
    fun suppress() {
        val today = timeProvider.currentDate()
        store.suppress(today)
        pending = null
        mutableState.update {
            it.copy(
                suggestion = null,
                message = "Tudo bem. Não vou sugerir hoje; você pode pedir quando quiser.",
            )
        }
    }

    fun consumeMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private suspend fun evaluate(explicit: Boolean) {
        val today = timeProvider.currentDate()
        val version = plans.getWeekRecord(today.weekStart())?.version ?: 1
        mutableState.update { it.copy(planVersion = version) }

        val result = evaluateReplan(explicit)
        val lastChange = loadLastChange(today)
        when (result) {
            is EvaluateReplanResult.Suggestion -> {
                val key = ReplanSuggestionStore.suggestionKey(result.suggestion)
                val blocked = !explicit &&
                    (store.isSuppressed(today) || store.declinedSuggestion(today) == key)
                pending = result
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        planVersion = result.suggestion.expectedVersion,
                        suggestion = if (blocked) null else result.suggestion.toUi(result.diff),
                        lastChange = lastChange,
                        message = null,
                    )
                }
            }

            is EvaluateReplanResult.NoChange -> {
                pending = null
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        suggestion = null,
                        lastChange = lastChange,
                        message = if (explicit) result.reason else null,
                    )
                }
            }

            is EvaluateReplanResult.Unavailable -> {
                pending = null
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        suggestion = null,
                        lastChange = lastChange,
                        message = if (explicit) result.reason else null,
                    )
                }
            }
        }
    }

    private fun runEvaluation(explicit: Boolean) {
        if (mutableState.value.isApplying) return
        if (evaluating) {
            // An explicit request must never be dropped by an automatic evaluation
            // that is just finishing; queue it instead.
            if (explicit) queuedExplicit = true
            return
        }
        evaluating = true
        mutableState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                evaluate(explicit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(message = "Não foi possível avaliar a semana agora. Tente de novo em instantes.")
                }
            } finally {
                evaluating = false
                if (queuedExplicit) {
                    queuedExplicit = false
                    runEvaluation(explicit = true)
                } else {
                    mutableState.update { if (it.isLoading) it.copy(isLoading = false) else it }
                }
            }
        }
    }

    private suspend fun reloadVersionAndChange(today: LocalDate) {
        val version = runCatching { plans.getWeekRecord(today.weekStart())?.version }
            .getOrNull()
            ?: mutableState.value.planVersion
        val lastChange = runCatching { loadLastChange(today) }.getOrNull()
            ?: mutableState.value.lastChange
        mutableState.update { it.copy(planVersion = version, lastChange = lastChange) }
    }

    private suspend fun loadLastChange(today: LocalDate): ReplanVersionChangeUi? {
        val weekStart = today.weekStart()
        val record = plans.getWeekRecord(weekStart) ?: return null
        if (record.version <= 1) return null
        val revision = plans.getLatestRevision(weekStart) ?: return null
        val current = plans.getWeek(weekStart) ?: return null
        val changes = diffFuturePlannedSessions(
            before = revision.replacedSessions.filter { it.date.isAfter(today) },
            after = current.sessions.filter {
                it.date.isAfter(today) && it.status == SessionStatus.PLANNED
            },
        )
        if (changes.isEmpty()) return null
        return ReplanVersionChangeUi(
            fromVersion = revision.fromVersion,
            toVersion = revision.toVersion,
            reason = revision.reason,
            changes = changes.map { it.toUi() },
        )
    }
}
