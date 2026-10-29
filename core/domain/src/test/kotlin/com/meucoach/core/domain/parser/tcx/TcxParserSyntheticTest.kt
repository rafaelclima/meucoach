package com.meucoach.core.domain.parser.tcx

import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.Instant

class TcxParserSyntheticTest {
    private val parser = TcxParser()

    @Test
    fun `parses laps positions extensions and normalizes running cadence`() {
        val activity = parser.parse(multiLapTcx().toByteArray())

        assertThat(activity.sport).isEqualTo(Sport.RUNNING)
        assertThat(activity.startTime).isEqualTo(Instant.parse("2026-02-01T10:00:00Z"))
        assertThat(activity.sourceId).isEqualTo("2026-02-01T10:00:00Z")
        assertThat(activity.durationSec).isEqualTo(120)
        assertThat(activity.distanceM).isCloseTo(420.0, within(0.001))
        assertThat(activity.averageHeartRate).isEqualTo(145)
        assertThat(activity.maxHeartRate).isEqualTo(160)
        assertThat(activity.averageCadenceSpm).isEqualTo(170)
        assertThat(activity.maxCadenceSpm).isEqualTo(180)
        assertThat(activity.averagePowerW).isEqualTo(250)
        assertThat(activity.maxPowerW).isEqualTo(300)
        assertThat(activity.averagePace?.secondsPerKm).isEqualTo(286)
        assertThat(activity.elevationGainM).isCloseTo(4.0, within(0.001))
        assertThat(activity.elevationLossM).isCloseTo(1.0, within(0.001))
        assertThat(activity.calories).isEqualTo(45)
        assertThat(activity.deviceName).isEqualTo("Test Watch")
        assertThat(activity.laps).hasSize(2)
        assertThat(activity.laps[0].durationSec).isEqualTo(60)
        assertThat(activity.laps[0].distanceM).isCloseTo(200.0, within(0.001))
        assertThat(activity.laps[0].averageHeartRate).isEqualTo(140)
        assertThat(activity.laps[0].maxHeartRate).isEqualTo(150)
        assertThat(activity.laps[0].averageCadenceSpm).isEqualTo(160)
        assertThat(activity.laps[0].averagePowerW).isEqualTo(200)
        assertThat(activity.laps[0].averagePace?.secondsPerKm).isEqualTo(300)
        assertThat(activity.laps[1].averageCadenceSpm).isEqualTo(180)
        assertThat(activity.laps[1].averagePowerW).isEqualTo(300)
        assertThat(activity.laps[1].averagePace?.secondsPerKm).isEqualTo(273)
        assertThat(activity.samples).hasSize(4)
        assertThat(activity.samples.map { it.offsetSec }).containsExactly(0, 59, 60, 119)
        assertThat(activity.samples.first().latitude).isEqualTo(0.0)
        assertThat(activity.samples.first().longitude).isEqualTo(0.0)
        assertThat(activity.samples.first().speedMps).isEqualTo(3.0)
        assertThat(activity.samples.first().cadenceSpm).isEqualTo(160)
        assertThat(activity.samples.first().heartRate).isEqualTo(140)
    }

    @Test
    fun `infers one lap and start time when activity has no lap or id`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Track>
                    <Trackpoint>
                      <Time>2026-02-02T10:00:00Z</Time>
                      <Position>
                        <LatitudeDegrees>0.0</LatitudeDegrees>
                        <LongitudeDegrees>0.0</LongitudeDegrees>
                      </Position>
                    </Trackpoint>
                    <Trackpoint>
                      <Time>2026-02-02T10:01:00Z</Time>
                      <Position>
                        <LatitudeDegrees>0.001</LatitudeDegrees>
                        <LongitudeDegrees>0.0</LongitudeDegrees>
                      </Position>
                    </Trackpoint>
                  </Track>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val activity = parser.parse(xml.toByteArray())

