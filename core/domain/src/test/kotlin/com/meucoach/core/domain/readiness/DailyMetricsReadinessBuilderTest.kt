package com.meucoach.core.domain.readiness

import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

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
        assertThat(inputs.hrv?.baseline60dMs).isCloseTo(64.5, within(1e-9))
        assertThat(inputs.hrv?.meanLog7d)
            .isCloseTo((60..66).map { ln(it.toDouble()) }.average(), within(1e-9))
        assertThat(inputs.hrv?.meanLog60d)
            .isCloseTo((60..69).map { ln(it.toDouble()) }.average(), within(1e-9))
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
    fun `computes the log-space z-score from the baseline instead of the raw ratio`() {
        // Two high nights and 58 low nights: the arithmetic and geometric
        // baselines differ enough that the old formula changes the z-score.
        val metrics = (0 until 60).map { daysAgo ->
            daily(daysAgo = daysAgo, hrv = if (daysAgo < 2) 200.0 else 20.0)
        }

        val hrv = requireNotNull(DailyMetricsReadinessBuilder.build(metrics, today).hrv)
        val logs7 = (0 until 7).map { ln(if (it < 2) 200.0 else 20.0) }
        val logs60 = (0 until 60).map { ln(if (it < 2) 200.0 else 20.0) }
        val meanLog7 = logs7.average()
        val meanLog60 = logs60.average()
        val sd = sqrt(logs60.sumOf { (it - meanLog60) * (it - meanLog60) } / logs60.size)

        assertThat(hrv.meanLog7d).isCloseTo(meanLog7, within(1e-9))
        assertThat(hrv.meanLog60d).isCloseTo(meanLog60, within(1e-9))
        assertThat(hrv.sdLnMs).isCloseTo(sd, within(1e-9))
        assertThat(hrv.zScore).isCloseTo((meanLog7 - meanLog60) / sd, within(1e-9))

        val rawRatioZ = ln(hrv.rmssd7dMs / hrv.baseline60dMs) / hrv.sdLnMs
        assertThat(abs(hrv.zScore - rawRatioZ)).isGreaterThan(0.1)
    }

    @Test
    fun `ignores non-finite HRV values`() {
        val metrics = (0..9).map { daysAgo ->
            daily(
                daysAgo = daysAgo,
                hrv = if (daysAgo == 0) Double.POSITIVE_INFINITY else 60.0 + daysAgo,
            )
        }

        val hrv = requireNotNull(DailyMetricsReadinessBuilder.build(metrics, today).hrv)

        assertThat(hrv.rmssd7dMs).isCloseTo(63.5, within(1e-9))
        assertThat(hrv.meanLog7d.isFinite()).isTrue()
        assertThat(hrv.meanLog60d.isFinite()).isTrue()
    }

    @Test
    fun `treats a flat baseline as an average z-score`() {
        val metrics = (0..30).map { daysAgo ->
            daily(daysAgo = daysAgo, hrv = 60.0)
        }

        val hrv = requireNotNull(DailyMetricsReadinessBuilder.build(metrics, today).hrv)

        assertThat(hrv.zScore).isZero()
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
