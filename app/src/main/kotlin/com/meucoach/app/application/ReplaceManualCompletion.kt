package com.meucoach.app.application

import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CompletionOutcome
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.SessionExecution
import com.meucoach.core.model.Sport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class ReplaceManualCompletionRequest(
    val date: LocalDate,
    val today: LocalDate,
    val activityId: String,
    /** Profile timezone used to compare the activity local date. */
    val zoneId: ZoneId = ZoneId.systemDefault(),
)

sealed interface ReplaceManualCompletionResult {
    data class Replaced(val execution: SessionExecution) : ReplaceManualCompletionResult

    data class Rejected(val message: String) : ReplaceManualCompletionResult
}

/**
 * Corrects an explicit "fiz sem registro" with the real imported run.
 *
 * Only `DONE` + `MANUAL` can be corrected. An `ACTIVITY` completion is already
 * verified, `GUIDED` has its own execution and `LEGACY_UNVERIFIED` must stay
 * untouched. The replacement is transactional and user-confirmed.
 */
class ReplaceManualCompletion @Inject constructor(
    private val plans: TrainingPlanRepository,
    private val activities: ActivityRepository,
    private val executions: SessionExecutionRepository,
) {
    suspend operator fun invoke(request: ReplaceManualCompletionRequest): ReplaceManualCompletionResult {
        val weekStart = request.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val plan = plans.getWeek(weekStart)
            ?: return ReplaceManualCompletionResult.Rejected("Plano da semana não encontrado.")
        val session = plan.sessions.firstOrNull { it.date == request.date }
            ?: return ReplaceManualCompletionResult.Rejected("Não há treino planejado para este dia.")
        if (request.date.isAfter(request.today)) {
            return ReplaceManualCompletionResult.Rejected("Não é possível corrigir um dia futuro.")
        }
        if (session.status != SessionStatus.DONE ||
            session.completionEvidence != CompletionEvidence.MANUAL
        ) {
            return ReplaceManualCompletionResult.Rejected(
                "Este treino não tem um registro manual para substituir.",
            )
        }

        val activity = activities.getActivitySummary(request.activityId)
            ?: return ReplaceManualCompletionResult.Rejected("Corrida não encontrada ou na lixeira.")
        if (activity.sport != Sport.RUNNING && activity.sport != Sport.TREADMILL) {
            return ReplaceManualCompletionResult.Rejected("Só corridas podem ser vinculadas a um treino.")
        }
        if (activity.startTime.atZone(request.zoneId).toLocalDate() != session.date) {
            return ReplaceManualCompletionResult.Rejected("Esta corrida pertence a outro dia.")
        }

        return when (
            val outcome = executions.replaceManualWithActivity(
                plannedSessionId = com.meucoach.core.data.db.entity.plannedSessionId(plan.weekStart, session.date),
                activityId = activity.id,
                startedAt = activity.startTime,
                durationSec = activity.durationSec,
                distanceM = activity.distance.meters.takeIf { it > 0.0 },
            )
        ) {
            is CompletionOutcome.Recorded -> ReplaceManualCompletionResult.Replaced(outcome.execution)

            is CompletionOutcome.ActivityAlreadyLinked ->
                ReplaceManualCompletionResult.Rejected(outcome.reason)

            is CompletionOutcome.AlreadyCompleted ->
                ReplaceManualCompletionResult.Rejected(outcome.reason)

            is CompletionOutcome.Rejected -> ReplaceManualCompletionResult.Rejected(outcome.reason)
        }
    }
}