        assertThat(activity.startTime).isEqualTo(Instant.parse("2026-02-02T10:00:00Z"))
        assertThat(activity.durationSec).isEqualTo(60)
        assertThat(activity.distanceM).isCloseTo(111.19, within(0.1))
        assertThat(activity.laps).hasSize(1)
        assertThat(activity.laps.single().index).isZero()
        assertThat(activity.laps.single().durationSec).isEqualTo(60)
        assertThat(activity.laps.single().distanceM).isCloseTo(111.19, within(0.1))
        assertThat(activity.samples).hasSize(2)
    }

    @Test
    fun `falls back to speed integration when distance and gps are absent`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
                                    xmlns:x="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-02-06T10:00:00Z</Id>
                  <Lap StartTime="2026-02-06T10:00:00Z">
                    <TotalTimeSeconds>60</TotalTimeSeconds>
                    <Track>
                      <Trackpoint>
                        <Time>2026-02-06T10:00:00Z</Time>
                        <Extensions><x:TPX><x:Speed>2.0</x:Speed></x:TPX></Extensions>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-02-06T10:01:00Z</Time>
                        <Extensions><x:TPX><x:Speed>4.0</x:Speed></x:TPX></Extensions>
                      </Trackpoint>
                    </Track>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val activity = parser.parse(xml.toByteArray())

        assertThat(activity.distanceM).isCloseTo(180.0, within(0.001))
        assertThat(activity.averagePace?.secondsPerKm).isEqualTo(333)
        assertThat(activity.samples).hasSize(2)
    }

    @Test
    fun `keeps walking cadence as steps and drops invalid values`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Walking">
                  <Id>2026-02-03T10:00:00Z</Id>
                  <Lap StartTime="2026-02-03T10:00:00Z">
                    <TotalTimeSeconds>60</TotalTimeSeconds>
                    <DistanceMeters>100</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-02-03T10:00:00Z</Time>
                        <Position>
                          <LatitudeDegrees>100</LatitudeDegrees>
                          <LongitudeDegrees>200</LongitudeDegrees>
                        </Position>
                        <AltitudeMeters>10000</AltitudeMeters>
                        <HeartRateBpm><Value>10</Value></HeartRateBpm>
                        <Cadence>80</Cadence>
                      </Trackpoint>
                    </Track>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val activity = parser.parse(xml.toByteArray())

        assertThat(activity.sport).isEqualTo(Sport.WALKING)
        assertThat(activity.averageCadenceSpm).isEqualTo(80)
        assertThat(activity.maxCadenceSpm).isEqualTo(80)
        assertThat(activity.samples).hasSize(1)
        assertThat(activity.samples.single().latitude).isNull()
        assertThat(activity.samples.single().longitude).isNull()
        assertThat(activity.samples.single().altitudeM).isNull()
        assertThat(activity.samples.single().heartRate).isNull()
    }

    @Test
    fun `parses declared duration when no track points are present`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-02-04T10:00:00Z</Id>
                  <Lap StartTime="2026-02-04T10:00:00Z">
                    <TotalTimeSeconds>60</TotalTimeSeconds>
                    <DistanceMeters>100</DistanceMeters>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val activity = parser.parse(xml.toByteArray())

        assertThat(activity.durationSec).isEqualTo(60)
        assertThat(activity.distanceM).isEqualTo(100.0)
        assertThat(activity.samples).isEmpty()
        assertThat(activity.laps).hasSize(1)
    }

    @Test
    fun `parses non utf8 xml declaration`() {
        val xml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-02-05T10:00:00Z</Id>
                  <Lap StartTime="2026-02-05T10:00:00Z">
                    <TotalTimeSeconds>60</TotalTimeSeconds>
                    <DistanceMeters>100</DistanceMeters>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val activity = parser.parse(xml.toByteArray(Charsets.ISO_8859_1))

        assertThat(activity.startTime).isEqualTo(Instant.parse("2026-02-05T10:00:00Z"))
    }

    @Test
    fun `rejects empty malformed and external entity xml`() {
        assertThatThrownBy { parser.parse(ByteArray(0)) }
            .isInstanceOf(TcxParseException::class.java)
            .hasMessageContaining("empty")

        assertThatThrownBy { parser.parse("<TrainingCenterDatabase>".toByteArray()) }
            .isInstanceOf(TcxParseException::class.java)
            .hasMessageContaining("Invalid XML")

        val xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities><Activity Sport="Running"><Id>&xxe;</Id></Activity></Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        assertThatThrownBy { parser.parse(xxe.toByteArray()) }
            .isInstanceOf(TcxParseException::class.java)
            .hasMessageContaining("Invalid XML")
    }

    @Test
    fun `rejects documents without activity or time`() {
        val noActivity = """
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"/>
        """.trimIndent()

        assertThatThrownBy { parser.parse(noActivity.toByteArray()) }
            .isInstanceOf(TcxParseException::class.java)
            .hasMessageContaining("No Activity")

        val noTime = """
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities><Activity Sport="Running"><Lap><TotalTimeSeconds>60</TotalTimeSeconds></Lap></Activity></Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        assertThatThrownBy { parser.parse(noTime.toByteArray()) }
            .isInstanceOf(TcxParseException::class.java)
            .hasMessageContaining("start time")
    }

    private fun multiLapTcx(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
                                xmlns:x="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
          <Activities>
            <Activity Sport="Running">
              <Id>2026-02-01T10:00:00Z</Id>
              <Creator><Name>Test Watch</Name></Creator>
              <Lap StartTime="2026-02-01T10:00:00Z">
                <TotalTimeSeconds>60</TotalTimeSeconds>
                <DistanceMeters>200</DistanceMeters>
                <Calories>20</Calories>
                <AverageHeartRateBpm><Value>140</Value></AverageHeartRateBpm>
                <MaximumHeartRateBpm><Value>150</Value></MaximumHeartRateBpm>
                <Track>
                  <Trackpoint>
                    <Time>2026-02-01T10:00:00Z</Time>
                    <Position><LatitudeDegrees>0.0</LatitudeDegrees><LongitudeDegrees>0.0</LongitudeDegrees></Position>
                    <AltitudeMeters>10</AltitudeMeters>
                    <HeartRateBpm><Value>140</Value></HeartRateBpm>
                    <Cadence>80</Cadence>
                    <Extensions><x:TPX><x:Speed>3.0</x:Speed><x:Watts>200</x:Watts></x:TPX></Extensions>
                  </Trackpoint>
                  <Trackpoint>
                    <Time>2026-02-01T10:00:59Z</Time>
                    <Position><LatitudeDegrees>0.001</LatitudeDegrees><LongitudeDegrees>0.0</LongitudeDegrees></Position>
                    <AltitudeMeters>12</AltitudeMeters>
                    <HeartRateBpm><Value>140</Value></HeartRateBpm>
                    <Cadence>80</Cadence>
                    <Extensions><x:TPX><x:Speed>3.0</x:Speed><x:Watts>200</x:Watts></x:TPX></Extensions>
                  </Trackpoint>
                </Track>
              </Lap>
              <Lap StartTime="2026-02-01T10:01:00Z">
                <TotalTimeSeconds>60</TotalTimeSeconds>
                <DistanceMeters>220</DistanceMeters>
                <Calories>25</Calories>
                <AverageHeartRateBpm><Value>150</Value></AverageHeartRateBpm>
                <MaximumHeartRateBpm><Value>160</Value></MaximumHeartRateBpm>
                <Track>
                  <Trackpoint>
                    <Time>2026-02-01T10:01:00Z</Time>
                    <Position><LatitudeDegrees>0.002</LatitudeDegrees><LongitudeDegrees>0.0</LongitudeDegrees></Position>
                    <AltitudeMeters>11</AltitudeMeters>
                    <HeartRateBpm><Value>150</Value></HeartRateBpm>
                    <Cadence>90</Cadence>
                    <Extensions><x:TPX><x:Speed>3.6</x:Speed><x:Watts>300</x:Watts></x:TPX></Extensions>
                  </Trackpoint>
                  <Trackpoint>
                    <Time>2026-02-01T10:01:59Z</Time>
                    <Position><LatitudeDegrees>0.003</LatitudeDegrees><LongitudeDegrees>0.0</LongitudeDegrees></Position>
                    <AltitudeMeters>13</AltitudeMeters>
                    <HeartRateBpm><Value>150</Value></HeartRateBpm>
                    <Cadence>90</Cadence>
                    <Extensions><x:TPX><x:Speed>3.6</x:Speed><x:Watts>300</x:Watts></x:TPX></Extensions>
                  </Trackpoint>
                </Track>
              </Lap>
            </Activity>
          </Activities>
        </TrainingCenterDatabase>
    """.trimIndent()
}
