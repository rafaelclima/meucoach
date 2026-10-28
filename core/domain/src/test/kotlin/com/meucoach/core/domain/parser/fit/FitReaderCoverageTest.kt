package com.meucoach.core.domain.parser.fit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Test
import java.time.Instant

class FitReaderCoverageTest {
    private val parser = FitParser()
    private val start = Instant.parse("2026-10-28T10:00:00Z")
    private val startRaw = FitTestBuilder.fitSeconds(start)

    @Test
    fun `rejects a declared data size larger than an Int`() {
        val file = FitTestBuilder().build().copyOf()
        file[4] = 0
        file[5] = 0
        file[6] = 0
        file[7] = 0x80.toByte()

        val error = catchThrowable { parser.parse(file) }

        assertThat(error).isInstanceOf(FitParseException::class.java)
        assertThat((error as FitParseException).code).isEqualTo(FitErrorCode.TRUNCATED)
    }

    @Test
    fun `rejects a developer field with zero size`() {
        val definition = byteArrayOf(
            0x60,
            0x00,
            0x00,
            0x14,
            0x00,
            0x01,
            0x03,
            0x01,
            0x02,
            0x01,
            0x00,
            0x00,
            0x00,
        )

        val error = catchThrowable { parser.parse(FitTestBuilder().raw(definition).build()) }

        assertThat(error).isInstanceOf(FitParseException::class.java)
        assertThat((error as FitParseException).code).isEqualTo(FitErrorCode.INVALID_DEFINITION)
    }

