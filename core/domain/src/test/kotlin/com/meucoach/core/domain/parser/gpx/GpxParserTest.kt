package com.meucoach.core.domain.parser.gpx

import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.Instant

class GpxParserTest {
    private val parser = GpxParser()

    private fun fixture(name: String): ByteArray {
        val stream = checkNotNull(javaClass.getResourceAsStream("/gpx/$name")) {
            "Missing test fixture $name"
        }
        return stream.use { it.readBytes() }
    }

    @Test
    fun `parses common track point extensions and cadence`() {
        val activity = parser.parse(fixture("synthetic-extensions.gpx"))

        assertThat(activity.source).isEqualTo(ActivitySource.GPX)
        assertThat(activity.sport).isEqualTo(Sport.RUNNING)
        assertThat(activity.startTime).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"))
        assertThat(activity.durationSec).isEqualTo(120)
        assertThat(activity.distanceM).isCloseTo(22.24, within(0.1))
        assertThat(activity.averageHeartRate).isEqualTo(150)
        assertThat(activity.maxHeartRate).isEqualTo(160)
        assertThat(activity.averageCadenceSpm).isEqualTo(164)
        assertThat(activity.maxCadenceSpm).isEqualTo(168)
        assertThat(activity.averagePowerW).isNull()
        assertThat(activity.maxPowerW).isNull()
        assertThat(activity.elevationGainM).isCloseTo(2.0, within(0.001))
        assertThat(activity.elevationLossM).isCloseTo(1.0, within(0.001))
        assertThat(activity.averagePace).isNotNull()
        assertThat(activity.deviceName).isEqualTo("example.invalid")
        assertThat(activity.parserVersion).isEqualTo(GpxParser.PARSER_VERSION)
        assertThat(activity.laps).isEmpty()
        assertThat(activity.samples).hasSize(3)
        assertThat(activity.samples.first().offsetSec).isZero()
        assertThat(activity.samples.last().offsetSec).isEqualTo(120)
        assertThat(activity.samples.first().latitude).isEqualTo(0.0)
        assertThat(activity.samples.first().altitudeM).isEqualTo(10.0)
        assertThat(activity.samples.first().speedMps).isEqualTo(2.5)
        assertThat(activity.samples.first().heartRate).isEqualTo(140)
        assertThat(activity.samples.first().cadenceSpm).isEqualTo(160)
    }

    @Test
    fun `parses the same fixture deterministically`() {
        val bytes = fixture("synthetic-extensions.gpx")

        assertThat(parser.parse(bytes)).isEqualTo(parser.parse(bytes))
    }

    @Test
    fun `keeps elevation absent when gpx has no elevation data`() {
        val activity = parser.parse(fixture("synthetic-no-elevation.gpx"))

        assertThat(activity.sport).isEqualTo(Sport.RUNNING)
        assertThat(activity.durationSec).isEqualTo(60)
        assertThat(activity.distanceM).isCloseTo(11.12, within(0.1))
        assertThat(activity.elevationGainM).isNull()
        assertThat(activity.elevationLossM).isNull()
        assertThat(activity.averageHeartRate).isNull()
        assertThat(activity.averageCadenceSpm).isNull()
        assertThat(activity.samples).hasSize(2)
        assertThat(activity.samples).allSatisfy {
            assertThat(it.altitudeM).isNull()
            assertThat(it.heartRate).isNull()
            assertThat(it.cadenceSpm).isNull()
        }
    }

    @Test
    fun `normalizes treadmill cadence and parses power extension`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Test Device"
                 xmlns="http://www.topografix.com/GPX/1/1"
                 xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
              <trk>
                <type>treadmill</type>
                <trkseg>
                  <trkpt lat="0.0" lon="0.0">
                    <time>2026-01-03T10:00:00Z</time>
                    <extensions>
                      <gpxtpx:TrackPointExtension>
                        <gpxtpx:hr>120</gpxtpx:hr>
                        <gpxtpx:cad>85</gpxtpx:cad>
                        <gpxtpx:power>250</gpxtpx:power>
                      </gpxtpx:TrackPointExtension>
                    </extensions>
                  </trkpt>
                  <trkpt lat="0.0" lon="0.0">
                    <time>2026-01-03T10:01:00Z</time>
                    <extensions>
                      <gpxtpx:TrackPointExtension>
                        <gpxtpx:hr>130</gpxtpx:hr>
                        <gpxtpx:cad>85</gpxtpx:cad>
                        <gpxtpx:power>250</gpxtpx:power>
                      </gpxtpx:TrackPointExtension>
                    </extensions>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val activity = parser.parse(xml.toByteArray())

        assertThat(activity.sport).isEqualTo(Sport.TREADMILL)
        assertThat(activity.durationSec).isEqualTo(60)
        assertThat(activity.distanceM).isZero()
        assertThat(activity.averageCadenceSpm).isEqualTo(170)
        assertThat(activity.averagePowerW).isEqualTo(250)
        assertThat(activity.maxPowerW).isEqualTo(250)
        assertThat(activity.averageHeartRate).isEqualTo(125)
        assertThat(activity.maxHeartRate).isEqualTo(130)
        assertThat(activity.deviceName).isEqualTo("Test Device")
    }

    @Test
    fun `drops invalid coordinates and elevation`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="100" lon="200">
                  <ele>10000</ele>
                  <time>2026-01-04T10:00:00Z</time>
                </trkpt>
                <trkpt lat="0.0" lon="0.0">
                  <time>2026-01-04T10:01:00Z</time>
                </trkpt>
              </trkseg></trk>
            </gpx>
        """.trimIndent()

        val activity = parser.parse(xml.toByteArray())

        assertThat(activity.distanceM).isZero()
        assertThat(activity.samples).hasSize(2)
        assertThat(activity.samples.first().latitude).isNull()
        assertThat(activity.samples.first().longitude).isNull()
        assertThat(activity.samples.first().altitudeM).isNull()
        assertThat(activity.samples.last().latitude).isEqualTo(0.0)
    }

    @Test
    fun `rejects empty malformed and incomplete gpx`() {
        assertThatThrownBy { parser.parse(ByteArray(0)) }
            .isInstanceOf(GpxParseException::class.java)
            .hasMessageContaining("empty")

        assertThatThrownBy { parser.parse("<gpx>".toByteArray()) }
            .isInstanceOf(GpxParseException::class.java)
            .hasMessageContaining("Invalid XML")

        assertThatThrownBy {
            parser.parse("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"/>".toByteArray())
        }.isInstanceOf(GpxParseException::class.java)
            .hasMessageContaining("No track points")

        val noTime = """
            <gpx xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg><trkpt lat="0" lon="0"/></trkseg></trk>
            </gpx>
        """.trimIndent()

        assertThatThrownBy { parser.parse(noTime.toByteArray()) }
            .isInstanceOf(GpxParseException::class.java)
            .hasMessageContaining("time")

        val onePoint = """
            <gpx xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="0" lon="0"><time>2026-01-05T10:00:00Z</time></trkpt>
              </trkseg></trk>
            </gpx>
        """.trimIndent()

        assertThatThrownBy { parser.parse(onePoint.toByteArray()) }
            .isInstanceOf(GpxParseException::class.java)
            .hasMessageContaining("duration")
    }

    @Test
    fun `rejects external entity xml`() {
        val xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <gpx xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="0" lon="0"><time>&xxe;</time></trkpt>
              </trkseg></trk>
            </gpx>
        """.trimIndent()

        assertThatThrownBy { parser.parse(xxe.toByteArray()) }
            .isInstanceOf(GpxParseException::class.java)
    }
}
