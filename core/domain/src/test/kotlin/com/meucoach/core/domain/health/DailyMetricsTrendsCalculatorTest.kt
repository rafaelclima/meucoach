package com.meucoach.core.domain.health

import com.meucoach.core.model.DailyMetrics
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.LocalDate

class DailyMetricsTrendsCalculatorTest {
    private val calculator = DailyMetricsTrendsCalculator()
    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun `keeps only the selected window and sorts the days`() {
        val metrics = listOf(
            day(today.minusDays(31), sleep = 300),
            day(today.minusDays(2), sleep = 420),
            day(today, sleep = 480),
        )

        val trends = calculator.calculate(metrics, today, windowDays = 30)

        assertThat(trends.startDate).isEqualTo(today.minusDays(29))
        assertThat(trends.endDate).isEqualTo(today)
        assertThat(trends.days).hasSize(2)
        assertThat(trends.days.first().date).isEqualTo(today.minusDays(2))
        assertThat(trends.days.last().date).isEqualTo(today)
    }

    @Test
    fun `counts coverage from days with any metric`() {
        val metrics = listOf(
            day(today.minusDays(1), sleep = 420, rhr = 58.0),
            day(today, hrv = 60.0),
        )

        val trends = calculator.calculate(metrics, today, windowDays = 30)

        assertThat(trends.daysWithAnyMetric).isEqualTo(2)
        assertThat(trends.coverage).isCloseTo(2.0 / 30.0, within(1e-9))
        assertThat(trends.latestDate).isEqualTo(today)
    }

    @Test
    fun `keeps gaps instead of inventing values`() {
        val metrics = listOf(
            day(today.minusDays(2), sleep = 420),
            day(today, sleep = 480),
        )

        val trends = calculator.calculate(metrics, today, windowDays = 7)

        val sleep = trends.trend(HealthMetric.SLEEP)
        assertThat(sleep).isNotNull
        assertThat(sleep!!.points).hasSize(2)
        assertThat(sleep.points.map { it.value }).containsExactly(420.0, 480.0)
        assertThat(sleep.average7d).isCloseTo(450.0, within(1e-9))
        assertThat(sleep.change).isCloseTo(0.0, within(1e-9))
    }

    @Test
    fun `computes a trailing seven day average for every covered day`() {
        val metrics = listOf(
            day(today.minusDays(8), sleep = 300),
            day(today.minusDays(7), sleep = 360),
            day(today.minusDays(1), sleep = 420),
            day(today, sleep = 480),
        )

        val trends = calculator.calculate(metrics, today, windowDays = 14)

        val rolling = trends.trend(HealthMetric.SLEEP)!!.rollingAverage7d
        val byDate = rolling.associate { it.date to it.value }
        assertThat(byDate[today.minusDays(8)]).isCloseTo(300.0, within(1e-9))
        assertThat(byDate[today.minusDays(7)]).isCloseTo(330.0, within(1e-9))
        assertThat(byDate[today.minusDays(2)]).isCloseTo(330.0, within(1e-9))
        assertThat(byDate[today]).isCloseTo(450.0, within(1e-9))
    }

    @Test
    fun `uses the individual baseline for recovery metrics and evidence ranges for sleep and spo2`() {
        val metrics = listOf(
            day(today.minusDays(1), rhr = 60.0, hrv = 55.0, spo2 = 97.0),
            day(today, sleep = 450, rhr = 58.0, hrv = 65.0, spo2 = 96.0),
        )

        val trends = calculator.calculate(metrics, today, windowDays = 30)

        val sleep = trends.trend(HealthMetric.SLEEP)!!
        assertThat(sleep.targetMin).isEqualTo(420.0)
        assertThat(sleep.targetMax).isEqualTo(540.0)
        assertThat(sleep.baseline).isCloseTo(450.0, within(1e-9))

        val rhr = trends.trend(HealthMetric.RESTING_HEART_RATE)!!
        assertThat(rhr.targetMin).isNull()
        assertThat(rhr.targetMax).isNull()
        assertThat(rhr.baseline).isCloseTo(59.0, within(1e-9))
        assertThat(rhr.change).isCloseTo(0.0, within(1e-9))
        assertThat(rhr.unit).isEqualTo(HealthMetricUnit.BPM)

        val hrv = trends.trend(HealthMetric.HRV)!!
        assertThat(hrv.unit).isEqualTo(HealthMetricUnit.MILLISECONDS)
        assertThat(hrv.baseline).isCloseTo(60.0, within(1e-9))

        val spo2 = trends.trend(HealthMetric.SPO2)!!
        assertThat(spo2.targetMin).isEqualTo(95.0)
        assertThat(spo2.targetMax).isEqualTo(100.0)
        assertThat(spo2.unit).isEqualTo(HealthMetricUnit.PERCENT)
    }

    @Test
    fun `keeps the last row when the same date appears twice`() {
        val metrics = listOf(
            day(today, sleep = 420),
            day(today, sleep = 480),
        )

        val trends = calculator.calculate(metrics, today, windowDays = 7)

        assertThat(trends.days).hasSize(1)
        assertThat(trends.trend(HealthMetric.SLEEP)!!.latest!!.value).isEqualTo(480.0)
    }

    @Test
    fun `returns no series when every value is absent`() {
        val trends = calculator.calculate(emptyList(), today, windowDays = 30)

        assertThat(trends.hasData).isFalse()
        assertThat(trends.daysWithAnyMetric).isZero()
        assertThat(trends.trends).hasSize(4)
        assertThat(trends.trends).allSatisfy { trend -> assertThat(trend.hasData).isFalse() }
    }

    @Test
    fun `rejects a non positive window`() {
        assertThatThrownBy { calculator.calculate(emptyList(), today, windowDays = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun day(
        date: LocalDate,
        sleep: Int? = null,
        rhr: Double? = null,
        hrv: Double? = null,
        spo2: Double? = null,
    ) = DailyMetrics(
        date = date,
        sleepDurationMin = sleep,
        restingHeartRate = rhr,
        hrvRmssdMs = hrv,
        spo2Avg = spo2,
    )
}
