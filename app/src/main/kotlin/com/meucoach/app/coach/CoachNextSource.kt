package com.meucoach.app.coach

import com.meucoach.app.application.readinessResult
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.db.entity.plannedSessionId
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.coach.CoachExecution
import com.meucoach.core.domain.coach.CoachNextResolver
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.feedback.ComparisonPlan
import com.meucoach.core.domain.feedback.SessionComparisonCalculator
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.readiness.DailyMetricsReadinessBuilder
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.SessionExecution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Next relevant event for the Coach tab (PRD evolução, F1).
 *
 * Read-only composition of the persisted week, the active race event and the
 * latest persisted prescription of the picked training date. Today also
 * carries the readiness computed from the persisted check-in and metrics; a
 * future day carries `readiness == null` instead of an invented semaphore.
 * It never computes or appends a prescription: a future day without a
 * persisted row surfaces with `prescription == null`, and the day authority
 * stays in `DailyPrescription`/`GetTodayDecision` (ADR 0023).
 */
data class CoachNextContext(
    val subject: CoachNextSubject?,
    val today: LocalDate,
    val hasPlan: Boolean,
    val hasProfile: Boolean,
    val prescription: DailyPrescription? = null,
    /** Prescription of [today], independent of which subject the next card points to. */
    val todayPrescription: DailyPrescription? = null,
    /** What the athlete already did today, when a session/execution exists. */
    val todayExecution: CoachExecution? = null,
    /** Last completed session up to [today], even when it happened on another day. */
    val lastExecution: CoachExecution? = null,
    /** Session linked to [lastExecution], used by the deterministic reference card. */
    val lastSession: PlannedSession? = null,
    /** Persisted prescription of [lastSession], when it had one. */
    val lastPrescription: DailyPrescription? = null,
    val readiness: ReadinessResult? = null,
    val hasCheckIn: Boolean = false,
    val sleepMinutes: Int? = null,
    val todaySession: PlannedSession? = null,
    val importedToday: Boolean = false,
) {
    val isToday: Boolean get() = subject?.date == today

    /** True only when a persisted prescription exists for a training day. */
    val training: CoachNextSubject.Training? get() = subject as? CoachNextSubject.Training
}

interface CoachNextSource {
    fun observeNext(): Flow<CoachNextContext>
}

