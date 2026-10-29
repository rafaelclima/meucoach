package com.meucoach.core.domain.parser.tcx

import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.Instant

class TcxParserRealFixturesTest {
    private val parser = TcxParser()

    private fun fixture(name: String): ByteArray {
        val stream = checkNotNull(javaClass.getResourceAsStream("/tcx/$name")) {
            "Missing test fixture $name"
        }
        return stream.use { it.readBytes() }
    }

    @Test
    fun `parses first sanitized outdoor run`() {
        val activity = parser.parse(fixture("Zepp20261008052132.tcx"))

        assertThat(activity.source).isEqualTo(ActivitySource.TCX)
        assertThat(activity.sport).isEqualTo(Sport.RUNNING)
        assertThat(activity.startTime).isEqualTo(Instant.parse("2026-10-08T08:21:32Z"))
        assertThat(activity.sourceId).isEqualTo("2026-10-08T08:21:32Z")
        assertThat(activity.durationSec).isEqualTo(2_064)
        assertThat(activity.movingTimeSec).isEqualTo(2_064)
        assertThat(activity.distanceM).isCloseTo(5_016.0, within(0.001))
        assertThat(activity.averageHeartRate).isEqualTo(171)
        assertThat(activity.maxHeartRate).isEqualTo(185)
        assertThat(activity.averageCadenceSpm).isEqualTo(185)
        assertThat(activity.maxCadenceSpm).isEqualTo(194)
        assertThat(activity.averagePace?.secondsPerKm).isEqualTo(411)
        assertThat(activity.averagePowerW).isNull()
        assertThat(activity.maxPowerW).isNull()
        assertThat(activity.elevationGainM).isCloseTo(81.84, within(0.01))
        assertThat(activity.elevationLossM).isCloseTo(82.91, within(0.01))
        assertThat(activity.calories).isEqualTo(528)
        assertThat(activity.deviceName).isNull()
        assertThat(activity.parserVersion).isEqualTo(TcxParser.PARSER_VERSION)
        assertThat(activity.laps).hasSize(1)
        assertThat(activity.laps.single().averageCadenceSpm).isEqualTo(185)
        assertThat(activity.laps.single().elevationGainM).isCloseTo(81.84, within(0.01))
        assertThat(activity.samples).hasSize(2_065)
        assertThat(activity.samples.first().offsetSec).isZero()
        assertThat(activity.samples.last().offsetSec).isEqualTo(2_064)
        assertThat(activity.samples).allSatisfy {
            assertThat(it.latitude).isNull()
            assertThat(it.longitude).isNull()
            assertThat(it.heartRate).isNotNull()
            assertThat(it.speedMps).isNotNull()
            assertThat(it.altitudeM).isNotNull()
            assertThat(it.cadenceSpm).isNotNull()
        }
    }

    @Test
    fun `parses the same fixture deterministically`() {
        val bytes = fixture("Zepp20261008052132.tcx")

        assertThat(parser.parse(bytes)).isEqualTo(parser.parse(bytes))
    }

    @Test
    fun `parses second sanitized outdoor run`() {
        val activity = parser.parse(fixture("Zepp20261013052204.tcx"))

        assertThat(activity.startTime).isEqualTo(Instant.parse("2026-10-13T08:22:04Z"))
        assertThat(activity.durationSec).isEqualTo(1_976)
        assertThat(activity.distanceM).isCloseTo(5_037.0, within(0.001))
        assertThat(activity.averageHeartRate).isEqualTo(170)
        assertThat(activity.maxHeartRate).isEqualTo(184)
        assertThat(activity.averageCadenceSpm).isEqualTo(186)
        assertThat(activity.maxCadenceSpm).isEqualTo(196)
        assertThat(activity.averagePace?.secondsPerKm).isEqualTo(392)
        assertThat(activity.calories).isEqualTo(501)
        assertThat(activity.samples).hasSize(1_977)
    }

    @Test
    fun `parses third sanitized outdoor run`() {
        val activity = parser.parse(fixture("Zepp20261016053227.tcx"))

        assertThat(activity.startTime).isEqualTo(Instant.parse("2026-10-16T08:32:27Z"))
        assertThat(activity.durationSec).isEqualTo(2_008)
        assertThat(activity.distanceM).isCloseTo(5_119.0, within(0.001))
        assertThat(activity.averageHeartRate).isEqualTo(170)
        assertThat(activity.maxHeartRate).isEqualTo(185)
        assertThat(activity.averageCadenceSpm).isEqualTo(188)
        assertThat(activity.maxCadenceSpm).isEqualTo(196)
        assertThat(activity.averagePace?.secondsPerKm).isEqualTo(392)
        assertThat(activity.calories).isEqualTo(510)
        assertThat(activity.samples).hasSize(2_009)
    }
}
