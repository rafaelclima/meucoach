package com.meucoach.app.application

import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.domain.load.ActivitySourceStatus
import com.meucoach.core.domain.load.LoadActivity
import com.meucoach.core.domain.load.LoadExecution
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.load.TrainingLoadEngine
import com.meucoach.core.domain.readiness.DailyMetricsReadinessBuilder
import com.meucoach.core.domain.readiness.ReadinessCalculator
import com.meucoach.core.domain.readiness.ReadinessHistoryCalculator
import com.meucoach.core.domain.readiness.ReadinessHistoryDay
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.domain.readiness.toSubjectiveCheckIn
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.SessionExecution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Builds the training load snapshot used by the plan, the prescription and the
 * replanner. It lives in the application layer so use cases never depend on a
 * Compose screen to read the same signals.
 *
 * Executions with a perceived effort (RPE) feed the subjective tier of the
 * series (ADR 0039) and keep the source fresh even without an import. The
 * engine still prefers TRIMP/rTSS when the linked activity has them — sRPE only
 * fills days without an objective metric (R-LOAD-01).
 */
internal fun buildLoadSnapshot(
    activities: List<ActivitySummary>,
    profile: AthleteProfile,
    today: LocalDate,
    sourceStatus: ActivitySourceStatus,
    executions: List<SessionExecution> = emptyList(),
): LoadSnapshot {
    val zone = runCatching { java.time.ZoneId.of(profile.timezone) }.getOrNull()
        ?: java.time.ZoneId.systemDefault()
    val loadExecutions = executions.mapNotNull { execution ->
        val rpe = execution.rpe ?: return@mapNotNull null
        val date = execution.startedAt?.atZone(zone)?.toLocalDate()
            ?: execution.plannedSessionId?.let { plannedSessionDate(it) }
            ?: return@mapNotNull null
        val durationMin = execution.durationSec / SECONDS_PER_MINUTE
        if (durationMin <= 0) return@mapNotNull null
        LoadExecution(date = date, durationMin = durationMin, rpe = rpe)
    }
    val subjectiveLoggedAt = executions
        .filter { it.rpe != null }
        .maxOfOrNull { it.createdAt }
    return TrainingLoadEngine().calculate(
        activities = activities.map { summary ->
            LoadActivity(
                date = summary.startTime.atZone(zone).toLocalDate(),
                durationSec = summary.durationSec,
                averageHeartRate = summary.averageHeartRate,
                averagePace = summary.averagePace,
                startTime = summary.startTime,
            )
        },
        profile = profile,
        today = today,
        sourceStatus = sourceStatus.copy(
            lastSubjectiveLogAt = listOfNotNull(
                sourceStatus.lastSubjectiveLogAt,
                subjectiveLoggedAt,
            ).maxOrNull(),
        ),
        executions = loadExecutions,
    )
}

/** `plannedSessionId` is `"$weekStart-$date"`; the last ten characters are the session date. */
private fun plannedSessionDate(plannedSessionId: String): LocalDate? =
    runCatching { LocalDate.parse(plannedSessionId.takeLast(10)) }.getOrNull()

private const val SECONDS_PER_MINUTE = 60

/** Readiness derived from the persisted check-in plus the available objective signals. */
internal fun readinessResult(checkIn: DailyCheckIn?, metrics: List<DailyMetrics>, today: LocalDate): ReadinessResult {
    val inputs = DailyMetricsReadinessBuilder.build(metrics, today)
    val signals = inputs.signals.copy(
        illnessOrInjury = inputs.signals.illnessOrInjury || checkIn?.illnessOrInjury == true,
    )
    return ReadinessCalculator.calculate(
        checkIn = checkIn?.toSubjectiveCheckIn(),
        sleep = inputs.sleep,
        hrv = inputs.hrv,
        restingHeartRate = inputs.restingHeartRate,
        signals = signals,
    )
}

