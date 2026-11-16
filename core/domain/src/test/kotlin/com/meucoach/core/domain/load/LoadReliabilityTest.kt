package com.meucoach.core.domain.load

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * ADR 0046: an incomplete 28-day window is "sem dado", never rest. The ACWR
 * may be shown as "em calibração", but it only vetoes quality or releases
 * progression when [LoadSnapshot.acwrReliable] is true.
 */
class LoadReliabilityTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val engine = TrainingLoadEngine()

    @Test
    fun `pre-history is missing data and cold start does not veto quality`() {
        val today14 = today.minusDays(20)
        val loadDays = listOf(0L, 2L, 4L, 6L, 8L, 10L, 17L, 19L)
            .map { offset -> activity(today14.plusDays(offset)) }
        val snapshot = engine.calculate(
            activities = loadDays,
            profile = profile(),
            today = today,
            sourceStatus = source(
                firstActivityAt = today14.atStartOfDay(zone).toInstant(),
            ),
            days = 28,
        )

        assertThat(snapshot.coverage.daysBeforeHistory).isEqualTo(7)
        assertThat(snapshot.coverage.daysWithLoad).isEqualTo(8)
        assertThat(snapshot.coverage.daysWithoutActivity).isEqualTo(13)
        assertThat(snapshot.coverage.daysWithoutData).isEqualTo(7)
        assertThat(snapshot.historyDays).isEqualTo(21)
        assertThat(snapshot.baselineDays).isEqualTo(8)
        assertThat(snapshot.acwr).isNotNull()
        assertThat(snapshot.dataState).isEqualTo(LoadDataState.KNOWN)
        assertThat(snapshot.acwrReliable).isFalse()
        assertThat(snapshot.acwrHigh).isFalse()
        // G-LOAD-02: the unstable ratio does not veto the opportunity.
        assertThat(snapshot.permitsQuality).isTrue()
        assertThat(snapshot.permitsProgression).isFalse()
    }

    @Test
    fun `a fully covered window makes the ratio reliable`() {
        val snapshot = engine.calculate(
            activities = (0L until 12L).map { index ->
                activity(today.minusDays(27L - index * 2L))
            },
            profile = profile(),
            today = today,
            sourceStatus = source(
                firstActivityAt = today.minusDays(40).atStartOfDay(zone).toInstant(),
            ),
            days = 28,
        )

        assertThat(snapshot.coverage.daysBeforeHistory).isZero()
        assertThat(snapshot.coverage.daysWithLoad).isEqualTo(12)
        assertThat(snapshot.coverage.daysWithoutData).isZero()
        assertThat(snapshot.coverage.daysWithoutActivity).isEqualTo(16)
        assertThat(snapshot.historyDays).isEqualTo(28)
        assertThat(snapshot.baselineDays).isEqualTo(12)
        assertThat(snapshot.acwrReliable).isTrue()
        assertThat(snapshot.strainReliable).isTrue()
    }

    @Test
    fun `an activity without a usable metric stays missing data`() {
        val snapshot = engine.calculate(
            activities = (0L until 8L).map { offset -> activity(today.minusDays(offset)) } +
                LoadActivity(date = today.minusDays(20), durationSec = 1_800),
            profile = profile(),
            today = today,
            sourceStatus = source(
                firstActivityAt = today.minusDays(40).atStartOfDay(zone).toInstant(),
            ),
            days = 28,
        )

        assertThat(snapshot.coverage.daysBeforeHistory).isZero()
        assertThat(snapshot.coverage.daysWithLoad).isEqualTo(8)
        assertThat(snapshot.coverage.daysWithoutData).isEqualTo(1)
        assertThat(snapshot.acwrReliable).isFalse()
    }

    @Test
    fun `subjective days keep the window from being reliable`() {
        val snapshot = engine.calculate(
            activities = (0L until 8L).map { offset -> activity(today.minusDays(offset)) },
            executions = listOf(LoadExecution(date = today.minusDays(10), durationMin = 40, rpe = 5)),
            profile = profile(),
            today = today,
            sourceStatus = source(
                firstActivityAt = today.minusDays(40).atStartOfDay(zone).toInstant(),
            ),
            days = 28,
        )

        assertThat(snapshot.subjectiveDaysInWindow).isEqualTo(1)
        assertThat(snapshot.baselineDays).isEqualTo(8)
        assertThat(snapshot.acwrReliable).isFalse()
        assertThat(snapshot.permitsProgression).isFalse()
    }

    @Test
    fun `a stale source is never reliable`() {
        val snapshot = engine.calculate(
            activities = (0L until 28L).map { offset -> activity(today.minusDays(offset)) },
            profile = profile(),
            today = today,
            sourceStatus = source(
                fresh = false,
                firstActivityAt = today.minusDays(40).atStartOfDay(zone).toInstant(),
            ),
            days = 28,
        )

        assertThat(snapshot.dataState).isEqualTo(LoadDataState.STALE)
        assertThat(snapshot.acwr).isNull()
        assertThat(snapshot.acwrReliable).isFalse()
        assertThat(snapshot.historyDays).isEqualTo(28)
    }

    @Test
    fun `no history at all is missing data rather than observed rest`() {
        val snapshot = engine.calculate(
            activities = emptyList(),
            profile = profile(),
            today = today,
            sourceStatus = source(firstActivityAt = null),
            days = 28,
        )

        assertThat(snapshot.coverage.daysBeforeHistory).isEqualTo(28)
        assertThat(snapshot.coverage.daysWithoutActivity).isZero()
        assertThat(snapshot.coverage.daysWithoutData).isEqualTo(28)
        assertThat(snapshot.historyDays).isZero()
        assertThat(snapshot.acwrReliable).isFalse()
        assertThat(snapshot.permitsQuality).isFalse()
    }

    @Test
    fun `derives the first history day when the source omits it`() {
        val snapshot = engine.calculate(
            activities = (0L until 8L).map { offset -> activity(today.minusDays(offset)) },
            profile = profile(),
            today = today,
            sourceStatus = source(firstActivityAt = null),
            days = 28,
        )

        assertThat(snapshot.coverage.daysBeforeHistory).isEqualTo(20)
        assertThat(snapshot.historyDays).isEqualTo(8)
        assertThat(snapshot.acwrReliable).isFalse()
    }

    private fun activity(date: LocalDate) = LoadActivity(
        date = date,
        durationSec = 3_600,
        averagePace = Pace.fromSecondsPerKm(360),
    )

    private fun source(fresh: Boolean = true, firstActivityAt: Instant?) = ActivitySourceStatus(
        lastImportedAt = if (fresh) {
            Instant.parse("2026-10-29T10:00:00Z")
        } else {
            Instant.parse("2026-09-01T10:00:00Z")
        },
        checkedAt = Instant.parse("2026-10-30T10:00:00Z"),
        firstActivityAt = firstActivityAt,
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        thresholdPace = Pace.fromSecondsPerKm(360),
        timezone = "America/Sao_Paulo",
    )
}
