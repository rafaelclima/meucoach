package com.meucoach.core.domain.trends

import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Distance
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class ActivityTrendsCalculatorTest {
    private val calculator = ActivityTrendsCalculator()
    private val today = LocalDate.of(2026, 10, 30)
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun `aggregates weekly volume and consistency`() {
        val activities = listOf(
            activity(today.minusDays(1), km = 5.0, pace = 360, hr = 150),
            activity(today.minusDays(3), km = 8.0, pace = 380, hr = 145),
            activity(today.minusDays(10), km = 6.0, pace = 370, hr = 148),
        )

        val trends = calculator.calculate(activities, today, zone, weeks = 4)

        assertThat(trends.weeks).hasSize(4)
        assertThat(trends.weeks.last().kilometers).isEqualTo(13.0)
        assertThat(trends.weeks.last().runs).isEqualTo(2)
        assertThat(trends.weeks[2].kilometers).isEqualTo(6.0)
        assertThat(trends.consistency.activeDays).isEqualTo(3)
        assertThat(trends.consistency.runs).isEqualTo(3)
        assertThat(trends.consistency.daysSinceLastRun).isEqualTo(1)
        assertThat(trends.totalKilometers).isEqualTo(19.0)
        assertThat(trends.averagePace).isEqualTo(Pace.fromSecondsPerKm(370))
        assertThat(trends.averageHeartRate).isEqualTo(148)
        assertThat(trends.paceHeartRate).hasSize(3)
    }

    @Test
    fun `omits pace points without heart rate`() {
        val activities = listOf(
            activity(today.minusDays(1), km = 5.0, pace = 360, hr = null),
            activity(today.minusDays(2), km = 5.0, pace = 360, hr = 150),
        )

        val trends = calculator.calculate(activities, today, zone)

        assertThat(trends.paceHeartRate).hasSize(1)
        assertThat(trends.averageHeartRate).isEqualTo(150)
    }

    @Test
    fun `ignores activities outside the consistency window`() {
        val activities = listOf(
            activity(today.minusDays(60), km = 5.0, pace = 360, hr = 150),
        )

        val trends = calculator.calculate(activities, today, zone, consistencyDays = 28)

        assertThat(trends.consistency.runs).isZero()
        assertThat(trends.totalKilometers).isZero()
        assertThat(trends.weeks.sumOf { it.kilometers }).isZero()
    }

    @Test
    fun `finds records from runs and ignores short efforts and walking`() {
        val activities = listOf(
            activity(today.minusDays(1), km = 10.0, pace = 300, hr = 100).copy(sport = Sport.WALKING),
            activity(today.minusDays(2), km = 2.0, pace = 300, hr = 150),
            activity(today.minusDays(3), km = 8.0, pace = 380, hr = 145),
            activity(today.minusDays(4), km = 5.0, pace = 360, hr = 150),
        )

        val records = calculator.calculate(activities, today, zone).records

        assertThat(records.longestDistanceKm).isEqualTo(8.0)
        assertThat(records.longestDistanceDate).isEqualTo(today.minusDays(3))
        assertThat(records.bestPace).isEqualTo(Pace.fromSecondsPerKm(360))
        assertThat(records.bestPaceDistanceKm).isEqualTo(5.0)
        assertThat(records.bestPaceDate).isEqualTo(today.minusDays(4))
        assertThat(records.longestDurationSec).isEqualTo(3_040)
    }

    @Test
    fun `handles an empty history`() {
        val trends = calculator.calculate(emptyList(), today, zone)

        assertThat(trends.weeks).hasSize(6)
        assertThat(trends.consistency.activeDays).isZero()
        assertThat(trends.consistency.daysSinceLastRun).isNull()
        assertThat(trends.averagePace).isNull()
        assertThat(trends.averageHeartRate).isNull()
        assertThat(trends.records.hasData).isFalse()
    }

    @Test
    fun `builds a monday-aligned consistency heatmap`() {
        val activities = listOf(
            activity(today.minusDays(1), km = 5.0, pace = 360, hr = 150),
            activity(today.minusDays(2), km = 3.0, pace = 360, hr = 150),
            activity(today.minusDays(2), km = 2.0, pace = 380, hr = 150),
        )

        val heatmap = calculator
            .calculate(activities, today, zone, weeks = 4, consistencyDays = 28)
            .heatmap

        assertThat(heatmap.first().date.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(heatmap.last().date).isEqualTo(today)
        assertThat(heatmap).hasSize(26)
        val doubled = heatmap.first { it.date == today.minusDays(2) }
        assertThat(doubled.kilometers).isEqualTo(5.0)
        assertThat(doubled.runs).isEqualTo(2)
        assertThat(heatmap.first { it.date == today }.runs).isZero()
    }

    @Test
    fun `computes efficiency in meters per heartbeat`() {
        val point = PaceHeartRatePoint(
            date = today,
            pace = Pace.fromSecondsPerKm(360),
            heartRate = 150,
        )

        assertThat(point.efficiencyMetersPerBeat).isCloseTo(1.111, within(0.001))
    }

    @Test
    fun `accepts a six month activity window`() {
        val trends = calculator.calculate(
            emptyList(),
            today,
            zone,
            weeks = 26,
            consistencyDays = 182,
        )

        assertThat(trends.weeks).hasSize(26)
    }

    @Test
    fun `rejects invalid windows`() {
        assertThatThrownBy { calculator.calculate(emptyList(), today, zone, weeks = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { calculator.calculate(emptyList(), today, zone, consistencyDays = 6) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { calculator.calculate(emptyList(), today, zone, consistencyDays = 366) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun activity(date: LocalDate, km: Double, pace: Int, hr: Int?) = ActivitySummary(
        id = "activity-$date-$pace-$hr",
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = date.atTime(8, 0).atZone(zone).toInstant(),
        durationSec = (km * pace).toInt(),
        distance = Distance(km * 1_000),
        averageHeartRate = hr,
        averagePace = Pace.fromSecondsPerKm(pace),
    )
}