/**
 * Single application-layer source for the readiness and load signals shared by
 * Hoje, Tendências, the tutorial and the replanner. Keeping the reads here means
 * every surface feeds the same load series — including subjective executions
 * (ADR 0039) — instead of each one assembling its own.
 */
class TrainingSignalSource @Inject constructor(
    private val checkIns: CheckInRepository,
    private val metrics: DailyMetricsRepository,
    private val activities: ActivityRepository,
    private val executions: SessionExecutionRepository,
) {
    /**
     * Reactive read of the same persisted signals used by [snapshot]. The
     * app-wide day authority and the Coach cards consume this instead of each
     * one assembling its own combination.
     */
    fun observeSignals(): Flow<PersistedTrainingSignals> = combine(
        checkIns.observeAll(),
        metrics.observeAll(),
        activities.observeActivitySummaries(),
        activities.observeLastImportedAt(),
        executions.observeAll(),
    ) { checkIns, metrics, summaries, lastImportedAt, executions ->
        PersistedTrainingSignals(
            checkIns = checkIns,
            metrics = metrics,
            activities = summaries,
            lastImportedAt = lastImportedAt,
            executions = executions,
        )
    }.distinctUntilChanged()

    suspend fun snapshot(profile: AthleteProfile, today: LocalDate, checkedAt: Instant): TrainingSignals {
        val persisted = PersistedTrainingSignals(
            checkIns = checkIns.getAll(),
            metrics = metrics.getAll(),
            activities = activities.listActivitySummaries(),
            lastImportedAt = activities.lastImportedAt(),
            executions = executions.listAll(),
        )
        return snapshot(persisted, profile, today, checkedAt)
    }

    /**
     * Same snapshot from one already-observed read. The Coach cards share a
     * single reactive emission instead of each repository being read again.
     */
    fun snapshot(
        persisted: PersistedTrainingSignals,
        profile: AthleteProfile,
        today: LocalDate,
        checkedAt: Instant,
    ): TrainingSignals {
        val checkIn = persisted.checkInOn(today)
        return TrainingSignals(
            checkIn = checkIn,
            metrics = persisted.metrics,
            activities = persisted.activities,
            readiness = readinessResult(checkIn, persisted.metrics, today),
            load = buildLoadSnapshot(
                activities = persisted.activities,
                profile = profile,
                today = today,
                sourceStatus = ActivitySourceStatus(
                    lastImportedAt = persisted.lastImportedAt,
                    checkedAt = checkedAt,
                    firstActivityAt = persisted.activities.minOfOrNull { it.startTime },
                ),
                executions = persisted.executions,
            ),
        )
    }

    suspend fun allCheckIns(): List<DailyCheckIn> = checkIns.getAll()

    suspend fun allMetrics(): List<DailyMetrics> = metrics.getAll()

    suspend fun readinessHistory(today: LocalDate, days: Int): List<ReadinessHistoryDay> =
        ReadinessHistoryCalculator().calculate(
            checkIns = checkIns.getAll(),
            metrics = metrics.getAll(),
            today = today,
            days = days,
        )
}

/**
 * One consistent read of the persisted inputs shared by the Coach cards
 * (`2026-11-19`, Bloco B). It is a value object, not a decision: every field is
 * what Room already holds, and consuming it never writes training data.
 */
data class PersistedTrainingSignals(
    val checkIns: List<DailyCheckIn>,
    val metrics: List<DailyMetrics>,
    val activities: List<ActivitySummary>,
    val lastImportedAt: Instant?,
    val executions: List<SessionExecution>,
) {
    fun checkInOn(date: LocalDate): DailyCheckIn? = checkIns.firstOrNull { it.date == date }
}

/** The persisted signals of one day, read once for every surface that needs them. */
data class TrainingSignals(
    val checkIn: DailyCheckIn?,
    val metrics: List<DailyMetrics>,
    val activities: List<ActivitySummary>,
    val readiness: ReadinessResult,
    val load: LoadSnapshot,
)
