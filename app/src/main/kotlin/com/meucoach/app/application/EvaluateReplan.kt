package com.meucoach.app.application

import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.replan.PlanReplanner
import com.meucoach.core.domain.replan.ReplanResult
import com.meucoach.core.domain.replan.ReplanSuggestion
import javax.inject.Inject

sealed interface EvaluateReplanResult {
    /** A revision the user may accept; [diff] is only the future that changes. */
    data class Suggestion(val suggestion: ReplanSuggestion, val candidate: WeeklyPlan, val diff: List<ReplanChange>) :
        EvaluateReplanResult

    /** The week is already as it should be, or a guardrail blocks a revision. */
    data class NoChange(val reason: String) : EvaluateReplanResult

    /** There is no persisted week/profile to evaluate yet. */
    data class Unavailable(val reason: String) : EvaluateReplanResult
}

/**
 * App-facing evaluation of the current week. It only gathers the persisted
 * state and delegates the decision to the pure `PlanReplanner`; no training
 * rule is repeated here.
 */
class EvaluateReplan @Inject constructor(private val contexts: ReplanContextBuilder) {
    private val replanner = PlanReplanner()

    suspend operator fun invoke(explicit: Boolean): EvaluateReplanResult {
        val context = contexts.build(explicit)
            ?: return EvaluateReplanResult.Unavailable(
                "Ainda não há uma semana planejada para ajustar.",
            )
        return when (val result = replanner.evaluate(context.input)) {
            is ReplanResult.Suggest -> EvaluateReplanResult.Suggestion(
                suggestion = result.suggestion,
                candidate = result.candidate,
                diff = replanDiff(context.record.plan, result.candidate, context.today),
            )

            is ReplanResult.NoChange -> EvaluateReplanResult.NoChange(result.reason)
        }
    }
}
