package com.meucoach.app.application

import com.meucoach.core.data.db.entity.plannedSessionId
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CompletionOutcome
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionCompletionDecision
import com.meucoach.core.domain.prescription.SessionCompletionRules
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.ExecutionSource
import com.meucoach.core.model.SessionExecution
import com.meucoach.core.model.Sport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Request to conclude a planned session. Evidence is mandatory (ADR 0024):
 * either a linked activity or an explicit manual declaration. There is no
 * "complete" without one of them.
 */
data class CompleteSessionRequest(
    val date: LocalDate,
    val today: LocalDate,
    val evidence: CompletionEvidence,
    val activityId: String? = null,
    val durationMin: Int? = null,
    val rpe: Int? = null,
    val prescription: DailyPrescription? = null,
    val illnessOrInjury: Boolean = false,
    /** Profile timezone used to compare the activity local date. */
    val zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
)

sealed interface CompleteSessionResult {
    data class Completed(val execution: SessionExecution, val warning: String? = null) : CompleteSessionResult

    data class Closed(val reason: SessionClosureReason, val message: String) : CompleteSessionResult

    data class Rejected(val message: String) : CompleteSessionResult
}

/**
 * Concludes a planned session only with evidence, or closes a day that cannot
 * be trained. The use case owns date/state validation; the repository owns the
 * transactional 1:1 link and the durable execution row.
 */
class CompleteSession @Inject constructor(
    private val planRepository: TrainingPlanRepository,
    private val activityRepository: ActivityRepository,
    private val executions: SessionExecutionRepository,
    private val raceEvents: RaceEventRepository,
) {
    suspend operator fun invoke(request: CompleteSessionRequest): CompleteSessionResult {
        // A race day has its own lifecycle. Completing a legacy overlapping
        // session would create a second execution decision for the same day.
        if (raceEvents.getForDate(request.date) != null) {
            return CompleteSessionResult.Rejected(
                "Este dia tem uma prova. Conclua, cancele ou registre que não compareceu no card da prova.",
            )
        }
        val weekStart = request.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val plan = planRepository.getWeek(weekStart)
            ?: return CompleteSessionResult.Rejected("Plano da semana não encontrado.")
        val session = plan.sessions.firstOrNull { it.date == request.date }
            ?: return CompleteSessionResult.Rejected("Não há treino planejado para este dia.")
        val sessionId = plannedSessionId(plan.weekStart, session.date)

        when (val decision = SessionCompletionRules.checkDate(session.date, plan.weekStart, request.today)) {
            is SessionCompletionDecision.Blocked -> return CompleteSessionResult.Rejected(decision.message)

            is SessionCompletionDecision.Allowed -> {
                val dateWarning = decision.warning
                when (
                    val state = SessionCompletionRules.checkSessionState(
                        status = session.status,
                        prescriptionIsRest = request.prescription?.isRest == true,
                        illnessOrInjury = request.illnessOrInjury,
                    )
                ) {
                    is SessionCompletionDecision.Blocked -> {
                        val reason = state.closureReason
                            ?: return CompleteSessionResult.Rejected(state.message)
                        executions.closeSession(sessionId, reason)
                        return CompleteSessionResult.Closed(reason, state.message)
                    }

                    is SessionCompletionDecision.Allowed -> Unit
                }

                return when (request.evidence) {
                    CompletionEvidence.ACTIVITY -> completeWithActivity(request, sessionId, session.date, dateWarning)

                    CompletionEvidence.MANUAL -> completeManually(request, sessionId, dateWarning)

                    CompletionEvidence.GUIDED -> CompleteSessionResult.Rejected(
                        "A sessão guiada persistente ainda não está disponível. " +
                            "Vincule a corrida importada ou registre sem registro.",
                    )

                    CompletionEvidence.LEGACY_UNVERIFIED -> CompleteSessionResult.Rejected(
                        "Esse tipo de evidência é apenas histórico e não pode ser criado.",
                    )
                }
            }
        }
    }

    private suspend fun completeWithActivity(
        request: CompleteSessionRequest,
        sessionId: String,
        sessionDate: LocalDate,
        dateWarning: String?,
    ): CompleteSessionResult {
        val activityId = request.activityId
            ?: return CompleteSessionResult.Rejected("Escolha a corrida que corresponde ao treino.")
        val activity = activityRepository.getActivitySummary(activityId)
            ?: return CompleteSessionResult.Rejected("Corrida não encontrada ou na lixeira.")
        if (activity.sport != Sport.RUNNING && activity.sport != Sport.TREADMILL) {
            return CompleteSessionResult.Rejected("Só corridas podem ser vinculadas a um treino.")
        }
        if (activity.startTime.atZone(request.zoneId).toLocalDate() != sessionDate) {
            return CompleteSessionResult.Rejected("Esta corrida pertence a outro dia.")
        }
        return when (
            val outcome = executions.recordActivityExecution(
                plannedSessionId = sessionId,
                activityId = activity.id,
                startedAt = activity.startTime,
                durationSec = activity.durationSec,
                distanceM = activity.distance.meters.takeIf { it > 0.0 },
            )
        ) {
            is CompletionOutcome.Recorded -> CompleteSessionResult.Completed(
                execution = outcome.execution,
                warning = dateWarning,
            )

            is CompletionOutcome.ActivityAlreadyLinked -> CompleteSessionResult.Rejected(outcome.reason)

            is CompletionOutcome.AlreadyCompleted -> CompleteSessionResult.Rejected(outcome.reason)

            is CompletionOutcome.Rejected -> CompleteSessionResult.Rejected(outcome.reason)
        }
    }

    private suspend fun completeManually(
        request: CompleteSessionRequest,
        sessionId: String,
        dateWarning: String?,
    ): CompleteSessionResult {
        when (val decision = SessionCompletionRules.checkManual(request.durationMin, request.rpe)) {
            is SessionCompletionDecision.Blocked -> return CompleteSessionResult.Rejected(decision.message)
            is SessionCompletionDecision.Allowed -> Unit
        }
        val duration = requireNotNull(request.durationMin)
        val rpe = requireNotNull(request.rpe)
        return when (val outcome = executions.recordManualExecution(sessionId, duration, rpe)) {
            is CompletionOutcome.Recorded -> CompleteSessionResult.Completed(
                execution = outcome.execution,
                warning = listOfNotNull(
                    dateWarning,
                    "Registrado sem corrida: não entra em volume, carga ou zonas.",
                ).joinToString(" "),
            )

            is CompletionOutcome.ActivityAlreadyLinked -> CompleteSessionResult.Rejected(outcome.reason)

            is CompletionOutcome.AlreadyCompleted -> CompleteSessionResult.Rejected(outcome.reason)

            is CompletionOutcome.Rejected -> CompleteSessionResult.Rejected(outcome.reason)
        }
    }
}