    @Test
    fun `ignores a record whose timestamp field is not numeric`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.addSession()
        builder.definition(
            1,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.STRING, 4),
                FitTestBuilder.field(3, TestBaseType.UINT8),
            ),
        )
        builder.data(1, listOf(builder.string("abcd", 4), builder.u8(150)))

        val result = parser.parse(builder.build())

        assertThat(result.activity.samples).isEmpty()
        assertThat(result.warnings.map { it.code }).contains(FitWarningCode.MISSING_TIMESTAMP)
    }

    @Test
    fun `scales developer fields and ignores incomplete descriptions`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.addSession()
        builder.addFieldDescription(local = 1, fields = listOf(1), values = listOf(builder.u8(0)))
        builder.addFieldDescription(local = 1, fields = listOf(0), values = listOf(builder.u8(0)))
        builder.addFieldDescription(
            local = 1,
            fields = listOf(0, 1, 2),
            values = listOf(builder.u8(0), builder.u8(0), builder.u8(TestBaseType.STRING.code)),
        )
        builder.addFieldDescription(
            local = 1,
            fields = listOf(0, 1, 2, 3),
            values = listOf(builder.u8(4), builder.u8(0), builder.u8(0xFF), builder.string("bad", 16)),
        )
        builder.addFieldDescription(
            local = 1,
            fields = listOf(0, 1, 2, 3, 6, 7, 8),
            values = listOf(
                builder.u8(0),
                builder.u8(0),
                builder.u8(TestBaseType.UINT16.code),
                builder.string("scaled", 16),
                builder.u8(10),
                byteArrayOf(2),
                builder.string("unit", 16),
            ),
        )
        builder.addFieldDescription(
            local = 1,
            fields = listOf(0, 1, 2, 3, 6),
            values = listOf(
                builder.u8(1),
                builder.u8(0),
                builder.u8(TestBaseType.UINT16.code),
                builder.string("zero", 16),
                builder.u8(0),
            ),
        )
        builder.addFieldDescription(
            local = 1,
            fields = listOf(0, 1, 2, 3, 6),
            values = listOf(
                builder.u8(2),
                builder.u8(0),
                builder.u8(TestBaseType.UINT16.code),
                builder.string("offsetless", 16),
                builder.u8(10),
            ),
        )
        builder.addFieldDescription(
            local = 1,
            fields = listOf(0, 1, 2, 3),
            values = listOf(
                builder.u8(3),
                builder.u8(0),
                builder.u8(TestBaseType.STRING.code),
                builder.string("text", 16),
            ),
        )
        builder.addFieldDescription(
            local = 1,
            fields = listOf(0, 1, 2, 3),
            values = listOf(
                builder.u8(4),
                builder.u8(0),
                builder.u8(0xFF),
                builder.string("invalid", 16),
            ),
        )
        builder.addFieldDescription(
            local = 1,
            fields = listOf(0, 1, 2, 3),
            values = listOf(
                builder.u8(5),
                builder.u8(0),
                builder.u8(TestBaseType.UINT8.code),
                builder.string("invalidValue", 16),
            ),
        )
        builder.addFieldDescription(
            local = 1,
            fields = listOf(0, 1, 2, 3, 6),
            values = listOf(
                builder.u8(6),
                builder.u8(0),
                builder.u8(TestBaseType.STRING.code),
                builder.string("scaledText", 16),
                builder.u8(10),
            ),
        )
        builder.addFieldDescription(
            local = 1,
            fields = listOf(0, 1, 2, 3, 6),
            values = listOf(
                builder.u8(7),
                builder.u8(0),
                builder.u8(TestBaseType.BYTE.code),
                builder.string("scaledBytes", 16),
                builder.u8(10),
            ),
        )

        builder.addRecord(local = 2, devIndex = 0, devSize = 4, devValue = builder.u16(100) + builder.u16(100))
        builder.addRecord(local = 3, devIndex = 1, devSize = 2, devValue = builder.u16(100))
        builder.addRecord(local = 4, devIndex = 2, devSize = 2, devValue = builder.u16(100))
        builder.addRecord(local = 5, devIndex = 3, devSize = 8, devValue = builder.string("text", 8))
        builder.addRecord(local = 6, devIndex = 4, devSize = 2, devValue = builder.u16(100))
        builder.addRecord(local = 7, devIndex = 5, devSize = 1, devValue = builder.u8(0xFF))
        builder.addRecord(local = 8, devIndex = 6, devSize = 8, devValue = builder.string("text", 8))
        builder.addRecord(local = 9, devIndex = 7, devSize = 4, devValue = byteArrayOf(1, 2, 3, 4))

        val result = parser.parse(builder.build())
        val samples = result.activity.samples

        assertThat(samples).hasSize(8)
        assertThat(samples[0].developerFields).containsEntry("scaled", 8.0)
        assertThat(samples[1].developerFields).containsEntry("zero", 100.0)
        assertThat(samples[2].developerFields).containsEntry("offsetless", 10.0)
        assertThat(samples[3].developerFields).isEmpty()
        assertThat(samples[4].developerFields).isEmpty()
        assertThat(samples[5].developerFields).isEmpty()
        assertThat(samples[6].developerFields).isEmpty()
        assertThat(samples[7].developerFields).isEmpty()
    }

    @Test
    fun `deduplicates repeated unknown developer field warnings`() {
        val builder = FitTestBuilder(littleEndian = true)
        builder.definition(
            0,
            20,
            listOf(FitTestBuilder.field(253, TestBaseType.UINT32)),
            developerFields = listOf(
                TestDeveloperField(0, 4, 0),
                TestDeveloperField(0, 4, 0),
            ),
        )
        builder.data(0, listOf(builder.u32(startRaw), builder.f32(1.0f), builder.f32(2.0f)))

        val decoded = FitReader(builder.build()).read()

        assertThat(decoded.warnings.map { it.code }).containsExactly(FitWarningCode.UNKNOWN_DEVELOPER_FIELD)
    }

    @Test
    fun `exposes field conversion fallbacks`() {
        assertThat(FitFieldValue.Numbers(emptyList()).asNumber()).isNull()
        assertThat(FitFieldValue.Text("text").asNumber()).isNull()
        assertThat(FitFieldValue.Bytes(byteArrayOf(1)).asNumber()).isNull()
        assertThat(FitFieldValue.Number(1.0).asText()).isNull()
        assertThat(FitFieldValue.Numbers(listOf(1.0)).asText()).isNull()
        assertThat(FitFieldValue.Bytes(byteArrayOf(1)).asText()).isNull()
    }

    private fun FitTestBuilder.addSession() {
        definition(
            0,
            18,
            listOf(
                FitTestBuilder.field(2, TestBaseType.UINT32),
                FitTestBuilder.field(5, TestBaseType.ENUM),
                FitTestBuilder.field(7, TestBaseType.UINT32),
            ),
        )
        data(0, listOf(u32(startRaw), u8(1), u32(60_000)))
    }

    private fun FitTestBuilder.addFieldDescription(local: Int, fields: List<Int>, values: List<ByteArray>) {
        val definitions = fields.map { number ->
            when (number) {
                3, 8 -> FitTestBuilder.field(number, TestBaseType.STRING, 16)
                6 -> FitTestBuilder.field(number, TestBaseType.UINT8)
                7 -> FitTestBuilder.field(number, TestBaseType.SINT8)
                else -> FitTestBuilder.field(number, TestBaseType.UINT8)
            }
        }
        definition(local, 206, definitions)
        data(local, values)
    }

    private fun FitTestBuilder.addRecord(local: Int, devIndex: Int, devSize: Int, devValue: ByteArray) {
        definition(
            local,
            20,
            listOf(
                FitTestBuilder.field(253, TestBaseType.UINT32),
                FitTestBuilder.field(3, TestBaseType.UINT8),
            ),
            developerFields = listOf(TestDeveloperField(0, devSize, devIndex)),
        )
        data(
            local,
            listOf(
                u32(startRaw + local),
                u8(150),
                devValue,
            ),
        )
    }
}
