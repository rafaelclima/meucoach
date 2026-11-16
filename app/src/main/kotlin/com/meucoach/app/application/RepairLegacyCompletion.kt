package com.meucoach.app.application

import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CompletionOutcome
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionCompletionDecision
import com.meucoach.core.domain.prescription.SessionCompletionRules
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.SessionExecution
import com.meucoach.core.model.Sport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class RepairLegacyCompletionRequest(
    val date: LocalDate,
    val today: LocalDate,
    val activityId: String,
    /** The confirmation the UI collected before calling the repair. */
    val confirmed: Boolean,
    /** Profile timezone used to compare the activity local date. */
    val zoneId: ZoneId = ZoneId.systemDefault(),
)

sealed interface RepairLegacyCompletionResult {
    data class Repaired(val execution: SessionExecution) : RepairLegacyCompletionResult

    data class Rejected(val message: String) : RepairLegacyCompletionResult
}

/**
 * Repairs a same-day `LEGACY_UNVERIFIED` completion with the imported run.
 *
 * Only the current day can be repaired; migrated history from older days keeps
 * its unverified evidence. The user must confirm that the run is the planned
 * session (ADR 0050 §2.4). The repository owns the transactional 1:1 link and
 * creates the missing execution.
 */
class RepairLegacyCompletion @Inject constructor(
    private val plans: TrainingPlanRepository,
    private val activities: ActivityRepository,
    private val executions: SessionExecutionRepository,
) {
    suspend operator fun invoke(request: RepairLegacyCompletionRequest): RepairLegacyCompletionResult {
        val weekStart = request.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val plan = plans.getWeek(weekStart)
        val session = plan?.sessions?.firstOrNull { it.date == request.date }
        val activity = session?.let { activities.getActivitySummary(request.activityId) }
        val decision = if (session != null && activity != null) {
            SessionCompletionRules.checkLegacyRepair(
                session = session,
                activityDate = activity.startTime.atZone(request.zoneId).toLocalDate(),
                today = request.today,
                confirmed = request.confirmed,
            )
        } else {
            null
        }
        return when {
            plan == null -> RepairLegacyCompletionResult.Rejected("Plano da semana não encontrado.")

            session == null -> RepairLegacyCompletionResult.Rejected("Não há treino planejado para este dia.")

            activity == null -> RepairLegacyCompletionResult.Rejected("Corrida não encontrada ou na lixeira.")

            activity.sport != Sport.RUNNING && activity.sport != Sport.TREADMILL ->
                RepairLegacyCompletionResult.Rejected("Só corridas podem ser vinculadas a um treino.")

            decision is SessionCompletionDecision.Blocked ->
                RepairLegacyCompletionResult.Rejected(decision.message)

            else -> repair(plan, session, activity)
        }
    }

    private suspend fun repair(
        plan: WeeklyPlan,
        session: PlannedSession,
        activity: ActivitySummary,
    ): RepairLegacyCompletionResult = when (
        val outcome = executions.repairLegacyWithActivity(
            plannedSessionId = com.meucoach.core.data.db.entity.plannedSessionId(plan.weekStart, session.date),
            activityId = activity.id,
            startedAt = activity.startTime,
            durationSec = activity.durationSec,
            distanceM = activity.distance.meters.takeIf { it > 0.0 },
        )
    ) {
        is CompletionOutcome.Recorded -> RepairLegacyCompletionResult.Repaired(outcome.execution)

        is CompletionOutcome.ActivityAlreadyLinked ->
            RepairLegacyCompletionResult.Rejected(outcome.reason)

        is CompletionOutcome.AlreadyCompleted ->
            RepairLegacyCompletionResult.Rejected(outcome.reason)

        is CompletionOutcome.Rejected -> RepairLegacyCompletionResult.Rejected(outcome.reason)
    }
}
