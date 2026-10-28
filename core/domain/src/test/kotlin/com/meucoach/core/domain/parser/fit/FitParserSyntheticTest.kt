package com.meucoach.core.domain.parser.fit

import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.Instant

class FitParserSyntheticTest {
    private val parser = FitParser()

    @Test
    fun `parses little endian activity with developer fields and compressed timestamp`() {
        val start = Instant.parse("2026-10-28T10:00:00Z")
        val startRaw = FitTestBuilder.fitSeconds(start)
        val builder = FitTestBuilder(headerSize = 14, littleEndian = true)

        builder
            .definition(
                local = 0,
                globalNumber = 0,
                fields = listOf(
                    FitTestBuilder.field(0, TestBaseType.ENUM),
                    FitTestBuilder.field(1, TestBaseType.UINT16),
                    FitTestBuilder.field(8, TestBaseType.STRING, 16),
                ),
            )
            .data(0, listOf(builder.u8(4), builder.u16(339), builder.string("Test Device", 16)))
            .definition(
                local = 1,
                globalNumber = 206,
                fields = listOf(
                    FitTestBuilder.field(0, TestBaseType.UINT8),
                    FitTestBuilder.field(1, TestBaseType.UINT8),
                    FitTestBuilder.field(2, TestBaseType.ENUM),
                    FitTestBuilder.field(3, TestBaseType.STRING, 16),
                ),
            )
            .data(
                1,
                listOf(
                    builder.u8(0),
                    builder.u8(0),
                    builder.u8(TestBaseType.STRING.code),
                    builder.string("source", 16),
                ),
            )
            .definition(
                local = 1,
                globalNumber = 206,
                fields = listOf(
                    FitTestBuilder.field(0, TestBaseType.UINT8),
                    FitTestBuilder.field(1, TestBaseType.UINT8),
                    FitTestBuilder.field(2, TestBaseType.ENUM),
                    FitTestBuilder.field(3, TestBaseType.STRING, 16),
                ),
            )
            .data(
                1,
                listOf(
                    builder.u8(1),
                    builder.u8(0),
                    builder.u8(TestBaseType.FLOAT32.code),
                    builder.string("SpO2", 16),
                ),
            )
            .definition(
                local = 2,
                globalNumber = 23,
                fields = listOf(
                    FitTestBuilder.field(253, TestBaseType.UINT32),
                    FitTestBuilder.field(2, TestBaseType.UINT16),
                ),
                developerFields = listOf(TestDeveloperField(0, 16, 0)),
            )
            .data(
                2,
                listOf(
                    builder.u32(startRaw),
                    builder.u16(339),
                    builder.string("example.invalid", 16),
                ),
            )
            .definition(
                local = 3,
                globalNumber = 18,
                fields = listOf(
                    FitTestBuilder.field(253, TestBaseType.UINT32),
                    FitTestBuilder.field(2, TestBaseType.UINT32),
                    FitTestBuilder.field(5, TestBaseType.ENUM),
                    FitTestBuilder.field(6, TestBaseType.ENUM),
                    FitTestBuilder.field(7, TestBaseType.UINT32),
                    FitTestBuilder.field(8, TestBaseType.UINT32),
                    FitTestBuilder.field(9, TestBaseType.UINT32),
                    FitTestBuilder.field(16, TestBaseType.UINT8),
                    FitTestBuilder.field(17, TestBaseType.UINT8),
                    FitTestBuilder.field(18, TestBaseType.UINT8),
                    FitTestBuilder.field(19, TestBaseType.UINT8),
                    FitTestBuilder.field(20, TestBaseType.UINT16),
                    FitTestBuilder.field(21, TestBaseType.UINT16),
                    FitTestBuilder.field(22, TestBaseType.UINT16),
                    FitTestBuilder.field(23, TestBaseType.UINT16),
                    FitTestBuilder.field(11, TestBaseType.UINT16),
                    FitTestBuilder.field(134, TestBaseType.UINT16),
                    FitTestBuilder.field(14, TestBaseType.UINT16),
                ),
            )
            .data(
                3,
                listOf(
                    builder.u32(startRaw + 300),
                    builder.u32(startRaw),
                    builder.u8(1),
                    builder.u8(1),
                    builder.u32(300_000),
                    builder.u32(300_000),
                    builder.u32(100_000),
                    builder.u8(150),
                    builder.u8(170),
                    builder.u8(90),
                    builder.u8(95),
                    builder.u16(250),
                    builder.u16(300),
                    builder.u16(5),
                    builder.u16(6),
                    builder.u16(100),
                    builder.u16(8_500),
                    builder.u16(3_333),
                ),
            )
            .definition(
                local = 0,
                globalNumber = 20,
                fields = listOf(
                    FitTestBuilder.field(253, TestBaseType.UINT32),
                    FitTestBuilder.field(3, TestBaseType.UINT8),
                    FitTestBuilder.field(4, TestBaseType.UINT8),
                    FitTestBuilder.field(6, TestBaseType.UINT16),
                    FitTestBuilder.field(2, TestBaseType.UINT16),
                    FitTestBuilder.field(7, TestBaseType.UINT16),
                    FitTestBuilder.field(85, TestBaseType.UINT16),
                    FitTestBuilder.field(0, TestBaseType.SINT32),
                    FitTestBuilder.field(1, TestBaseType.SINT32),
                ),
                developerFields = listOf(TestDeveloperField(0, 4, 1)),
            )
            .data(
                0,
                listOf(
                    builder.u32(startRaw),
                    builder.u8(150),
                    builder.u8(90),
                    builder.u16(3_333),
                    builder.u16(2_671),
                    builder.u16(250),
                    builder.u16(8_500),
                    builder.s32(0x20000000),
                    builder.s32(0x10000000),
                    builder.f32(98.5f),
                ),
            )
            .compressedData(
                local = 0,
                timeOffset = ((startRaw + 1) and 0x1F).toInt(),
                values = listOf(
                    builder.u8(151),
                    builder.u8(91),
                    builder.u16(3_400),
                    builder.u16(2_671),
                    builder.u16(260),
                    builder.u16(8_500),
                    builder.s32(0x20000000),
                    builder.s32(0x10000000),
                    builder.f32(98.0f),
                ),
            )
            .definition(
                local = 5,
                globalNumber = 19,
                fields = listOf(
                    FitTestBuilder.field(254, TestBaseType.UINT16),
                    FitTestBuilder.field(2, TestBaseType.UINT32),
                    FitTestBuilder.field(7, TestBaseType.UINT32),
                    FitTestBuilder.field(8, TestBaseType.UINT32),
                    FitTestBuilder.field(9, TestBaseType.UINT32),
                    FitTestBuilder.field(15, TestBaseType.UINT8),
                    FitTestBuilder.field(16, TestBaseType.UINT8),
                    FitTestBuilder.field(17, TestBaseType.UINT8),
                    FitTestBuilder.field(19, TestBaseType.UINT16),
                    FitTestBuilder.field(21, TestBaseType.UINT16),
                ),
            )
            .data(
                5,
                listOf(
                    builder.u16(0),
                    builder.u32(startRaw),
                    builder.u32(300_000),
                    builder.u32(300_000),
                    builder.u32(100_000),
                    builder.u8(150),
                    builder.u8(170),
                    builder.u8(90),
                    builder.u16(250),
                    builder.u16(5),
                ),
            )
            .definition(
                local = 6,
                globalNumber = 34,
                fields = listOf(
                    FitTestBuilder.field(253, TestBaseType.UINT32),
                    FitTestBuilder.field(0, TestBaseType.UINT32),
                    FitTestBuilder.field(1, TestBaseType.UINT8),
                    FitTestBuilder.field(2, TestBaseType.ENUM),
                ),
            )
            .data(
                6,
                listOf(builder.u32(startRaw + 300), builder.u32(300_000), builder.u8(1), builder.u8(4)),
            )

        val result = parser.parse(builder.build())
        val activity = result.activity

        assertThat(activity.sport).isEqualTo(Sport.TREADMILL)
        assertThat(activity.subSport).isEqualTo("treadmill")
        assertThat(activity.startTime).isEqualTo(start)
        assertThat(activity.durationSec).isEqualTo(300)
        assertThat(activity.movingTimeSec).isEqualTo(300)
        assertThat(activity.distanceM).isEqualTo(1_000.0)
        assertThat(activity.averageHeartRate).isEqualTo(150)
        assertThat(activity.maxHeartRate).isEqualTo(170)
        assertThat(activity.averageCadenceSpm).isEqualTo(180)
        assertThat(activity.maxCadenceSpm).isEqualTo(190)
        assertThat(activity.averagePowerW).isEqualTo(250)
        assertThat(activity.maxPowerW).isEqualTo(300)
        assertThat(activity.averageStepLengthM).isEqualTo(0.85)
        assertThat(activity.elevationGainM).isEqualTo(5.0)
        assertThat(activity.elevationLossM).isEqualTo(6.0)
        assertThat(activity.calories).isEqualTo(100)
        assertThat(activity.averagePace?.secondsPerKm).isEqualTo(300)
        assertThat(activity.samples).hasSize(2)
        assertThat(activity.laps).hasSize(1)

        val first = activity.samples.first()
        assertThat(first.offsetSec).isZero()
        assertThat(first.latitude).isEqualTo(45.0)
        assertThat(first.longitude).isEqualTo(22.5)
        assertThat(first.altitudeM).isCloseTo(34.2, within(0.0001))
        assertThat(first.speedMps).isCloseTo(3.333, within(0.0001))
        assertThat(first.heartRate).isEqualTo(150)
        assertThat(first.cadenceSpm).isEqualTo(180)
        assertThat(first.powerW).isEqualTo(250)
        assertThat(first.stepLengthM).isCloseTo(0.85, within(0.0001))
        assertThat(first.developerFields).containsEntry("SpO2", 98.5)

        val second = activity.samples[1]
        assertThat(second.offsetSec).isEqualTo(1)
        assertThat(second.heartRate).isEqualTo(151)
        assertThat(second.cadenceSpm).isEqualTo(182)
        assertThat(second.developerFields).containsEntry("SpO2", 98.0)

        assertThat(result.fileId?.fileType).isEqualTo("activity")
        assertThat(result.fileId?.productName).isEqualTo("Test Device")
        assertThat(result.device?.productName).isNull()
        assertThat(result.device?.source).isEqualTo("example.invalid")
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `handles compressed timestamp rollover`() {
        val base = FitTestBuilder.fitSeconds(Instant.parse("2026-10-28T10:00:00Z"))
        val startRaw = (base and 0x1F.inv()) + 30
        val start = FitTestBuilder.fitInstant(startRaw)
        val builder = FitTestBuilder(littleEndian = true)

        builder
            .definition(
                local = 0,
                globalNumber = 18,
                fields = listOf(
                    FitTestBuilder.field(253, TestBaseType.UINT32),
                    FitTestBuilder.field(2, TestBaseType.UINT32),
                    FitTestBuilder.field(5, TestBaseType.ENUM),
                    FitTestBuilder.field(7, TestBaseType.UINT32),
                ),
            )
            .data(0, listOf(builder.u32(startRaw + 2), builder.u32(startRaw), builder.u8(1), builder.u32(2_000)))
            .definition(
                local = 1,
                globalNumber = 20,
                fields = listOf(
                    FitTestBuilder.field(253, TestBaseType.UINT32),
                    FitTestBuilder.field(3, TestBaseType.UINT8),
                ),
            )
            .data(1, listOf(builder.u32(startRaw), builder.u8(150)))
            .compressedData(1, 0, listOf(builder.u8(151)))
            .definition(
                local = 2,
                globalNumber = 34,
                fields = listOf(FitTestBuilder.field(253, TestBaseType.UINT32)),
            )
            .data(2, listOf(builder.u32(startRaw + 2)))

        val activity = parser.parse(builder.build()).activity

        assertThat(activity.startTime).isEqualTo(start)
        assertThat(activity.durationSec).isEqualTo(2)
        assertThat(activity.samples.map { it.offsetSec }).containsExactly(0, 2)
        assertThat(activity.samples.map { it.heartRate }).containsExactly(150, 151)
    }

    @Test
    fun `uses event start and record bounds when session is absent`() {
        val start = Instant.parse("2026-10-28T10:00:00Z")
        val startRaw = FitTestBuilder.fitSeconds(start)
        val builder = FitTestBuilder(littleEndian = true)

        builder
            .definition(
                local = 0,
                globalNumber = 21,
                fields = listOf(
                    FitTestBuilder.field(253, TestBaseType.UINT32),
                    FitTestBuilder.field(0, TestBaseType.ENUM),
                    FitTestBuilder.field(1, TestBaseType.ENUM),
                ),
            )
            .data(0, listOf(builder.u32(startRaw), builder.u8(0), builder.u8(0)))
            .definition(
                local = 1,
                globalNumber = 20,
                fields = listOf(
                    FitTestBuilder.field(253, TestBaseType.UINT32),
                    FitTestBuilder.field(3, TestBaseType.UINT8),
                ),
            )
            .data(1, listOf(builder.u32(startRaw), builder.u8(140)))
            .data(1, listOf(builder.u32(startRaw + 10), builder.u8(145)))

        val result = parser.parse(builder.build())
        val activity = result.activity

        assertThat(activity.startTime).isEqualTo(start)
        assertThat(activity.durationSec).isEqualTo(10)
        assertThat(activity.sport).isEqualTo(Sport.OTHER)
        assertThat(activity.averageHeartRate).isEqualTo(143)
        assertThat(activity.maxHeartRate).isEqualTo(145)
        assertThat(result.warnings.map { it.code })
            .contains(FitWarningCode.MISSING_SESSION, FitWarningCode.UNKNOWN_SPORT)
    }
}
