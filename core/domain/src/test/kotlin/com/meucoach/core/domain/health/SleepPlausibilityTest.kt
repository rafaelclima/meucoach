package com.meucoach.core.domain.health

import com.meucoach.core.domain.readiness.DailyMetricsReadinessBuilder
import com.meucoach.core.domain.readiness.ReadinessCalculator
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.review.WeeklyReviewCalculator
import com.meucoach.core.domain.review.WeeklyReviewObservationKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * C4-36 — readiness and the weekly review ignore an implausible night
 * (ADR 0049 §2.4). A 36-minute sync artifact is absent data, not a short night.
 */
class SleepPlausibilityTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 30)
    private val yesterday = today.minusDays(1)

    @Test
    fun `a partial night never becomes short sleep in readiness`() {
        val days = aggregate(
            session("2026-10-30T02:00:00Z", "2026-10-30T02:36:00Z"),
        )
        val inputs = DailyMetricsReadinessBuilder.build(days, today)
        val readiness = ReadinessCalculator.calculate(
            checkIn = null,
            sleep = inputs.sleep,
            hrv = null,
            restingHeartRate = null,
        )

        assertThat(days.single().sleepDurationMin).isNull()
        assertThat(inputs.sleep).isNull()
        assertThat(readiness.flags).doesNotContain(ReadinessFlag.SHORT_SLEEP)
        assertThat(readiness.qualityEligibleByReadiness).isFalse()
    }

    @Test
    fun `a valid five and a half hour night still blocks quality as short`() {
        val days = aggregate(
            session("2026-10-30T00:00:00Z", "2026-10-30T05:30:00Z"),
        )
        val inputs = DailyMetricsReadinessBuilder.build(days, today)
        val readiness = ReadinessCalculator.calculate(
            checkIn = null,
            sleep = inputs.sleep,
            hrv = null,
            restingHeartRate = null,
        )

        assertThat(inputs.sleep?.durationMin).isEqualTo(330)
        assertThat(readiness.flags).contains(ReadinessFlag.SHORT_SLEEP)
        assertThat(readiness.qualityEligibleByReadiness).isFalse()
    }

    @Test
    fun `the weekly review does not count a suspect night as measured`() {
        val days = aggregate(
            session("2026-10-29T00:00:00Z", "2026-10-29T05:30:00Z"),
            session("2026-10-30T02:00:00Z", "2026-10-30T02:36:00Z"),
        )
        val review = WeeklyReviewCalculator().calculate(
            weekStart = LocalDate.of(2026, 10, 26),
            today = today,
            metrics = days,
        )

        assertThat(days).hasSize(2)
        assertThat(review.sleep?.measuredNights).isEqualTo(1)
        assertThat(review.sleep?.averageMinutes).isEqualTo(330)
        assertThat(review.sleep?.shortNights).isEqualTo(1)
        assertThat(review.observations.map { it.kind }).doesNotContain(
            WeeklyReviewObservationKind.SHORT_SLEEP,
        )
    }

    @Test
    fun `a suspect-only week has no sleep section`() {
        val days = aggregate(
            session("2026-10-30T02:00:00Z", "2026-10-30T02:36:00Z"),
        )
        val review = WeeklyReviewCalculator().calculate(
            weekStart = LocalDate.of(2026, 10, 26),
            today = today,
            metrics = days,
        )

        assertThat(review.sleep).isNull()
        assertThat(review.observations.map { it.kind }).doesNotContain(
            WeeklyReviewObservationKind.SHORT_SLEEP,
        )
    }

    private fun aggregate(vararg sessions: HealthSleepSession) = HealthMetricsAggregator.aggregate(
        data = HealthRawData(sleepSessions = sessions.toList()),
        zoneId = zone,
        from = yesterday,
        to = today,
    )

    private fun session(start: String, end: String) = HealthSleepSession(
        start = Instant.parse(start),
        end = Instant.parse(end),
    )
}
