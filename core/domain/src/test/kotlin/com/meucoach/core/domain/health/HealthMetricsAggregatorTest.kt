package com.meucoach.core.domain.health

import com.meucoach.core.model.DailyMetricsSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthMetricsAggregatorTest {
    private val zone = ZoneId.of("UTC")
    private val from = LocalDate.of(2026, 10, 29)
    private val to = LocalDate.of(2026, 10, 30)

    @Test
    fun `assigns sleep to the local end date and merges overlaps`() {
        val data = HealthRawData(
            sleepSessions = listOf(
                HealthSleepSession(
                    start = Instant.parse("2026-10-29T23:00:00Z"),
                    end = Instant.parse("2026-10-30T02:00:00Z"),
                ),
                HealthSleepSession(
                    start = Instant.parse("2026-10-30T01:00:00Z"),
                    end = Instant.parse("2026-10-30T07:00:00Z"),
                ),
            ),
        )

        val metrics = HealthMetricsAggregator.aggregate(data, zone, from, to).single()

        assertThat(metrics.date).isEqualTo(LocalDate.of(2026, 10, 30))
        assertThat(metrics.sleepDurationMin).isEqualTo(480)
        assertThat(metrics.source).isEqualTo(DailyMetricsSource.HEALTH_CONNECT)
    }

    @Test
    fun `adds disjoint sleep sessions inside the same block`() {
        val data = HealthRawData(
            sleepSessions = listOf(
                HealthSleepSession(
                    Instant.parse("2026-10-30T00:00:00Z"),
                    Instant.parse("2026-10-30T02:00:00Z"),
                ),
                HealthSleepSession(
                    Instant.parse("2026-10-30T03:00:00Z"),
                    Instant.parse("2026-10-30T05:00:00Z"),
                ),
            ),
        )

        val aggregation = HealthMetricsAggregator.aggregateWithDiagnostics(data, zone, from, to)

        assertThat(aggregation.days.single().sleepDurationMin).isEqualTo(240)
        assertThat(aggregation.fragmentedSleepNights).isZero()
    }

    @Test
    fun `a partial session is absent and counted as suspect`() {
        val data = HealthRawData(
            sleepSessions = listOf(
                HealthSleepSession(
                    Instant.parse("2026-10-30T02:00:00Z"),
                    Instant.parse("2026-10-30T02:36:00Z"),
                ),
            ),
        )

        val aggregation = HealthMetricsAggregator.aggregateWithDiagnostics(data, zone, from, to)

        assertThat(aggregation.days.single().sleepDurationMin).isNull()
        assertThat(aggregation.suspectSleepNights).isEqualTo(1)
        assertThat(aggregation.fragmentedSleepNights).isZero()
        assertThat(aggregation.suspectSleepDates).containsExactly(LocalDate.of(2026, 10, 30))
    }

    @Test
    fun `a suspect night does not erase the other metrics of the day`() {
        val data = HealthRawData(
            sleepSessions = listOf(
                HealthSleepSession(
                    Instant.parse("2026-10-30T02:00:00Z"),
                    Instant.parse("2026-10-30T02:36:00Z"),
                ),
            ),
            restingHeartRates = listOf(HealthSample(Instant.parse("2026-10-30T01:00:00Z"), 56.0)),
        )

        val day = HealthMetricsAggregator.aggregate(data, zone, from, to).single()

        assertThat(day.sleepDurationMin).isNull()
        assertThat(day.restingHeartRate).isEqualTo(56.0)
    }

    @Test
    fun `a valid short night stays measured`() {
        val data = HealthRawData(
            sleepSessions = listOf(
                HealthSleepSession(
                    Instant.parse("2026-10-30T00:00:00Z"),
                    Instant.parse("2026-10-30T05:30:00Z"),
                ),
            ),
        )

        val aggregation = HealthMetricsAggregator.aggregateWithDiagnostics(data, zone, from, to)

        assertThat(aggregation.days.single().sleepDurationMin).isEqualTo(330)
        assertThat(aggregation.suspectSleepNights).isZero()
    }

    @Test
    fun `a fragmented night keeps only the largest block`() {
        val data = HealthRawData(
            sleepSessions = listOf(
                HealthSleepSession(
                    Instant.parse("2026-10-29T22:00:00Z"),
                    Instant.parse("2026-10-30T02:00:00Z"),
                ),
                HealthSleepSession(
                    Instant.parse("2026-10-30T05:00:00Z"),
                    Instant.parse("2026-10-30T09:00:00Z"),
                ),
            ),
        )

        val aggregation = HealthMetricsAggregator.aggregateWithDiagnostics(data, zone, from, to)

        assertThat(aggregation.days.single().sleepDurationMin).isEqualTo(240)
        assertThat(aggregation.fragmentedSleepNights).isEqualTo(1)
        assertThat(aggregation.suspectSleepNights).isZero()
    }

    @Test
    fun `a daytime nap does not inflate the night`() {
        val data = HealthRawData(
            sleepSessions = listOf(
                HealthSleepSession(
                    Instant.parse("2026-10-29T23:00:00Z"),
                    Instant.parse("2026-10-30T05:00:00Z"),
                ),
                HealthSleepSession(
                    Instant.parse("2026-10-30T14:00:00Z"),
                    Instant.parse("2026-10-30T14:30:00Z"),
                ),
            ),
        )

        assertThat(HealthMetricsAggregator.aggregate(data, zone, from, to).single().sleepDurationMin)
            .isEqualTo(360)
    }

    @Test
    fun `averages samples per local day`() {
        val data = HealthRawData(
            restingHeartRates = listOf(
                HealthSample(Instant.parse("2026-10-30T01:00:00Z"), 56.0),
                HealthSample(Instant.parse("2026-10-30T02:00:00Z"), 60.0),
                HealthSample(Instant.parse("2026-10-29T01:00:00Z"), 50.0),
            ),
            hrvRmssd = listOf(HealthSample(Instant.parse("2026-10-30T01:00:00Z"), 60.0)),
            oxygenSaturation = listOf(
                HealthSample(Instant.parse("2026-10-30T01:00:00Z"), 97.0),
                HealthSample(Instant.parse("2026-10-30T02:00:00Z"), 95.0),
            ),
        )

        val metrics = HealthMetricsAggregator.aggregate(data, zone, from, to)

        assertThat(metrics).hasSize(2)
        assertThat(metrics.last().restingHeartRate).isEqualTo(58.0)
        assertThat(metrics.last().hrvRmssdMs).isEqualTo(60.0)
        assertThat(metrics.last().spo2Avg).isEqualTo(96.0)
        assertThat(metrics.first().restingHeartRate).isEqualTo(50.0)
    }

    @Test
    fun `uses the profile timezone for the day boundary`() {
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        val data = HealthRawData(
            sleepSessions = listOf(
                HealthSleepSession(
                    start = Instant.parse("2026-10-29T23:00:00Z"),
                    end = Instant.parse("2026-10-30T02:00:00Z"),
                ),
            ),
        )

        val metrics = HealthMetricsAggregator.aggregate(
            data,
            saoPaulo,
            LocalDate.of(2026, 10, 28),
            LocalDate.of(2026, 10, 30),
        ).single()

        assertThat(metrics.date).isEqualTo(LocalDate.of(2026, 10, 29))
    }

    @Test
    fun `filters dates outside the range`() {
        val data = HealthRawData(
            restingHeartRates = listOf(HealthSample(Instant.parse("2026-10-25T01:00:00Z"), 50.0)),
        )

        assertThat(HealthMetricsAggregator.aggregate(data, zone, from, to)).isEmpty()
    }

    @Test
    fun `rejects an inverted range`() {
        assertThatThrownBy {
            HealthMetricsAggregator.aggregate(HealthRawData(), zone, to, from)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