class RoomCoachNextSource @Inject constructor(
    private val plans: TrainingPlanRepository,
    private val prescriptions: DailyPrescriptionRepository,
    private val raceEvents: RaceEventRepository,
    private val profiles: ProfileRepository,
    private val signals: CoachSignalsSource,
    private val time: TimeProvider,
) : CoachNextSource {
    private data class Draft(
        val today: LocalDate,
        val subject: CoachNextSubject?,
        val hasPlan: Boolean,
        val hasProfile: Boolean,
        val readiness: ReadinessResult,
        val hasCheckIn: Boolean,
        val sleepMinutes: Int?,
        val prescriptionDate: LocalDate?,
        val todaySession: PlannedSession?,
        val todayActivity: ActivitySummary?,
        val todayExecutionRow: SessionExecution?,
        val lastSession: PlannedSession?,
        val lastActivity: ActivitySummary?,
        val lastExecutionRow: SessionExecution?,
        val importedToday: Boolean,
    ) {
        fun toContext(
            prescription: DailyPrescription?,
            todayPrescription: DailyPrescription?,
            lastPrescription: DailyPrescription?,
        ) = CoachNextContext(
            subject = subject,
            today = today,
            hasPlan = hasPlan,
            hasProfile = hasProfile,
            prescription = prescription,
            todayPrescription = todayPrescription,
            todayExecution = toExecution(todaySession, todayActivity, todayExecutionRow, todayPrescription),
            lastExecution = toExecution(lastSession, lastActivity, lastExecutionRow, lastPrescription),
            lastSession = lastSession,
            lastPrescription = lastPrescription,
            readiness = readiness,
            hasCheckIn = hasCheckIn,
            sleepMinutes = sleepMinutes,
            todaySession = todaySession,
            importedToday = importedToday,
        )

        private fun toExecution(
            session: PlannedSession?,
            activity: ActivitySummary?,
            execution: SessionExecution?,
            prescription: DailyPrescription?,
        ): CoachExecution? {
            if (session == null) return null
            val durationSec = activity?.durationSec ?: execution?.durationSec ?: return null
            val comparison = activity?.let { summary ->
                val plan = prescription?.let(ComparisonPlan::from)
                    ?: ComparisonPlan.from(session)
                SessionComparisonCalculator.compare(plan, summary, execution?.rpe)
            }
            return CoachExecution(
                date = session.date,
                durationMin = (durationSec / SECONDS_PER_MINUTE).coerceAtLeast(1),
                distanceKm = activity?.distance?.kilometers?.let { roundToOneDecimal(it) },
                rpe = execution?.rpe,
                evidence = session.completionEvidence,
                comparison = comparison,
            )
        }

        private fun roundToOneDecimal(value: Double): Double = (value * 10.0).roundToInt() / 10.0
    }

    /** A completed session together with the week that owns its persisted id. */
    private data class CompletedSession(val weekStart: LocalDate, val session: PlannedSession)

    /** The most recent completed session that actually has an execution to narrate. */
    private data class ExecutedSession(
        val completed: CompletedSession,
        val row: SessionExecution?,
        val activity: ActivitySummary?,
    )

    private fun PlannedSession.isCompletedThrough(today: LocalDate): Boolean =
        !date.isAfter(today) && (status == SessionStatus.DONE || status == SessionStatus.CLOSED)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeNext(): Flow<CoachNextContext> = time.today
        .map { it to it.weekStart() }
        .distinctUntilChanged()
        .flatMapLatest { (today, weekStart) ->
            combine(
                plans.observeWeek(weekStart),
                plans.observeWeek(weekStart.minusWeeks(1)),
                profiles.observeProfile(),
                signals.observeSignals(),
                combine(raceEvents.observeAll(), time.zoneId) { races, zone -> races to zone },
            ) { plan, previousPlan, profile, persisted, racesAndZone ->
                val (races, zone) = racesAndZone
                val next = CoachNextResolver.resolve(
                    sessions = plan?.sessions.orEmpty(),
                    today = today,
                    races = races,
                )
                val checkIn = persisted.checkInOn(today)
                val dailyMetrics = persisted.metrics
                val inputs = DailyMetricsReadinessBuilder.build(dailyMetrics, today)
                val todaySession = plan?.sessions?.find { it.date == today }
                val todayExecutionRow = if (plan != null && todaySession != null) {
                    persisted.executions.firstOrNull {
                        it.plannedSessionId == plannedSessionId(plan.weekStart, todaySession.date)
                    }
                } else {
                    null
                }
                val todayActivityId = todayExecutionRow?.activityId ?: todaySession?.activityId
                val completedSessions = buildList {
                    plan?.let { current ->
                        current.sessions
                            .filter { it.isCompletedThrough(today) }
                            .forEach { add(CompletedSession(current.weekStart, it)) }
                    }
                    previousPlan?.let { previous ->
                        previous.sessions
                            .filter { it.isCompletedThrough(today) }
                            .forEach { add(CompletedSession(previous.weekStart, it)) }
                    }
                }
                val lastExecuted = completedSessions
                    .mapNotNull { completed ->
                        val row = persisted.executions.firstOrNull {
                            it.plannedSessionId == plannedSessionId(completed.weekStart, completed.session.date)
                        }
                        val activityId = row?.activityId ?: completed.session.activityId
                        val activity = persisted.activities.firstOrNull { it.id == activityId }
                        val durationSec = activity?.durationSec ?: row?.durationSec
                        if (durationSec == null) null else ExecutedSession(completed, row, activity)
                    }
                    .maxByOrNull { it.completed.session.date }
                val lastSession = lastExecuted?.completed?.session
                val lastExecutionRow = lastExecuted?.row
                Draft(
                    today = today,
                    subject = next?.subject,
                    hasPlan = plan != null,
                    hasProfile = profile != null,
                    readiness = readinessResult(checkIn, dailyMetrics, today),
                    hasCheckIn = checkIn != null,
                    sleepMinutes = inputs.sleep?.durationMin,
                    prescriptionDate = (next?.subject as? CoachNextSubject.Training)?.session?.date,
                    todaySession = todaySession,
                    todayActivity = persisted.activities.firstOrNull { it.id == todayActivityId },
                    todayExecutionRow = todayExecutionRow,
                    lastSession = lastSession,
                    lastActivity = lastExecuted?.activity,
                    lastExecutionRow = lastExecutionRow,
                    importedToday = persisted.lastImportedAt?.atZone(zone)?.toLocalDate() == today,
                )
            }.flatMapLatest { draft ->
                val dates = listOfNotNull(
                    draft.today,
                    draft.prescriptionDate,
                    draft.lastSession?.date,
                ).distinct()
                val flows = dates.map(prescriptions::observeLatest)
                combine(flows) { rows ->
                    val byDate = dates.zip(rows.asList()).toMap()
                    draft.toContext(
                        prescription = draft.prescriptionDate?.let(byDate::get),
                        todayPrescription = byDate[draft.today],
                        lastPrescription = draft.lastSession?.date?.let(byDate::get),
                    )
                }
            }
        }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
