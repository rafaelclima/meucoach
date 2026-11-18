package com.meucoach.app.application

import com.meucoach.core.data.repository.ReplanApplyFailure
import com.meucoach.core.data.repository.ReplanUndoOutcome
import com.meucoach.core.data.repository.TrainingPlanRepository
import java.time.LocalDate
import javax.inject.Inject

sealed interface UndoReplanResult {
    data class Applied(val version: Int) : UndoReplanResult

    /** The week moved or vanished since the screen read it; nothing was written. */
    data class Stale(val message: String) : UndoReplanResult

    /** The undo is not possible (nothing to undo or a protected session would change). */
    data class Rejected(val message: String) : UndoReplanResult
}

/**
 * Undoes the last accepted revision as a new version. It only maps the
 * repository outcome; the append-only rule lives in
 * `TrainingPlanRepository.undoReplan` (ADR 0028).
 */
class UndoReplan @Inject constructor(private val plans: TrainingPlanRepository) {
    suspend operator fun invoke(weekStart: LocalDate, today: LocalDate, expectedVersion: Int): UndoReplanResult =
        when (val outcome = plans.undoReplan(weekStart, today, expectedVersion)) {
            is ReplanUndoOutcome.Applied -> UndoReplanResult.Applied(outcome.version)

            is ReplanUndoOutcome.Failed -> when (outcome.failure) {
                ReplanApplyFailure.NO_PLAN,
                ReplanApplyFailure.STALE_VERSION,
                ReplanApplyFailure.STALE_INPUTS,
                ReplanApplyFailure.STALE_AVAILABILITY,
                ReplanApplyFailure.STALE_PROFILE,
                -> UndoReplanResult.Stale(outcome.message)

                ReplanApplyFailure.INVALID_CANDIDATE,
                ReplanApplyFailure.PROTECTED_CONFLICT,
                ReplanApplyFailure.NOTHING_TO_UNDO,
                -> UndoReplanResult.Rejected(outcome.message)
            }
        }
}
