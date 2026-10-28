package com.meucoach.core.domain.parser.fit

import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant

class FitParserRealFixturesTest {
    private val parser = FitParser()

    private fun fixture(name: String): ByteArray {
        val stream = checkNotNull(javaClass.getResourceAsStream("/fit/$name")) {
            "Missing test fixture $name"
        }
        return stream.use { it.readBytes() }
    }

    @Test
    fun `parses sanitized treadmill recovery run`() {
        val result = parser.parse(fixture("Zepp20261022195619.fit"))
        val activity = result.activity

        assertThat(activity.source).isEqualTo(ActivitySource.FIT)
        assertThat(activity.sport).isEqualTo(Sport.TREADMILL)
        assertThat(activity.subSport).isEqualTo("treadmill")
        assertThat(activity.startTime).isEqualTo(Instant.parse("2026-10-22T22:56:19Z"))
        assertThat(activity.durationSec).isEqualTo(1_800)
        assertThat(activity.movingTimeSec).isEqualTo(1_800)
        assertThat(activity.distanceM).isEqualTo(3_030.0)
        assertThat(activity.averageHeartRate).isEqualTo(106)
        assertThat(activity.maxHeartRate).isEqualTo(131)
        assertThat(activity.averageCadenceSpm).isEqualTo(138)
        assertThat(activity.maxCadenceSpm).isEqualTo(182)
        assertThat(activity.averagePowerW).isNull()
        assertThat(activity.maxPowerW).isNull()
        assertThat(activity.averageStepLengthM).isEqualTo(0.72)
        assertThat(activity.elevationGainM).isNull()
        assertThat(activity.elevationLossM).isNull()
        assertThat(activity.calories).isEqualTo(203)
        assertThat(activity.laps).hasSize(4)
        assertThat(activity.samples).hasSize(1_801)
        assertThat(activity.samples.first().offsetSec).isZero()
        assertThat(activity.samples.last().offsetSec).isEqualTo(1_800)
        assertThat(activity.samples).allSatisfy {
            assertThat(it.latitude).isNull()
            assertThat(it.longitude).isNull()
        }

        assertThat(result.fileId?.productName).isEqualTo("Amazfit Balance")
        assertThat(result.fileId?.fileType).isEqualTo("activity")
        assertThat(result.device?.source).isEqualTo("example.invalid")
        assertThat(result.device?.productName).isEqualTo("Amazfit Balance")
    }

    @Test
    fun `parses sanitized outdoor race`() {
        val result = parser.parse(fixture("Zepp20261024051710.fit"))
        val activity = result.activity

        assertThat(activity.sport).isEqualTo(Sport.RUNNING)
        assertThat(activity.subSport).isEqualTo("street")
        assertThat(activity.startTime).isEqualTo(Instant.parse("2026-10-24T08:17:10Z"))
        assertThat(activity.durationSec).isEqualTo(1_859)
        assertThat(activity.distanceM).isEqualTo(5_106.0)
        assertThat(activity.averageHeartRate).isEqualTo(181)
        assertThat(activity.maxHeartRate).isEqualTo(187)
        assertThat(activity.averageCadenceSpm).isEqualTo(188)
        assertThat(activity.maxCadenceSpm).isEqualTo(198)
        assertThat(activity.averagePowerW).isEqualTo(322)
        assertThat(activity.maxPowerW).isEqualTo(468)
        assertThat(activity.averageStepLengthM).isEqualTo(0.87)
        assertThat(activity.elevationGainM).isEqualTo(52.0)
        assertThat(activity.elevationLossM).isEqualTo(63.0)
        assertThat(activity.calories).isEqualTo(483)
        assertThat(activity.laps).hasSize(6)
        assertThat(activity.samples).hasSize(1_860)
        assertThat(activity.samples.last().offsetSec).isEqualTo(1_859)
        assertThat(activity.samples).anySatisfy {
            assertThat(it.altitudeM).isNotNull()
            assertThat(it.speedMps).isNotNull()
            assertThat(it.powerW).isNotNull()
        }
        assertThat(activity.samples).allSatisfy {
            assertThat(it.latitude).isNull()
            assertThat(it.longitude).isNull()
        }
        assertThat(activity.averagePace?.secondsPerKm).isEqualTo(364)
    }

    @Test
    fun `parses sanitized indoor run`() {
        val result = parser.parse(fixture("Zepp20261026202359.fit"))
        val activity = result.activity

        assertThat(activity.sport).isEqualTo(Sport.TREADMILL)
        assertThat(activity.startTime).isEqualTo(Instant.parse("2026-10-26T23:23:59Z"))
        assertThat(activity.durationSec).isEqualTo(1_920)
        assertThat(activity.distanceM).isEqualTo(3_340.0)
        assertThat(activity.averageHeartRate).isEqualTo(122)
        assertThat(activity.maxHeartRate).isEqualTo(138)
        assertThat(activity.averageCadenceSpm).isEqualTo(142)
        assertThat(activity.maxCadenceSpm).isEqualTo(190)
        assertThat(activity.averagePowerW).isNull()
        assertThat(activity.averageStepLengthM).isEqualTo(0.73)
        assertThat(activity.calories).isEqualTo(298)
        assertThat(activity.laps).hasSize(4)
        assertThat(activity.samples).hasSize(1_921)
        assertThat(activity.samples.last().offsetSec).isEqualTo(1_920)
    }

    @Test
    fun `parses sanitized outdoor run`() {
        val result = parser.parse(fixture("Zepp20261027052000.fit"))
        val activity = result.activity

        assertThat(activity.sport).isEqualTo(Sport.RUNNING)
        assertThat(activity.subSport).isEqualTo("street")
        assertThat(activity.startTime).isEqualTo(Instant.parse("2026-10-27T08:20:00Z"))
        assertThat(activity.durationSec).isEqualTo(1_675)
        assertThat(activity.distanceM).isEqualTo(4_049.0)
        assertThat(activity.averageHeartRate).isEqualTo(168)
        assertThat(activity.maxHeartRate).isEqualTo(180)
        assertThat(activity.averageCadenceSpm).isEqualTo(186)
        assertThat(activity.maxCadenceSpm).isEqualTo(196)
        assertThat(activity.averagePowerW).isEqualTo(291)
        assertThat(activity.maxPowerW).isEqualTo(394)
        assertThat(activity.averageStepLengthM).isEqualTo(0.77)
        assertThat(activity.elevationGainM).isEqualTo(36.0)
        assertThat(activity.elevationLossM).isEqualTo(40.0)
        assertThat(activity.calories).isEqualTo(419)
        assertThat(activity.laps).hasSize(5)
        assertThat(activity.samples).hasSize(1_676)
        assertThat(activity.samples.last().offsetSec).isEqualTo(1_675)
        assertThat(activity.samples).allSatisfy {
            assertThat(it.latitude).isNull()
            assertThat(it.longitude).isNull()
        }
    }
}
