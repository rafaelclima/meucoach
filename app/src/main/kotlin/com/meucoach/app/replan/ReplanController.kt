package com.meucoach.app.replan

import com.meucoach.app.application.ApplyReplanRequest
import com.meucoach.app.application.ApplyReplanResult
import com.meucoach.app.application.EvaluateReplanResult
import com.meucoach.app.application.UndoReplanResult
import com.meucoach.app.application.diffFuturePlannedSessions
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.app.ui.replan.LessTimeChange
import com.meucoach.app.ui.replan.LessTimeDayUi
import com.meucoach.app.ui.replan.MissedSessionUi
import com.meucoach.app.ui.replan.ReplanUiState
import com.meucoach.app.ui.replan.ReplanVersionChangeUi
import com.meucoach.app.ui.replan.toUi
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.replan.PlanUpgrade
import com.meucoach.core.domain.replan.ReplanIntent
import com.meucoach.core.domain.replan.ReplanTrigger
import com.meucoach.core.domain.replan.TemporaryAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Shared acceptance machine for replanning (Today, Perfil and Coach).
 *
 * Every training decision comes from `EvaluateReplan`/`ApplyReplan`/`UndoReplan`;
 * this controller only owns the presentation state, the stale handoff and the
 * user preference store. Screens must not duplicate the accept flow.
 */
