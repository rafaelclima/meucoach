package com.meucoach.app.application

import com.meucoach.core.domain.load.ActivitySourceStatus
import com.meucoach.core.domain.load.LoadDataState
import com.meucoach.core.domain.load.LoadMetric
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Distance
import com.meucoach.core.model.ExecutionSource
import com.meucoach.core.model.Pace
import com.meucoach.core.model.SessionExecution
import com.meucoach.core.model.Sex
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Fatia 2 do ADR 0039: as execuções persistidas chegam ao motor de carga como
 * sRPE, com a data vinda do vínculo (início da corrida) ou do
 * `plannedSessionId` no registro manual, e um RPE recente mantém a fonte fresca.
 */
class TrainingSignalsTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun `a manual execution fills the day as perceived effort`() {
        val snapshot = buildLoadSnapshot(
            activities = emptyList(),
            profile = profile(),
            today = today,
            sourceStatus = source(lastImportedAt = null),
            executions = listOf(manualExecution(rpe = 6, durationSec = 2_400)),
        )

        val day = snapshot.daily.last()
        assertThat(day.date).isEqualTo(today)
        assertThat(day.metric).isEqualTo(LoadMetric.SRPE)
        assertThat(day.load).isCloseTo(240.0, within(1e-6))
        assertThat(snapshot.subjectiveDaysInWindow).isEqualTo(1)
        assertThat(snapshot.objectiveDaysWithLoad).isEqualTo(0)
    }

    @Test
    fun `a linked activity with heart rate keeps TRIMP and ignores the RPE`() {
        val snapshot = buildLoadSnapshot(
            activities = listOf(
                activity(startTime = Instant.parse("2026-10-30T10:00:00Z"), averageHeartRate = 150),
            ),
            profile = profile(),
            today = today,
            sourceStatus = source(lastImportedAt = Instant.parse("2026-10-30T11:00:00Z")),
            executions = listOf(
                linkedExecution(
                    rpe = 8,
                    startedAt = Instant.parse("2026-10-30T10:00:00Z"),
                    durationSec = 3_600,
                ),
            ),
        )

        assertThat(snapshot.daily.last().metric).isEqualTo(LoadMetric.TRIMP)
        assertThat(snapshot.subjectiveDaysInWindow).isEqualTo(0)
    }

    @Test
    fun `a linked activity without objective metric uses the logged RPE`() {
        val snapshot = buildLoadSnapshot(
            activities = listOf(
                activity(startTime = Instant.parse("2026-10-30T10:00:00Z"), averageHeartRate = null),
            ),
            profile = profile(thresholdPace = null),
            today = today,
            sourceStatus = source(lastImportedAt = Instant.parse("2026-10-30T11:00:00Z")),
            executions = listOf(
                linkedExecution(
                    rpe = 6,
                    startedAt = Instant.parse("2026-10-30T10:00:00Z"),
                    durationSec = 2_400,
                ),
            ),
        )

        val day = snapshot.daily.last()
        assertThat(day.metric).isEqualTo(LoadMetric.SRPE)
        assertThat(day.load).isCloseTo(240.0, within(1e-6))
    }

    @Test
    fun `a recent RPE log keeps the source fresh without an import`() {
        val snapshot = buildLoadSnapshot(
            activities = emptyList(),
            profile = profile(),
            today = today,
            sourceStatus = source(lastImportedAt = Instant.parse("2026-09-01T10:00:00Z")),
            executions = listOf(
                manualExecution(
                    rpe = 5,
                    durationSec = 2_400,
                    createdAt = Instant.parse("2026-10-28T10:00:00Z"),
                ),
            ),
        )

        assertThat(snapshot.coverage.sourceFresh).isTrue()
        assertThat(snapshot.dataState).isNotEqualTo(LoadDataState.STALE)
    }

    @Test
    fun `an execution without RPE does not create load or freshness`() {
        val snapshot = buildLoadSnapshot(
            activities = emptyList(),
            profile = profile(),
            today = today,
            sourceStatus = source(lastImportedAt = null),
            executions = emptyList(),
        )

        assertThat(snapshot.daily.last().metric).isNull()
        assertThat(snapshot.coverage.sourceFresh).isFalse()
        assertThat(snapshot.subjectiveDaysInWindow).isEqualTo(0)
    }

    private fun manualExecution(
        rpe: Int,
        durationSec: Int,
        createdAt: Instant = Instant.parse("2026-10-30T09:00:00Z"),
    ) = SessionExecution(
        id = "execution-1",
        plannedSessionId = "2026-10-26-$today",
        activityId = null,
        startedAt = null,
        endedAt = null,
        durationSec = durationSec,
        rpe = rpe,
        source = ExecutionSource.MANUAL,
        createdAt = createdAt,
    )

    private fun linkedExecution(rpe: Int, startedAt: Instant, durationSec: Int) = SessionExecution(
        id = "execution-2",
        plannedSessionId = "2026-10-26-$today",
        activityId = "activity-1",
        startedAt = startedAt,
        endedAt = startedAt.plusSeconds(durationSec.toLong()),
        durationSec = durationSec,
        rpe = rpe,
        source = ExecutionSource.ACTIVITY,
        createdAt = Instant.parse("2026-10-30T11:00:00Z"),
    )

    private fun activity(startTime: Instant, averageHeartRate: Int?) = ActivitySummary(
        id = "activity-1",
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = startTime,
        durationSec = 3_600,
        distance = Distance(6_000.0),
        averageHeartRate = averageHeartRate,
        averagePace = Pace.fromSecondsPerKm(360),
    )

    private fun source(lastImportedAt: Instant?) = ActivitySourceStatus(
        lastImportedAt = lastImportedAt,
        checkedAt = Instant.parse("2026-10-30T12:00:00Z"),
    )

    private fun profile(thresholdPace: Pace? = Pace.fromSecondsPerKm(360)) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        thresholdPace = thresholdPace,
        timezone = "America/Sao_Paulo",
    )
}
