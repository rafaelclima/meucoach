package com.meucoach.core.domain.readiness

import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.LocalDate

class DailyMetricsReadinessBuilderTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun `builds inputs from the recent windows`() {
        val metrics = (0..9).map { daysAgo ->
            daily(
                daysAgo = daysAgo,
                sleep = if (daysAgo == 0) 450 else null,
                hrv = 60.0 + daysAgo,
                rhr = 58.0,
            )
        }

        val inputs = DailyMetricsReadinessBuilder.build(metrics, today)

        assertThat(inputs.sleep?.durationMin).isEqualTo(450)
        assertThat(inputs.hrv).isNotNull
        assertThat(inputs.hrv?.validNights).isEqualTo(7)
        assertThat(inputs.hrv?.rmssd7dMs).isCloseTo(63.0, within(1e-9))
        assertThat(inputs.restingHeartRate?.average7dBpm).isEqualTo(58.0)
        assertThat(inputs.restingHeartRate?.baseline30dBpm).isEqualTo(58.0)
        assertThat(inputs.signals.restingHeartRateElevatedTwoDays).isFalse()
    }

    @Test
    fun `flags two consecutive elevated resting heart rate days`() {
        val metrics = (0..20).map { daysAgo ->
            daily(
                daysAgo = daysAgo,
                rhr = if (daysAgo <= 1) 66.0 else 50.0,
            )
        }

        val inputs = DailyMetricsReadinessBuilder.build(metrics, today)

        assertThat(inputs.signals.restingHeartRateElevatedTwoDays).isTrue()
    }

    @Test
    fun `does not flag a single elevated day`() {
        val metrics = (0..20).map { daysAgo ->
            daily(
                daysAgo = daysAgo,
                rhr = if (daysAgo == 0) 66.0 else 50.0,
            )
        }

        assertThat(
            DailyMetricsReadinessBuilder.build(metrics, today)
                .signals.restingHeartRateElevatedTwoDays,
        ).isFalse()
    }

    @Test
    fun `requires enough HRV nights`() {
        val metrics = listOf(
            daily(daysAgo = 0, hrv = 60.0),
            daily(daysAgo = 1, hrv = 61.0),
            daily(daysAgo = 2, hrv = 62.0),
        )

        assertThat(DailyMetricsReadinessBuilder.build(metrics, today).hrv).isNull()
    }

    @Test
    fun `returns null inputs without metrics`() {
        val inputs = DailyMetricsReadinessBuilder.build(emptyList(), today)

        assertThat(inputs.sleep).isNull()
        assertThat(inputs.hrv).isNull()
        assertThat(inputs.restingHeartRate).isNull()
        assertThat(inputs.signals).isEqualTo(ReadinessSignals())
    }

    @Test
    fun `ignores stale sleep`() {
        val metrics = listOf(daily(daysAgo = 10, sleep = 450))

        assertThat(DailyMetricsReadinessBuilder.build(metrics, today).sleep).isNull()
    }

    @Test
    fun `ignores future rows`() {
        val metrics = listOf(daily(daysAgo = -1, sleep = 450, hrv = 60.0))

        assertThat(DailyMetricsReadinessBuilder.build(metrics, today).sleep).isNull()
    }

    private fun daily(daysAgo: Int, sleep: Int? = null, hrv: Double? = null, rhr: Double? = null) = DailyMetrics(
        date = today.minusDays(daysAgo.toLong()),
        sleepDurationMin = sleep,
        restingHeartRate = rhr,
        hrvRmssdMs = hrv,
        source = DailyMetricsSource.CSV,
    )
}
