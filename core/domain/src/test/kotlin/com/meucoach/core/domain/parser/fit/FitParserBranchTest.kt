package com.meucoach.core.domain.parser.fit

import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Test
import java.time.Instant

class FitParserBranchTest {
    private val parser = FitParser()
    private val start = Instant.parse("2026-10-28T10:00:00Z")
    private val startRaw = FitTestBuilder.fitSeconds(start)

    @Test
    fun `uses session timer when elapsed is zero and record start fallback`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            18,
            listOf(
                FitTestBuilder.field(5, TestBaseType.ENUM),
                FitTestBuilder.field(6, TestBaseType.ENUM),
                FitTestBuilder.field(7, TestBaseType.UINT32),
                FitTestBuilder.field(8, TestBaseType.UINT32),
            ),
        )
        builder.data(0, listOf(builder.u8(11), builder.u8(3), builder.u32(0), builder.u32(60_000)))
        builder.definition(
            1,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(3, TestBaseType.UINT8),
            ),
        )
        builder.data(1, listOf(builder.u32(startRaw), builder.u8(150)))

        val activity = parser.parse(builder.build()).activity

        assertThat(activity.startTime).isEqualTo(start)
        assertThat(activity.durationSec).isEqualTo(60)
        assertThat(activity.movingTimeSec).isEqualTo(60)
        assertThat(activity.sport).isEqualTo(Sport.WALKING)
        assertThat(activity.subSport).isEqualTo("trail")
        assertThat(activity.samples).hasSize(1)
        assertThat(activity.averageCadenceSpm).isNull()
        assertThat(activity.averagePowerW).isNull()
        assertThat(activity.averageStepLengthM).isNull()
    }

    @Test
    fun `falls back to record duration when activity timer is zero`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            34,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(0, TestBaseType.UINT32),
            ),
        )
        builder.data(0, listOf(builder.u32(startRaw), builder.u32(0)))
        builder.definition(
            1,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(3, TestBaseType.UINT8),
            ),
        )
        builder.data(1, listOf(builder.u32(startRaw), builder.u8(140)))
        builder.data(1, listOf(builder.u32(startRaw + 10), builder.u8(150)))

        val activity = parser.parse(builder.build()).activity

        assertThat(activity.startTime).isEqualTo(start)
        assertThat(activity.durationSec).isEqualTo(10)
        assertThat(activity.movingTimeSec).isNull()
        assertThat(activity.samples).hasSize(2)
    }

    @Test
    fun `handles records without optional sample fields`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            18,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(5, TestBaseType.ENUM),
                FitTestBuilder.field(8, TestBaseType.UINT32),
            ),
        )
        builder.data(0, listOf(builder.u32(startRaw), builder.u8(1), builder.u32(60_000)))
        builder.definition(
            1,
            20,
            listOf(FitTestBuilder.field(253, TestBaseType.UINT32)),
        )
        builder.data(1, listOf(builder.u32(startRaw)))

        val activity = parser.parse(builder.build()).activity
        val sample = activity.samples.single()

        assertThat(sample.heartRate).isNull()
        assertThat(sample.cadenceSpm).isNull()
        assertThat(sample.powerW).isNull()
        assertThat(sample.stepLengthM).isNull()
        assertThat(sample.speedMps).isNull()
        assertThat(sample.altitudeM).isNull()
        assertThat(sample.latitude).isNull()
        assertThat(sample.longitude).isNull()
        assertThat(sample.developerFields).isEmpty()
    }

    @Test
    fun `handles zero lap elapsed and zero lap speed`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            18,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(5, TestBaseType.ENUM),
                FitTestBuilder.field(8, TestBaseType.UINT32),
            ),
        )
        builder.data(0, listOf(builder.u32(startRaw), builder.u8(1), builder.u32(60_000)))
        builder.definition(
            1,
            19,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(7, TestBaseType.UINT32),
                FitTestBuilder.field(8, TestBaseType.UINT32),
                FitTestBuilder.field(13, TestBaseType.UINT16),
            ),
        )
        builder.data(
            1,
            listOf(
                builder.u32(startRaw),
                builder.u32(0),
                builder.u32(60_000),
                builder.u16(0),
            ),
        )

        val lap = parser.parse(builder.build()).activity.laps.single()

        assertThat(lap.durationSec).isEqualTo(60)
        assertThat(lap.averagePace).isNull()
    }

    @Test
    fun `handles missing file id and device optional fields`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            0,
            listOf(
                FitTestBuilder.field(1, TestBaseType.UINT16),
                FitTestBuilder.field(8, TestBaseType.STRING, 16),
            ),
        )
        builder.data(0, listOf(builder.u16(339), builder.string("", 16)))
        builder.definition(
            1,
            23,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(27, TestBaseType.STRING, 16),
            ),
        )
        builder.data(1, listOf(builder.u32(startRaw), builder.string("", 16)))
        builder.definition(
            2,
            18,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(5, TestBaseType.STRING, 4),
                FitTestBuilder.field(8, TestBaseType.UINT32),
            ),
        )
        builder.data(2, listOf(builder.u32(startRaw), builder.string("run", 4), builder.u32(60_000)))

        val result = parser.parse(builder.build())

        assertThat(result.fileId?.fileType).isNull()
        assertThat(result.fileId?.serialNumber).isNull()
        assertThat(result.fileId?.productName).isNull()
        assertThat(result.device?.source).isNull()
        assertThat(result.device?.productName).isNull()
        assertThat(result.activity.sport).isEqualTo(Sport.OTHER)
        assertThat(result.warnings.map { it.code }).contains(FitWarningCode.UNKNOWN_SPORT)
    }

    @Test
    fun `falls back to null pace when seconds per km rounds to zero`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            18,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(5, TestBaseType.ENUM),
                FitTestBuilder.field(8, TestBaseType.UINT32),
                FitTestBuilder.field(9, TestBaseType.UINT32),
            ),
        )
        builder.data(
            0,
            listOf(builder.u32(startRaw), builder.u8(1), builder.u32(1_000), builder.u32(300_000)),
        )

        val activity = parser.parse(builder.build()).activity

        assertThat(activity.distanceM).isEqualTo(3_000.0)
        assertThat(activity.averagePace).isNull()
    }

    @Test
    fun `ignores non finite developer fields`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            18,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(5, TestBaseType.ENUM),
                FitTestBuilder.field(8, TestBaseType.UINT32),
            ),
        )
        builder.data(0, listOf(builder.u32(startRaw), builder.u8(1), builder.u32(60_000)))
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
                builder.u8(TestBaseType.FLOAT32.code),
                builder.string("value", 16),
            ),
        )
        builder.definition(
            2,
            20,
            listOf(FitTestBuilder.field(253, TestBaseType.UINT32)),
            developerFields = listOf(TestDeveloperField(0, 4, 0)),
        )
        builder.data(2, listOf(builder.u32(startRaw), builder.f32(Float.NaN)))

        val sample = parser.parse(builder.build()).activity.samples.single()

        assertThat(sample.developerFields).isEmpty()
    }

    @Test
    fun `rejects a record-only activity with zero duration`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            20,
            listOf(FitTestBuilder.field(253, TestBaseType.UINT32)),
        )
        builder.data(0, listOf(builder.u32(startRaw)))

        val error = catchThrowable { parser.parse(builder.build()) }

        assertThat(error).isInstanceOf(FitParseException::class.java)
        assertThat((error as FitParseException).code).isEqualTo(FitErrorCode.INVALID_DURATION)
    }
}
