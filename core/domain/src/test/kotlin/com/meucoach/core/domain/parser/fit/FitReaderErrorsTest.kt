package com.meucoach.core.domain.parser.fit

import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Test
import java.time.Instant

class FitReaderErrorsTest {
    private val parser = FitParser()
    private val start = Instant.parse("2026-10-28T10:00:00Z")
    private val startRaw = FitTestBuilder.fitSeconds(start)

    @Test
    fun `rejects empty and short files`() {
        assertCode(ByteArray(0), FitErrorCode.EMPTY_FILE)
        assertCode(ByteArray(5), FitErrorCode.TOO_SHORT)
    }

    @Test
    fun `rejects invalid header signature and size`() {
        val badSignature = minimalFile().copyOf().also { it[8] = 0 }
        assertCode(badSignature, FitErrorCode.INVALID_HEADER)

        val badSize = minimalFile().copyOf().also { it[0] = 13 }
        assertCode(badSize, FitErrorCode.INVALID_HEADER)

        val badArchitecture = FitTestBuilder()
            .raw(byteArrayOf(0x40, 0x00, 0x02, 0x00, 0x12, 0x01, 0x01, 0x01, 0x02))
            .build()
        assertCode(badArchitecture, FitErrorCode.INVALID_DEFINITION)
    }

    @Test
    fun `rejects bad header and file CRC`() {
        val badHeader = minimalFile().copyOf().also { it[12] = (it[12].toInt() xor 1).toByte() }
        assertCode(badHeader, FitErrorCode.INVALID_CRC)

        val badFile = minimalFile().copyOf().also {
            it[it.lastIndex] = (it[it.lastIndex].toInt() xor 1).toByte()
        }
        assertCode(badFile, FitErrorCode.INVALID_CRC)
    }

    @Test
    fun `rejects truncated data and oversized data size`() {
        val file = minimalFile()
        assertCode(file.copyOf(file.size - 1), FitErrorCode.TRUNCATED)

        val oversized = file.copyOf()
        writeLittleUInt32(oversized, 4, 0xFFFFFF)
        assertCode(oversized, FitErrorCode.TRUNCATED)
    }

    @Test
    fun `warns about trailing bytes and zero CRC`() {
        val trailing = minimalFile() + byteArrayOf(0x7F)
        assertThat(parser.parse(trailing).warnings.map { it.code })
            .contains(FitWarningCode.TRAILING_DATA)

        val zeroCrc = minimalFile().copyOf().also {
            it[it.lastIndex - 1] = 0
            it[it.lastIndex] = 0
        }
        assertThat(parser.parse(zeroCrc).warnings.map { it.code })
            .contains(FitWarningCode.MISSING_CRC)
    }

    @Test
    fun `accepts a twelve byte header without header CRC`() {
        val result = parser.parse(minimalFile(headerSize = 12))

        assertThat(result.activity.durationSec).isEqualTo(60)
        assertThat(result.warnings.map { it.code }).contains(FitWarningCode.MISSING_FILE_ID)
    }

    @Test
    fun `rejects data messages with undefined local type`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.addSession()
        builder.raw(byteArrayOf(2))

