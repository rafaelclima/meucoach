package com.meucoach.core.domain.parser.fit

import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.Instant

class FitParserCoverageTest {
    private val parser = FitParser()
    private val start = Instant.parse("2026-10-28T10:00:00Z")
    private val startRaw = FitTestBuilder.fitSeconds(start)

    @Test
    fun `uses activity timestamp and timer as fallbacks`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            34,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(0, TestBaseType.UINT32),
                FitTestBuilder.field(1, TestBaseType.UINT8),
                FitTestBuilder.field(2, TestBaseType.ENUM),
            ),
        )
        builder.data(0, listOf(builder.u32(startRaw), builder.u32(120_000), builder.u8(1), builder.u8(4)))

        val result = parser.parse(builder.build())
        val activity = result.activity

        assertThat(activity.startTime).isEqualTo(start)
        assertThat(activity.durationSec).isEqualTo(120)
        assertThat(activity.movingTimeSec).isEqualTo(120)
        assertThat(activity.distanceM).isZero()
        assertThat(activity.averageHeartRate).isNull()
        assertThat(activity.maxHeartRate).isNull()
        assertThat(activity.averageCadenceSpm).isNull()
        assertThat(activity.maxCadenceSpm).isNull()
        assertThat(activity.averagePowerW).isNull()
        assertThat(activity.maxPowerW).isNull()
        assertThat(activity.averageStepLengthM).isNull()
        assertThat(activity.elevationGainM).isNull()
        assertThat(activity.elevationLossM).isNull()
        assertThat(activity.calories).isNull()
        assertThat(activity.samples).isEmpty()
        assertThat(activity.laps).isEmpty()
        assertThat(result.warnings.map { it.code }).contains(
            FitWarningCode.MISSING_FILE_ID,
            FitWarningCode.MISSING_SESSION,
            FitWarningCode.UNKNOWN_SPORT,
        )
    }

    @Test
    fun `uses record bounds and samples when session is absent`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            0,
            listOf(
                FitTestBuilder.field(0, TestBaseType.ENUM),
                FitTestBuilder.field(1, TestBaseType.UINT16),
                FitTestBuilder.field(3, TestBaseType.UINT32),
                FitTestBuilder.field(8, TestBaseType.STRING, 16),
            ),
        )
        builder.data(0, listOf(builder.u8(4), builder.u16(339), builder.u32(123), builder.string("Device", 16)))
        builder.definition(
            1,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(3, TestBaseType.UINT8),
                FitTestBuilder.field(4, TestBaseType.UINT8),
                FitTestBuilder.field(7, TestBaseType.UINT16),
                FitTestBuilder.field(85, TestBaseType.UINT16),
                FitTestBuilder.field(6, TestBaseType.UINT16),
                FitTestBuilder.field(2, TestBaseType.UINT16),
                FitTestBuilder.field(5, TestBaseType.UINT32),
                FitTestBuilder.field(0, TestBaseType.SINT32),
                FitTestBuilder.field(1, TestBaseType.SINT32),
            ),
        )
        builder.data(
            1,
            listOf(
                builder.u32(startRaw),
                builder.u8(140),
                builder.u8(60),
                builder.u16(200),
                builder.u16(8_000),
                builder.u16(2_500),
                builder.u16(2_671),
                builder.u32(100_000),
                builder.s32(0x20000000),
                builder.s32(0x10000000),
            ),
        )
        builder.data(
            1,
            listOf(
                builder.u32(startRaw + 10),
                builder.u8(150),
                builder.u8(62),
                builder.u16(200),
                builder.u16(8_000),
                builder.u16(2_500),
                builder.u16(2_671),
                builder.u32(100_000),
                builder.s32(0x20000000),
                builder.s32(0x10000000),
            ),
        )

        val result = parser.parse(builder.build())
        val activity = result.activity

        assertThat(activity.sport).isEqualTo(Sport.OTHER)
        assertThat(activity.startTime).isEqualTo(start)
        assertThat(activity.durationSec).isEqualTo(10)
        assertThat(activity.distanceM).isEqualTo(1_000.0)
        assertThat(activity.averageHeartRate).isEqualTo(145)
        assertThat(activity.maxHeartRate).isEqualTo(150)
        assertThat(activity.averageCadenceSpm).isEqualTo(61)
        assertThat(activity.maxCadenceSpm).isEqualTo(62)
        assertThat(activity.averagePowerW).isEqualTo(200)
        assertThat(activity.maxPowerW).isEqualTo(200)
        assertThat(activity.averageStepLengthM).isNull()
        assertThat(activity.averagePace?.secondsPerKm).isEqualTo(10)
        assertThat(activity.sourceId).isEqualTo("123")
        assertThat(activity.deviceName).isEqualTo("Device")
        assertThat(result.fileId?.fileType).isEqualTo("activity")
        assertThat(result.fileId?.serialNumber).isEqualTo(123)
        assertThat(activity.samples).hasSize(2)
        assertThat(activity.samples.first().offsetSec).isZero()
        assertThat(activity.samples.last().offsetSec).isEqualTo(10)
        assertThat(activity.samples.first().latitude).isEqualTo(45.0)
        assertThat(activity.samples.first().longitude).isEqualTo(22.5)
        assertThat(activity.samples.first().altitudeM).isCloseTo(34.2, within(0.0001))
        assertThat(activity.samples.first().speedMps).isCloseTo(2.5, within(0.0001))
        assertThat(activity.samples.first().stepLengthM).isCloseTo(0.8, within(0.0001))
        assertThat(result.warnings.map { it.code }).contains(
            FitWarningCode.MISSING_SESSION,
            FitWarningCode.UNKNOWN_SPORT,
        )
    }

    @Test
    fun `uses file id time when no activity anchor exists`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            0,
            listOf(
                FitTestBuilder.field(0, TestBaseType.ENUM),
                FitTestBuilder.field(3, TestBaseType.UINT32),
                FitTestBuilder.field(4, TestBaseType.UINT32),
                FitTestBuilder.field(8, TestBaseType.STRING, 16),
            ),
        )
        builder.data(
            0,
            listOf(builder.u8(99), builder.u32(1), builder.u32(startRaw), builder.string("Device", 16)),
        )

        val error = catchThrowable { parser.parse(builder.build()) }

        assertThat(error).isInstanceOf(FitParseException::class.java)
        assertThat((error as FitParseException).code).isEqualTo(FitErrorCode.INVALID_DURATION)
    }

    @Test
    fun `uses session device and lap fallbacks`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            0,
            listOf(
                FitTestBuilder.field(0, TestBaseType.ENUM),
                FitTestBuilder.field(3, TestBaseType.UINT32),
                FitTestBuilder.field(8, TestBaseType.STRING, 16),
            ),
        )
        builder.data(0, listOf(builder.u8(4), builder.u32(777), builder.string("Device", 16)))
        builder.definition(
            1,
            206,
            listOf(
                FitTestBuilder.field(0, TestBaseType.UINT8),
                FitTestBuilder.field(1, TestBaseType.UINT8),
                FitTestBuilder.field(2, TestBaseType.ENUM),
                FitTestBuilder.field(3, TestBaseType.STRING, 16),
                FitTestBuilder.field(8, TestBaseType.STRING, 8),
            ),
        )
        builder.data(
            1,
            listOf(
                builder.u8(0),
                builder.u8(0),
                builder.u8(TestBaseType.STRING.code),
                builder.string("source", 16),
                builder.string("text", 8),
            ),
        )
        builder.definition(
            2,
            23,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(2, TestBaseType.UINT16),
                FitTestBuilder.field(3, TestBaseType.UINT32),
                FitTestBuilder.field(27, TestBaseType.STRING, 16),
            ),
            developerFields = listOf(TestDeveloperField(0, 16, 0)),
        )
        builder.data(
            2,
            listOf(
                builder.u32(startRaw),
                builder.u16(339),
                builder.u32(42),
                builder.string("Watch", 16),
                builder.string("example.invalid", 16),
            ),
        )
        builder.definition(
            3,
            18,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(5, TestBaseType.ENUM),
                FitTestBuilder.field(6, TestBaseType.ENUM),
                FitTestBuilder.field(8, TestBaseType.UINT32),
                FitTestBuilder.field(16, TestBaseType.UINT8),
                FitTestBuilder.field(17, TestBaseType.UINT8),
                FitTestBuilder.field(14, TestBaseType.UINT16),
                FitTestBuilder.field(22, TestBaseType.UINT16),
                FitTestBuilder.field(11, TestBaseType.UINT16),
                FitTestBuilder.field(134, TestBaseType.UINT16),
            ),
        )
        builder.data(
            3,
            listOf(
                builder.u32(startRaw),
                builder.u8(11),
                builder.u8(4),
                builder.u32(60_000),
                builder.u8(150),
                builder.u8(170),
                builder.u16(2_500),
                builder.u16(0),
                builder.u16(0),
                builder.u16(0),
            ),
        )
        builder.definition(
            4,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(3, TestBaseType.UINT8),
                FitTestBuilder.field(4, TestBaseType.UINT8),
                FitTestBuilder.field(7, TestBaseType.UINT16),
                FitTestBuilder.field(85, TestBaseType.UINT16),
                FitTestBuilder.field(6, TestBaseType.UINT16),
                FitTestBuilder.field(2, TestBaseType.UINT16),
                FitTestBuilder.field(0, TestBaseType.SINT32),
                FitTestBuilder.field(1, TestBaseType.SINT32),
            ),
        )
        builder.data(
            4,
            listOf(
                builder.u32(startRaw),
                builder.u8(150),
                builder.u8(90),
                builder.u16(250),
                builder.u16(8_000),
                builder.u16(2_500),
                builder.u16(2_671),
                builder.s32(0x20000000),
                builder.s32(0x10000000),
            ),
        )
        builder.definition(
            5,
            19,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(7, TestBaseType.UINT32),
                FitTestBuilder.field(15, TestBaseType.UINT8),
                FitTestBuilder.field(16, TestBaseType.UINT8),
                FitTestBuilder.field(17, TestBaseType.UINT8),
                FitTestBuilder.field(19, TestBaseType.UINT16),
                FitTestBuilder.field(21, TestBaseType.UINT16),
                FitTestBuilder.field(13, TestBaseType.UINT16),
            ),
        )
        builder.data(
            5,
            listOf(
                builder.u32(startRaw),
                builder.u32(60_000),
                builder.u8(150),
                builder.u8(170),
                builder.u8(90),
                builder.u16(250),
                builder.u16(0),
                builder.u16(2_500),
            ),
        )
        builder.definition(
            6,
            19,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(8, TestBaseType.UINT32),
                FitTestBuilder.field(254, TestBaseType.UINT16),
                FitTestBuilder.field(9, TestBaseType.UINT32),
                FitTestBuilder.field(110, TestBaseType.UINT16),
                FitTestBuilder.field(21, TestBaseType.UINT16),
            ),
        )
        builder.data(
            6,
            listOf(
                builder.u32(startRaw),
                builder.u32(30_000),
                builder.u16(7),
                builder.u32(50_000),
                builder.u16(3_000),
                builder.u16(0),
            ),
        )
        builder.definition(7, 19, listOf(FitTestBuilder.field(2, TestBaseType.UINT32)))
        builder.data(7, listOf(builder.u32(startRaw)))

        val result = parser.parse(builder.build())
        val activity = result.activity

        assertThat(activity.sport).isEqualTo(Sport.WALKING)
        assertThat(activity.subSport).isEqualTo("track")
        assertThat(activity.startTime).isEqualTo(start)
        assertThat(activity.durationSec).isEqualTo(60)
        assertThat(activity.movingTimeSec).isEqualTo(60)
        assertThat(activity.distanceM).isZero()
        assertThat(activity.averageHeartRate).isEqualTo(150)
        assertThat(activity.maxHeartRate).isEqualTo(170)
        assertThat(activity.averageCadenceSpm).isEqualTo(90)
        assertThat(activity.maxCadenceSpm).isEqualTo(90)
        assertThat(activity.averagePowerW).isEqualTo(250)
        assertThat(activity.maxPowerW).isEqualTo(250)
        assertThat(activity.averageStepLengthM).isNull()
        assertThat(activity.elevationGainM).isZero()
        assertThat(activity.elevationLossM).isNull()
        assertThat(activity.calories).isZero()
        assertThat(activity.averagePace?.secondsPerKm).isEqualTo(400)
        assertThat(activity.sourceId).isEqualTo("777")
        assertThat(activity.deviceName).isEqualTo("Watch")
        assertThat(result.device?.source).isEqualTo("example.invalid")
        assertThat(result.device?.productName).isEqualTo("Watch")
        assertThat(activity.laps).hasSize(3)
        assertThat(activity.laps[0].index).isZero()
        assertThat(activity.laps[0].durationSec).isEqualTo(60)
        assertThat(activity.laps[0].distanceM).isZero()
        assertThat(activity.laps[0].averagePace?.secondsPerKm).isEqualTo(400)
        assertThat(activity.laps[0].elevationGainM).isZero()
        assertThat(activity.laps[1].index).isEqualTo(7)
        assertThat(activity.laps[1].durationSec).isEqualTo(30)
        assertThat(activity.laps[1].distanceM).isEqualTo(500.0)
        assertThat(activity.laps[1].averagePace).isNotNull()
        assertThat(activity.laps[2].index).isEqualTo(2)
        assertThat(activity.laps[2].durationSec).isZero()
        assertThat(activity.laps[2].distanceM).isZero()
        assertThat(activity.laps[2].averagePace).isNull()
        assertThat(activity.laps[2].elevationGainM).isNull()
    }

    @Test
    fun `uses float fields and rejects out of range sample values`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            18,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(5, TestBaseType.ENUM),
                FitTestBuilder.field(6, TestBaseType.ENUM),
                FitTestBuilder.field(7, TestBaseType.UINT32),
            ),
        )
        builder.data(0, listOf(builder.u32(startRaw), builder.u8(1), builder.u8(45), builder.u32(60_000)))
        builder.definition(
            1,
            206,
            listOf(
                FitTestBuilder.field(0, TestBaseType.UINT8),
                FitTestBuilder.field(1, TestBaseType.UINT8),
                FitTestBuilder.field(2, TestBaseType.ENUM),
                FitTestBuilder.field(3, TestBaseType.STRING, 16),
            ),
        )
        builder.data(
            1,
            listOf(
                builder.u8(0),
                builder.u8(0),
                builder.u8(TestBaseType.STRING.code),
                builder.string("note", 16),
            ),
        )
        builder.definition(
            2,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(3, TestBaseType.UINT8),
                FitTestBuilder.field(4, TestBaseType.UINT8),
                FitTestBuilder.field(7, TestBaseType.UINT16),
                FitTestBuilder.field(85, TestBaseType.FLOAT32),
                FitTestBuilder.field(73, TestBaseType.FLOAT32),
                FitTestBuilder.field(78, TestBaseType.FLOAT32),
                FitTestBuilder.field(0, TestBaseType.SINT32),
                FitTestBuilder.field(1, TestBaseType.SINT32),
            ),
            developerFields = listOf(TestDeveloperField(0, 8, 0)),
        )
        builder.data(
            2,
            listOf(
                builder.u32(startRaw),
                builder.u8(20),
                builder.u8(200),
                builder.u16(3_000),
                builder.f32(4.0f),
                builder.f32(31.0f),
                builder.f32(10_000.0f),
                builder.s32(0x7FFFFFFF),
                builder.s32(0x7FFFFFFF),
                builder.string("note", 8),
            ),
        )

        val result = parser.parse(builder.build())
        val activity = result.activity

        assertThat(activity.sport).isEqualTo(Sport.TREADMILL)
        assertThat(activity.subSport).isEqualTo("indoor_running")
        assertThat(activity.samples).hasSize(1)
        assertThat(activity.samples.first().heartRate).isNull()
        assertThat(activity.samples.first().cadenceSpm).isNull()
        assertThat(activity.samples.first().powerW).isNull()
        assertThat(activity.samples.first().stepLengthM).isNull()
        assertThat(activity.samples.first().speedMps).isNull()
        assertThat(activity.samples.first().altitudeM).isNull()
        assertThat(activity.samples.first().latitude).isNull()
        assertThat(activity.samples.first().longitude).isNull()
        assertThat(activity.samples.first().developerFields).isEmpty()
        assertThat(activity.averageHeartRate).isNull()
        assertThat(activity.maxHeartRate).isNull()
        assertThat(activity.averageCadenceSpm).isNull()
        assertThat(activity.maxCadenceSpm).isNull()
        assertThat(activity.averagePowerW).isNull()
        assertThat(activity.maxPowerW).isNull()
        assertThat(activity.averageStepLengthM).isNull()
    }

    @Test
    fun `rejects samples whose offset does not fit an Int`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            18,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(5, TestBaseType.ENUM),
                FitTestBuilder.field(7, TestBaseType.UINT32),
            ),
        )
        builder.data(0, listOf(builder.u32(0), builder.u8(1), builder.u32(1_000)))
        builder.definition(
            1,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(3, TestBaseType.UINT8),
            ),
        )
        builder.data(1, listOf(builder.u32(2_200_000_000), builder.u8(150)))

        val result = parser.parse(builder.build())

        assertThat(result.activity.startTime).isEqualTo(FitTestBuilder.fitInstant(0))
        assertThat(result.activity.durationSec).isEqualTo(1)
        assertThat(result.activity.samples).isEmpty()
        assertThat(result.warnings.map { it.code })
            .contains(FitWarningCode.NEGATIVE_SAMPLE_OFFSET)
    }

    @Test
    fun `compressed record without a prior timestamp is ignored`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(3, TestBaseType.UINT8),
            ),
        )
        builder.compressedData(0, 0, listOf(builder.u8(150)))
        builder.definition(
            1,
            18,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(5, TestBaseType.ENUM),
                FitTestBuilder.field(7, TestBaseType.UINT32),
            ),
        )
        builder.data(1, listOf(builder.u32(startRaw), builder.u8(1), builder.u32(60_000)))

        val result = parser.parse(builder.build())

        assertThat(result.activity.startTime).isEqualTo(start)
        assertThat(result.activity.samples).isEmpty()
        assertThat(result.warnings.map { it.code }).contains(FitWarningCode.MISSING_TIMESTAMP)
    }

    @Test
    fun `uses session speed fallbacks for average pace`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            18,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(5, TestBaseType.ENUM),
                FitTestBuilder.field(8, TestBaseType.UINT32),
                FitTestBuilder.field(124, TestBaseType.UINT16),
            ),
        )
        builder.data(0, listOf(builder.u32(startRaw), builder.u8(1), builder.u32(60_000), builder.u16(3_000)))

        val activity = parser.parse(builder.build()).activity

        assertThat(activity.distanceM).isZero()
        assertThat(activity.averagePace).isEqualTo(Pace.fromSpeedMetersPerSecond(3.0))
    }
}
