package com.meucoach.core.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.Instant

class ActivityImportTest {
    private val start = Instant.parse("2026-10-24T08:17:10Z")

    private fun activity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = start,
        durationSec = 1_859,
        distanceM = 5_106.0,
        sourceId = "serial-1",
        subSport = "street",
        movingTimeSec = 1_859,
        averageHeartRate = 181,
        maxHeartRate = 187,
        averagePace = Pace.of(6, 4),
        averageCadenceSpm = 188,
        maxCadenceSpm = 198,
        averagePowerW = 322,
        maxPowerW = 468,
        averageStepLengthM = 0.87,
        elevationGainM = 52.0,
        elevationLossM = 63.0,
        calories = 483,
        laps = listOf(
            ActivityLap(
                index = 0,
                durationSec = 300,
                distanceM = 1_000.0,
                startTime = start,
                averageHeartRate = 175,
                maxHeartRate = 180,
                averagePace = Pace.of(5, 0),
                averageCadenceSpm = 186,
                averagePowerW = 310,
                elevationGainM = 4.0,
            ),
        ),
        samples = listOf(
            ActivitySample(
                offsetSec = 0,
                timestamp = start,
                latitude = -5.79,
                longitude = -35.21,
                altitudeM = 34.2,
                speedMps = 2.71,
                heartRate = 170,
                cadenceSpm = 188,
                powerW = 300,
                stepLengthM = 0.87,
                developerFields = mapOf("SpO2" to 98.0),
            ),
        ),
        deviceName = "Amazfit Balance",
    )

    @Test
    fun `accepts a complete imported activity`() {
        val activity = activity()

        assertThat(activity.samples).hasSize(1)
        assertThat(activity.laps).hasSize(1)
        assertThat(activity.samples.first().developerFields).containsEntry("SpO2", 98.0)
    }

    @Test
    fun `rejects invalid activity values`() {
        assertThatThrownBy { activity().copy(durationSec = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { activity().copy(distanceM = -1.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { activity().copy(averageHeartRate = 20) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { activity().copy(maxCadenceSpm = 301) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { activity().copy(averagePowerW = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { activity().copy(averageStepLengthM = -0.1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { activity().copy(elevationGainM = -1.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { activity().copy(calories = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { activity().copy(parserVersion = " ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `rejects invalid lap values`() {
        val lap = activity().laps.first()

        assertThatThrownBy { lap.copy(index = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { lap.copy(durationSec = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { lap.copy(distanceM = -1.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { lap.copy(averageHeartRate = 20) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { lap.copy(averageCadenceSpm = 301) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { lap.copy(averagePowerW = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { lap.copy(elevationGainM = -1.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `rejects invalid sample values`() {
        val sample = activity().samples.first()

        assertThatThrownBy { sample.copy(offsetSec = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { sample.copy(latitude = 91.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { sample.copy(longitude = 181.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { sample.copy(altitudeM = 10_000.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { sample.copy(speedMps = -1.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { sample.copy(heartRate = 20) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { sample.copy(cadenceSpm = 301) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { sample.copy(powerW = 3_000) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { sample.copy(stepLengthM = -0.1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { sample.copy(developerFields = mapOf(" " to 1.0)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { sample.copy(developerFields = mapOf("bad" to Double.NaN)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `allows measured zeros and missing optional values`() {
        val sample = ActivitySample(
            offsetSec = 0,
            latitude = null,
            longitude = null,
            altitudeM = null,
            speedMps = 0.0,
            heartRate = null,
            cadenceSpm = 0,
            powerW = 0,
            stepLengthM = 0.0,
        )
        val activity = activity().copy(
            averageHeartRate = null,
            averageCadenceSpm = 0,
            averagePowerW = null,
            averageStepLengthM = null,
            elevationGainM = null,
            calories = null,
            samples = listOf(sample),
        )

        assertThat(activity.samples.first().speedMps).isZero()
        assertThat(activity.samples.first().heartRate).isNull()
    }
}
