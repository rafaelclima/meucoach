package com.meucoach.core.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

class ActivitySummaryTest {
    private fun activity(
        durationSec: Int = 1_860,
        distanceKm: Double = 5.11,
        averageHeartRate: Int? = 181,
        maxHeartRate: Int? = 187,
    ) = ActivitySummary(
        id = "activity-1",
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-10-24T09:00:00Z"),
        durationSec = durationSec,
        distance = Distance.ofKilometers(distanceKm),
        averageHeartRate = averageHeartRate,
        maxHeartRate = maxHeartRate,
    )

    @Test
    fun `calculates pace from duration and distance`() {
        val activity = activity()

        assertThat(activity.calculatedPace?.secondsPerKm)
            .isEqualTo((1_860 / 5.11).roundToInt())
    }

    @Test
    fun `returns null pace when distance is zero`() {
        assertThat(activity(distanceKm = 0.0).calculatedPace).isNull()
    }

    @Test
    fun `rejects invalid summary values`() {
        assertThatThrownBy { activity(durationSec = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { activity(averageHeartRate = 10) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { activity(maxHeartRate = 300) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `validates athlete profile ranges`() {
        val profile =
            AthleteProfile(
                id = "athlete-1",
                birthDate = LocalDate.of(1988, 1, 1),
                sex = Sex.MALE,
                heightCm = 174.0,
                weightKg = 97.0,
                restingHeartRate = 58,
                maxHeartRateObserved = 187,
                lactateThresholdHeartRate = 180,
                thresholdPace = Pace.fromSecondsPerKm(364),
                vdot = 31.0,
            )

        assertThat(profile.lactateThresholdHeartRate).isEqualTo(180)
        assertThatThrownBy { profile.copy(restingHeartRate = 200) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { profile.copy(lactateThresholdHeartRate = 90) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
