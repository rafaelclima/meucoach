package com.meucoach.app.application

import com.meucoach.app.health.HealthSyncGate
import com.meucoach.core.data.repository.PlanUpgradeOutcome
import com.meucoach.core.data.repository.ReplanApplyOutcome
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.replan.DATA_INCOMPLETE_RULE_ID
import com.meucoach.core.domain.replan.PlanReplanner
import com.meucoach.core.domain.replan.ReplanResult
import com.meucoach.core.domain.replan.ReplanTrigger
import javax.inject.Inject

sealed interface OpeningUpgradeResult {
    /** The week was rewritten; the revision records [trigger]. */
    data class Upgraded(val version: Int, val trigger: ReplanTrigger) : OpeningUpgradeResult

    /** The week is already as it should be; nothing was written. */
    data object NotNeeded : OpeningUpgradeResult

    /** There is no persisted week to upgrade yet. */
    data object NoWeek : OpeningUpgradeResult

    /** The sync has not settled; the opening flow retries on the next emission. */
    data object GateOpen : OpeningUpgradeResult

    data class Failed(val message: String) : OpeningUpgradeResult
}

/**
 * Automatic opening upgrade (ADR 0048 §2.1–§2.2, FR-409).
 *
 * Unlike the manual replan flow — where the user accepts a suggestion — the
 * two system triggers apply themselves on opening and are recorded in
 * `weekly_plan_revisions`: `ENGINE_UPGRADE` goes through the versioned
 * `applyUpgrade` (which bumps `generatorVersion`) and `DATA_READY` through
 * `applyReplan` (same engine, complete data). Any other suggestion stays with
 * the user on the replan screen; readiness values alone never trigger.
 */
class UpgradeWeekOnOpening @Inject constructor(
    private val contexts: ReplanContextBuilder,
    private val plans: TrainingPlanRepository,
) {
    private val replanner = PlanReplanner()

    suspend operator fun invoke(gate: HealthSyncGate): OpeningUpgradeResult {
        if (gate is HealthSyncGate.Idle || gate is HealthSyncGate.Syncing) {
            return OpeningUpgradeResult.GateOpen
        }
        val context = contexts.build(explicit = false) ?: return OpeningUpgradeResult.NoWeek
        val record = context.record
        val dataComplete = gate is HealthSyncGate.Ready
        val stale = record.plan.engineVersion != WeeklyPlanGenerator.ENGINE_VERSION
        val bornIncomplete = DATA_INCOMPLETE_RULE_ID in record.plan.ruleIds
        if (!stale && !(bornIncomplete && dataComplete)) return OpeningUpgradeResult.NotNeeded
        val input = context.input.copy(
            dataNowComplete = dataComplete,
            allowStructuralQualitySlot = true,
        )
        return when (val result = replanner.evaluate(input)) {
            is ReplanResult.Suggest -> when (result.suggestion.trigger) {
                ReplanTrigger.ENGINE_UPGRADE -> when (
                    val outcome = plans.applyUpgrade(
                        candidate = result.candidate,
                        today = context.today,
                        targetVersion = WeeklyPlanGenerator.ENGINE_VERSION,
                        experience = context.input.profile.experience,
                    )
                ) {
                    is PlanUpgradeOutcome.Applied ->
                        OpeningUpgradeResult.Upgraded(outcome.version, ReplanTrigger.ENGINE_UPGRADE)

                    is PlanUpgradeOutcome.Skipped -> OpeningUpgradeResult.NotNeeded

                    is PlanUpgradeOutcome.Failed -> OpeningUpgradeResult.Failed(outcome.message)
                }

                ReplanTrigger.DATA_READY -> when (
                    val outcome = plans.applyReplan(
                        suggestion = result.suggestion,
                        candidate = result.candidate,
                        today = context.today,
                        currentInputsHash = PlanReplanner.inputsHash(input),
                    )
                ) {
                    is ReplanApplyOutcome.Applied ->
                        OpeningUpgradeResult.Upgraded(outcome.version, ReplanTrigger.DATA_READY)

                    is ReplanApplyOutcome.Failed -> OpeningUpgradeResult.Failed(outcome.message)
                }

                else -> OpeningUpgradeResult.NotNeeded
            }

            is ReplanResult.NoChange -> OpeningUpgradeResult.NotNeeded
        }
    }
}
