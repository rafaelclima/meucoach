package com.meucoach.core.domain.parser.fit

import java.io.ByteArrayOutputStream
import java.time.Instant

internal enum class TestBaseType(val code: Int, val size: Int) {
    ENUM(0x00, 1),
    SINT8(0x01, 1),
    UINT8(0x02, 1),
    SINT16(0x83, 2),
    UINT16(0x84, 2),
    SINT32(0x85, 4),
    UINT32(0x86, 4),
    STRING(0x07, 1),
    FLOAT32(0x88, 4),
    FLOAT64(0x89, 8),
    UINT8Z(0x0A, 1),
    UINT16Z(0x8B, 2),
    UINT32Z(0x8C, 4),
    BYTE(0x0D, 1),
    SINT64(0x8E, 8),
    UINT64(0x8F, 8),
    UINT64Z(0x90, 8),
}

internal data class TestField(val number: Int, val size: Int, val baseType: TestBaseType)

internal data class TestDeveloperField(val number: Int, val size: Int, val developerDataIndex: Int)

internal class FitTestBuilder(private val headerSize: Int = 14, private val littleEndian: Boolean = false) {
    private val payload = ByteArrayOutputStream()
    private val definitions = mutableMapOf<Int, Pair<List<TestField>, List<TestDeveloperField>>>()

    fun definition(
        local: Int,
        globalNumber: Int,
        fields: List<TestField>,
        developerFields: List<TestDeveloperField> = emptyList(),
    ): FitTestBuilder {
        require(local in 0..15) { "Local type out of range" }
        require(fields.isNotEmpty() || developerFields.isNotEmpty()) {
            "Definition must have at least one field"
        }
        val developerFlag = if (developerFields.isEmpty()) 0 else 0x20
        payload.write(0x40 or developerFlag or local)
        payload.write(0)
        payload.write(if (littleEndian) 0 else 1)
        writeUInt16(payload, globalNumber)
        payload.write(fields.size)
        fields.forEach { field ->
            payload.write(field.number)
            payload.write(field.size)
            payload.write(field.baseType.code)
        }
        if (developerFields.isNotEmpty()) {
            payload.write(developerFields.size)
            developerFields.forEach { field ->
                payload.write(field.number)
                payload.write(field.size)
                payload.write(field.developerDataIndex)
            }
        }
        definitions[local] = fields to developerFields
        return this
    }

    fun data(local: Int, values: List<ByteArray>): FitTestBuilder {
        val definition = requireDefinition(local)
        val expected = definition.first.sumOf { it.size } + definition.second.sumOf { it.size }
        require(values.sumOf { it.size } == expected) {
            "Data for local $local has ${values.sumOf { it.size }} bytes, expected $expected"
        }
        payload.write(local)
        values.forEach { payload.write(it) }
        return this
    }

    fun compressedData(local: Int, timeOffset: Int, values: List<ByteArray>): FitTestBuilder {
        require(local in 0..3) { "Compressed timestamp only supports local types 0..3" }
        require(timeOffset in 0..31) { "Timestamp offset out of range" }
        val definition = requireDefinition(local)
        val expected = definition.first
            .filterNot { it.number == 253 }
            .sumOf { it.size } + definition.second.sumOf { it.size }
        require(values.sumOf { it.size } == expected) {
            "Compressed data for local $local has ${values.sumOf { it.size }} bytes, expected $expected"
        }
        payload.write(0x80 or (local shl 5) or timeOffset)
        values.forEach { payload.write(it) }
        return this
    }

    fun raw(bytes: ByteArray): FitTestBuilder {
        payload.write(bytes)
        return this
    }

    fun build(): ByteArray {
        require(headerSize == 12 || headerSize == 14) { "Header must be 12 or 14 bytes" }
        val data = payload.toByteArray()
        val header = ByteArrayOutputStream()
        header.write(headerSize)
        header.write(0x20)
        header.write(0x3C)
        header.write(0x53)
        writeUInt32(header, data.size.toLong())
        header.write(".FIT".toByteArray(Charsets.US_ASCII))
        if (headerSize == 14) {
            val headerBytes = header.toByteArray()
            writeUInt16(header, FitCrc.compute(headerBytes, 0, 12))
        }
        val result = ByteArrayOutputStream()
        result.write(header.toByteArray())
        result.write(data)
        val withoutCrc = result.toByteArray()
        writeUInt16(result, FitCrc.compute(withoutCrc))
        return result.toByteArray()
    }

    private fun requireDefinition(local: Int): Pair<List<TestField>, List<TestDeveloperField>> =
        checkNotNull(definitions[local]) { "No definition for local type $local" }

    fun u8(value: Int): ByteArray = byteArrayOf(value.toByte())

    fun u16(value: Int): ByteArray {
        val result = ByteArray(2)
        if (littleEndian) {
            result[0] = value.toByte()
            result[1] = (value shr 8).toByte()
        } else {
            result[0] = (value shr 8).toByte()
            result[1] = value.toByte()
        }
        return result
    }

    fun u32(value: Long): ByteArray {
        val result = ByteArray(4)
        for (index in 0..3) {
            val shift = if (littleEndian) index * 8 else (3 - index) * 8
            result[index] = (value shr shift).toByte()
        }
        return result
    }

    fun s32(value: Int): ByteArray = u32(value.toLong() and 0xFFFFFFFFL)

    fun f32(value: Float): ByteArray {
        val bits = value.toRawBits()
        val result = ByteArray(4)
        for (index in 0..3) {
            val shift = if (littleEndian) index * 8 else (3 - index) * 8
            result[index] = (bits shr shift).toByte()
        }
        return result
    }

    fun string(value: String, size: Int): ByteArray {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= size) { "String does not fit in $size bytes" }
        return encoded.copyOf(size)
    }

    private fun writeUInt16(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
    }

    private fun writeUInt32(output: ByteArrayOutputStream, value: Long) {
        for (index in 0..3) {
            output.write(((value shr (index * 8)) and 0xFF).toInt())
        }
    }

    companion object {
        const val FIT_EPOCH_SECONDS = 631_065_600L

        fun fitSeconds(instant: Instant): Long = instant.epochSecond - FIT_EPOCH_SECONDS

        fun fitInstant(seconds: Long): Instant = Instant.ofEpochSecond(FIT_EPOCH_SECONDS + seconds)

        fun field(number: Int, baseType: TestBaseType, size: Int = baseType.size): TestField =
            TestField(number, size, baseType)
    }
}