        assertCode(builder.build(), FitErrorCode.UNKNOWN_LOCAL_MESSAGE)
    }

    @Test
    fun `rejects invalid field definitions`() {
        val unknownBaseType = FitTestBuilder()
            .raw(byteArrayOf(0x40, 0x00, 0x00, 0x12, 0x00, 0x01, 0x01, 0x01, 0xFF.toByte()))
            .build()
        assertCode(unknownBaseType, FitErrorCode.INVALID_DEFINITION)

        val zeroSize = FitTestBuilder()
            .raw(byteArrayOf(0x40, 0x00, 0x00, 0x12, 0x00, 0x01, 0x01, 0x00, 0x02))
            .build()
        assertCode(zeroSize, FitErrorCode.INVALID_DEFINITION)

        val wrongSize = FitTestBuilder()
            .raw(byteArrayOf(0x40, 0x00, 0x00, 0x12, 0x00, 0x01, 0x01, 0x03, 0x84.toByte()))
            .build()
        assertCode(wrongSize, FitErrorCode.INVALID_DEFINITION)
    }

    @Test
    fun `warns about compressed message without timestamp`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.addSession()
        builder.definition(1, 20, listOf(FitTestBuilder.field(3, TestBaseType.UINT8)))
        builder.compressedData(1, 0, listOf(builder.u8(150)))

        val result = parser.parse(builder.build())

        assertThat(result.activity.samples).isEmpty()
        assertThat(result.warnings.map { it.code }).contains(
            FitWarningCode.COMPRESSED_WITHOUT_TIMESTAMP,
            FitWarningCode.MISSING_TIMESTAMP,
        )
    }

    @Test
    fun `warns about unknown developer fields`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.addSession()
        builder.definition(
            1,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(3, TestBaseType.UINT8),
            ),
            developerFields = listOf(TestDeveloperField(0, 4, 0)),
        )
        builder.data(1, listOf(builder.u32(startRaw), builder.u8(150), builder.f32(98.0f)))

        val result = parser.parse(builder.build())

        assertThat(result.activity.samples).hasSize(1)
        assertThat(result.warnings.map { it.code })
            .contains(FitWarningCode.UNKNOWN_DEVELOPER_FIELD)
    }

    @Test
    fun `warns about invalid developer field size`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.addSession()
        builder.definition(
            2,
            206,
            listOf(
                FitTestBuilder.field(0, TestBaseType.UINT8),
                FitTestBuilder.field(1, TestBaseType.UINT8),
                FitTestBuilder.field(2, TestBaseType.ENUM),
                FitTestBuilder.field(3, TestBaseType.STRING, 16),
            ),
        )
        builder.data(
            2,
            listOf(
                builder.u8(0),
                builder.u8(0),
                builder.u8(TestBaseType.UINT16.code),
                builder.string("SpO2", 16),
            ),
        )
        builder.definition(
            1,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(3, TestBaseType.UINT8),
            ),
            developerFields = listOf(TestDeveloperField(0, 3, 0)),
        )
        builder.data(1, listOf(builder.u32(startRaw), builder.u8(150), byteArrayOf(0, 0, 1)))

        val result = parser.parse(builder.build())

        assertThat(result.activity.samples).hasSize(1)
        assertThat(result.warnings.map { it.code })
            .contains(FitWarningCode.UNKNOWN_DEVELOPER_FIELD)
    }

    @Test
    fun `warns when a record has no timestamp`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.addSession()
        builder.definition(1, 20, listOf(FitTestBuilder.field(3, TestBaseType.UINT8)))
        builder.data(1, listOf(builder.u8(150)))

        val result = parser.parse(builder.build())

        assertThat(result.activity.samples).isEmpty()
        assertThat(result.warnings.map { it.code }).contains(FitWarningCode.MISSING_TIMESTAMP)
    }

    @Test
    fun `warns when samples precede the activity start`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.addSession(start = startRaw + 10)
        builder.definition(
            1,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(3, TestBaseType.UINT8),
            ),
        )
        builder.data(1, listOf(builder.u32(startRaw), builder.u8(150)))

        val result = parser.parse(builder.build())

        assertThat(result.activity.samples).isEmpty()
        assertThat(result.warnings.map { it.code })
            .contains(FitWarningCode.NEGATIVE_SAMPLE_OFFSET)
    }

    @Test
    fun `warns about multiple sessions and unknown sport`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.addSession(local = 0, sport = null)
        builder.addSession(local = 1, sport = null)

        val result = parser.parse(builder.build())

        assertThat(result.activity.sport).isEqualTo(Sport.OTHER)
        assertThat(result.warnings.map { it.code }).contains(
            FitWarningCode.MULTIPLE_SESSIONS,
            FitWarningCode.UNKNOWN_SPORT,
        )
    }

    @Test
    fun `rejects activity without a positive duration`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(0, 18, listOf(FitTestBuilder.field(2, TestBaseType.UINT32)))
        builder.data(0, listOf(builder.u32(startRaw)))

        assertCode(builder.build(), FitErrorCode.INVALID_DURATION)
    }

    @Test
    fun `rejects file without an activity anchor`() {
        val file = FitTestBuilder().build()

        assertCode(file, FitErrorCode.MISSING_ACTIVITY)
    }

    private fun FitTestBuilder.addSession(
        local: Int = 0,
        start: Long = startRaw,
        sport: Int? = 1,
        elapsedMs: Long = 60_000,
    ) {
        val fields = buildList {
            add(FitTestBuilder.field(2, TestBaseType.UINT32))
            if (sport != null) add(FitTestBuilder.field(5, TestBaseType.ENUM))
            add(FitTestBuilder.field(7, TestBaseType.UINT32))
        }
        definition(local, 18, fields)
        val values = buildList {
            add(u32(start))
            if (sport != null) add(u8(sport))
            add(u32(elapsedMs))
        }
        data(local, values)
    }

    private fun FitTestBuilder.addRecord(local: Int = 1, timestamp: Long = startRaw, heartRate: Int = 150) {
        definition(
            local,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(3, TestBaseType.UINT8),
            ),
        )
        data(local, listOf(u32(timestamp), u8(heartRate)))
    }

    private fun minimalFile(headerSize: Int = 14): ByteArray {
        val builder = FitTestBuilder(headerSize = headerSize, littleEndian = true)
        builder.addSession()
        builder.addRecord()
        return builder.build()
    }

    private fun assertCode(bytes: ByteArray, code: FitErrorCode) {
        val error = catchThrowable { parser.parse(bytes) }
        assertThat(error).isInstanceOf(FitParseException::class.java)
        assertThat((error as FitParseException).code).isEqualTo(code)
    }

    private fun writeLittleUInt32(bytes: ByteArray, offset: Int, value: Int) {
        for (index in 0..3) {
            bytes[offset + index] = (value shr (index * 8)).toByte()
        }
    }
}
