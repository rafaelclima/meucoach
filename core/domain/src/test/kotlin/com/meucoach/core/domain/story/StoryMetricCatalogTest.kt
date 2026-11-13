package com.meucoach.core.domain.story

import com.meucoach.core.model.Pace
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class StoryMetricCatalogTest {
    @Test
    fun `formats the three essentials like the story frames`() {
        val values = values(
            distanceKm = 5.02,
            totalTimeSec = 1_694,
            averagePace = Pace.fromSecondsPerKm(337),
        )

        assertThat(StoryMetricCatalog.format(StoryEssential.DISTANCE, values)).isEqualTo("5,02 km")
        assertThat(StoryMetricCatalog.format(StoryEssential.AVG_PACE, values)).isEqualTo("5:37 /km")
        assertThat(StoryMetricCatalog.format(StoryEssential.TOTAL_TIME, values)).isEqualTo("28:14")
    }

    @Test
    fun `derives average pace from distance and time when the summary is absent`() {
        val values = values(distanceKm = 5.0, totalTimeSec = 1_500)

        assertThat(StoryMetricCatalog.isAvailable(StoryEssential.AVG_PACE, values)).isTrue
        assertThat(StoryMetricCatalog.format(StoryEssential.AVG_PACE, values)).isEqualTo("5:00 /km")
    }

    @Test
    fun `formats every extra with its unit`() {
        val values = values(
            calories = 312,
            elevationGainM = 84.4,
            averageHeartRate = 152,
            maxHeartRate = 178,
            averageCadenceSpm = 172,
            completedSplits = 10,
            fastestKm = 4,
            fastestPace = Pace.fromSecondsPerKm(302),
            localDate = LocalDate.parse("2026-11-13"),
        )

        assertThat(StoryMetricCatalog.format(StoryExtra.CALORIES_TOTAL, values)).isEqualTo("312 kcal")
        assertThat(StoryMetricCatalog.format(StoryExtra.ELEVATION_GAIN, values)).isEqualTo("+84 m")
        assertThat(StoryMetricCatalog.format(StoryExtra.AVG_HR, values)).isEqualTo("152 bpm")
        assertThat(StoryMetricCatalog.format(StoryExtra.MAX_HR, values)).isEqualTo("178 bpm")
        assertThat(StoryMetricCatalog.format(StoryExtra.CADENCE, values)).isEqualTo("172 spm")
        assertThat(StoryMetricCatalog.format(StoryExtra.FASTEST_KM, values)).isEqualTo("km 4 · 5:02")
        assertThat(StoryMetricCatalog.format(StoryExtra.DATE, values)).isEqualTo("13 nov")
    }

    @Test
    fun `missing extras are unavailable with a reason and never become zero`() {
        val values = values()

        assertThat(StoryMetricCatalog.availability(StoryExtra.CALORIES_TOTAL, values).reason)
            .isEqualTo(StoryMetricReason.NO_CALORIES)
        assertThat(StoryMetricCatalog.availability(StoryExtra.ELEVATION_GAIN, values).reason)
            .isEqualTo(StoryMetricReason.NO_ELEVATION)
        assertThat(StoryMetricCatalog.availability(StoryExtra.AVG_HR, values).reason)
            .isEqualTo(StoryMetricReason.NO_HEART_RATE)
        assertThat(StoryMetricCatalog.availability(StoryExtra.MAX_HR, values).reason)
            .isEqualTo(StoryMetricReason.NO_MAX_HEART_RATE)
        assertThat(StoryMetricCatalog.availability(StoryExtra.CADENCE, values).reason)
            .isEqualTo(StoryMetricReason.NO_CADENCE)
        assertThat(StoryMetricCatalog.availability(StoryExtra.FASTEST_KM, values).reason)
            .isEqualTo(StoryMetricReason.NO_SPLITS)
        assertThat(StoryMetricCatalog.availability(StoryExtra.DATE, values).reason)
            .isEqualTo(StoryMetricReason.NO_DATE)
        assertThat(StoryMetricCatalog.format(StoryExtra.CALORIES_TOTAL, values)).isNull()
        assertThat(StoryMetricCatalog.format(StoryExtra.AVG_HR, values)).isNull()
    }

    @Test
    fun `a measured zero elevation is available and stays zero`() {
        val values = values(elevationGainM = 0.0)

        assertThat(StoryMetricCatalog.availability(StoryExtra.ELEVATION_GAIN, values).isAvailable).isTrue
        assertThat(StoryMetricCatalog.format(StoryExtra.ELEVATION_GAIN, values)).isEqualTo("+0 m")
    }

    @Test
    fun `zero calories are treated as absence`() {
        val values = values(calories = 0)

        assertThat(StoryMetricCatalog.availability(StoryExtra.CALORIES_TOTAL, values).isAvailable).isFalse
        assertThat(StoryMetricCatalog.format(StoryExtra.CALORIES_TOTAL, values)).isNull()
    }

    @Test
    fun `only heart rate, cadence and elevation have a per-km value`() {
        assertThat(StoryMetricCatalog.appearsPerKm(StoryExtra.AVG_HR)).isTrue
        assertThat(StoryMetricCatalog.appearsPerKm(StoryExtra.CADENCE)).isTrue
        assertThat(StoryMetricCatalog.appearsPerKm(StoryExtra.ELEVATION_GAIN)).isTrue
        assertThat(StoryMetricCatalog.appearsPerKm(StoryExtra.CALORIES_TOTAL)).isFalse
        assertThat(StoryMetricCatalog.appearsPerKm(StoryExtra.MAX_HR)).isFalse
        assertThat(StoryMetricCatalog.appearsPerKm(StoryExtra.DATE)).isFalse
        assertThat(StoryMetricCatalog.appearsPerKm(StoryExtra.FASTEST_KM)).isFalse
        assertThat(StoryMetricCatalog.isPerKmBadge(StoryExtra.FASTEST_KM)).isTrue
    }

    @Test
    fun `formats per-km values and keeps missing segment data null`() {
        val split = split(heartRate = 154, cadenceSpm = 170, elevationGainM = 12.2)

        assertThat(StoryMetricCatalog.formatPerKm(StoryExtra.AVG_HR, split)).isEqualTo("154 bpm")
        assertThat(StoryMetricCatalog.formatPerKm(StoryExtra.CADENCE, split)).isEqualTo("170 spm")
        assertThat(StoryMetricCatalog.formatPerKm(StoryExtra.ELEVATION_GAIN, split)).isEqualTo("+12 m")
        assertThat(StoryMetricCatalog.formatPerKm(StoryExtra.CALORIES_TOTAL, split)).isNull()
        assertThat(StoryMetricCatalog.formatPerKm(StoryExtra.AVG_HR, split.copy(heartRate = null))).isNull()
        assertThat(StoryMetricCatalog.perKmAvailability(StoryExtra.AVG_HR, split.copy(heartRate = null)).reason)
            .isEqualTo(StoryMetricReason.NO_HEART_RATE)
    }

    @Test
    fun `fastest km needs at least two completed splits`() {
        val single = values(completedSplits = 1, fastestKm = 1, fastestPace = Pace.fromSecondsPerKm(300))

        assertThat(StoryMetricCatalog.availability(StoryExtra.FASTEST_KM, single).isAvailable).isFalse
        assertThat(StoryMetricCatalog.format(StoryExtra.FASTEST_KM, single)).isNull()
    }

    private fun values(
        distanceKm: Double? = 5.0,
        totalTimeSec: Int? = 1_500,
        averagePace: Pace? = Pace.fromSecondsPerKm(300),
        calories: Int? = null,
        elevationGainM: Double? = null,
        averageHeartRate: Int? = null,
        maxHeartRate: Int? = null,
        averageCadenceSpm: Int? = null,
        completedSplits: Int = 0,
        fastestKm: Int? = null,
        fastestPace: Pace? = null,
        localDate: LocalDate? = null,
    ) = StoryMetricValues(
        distanceKm = distanceKm,
        totalTimeSec = totalTimeSec,
        averagePace = averagePace,
        calories = calories,
        elevationGainM = elevationGainM,
        averageHeartRate = averageHeartRate,
        maxHeartRate = maxHeartRate,
        averageCadenceSpm = averageCadenceSpm,
        completedSplits = completedSplits,
        fastestKm = fastestKm,
        fastestPace = fastestPace,
        localDate = localDate,
    )

    private fun split(heartRate: Int? = 150, cadenceSpm: Int? = 170, elevationGainM: Double? = 10.0) = StorySplit(
        km = 3,
        pace = Pace.fromSecondsPerKm(330),
        heartRate = heartRate,
        cadenceSpm = cadenceSpm,
        elevationGainM = elevationGainM,
        progress = 0.3f,
    )
}