class ReplanController(
    private val operations: ReplanOperations,
    private val plans: TrainingPlanRepository,
    private val profiles: ProfileRepository,
    private val store: ReplanSuggestionStore,
    private val profileResults: ProfilePlanEvaluationStore,
    private val timeProvider: TimeProvider,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ReplanUiState())
    val uiState: StateFlow<ReplanUiState> = mutableState.asStateFlow()

    private var pending: EvaluateReplanResult.Suggestion? = null
    private val queue = ReplanRequestQueue()

    /** Called when a screen becomes visible; never surfaces an explicit-only message. */
    fun refresh() = runEvaluation(explicit = false)

    /**
     * Called when Perfil becomes visible. It prefers the result produced right
     * after a profile/calibration save (including a one-shot `NoChange`/limit
     * reason) and falls back to a fresh automatic evaluation.
     */
    fun refreshForProfile() {
        val stored = profileResults.consume()
        val today = timeProvider.currentDate()
        scope.launch {
            val currentVersion = plans.getWeekRecord(today.weekStart())?.version
            val stillCurrent = stored !is EvaluateReplanResult.Suggestion ||
                currentVersion == stored.suggestion.expectedVersion
            if (stored != null && stillCurrent) {
                mutableState.update { it.copy(isLoading = true) }
                present(
                    result = stored,
                    today = today,
                    explicit = false,
                    surfaceNoChange = true,
                )
            } else {
                runEvaluation(explicit = false, surfaceNoChange = false)
            }
        }
    }

    /** Explicit T3 request: ignores suppression and reports `NoChange` reasons. */
    fun replanNow() {
        dismissIntent()
        runEvaluation(explicit = true)
    }

    /**
     * Opens the lost-session picker. The eligible list is the replanner's own T1
     * rule, so the picker can never offer an ineligible day.
     */
    fun requestMissedSession() {
        scope.launch {
            mutableState.update { it.copy(isLoading = true) }
            try {
                val candidates = operations.evaluate.missedCandidates()
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        missedSessions = candidates.map { session ->
                            MissedSessionUi(
                                date = session.date,
                                title = session.title,
                                durationMin = session.durationMin,
                            )
                        },
                        lessTimeDays = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.d("Replan", "missed candidates failed: ${error.message}")
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        message = "Não foi possível listar os treinos agora. Tente de novo em instantes.",
                    )
                }
            }
        }
    }

    /** Evaluates with the specific lost session; a pick below T1 still forces T1. */
    fun replanMissed(date: LocalDate) {
        dismissIntent()
        runEvaluation(explicit = true, intent = ReplanIntent(requestedMissedDate = date))
    }

    /** Opens the temporary availability picker with the persisted future days. */
    fun requestLessTime() {
        scope.launch {
            mutableState.update { it.copy(isLoading = true) }
            try {
                val today = timeProvider.currentDate()
                val profile = profiles.getProfile()
                if (profile == null) {
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            message = "Ainda não há um perfil para ajustar a semana.",
                        )
                    }
                    return@launch
                }
                val weekStart = today.weekStart()
                val days = (1L..6L)
                    .map { weekStart.plusDays(it) }
                    .filter { it.isAfter(today) }
                    .mapNotNull { date ->
                        profile.weeklyAvailability.minutesByDay[date.dayOfWeek]
                            ?.let { LessTimeDayUi(date = date, currentMinutes = it) }
                    }
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        lessTimeDays = days,
                        missedSessions = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.d("Replan", "less-time days failed: ${error.message}")
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        message = "Não foi possível abrir os dias agora. Tente de novo em instantes.",
                    )
                }
            }
        }
    }

    /**
     * Evaluates with temporary changes for one or more future days (`0` removes
     * the day). The override never touches the profile: it only constrains this
     * evaluation and its acceptance.
     */
    fun replanLessTime(changes: List<LessTimeChange>) {
        scope.launch {
            val today = timeProvider.currentDate()
            val weekStart = today.weekStart()
            val profile = profiles.getProfile()
            val reason = when {
                profile == null -> "Ainda não há um perfil para ajustar a semana."

                changes.isEmpty() -> "Escolha pelo menos um dia para ajustar."

                changes.map { it.date }.distinct().size != changes.size ->
                    "Escolha cada dia apenas uma vez."

                changes.any { !it.date.isAfter(today) || it.date.isAfter(weekStart.plusDays(6)) } ->
                    "Escolha dias futuros desta semana."

                changes.any { profile.weeklyAvailability.minutesByDay[it.date.dayOfWeek] == null } ->
                    "Um dos dias não faz parte da sua disponibilidade."

                changes.any { change ->
                    val current = profile.weeklyAvailability.minutesByDay
                        .getValue(change.date.dayOfWeek)
                    change.minutes !in 0 until current
                } -> "A disponibilidade temporária só pode diminuir o tempo do dia; use 0 para remover o dia."

                else -> null
            }
            if (reason != null) {
                mutableState.update { it.copy(message = reason) }
                return@launch
            }
            dismissIntent()
            runEvaluation(
                explicit = true,
                intent = ReplanIntent(
                    temporaryAvailability = changes
                        .sortedBy { it.date }
                        .map { TemporaryAvailability(date = it.date, minutes = it.minutes) },
                ),
            )
        }
    }

    /** Closes the intent pickers without evaluating or remembering a decline. */
    fun dismissIntent() {
        mutableState.update { it.copy(missedSessions = null, lessTimeDays = null) }
    }

    fun accept() {
        val suggestion = pending ?: return
        if (mutableState.value.isApplying) return
        mutableState.update { it.copy(isApplying = true) }
        scope.launch {
            val today = timeProvider.currentDate()
            try {
                val result = operations.apply(
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
                        presentStale(result)
                    }

                    is ApplyReplanResult.Rejected -> mutableState.update {
                        it.copy(isApplying = false, message = result.message)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.d("Replan", "accept failed: ${error.message}")
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
        // ADR 0037: recovery proposals are remembered per week+event, so the same
        // change is not offered again every day of the window.
        suggestion.suggestion.recoveryEventId?.let { eventId ->
            store.declineRecovery(suggestion.suggestion.weekStart, eventId)
        }
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

    /** Hides a proposal without remembering a decline. */
    fun dismissSuggestion() {
        pending = null
        mutableState.update { it.copy(suggestion = null) }
    }

    /**
     * Reverts the last accepted revision through `UndoReplan`. The undo is a new
     * version; it never rewrites the revision history (ADR 0028).
     */
    fun undo() {
        val current = mutableState.value
        if (current.isApplying || current.isUndoing) return
        mutableState.update { it.copy(isUndoing = true) }
        scope.launch {
            val today = timeProvider.currentDate()
            val weekStart = today.weekStart()
            try {
                when (val result = operations.undo(weekStart, today, current.planVersion)) {
                    is UndoReplanResult.Applied -> {
                        reloadVersionAndChange(today)
                        mutableState.update {
                            it.copy(
                                isUndoing = false,
                                suggestion = null,
                                message = "Ajuste desfeito. O resto da semana voltou ao que era.",
                            )
                        }
                    }

                    is UndoReplanResult.Stale -> {
                        reloadVersionAndChange(today)
                        mutableState.update { it.copy(isUndoing = false, message = result.message) }
                    }

                    is UndoReplanResult.Rejected -> {
                        reloadVersionAndChange(today)
                        mutableState.update { it.copy(isUndoing = false, message = result.message) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.d("Replan", "undo failed: ${error.message}")
                mutableState.update {
                    it.copy(
                        isUndoing = false,
                        message = "Não foi possível desfazer agora. Tente de novo em instantes.",
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private suspend fun presentStale(result: ApplyReplanResult.Stale) {
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
                it.copy(isApplying = false, suggestion = null, message = fresh.reason)
            }
        }
    }

    private suspend fun evaluate(explicit: Boolean, intent: ReplanIntent, surfaceNoChange: Boolean) {
        val today = timeProvider.currentDate()
        val result = operations.evaluate(explicit, intent)
        present(result, today, explicit, surfaceNoChange)
    }

    private suspend fun present(
        result: EvaluateReplanResult,
        today: LocalDate,
        explicit: Boolean,
        surfaceNoChange: Boolean,
    ) {
        val version = plans.getWeekRecord(today.weekStart())?.version ?: 1
        val lastChange = loadLastChange(today)
        val canUndo = loadCanUndo(today, version)
        when (result) {
            is EvaluateReplanResult.Suggestion -> {
                val key = ReplanSuggestionStore.suggestionKey(result.suggestion)
                val recoveryDeclined = result.suggestion.recoveryEventId?.let { eventId ->
                    store.declinedRecovery(result.suggestion.weekStart, eventId)
                } ?: false
                val blocked = !explicit &&
                    (
                        store.isSuppressed(today) ||
                            store.declinedSuggestion(today) == key ||
                            recoveryDeclined
                        )
                pending = result
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        planVersion = result.suggestion.expectedVersion,
                        suggestion = if (blocked) null else result.suggestion.toUi(result.diff),
                        lastChange = lastChange,
                        canUndo = canUndo,
                        message = null,
                    )
                }
            }

            is EvaluateReplanResult.NoChange -> {
                pending = null
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        planVersion = version,
                        suggestion = null,
                        lastChange = lastChange,
                        canUndo = canUndo,
                        message = if (explicit || surfaceNoChange) result.reason else null,
                    )
                }
            }

            is EvaluateReplanResult.Unavailable -> {
                pending = null
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        planVersion = version,
                        suggestion = null,
                        lastChange = lastChange,
                        canUndo = canUndo,
                        message = if (explicit) result.reason else null,
                    )
                }
            }
        }
    }

    private fun runEvaluation(
        explicit: Boolean,
        intent: ReplanIntent = ReplanIntent(),
        surfaceNoChange: Boolean = explicit,
    ) {
        if (mutableState.value.isApplying || mutableState.value.isUndoing) return
        if (!queue.begin()) {
            // An explicit request must never be dropped by an automatic evaluation
            // that is just finishing; queue it with its exact intent.
            if (explicit) queue.enqueue(intent)
            return
        }
        mutableState.update { it.copy(isLoading = true) }
        scope.launch {
            try {
                evaluate(explicit, intent, surfaceNoChange)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.d("Replan", "evaluation failed: ${error.message}")
                mutableState.update {
                    it.copy(message = "Não foi possível avaliar a semana agora. Tente de novo em instantes.")
                }
            } finally {
                val queuedIntent = queue.finish()
                if (queuedIntent != null) {
                    runEvaluation(explicit = true, intent = queuedIntent)
                } else {
                    mutableState.update { if (it.isLoading) it.copy(isLoading = false) else it }
                }
            }
        }
    }

    private suspend fun reloadVersionAndChange(today: LocalDate) {
        val record = runCatching { plans.getWeekRecord(today.weekStart()) }.getOrNull()
        val version = record?.version ?: mutableState.value.planVersion
        val lastChange = runCatching { loadLastChange(today) }.getOrNull()
            ?: mutableState.value.lastChange
        val canUndo = runCatching { loadCanUndo(today, version) }.getOrDefault(false)
        mutableState.update {
            it.copy(planVersion = version, lastChange = lastChange, canUndo = canUndo)
        }
    }

    private suspend fun loadLastChange(today: LocalDate): ReplanVersionChangeUi? {
        val weekStart = today.weekStart()
        val record = plans.getWeekRecord(weekStart)?.takeIf { it.version > 1 } ?: return null
        val revision = plans.getLatestRevision(weekStart) ?: return null
        val current = plans.getWeek(weekStart) ?: return null
        val changes = diffFuturePlannedSessions(
            before = revision.replacedSessions.filter { it.date.isAfter(today) },
            after = current.sessions.filter {
                it.date.isAfter(today) && it.status == SessionStatus.PLANNED
            },
        )
        return changes.takeIf { it.isNotEmpty() }?.let {
            ReplanVersionChangeUi(
                fromVersion = revision.fromVersion,
                toVersion = revision.toVersion,
                reason = revision.reason,
                changes = it.map { change -> change.toUi() },
            )
        }
    }

    /**
     * Only the latest accepted revision is undoable, and a system-applied
     * revision is not: reverting its sessions while the row keeps the new
     * engine/data marker would leave stale content that the opening path no
     * longer revisits.
     */
    private suspend fun loadCanUndo(today: LocalDate, version: Int): Boolean {
        val weekStart = today.weekStart()
        val revision = plans.getLatestRevision(weekStart) ?: return false
        return revision.toVersion == version &&
            revision.trigger != TrainingPlanRepository.UNDO_TRIGGER &&
            revision.trigger != PlanUpgrade.ENGINE_UPGRADE_TRIGGER &&
            revision.trigger != ReplanTrigger.DATA_READY.name
    }
}
