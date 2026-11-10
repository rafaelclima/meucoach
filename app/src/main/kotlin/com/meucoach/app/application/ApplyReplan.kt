package com.meucoach.app.application

import com.meucoach.core.data.repository.ReplanApplyFailure
import com.meucoach.core.data.repository.ReplanApplyOutcome
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.replan.PlanReplanner
import com.meucoach.core.domain.replan.ReplanSuggestion
import java.time.LocalDate
import javax.inject.Inject

data class ApplyReplanRequest(
    val suggestion: ReplanSuggestion,
    val candidate: WeeklyPlan,
    val today: LocalDate,
    /** True when the user pressed the explicit replan button. */
    val explicit: Boolean,
)

sealed interface ApplyReplanResult {
    data class Applied(val version: Int) : ApplyReplanResult

    /** The suggestion no longer matches the week; [reevaluated] carries a fresh one. */
    data class Stale(val reevaluated: EvaluateReplanResult) : ApplyReplanResult

    data class Rejected(val message: String) : ApplyReplanResult
}

/**
 * Accepts a suggestion after revalidating it against the current persisted
 * state. If the version or any input changed, the stale candidate is never
 * applied: the week is re-evaluated first so the user sees an updated proposal.
 */
class ApplyReplan @Inject constructor(
    private val plans: TrainingPlanRepository,
    private val contexts: ReplanContextBuilder,
    private val evaluateReplan: EvaluateReplan,
) {
    suspend operator fun invoke(request: ApplyReplanRequest): ApplyReplanResult {
        val context = contexts.build(request.explicit)
            ?: return stale(request)

        if (context.today != request.today ||
            context.record.version != request.suggestion.expectedVersion
        ) {
            return stale(request)
        }

        val currentInputsHash = PlanReplanner.inputsHash(context.input)
        if (currentInputsHash != request.suggestion.expectedInputsHash) {
            return stale(request)
        }

        return when (
            val outcome = plans.applyReplan(
                suggestion = request.suggestion,
                candidate = request.candidate,
                today = request.today,
                currentInputsHash = currentInputsHash,
            )
        ) {
            is ReplanApplyOutcome.Applied -> ApplyReplanResult.Applied(outcome.version)

            is ReplanApplyOutcome.Failed -> when (outcome.failure) {
                ReplanApplyFailure.NO_PLAN,
                ReplanApplyFailure.STALE_VERSION,
                ReplanApplyFailure.STALE_INPUTS,
                ReplanApplyFailure.STALE_AVAILABILITY,
                ReplanApplyFailure.STALE_PROFILE,
                -> stale(request)

                ReplanApplyFailure.INVALID_CANDIDATE,
                ReplanApplyFailure.PROTECTED_CONFLICT,
                -> ApplyReplanResult.Rejected(outcome.message)
            }
        }
    }

    private suspend fun stale(request: ApplyReplanRequest): ApplyReplanResult.Stale =
        ApplyReplanResult.Stale(evaluateReplan(request.explicit))
}
