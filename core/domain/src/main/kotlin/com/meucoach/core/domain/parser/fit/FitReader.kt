package com.meucoach.core.domain.parser.fit

import java.nio.ByteOrder
import java.time.Instant

internal sealed interface FitFieldValue {
    data class Number(val value: Double) : FitFieldValue

    data class Numbers(val values: List<Double>) : FitFieldValue

    data class Text(val value: String) : FitFieldValue

    data class Bytes(val value: ByteArray) : FitFieldValue
}

internal fun FitFieldValue.asNumber(): Double? = when (this) {
    is FitFieldValue.Number -> value
    is FitFieldValue.Numbers -> values.firstOrNull()
    is FitFieldValue.Text, is FitFieldValue.Bytes -> null
}

internal fun FitFieldValue.asText(): String? = when (this) {
    is FitFieldValue.Text -> value
    is FitFieldValue.Bytes -> null
    is FitFieldValue.Number, is FitFieldValue.Numbers -> null
}

internal enum class FitBaseType(val code: Int, val size: Int) {
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
    ;

    fun decode(bytes: ByteArray, order: ByteOrder): FitFieldValue? {
        when (this) {
            STRING -> {
                val end = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
                return FitFieldValue.Text(bytes.copyOfRange(0, end).toString(Charsets.UTF_8))
            }

            BYTE -> return FitFieldValue.Bytes(bytes.copyOf())

            else -> Unit
        }
        if (bytes.size < size || bytes.size % size != 0) {
            throw FitParseException(
                FitErrorCode.INVALID_DEFINITION,
                "Field size ${bytes.size} is not a multiple of $this",
            )
        }
        val values = mutableListOf<Double>()
        var offset = 0
        while (offset < bytes.size) {
            val value = decodeOne(bytes, offset, order)
            if (value != null) values += value
            offset += size
        }
        return when (values.size) {
            0 -> null
            1 -> FitFieldValue.Number(values[0])
            else -> FitFieldValue.Numbers(values)
        }
    }

    private fun decodeOne(bytes: ByteArray, offset: Int, order: ByteOrder): Double? {
        val raw8 = bytes[offset].toInt() and 0xFF
        return when (this) {
            ENUM -> if (raw8 == 0xFF) null else raw8.toDouble()

            UINT8 -> if (raw8 == 0xFF) null else raw8.toDouble()

            UINT8Z -> if (raw8 == 0) null else raw8.toDouble()

            SINT8 -> if (raw8 == 0x7F) null else bytes[offset].toInt().toDouble()

            SINT16 -> {
                val raw = readU16(bytes, offset, order)
                if (raw == 0x7FFF) null else raw.toShort().toDouble()
            }

            UINT16 -> {
                val raw = readU16(bytes, offset, order)
                if (raw == 0xFFFF) null else raw.toDouble()
            }

            UINT16Z -> {
                val raw = readU16(bytes, offset, order)
                if (raw == 0) null else raw.toDouble()
            }

            SINT32 -> {
                val raw = readU32(bytes, offset, order)
                if (raw == 0x7FFFFFFFL) null else raw.toInt().toDouble()
            }

            UINT32 -> {
                val raw = readU32(bytes, offset, order)
                if (raw == 0xFFFFFFFFL) null else raw.toDouble()
            }

            UINT32Z -> {
                val raw = readU32(bytes, offset, order)
                if (raw == 0L) null else raw.toDouble()
            }

            FLOAT32 -> {
                val value = Float.fromBits(readU32(bytes, offset, order).toInt())
                if (value.isNaN()) null else value.toDouble()
            }

            FLOAT64 -> {
                val value = Double.fromBits(readU64(bytes, offset, order))
                if (value.isNaN()) null else value
            }

            SINT64 -> {
                val raw = readU64(bytes, offset, order)
                if (raw == Long.MAX_VALUE) null else raw.toDouble()
            }

            UINT64 -> {
                val raw = readU64(bytes, offset, order)
                if (raw == -1L) null else raw.toULong().toDouble()
            }

            UINT64Z -> {
                val raw = readU64(bytes, offset, order)
                if (raw == 0L) null else raw.toULong().toDouble()
            }

            STRING, BYTE -> null
        }
    }

    companion object {
        fun fromCode(code: Int): FitBaseType? = entries.firstOrNull { it.code == code }

        private fun readU16(bytes: ByteArray, offset: Int, order: ByteOrder): Int {
            val first = bytes[offset].toInt() and 0xFF
            val second = bytes[offset + 1].toInt() and 0xFF
            return if (order == ByteOrder.LITTLE_ENDIAN) {
                first or (second shl 8)
            } else {
                (first shl 8) or second
            }
        }

        private fun readU32(bytes: ByteArray, offset: Int, order: ByteOrder): Long {
            var value = 0L
            if (order == ByteOrder.LITTLE_ENDIAN) {
                for (index in 3 downTo 0) {
                    value = (value shl 8) or (bytes[offset + index].toLong() and 0xFF)
                }
            } else {
                for (index in 0..3) {
                    value = (value shl 8) or (bytes[offset + index].toLong() and 0xFF)
                }
            }
            return value
        }

        private fun readU64(bytes: ByteArray, offset: Int, order: ByteOrder): Long {
            var value = 0L
            if (order == ByteOrder.LITTLE_ENDIAN) {
                for (index in 7 downTo 0) {
                    value = (value shl 8) or (bytes[offset + index].toLong() and 0xFF)
                }
            } else {
                for (index in 0..7) {
                    value = (value shl 8) or (bytes[offset + index].toLong() and 0xFF)
                }
            }
            return value
        }
    }
}

internal data class FitFieldDefinition(val number: Int, val size: Int, val baseType: FitBaseType)

internal data class FitDeveloperFieldDefinition(val number: Int, val size: Int, val developerDataIndex: Int)

internal data class FitMessageDefinition(
    val globalNumber: Int,
    val order: ByteOrder,
    val fields: List<FitFieldDefinition>,
    val developerFields: List<FitDeveloperFieldDefinition>,
)

internal data class DecodedMessage(
    val globalNumber: Int,
    val fields: Map<Int, FitFieldValue>,
    val fieldTypes: Map<Int, FitBaseType>,
    val developerFields: Map<String, FitFieldValue>,
    val timestamp: Instant?,
)

internal data class DecodedFile(val messages: List<DecodedMessage>, val warnings: List<FitWarning>)

private const val FIT_EPOCH_SECONDS = 631_065_600L

internal fun fitTimestamp(seconds: Long): Instant = Instant.ofEpochSecond(FIT_EPOCH_SECONDS + seconds)

internal class FitReader(private val bytes: ByteArray) {
    private val warnings = mutableListOf<FitWarning>()
    private val warningKeys = mutableSetOf<String>()
    private val definitions = arrayOfNulls<FitMessageDefinition>(16)
    private val developerDefinitions = mutableMapOf<Pair<Int, Int>, FitDeveloperDescription>()
    private val messages = mutableListOf<DecodedMessage>()
    private var lastTimestampSeconds: Long? = null
    private var dataEnd = 0
    private lateinit var cursor: FitCursor

    fun read(): DecodedFile {
        val headerSize = validateHeader()
        cursor = FitCursor(bytes, headerSize, dataEnd)
        while (cursor.offset < dataEnd) {
            val header = cursor.readU8()
            when {
                header and 0x80 != 0 -> readData(header, compressed = true)
                header and 0x40 != 0 -> readDefinition(header)
                else -> readData(header, compressed = false)
            }
        }
        return DecodedFile(messages = messages.toList(), warnings = warnings.toList())
    }

    private fun validateHeader(): Int {
        if (bytes.isEmpty()) {
            throw FitParseException(FitErrorCode.EMPTY_FILE, "FIT file is empty")
        }
        if (bytes.size < 14) {
            throw FitParseException(FitErrorCode.TOO_SHORT, "FIT file is too short")
        }
        val headerSize = bytes[0].toInt() and 0xFF
        if (headerSize != 12 && headerSize != 14) {
            throw FitParseException(
                FitErrorCode.INVALID_HEADER,
                "Unsupported header size $headerSize",
            )
        }
        if (String(bytes, 8, 4, Charsets.US_ASCII) != ".FIT") {
            throw FitParseException(FitErrorCode.INVALID_HEADER, "Missing .FIT signature")
        }
        val dataSize = readU32Le(4)
        if (dataSize > Int.MAX_VALUE.toLong()) {
            throw FitParseException(FitErrorCode.TRUNCATED, "Data size is too large")
        }
        dataEnd = headerSize + dataSize.toInt()
        if (dataEnd + 2 > bytes.size) {
            throw FitParseException(
                FitErrorCode.TRUNCATED,
                "File ends before declared data size",
            )
        }
        if (dataEnd + 2 < bytes.size) {
            warn(
                FitWarningCode.TRAILING_DATA,
                "${bytes.size - (dataEnd + 2)} byte(s) after the FIT CRC",
            )
        }
        if (headerSize == 14) {
            val storedHeaderCrc = readU16Le(12)
            val actualHeaderCrc = FitCrc.compute(bytes, 0, 12)
            if (storedHeaderCrc != actualHeaderCrc) {
                throw FitParseException(
                    FitErrorCode.INVALID_CRC,
                    "Header CRC $storedHeaderCrc != $actualHeaderCrc",
                )
            }
        }
        val storedFileCrc = readU16Le(dataEnd)
        if (storedFileCrc == 0) {
            warn(FitWarningCode.MISSING_CRC, "FIT file CRC is zero")
        } else {
            val actualFileCrc = FitCrc.compute(bytes, 0, dataEnd)
            if (storedFileCrc != actualFileCrc) {
                throw FitParseException(
                    FitErrorCode.INVALID_CRC,
                    "File CRC $storedFileCrc != $actualFileCrc",
                )
            }
        }
        return headerSize
    }

    private fun readDefinition(header: Int) {
        val local = header and 0x0F
        cursor.readU8() // reserved
        val architecture = cursor.readU8()
        val order = when (architecture) {
            0 -> ByteOrder.LITTLE_ENDIAN

            1 -> ByteOrder.BIG_ENDIAN

            else -> throw FitParseException(
                FitErrorCode.INVALID_DEFINITION,
                "Unknown architecture $architecture",
            )
        }
        val globalNumber = cursor.readU16(order)
        val fieldCount = cursor.readU8()
        val fields = buildList {
            repeat(fieldCount) {
                val number = cursor.readU8()
                val size = cursor.readU8()
                val baseType = FitBaseType.fromCode(cursor.readU8())
                    ?: throw FitParseException(
                        FitErrorCode.INVALID_DEFINITION,
                        "Unknown base type for field $number",
                    )
                if (size == 0) {
                    throw FitParseException(
                        FitErrorCode.INVALID_DEFINITION,
                        "Field $number has zero size",
                    )
                }
                if (baseType != FitBaseType.STRING &&
                    baseType != FitBaseType.BYTE &&
                    size % baseType.size != 0
                ) {
                    throw FitParseException(
                        FitErrorCode.INVALID_DEFINITION,
                        "Field $number has invalid size $size for $baseType",
                    )
                }
                add(FitFieldDefinition(number, size, baseType))
            }
        }
        val developerFields = if (header and 0x20 != 0) {
            val devCount = cursor.readU8()
            buildList {
                repeat(devCount) {
                    val number = cursor.readU8()
                    val size = cursor.readU8()
                    val devIndex = cursor.readU8()
                    if (size == 0) {
                        throw FitParseException(
                            FitErrorCode.INVALID_DEFINITION,
                            "Developer field $number has zero size",
                        )
                    }
                    add(FitDeveloperFieldDefinition(number, size, devIndex))
                }
            }
        } else {
            emptyList()
        }
        definitions[local] = FitMessageDefinition(
            globalNumber = globalNumber,
            order = order,
            fields = fields,
            developerFields = developerFields,
        )
    }

    private fun readData(header: Int, compressed: Boolean) {
        val local = if (compressed) (header shr 5) and 0x03 else header and 0x0F
        val definition = definitions[local]
            ?: throw FitParseException(
                FitErrorCode.UNKNOWN_LOCAL_MESSAGE,
                "Data message references undefined local type $local",
            )
        val timestampField = definition.fields.firstOrNull { it.number == TIMESTAMP_FIELD }
        val values = linkedMapOf<Int, FitFieldValue>()
        var timestampSeconds: Long? = null

        for (field in definition.fields) {
            if (compressed && field.number == TIMESTAMP_FIELD) continue
            val raw = cursor.readBytes(field.size)
            val value = field.baseType.decode(raw, definition.order)
            if (value != null) {
                values[field.number] = value
                if (!compressed && field.number == TIMESTAMP_FIELD) {
                    timestampSeconds = value.asNumber()?.toLong()
                }
            }
        }

        if (compressed) {
            if (timestampField == null) {
                warn(
                    FitWarningCode.COMPRESSED_WITHOUT_TIMESTAMP,
                    "Compressed data message has no timestamp field",
                )
            } else {
                val calculated = compressedTimestamp(header and 0x1F)
                if (calculated != null) {
                    timestampSeconds = calculated
                    values[TIMESTAMP_FIELD] = FitFieldValue.Number(calculated.toDouble())
                }
            }
        }
        if (timestampSeconds != null) {
            lastTimestampSeconds = timestampSeconds
        }

        val developerValues = linkedMapOf<String, FitFieldValue>()
        for (field in definition.developerFields) {
            val raw = cursor.readBytes(field.size)
            val description = developerDefinitions[field.developerDataIndex to field.number]
            if (description == null) {
                warn(
                    FitWarningCode.UNKNOWN_DEVELOPER_FIELD,
                    "Unknown developer field ${field.developerDataIndex}:${field.number}",
                )
                continue
            }
            val baseType = description.baseType ?: continue
            val value = try {
                baseType.decode(raw, definition.order)?.let {
                    it.scaled(description.scale, description.offset)
                }
            } catch (error: FitParseException) {
                warn(
                    FitWarningCode.UNKNOWN_DEVELOPER_FIELD,
                    "Invalid developer field ${description.name}: ${error.message}",
                )
                null
            }
            if (value != null) developerValues[description.name] = value
        }

        messages += DecodedMessage(
            globalNumber = definition.globalNumber,
            fields = values,
            fieldTypes = definition.fields.associate { it.number to it.baseType },
            developerFields = developerValues,
            timestamp = timestampSeconds?.let(::fitTimestamp),
        )

        if (definition.globalNumber == MESSAGE_FIELD_DESCRIPTION) {
            registerDeveloperDescription(values)
        }
    }

    private fun registerDeveloperDescription(values: Map<Int, FitFieldValue>) {
        val devIndex = values[0]?.asNumber()?.toInt() ?: return
        val fieldNumber = values[1]?.asNumber()?.toInt() ?: return
        val baseType = values[2]?.asNumber()?.toInt()?.let(FitBaseType::fromCode)
        val name = values[3]?.asText()?.takeIf { it.isNotBlank() } ?: return
        val scale = values[6]?.asNumber()
        val offset = values[7]?.asNumber()
        val units = values[8]?.asText()
        developerDefinitions[devIndex to fieldNumber] = FitDeveloperDescription(
            name = name,
            baseType = baseType,
            scale = scale,
            offset = offset,
            units = units,
        )
    }

    private fun compressedTimestamp(offset: Int): Long? {
        val last = lastTimestampSeconds ?: return null
        val lastLow = last and 0x1F
        var result = last - lastLow + offset
        if (offset < lastLow) result += 0x20
        return result
    }

    private fun warn(code: FitWarningCode, message: String) {
        val key = "$code:$message"
        if (warningKeys.add(key)) {
            warnings += FitWarning(code, message)
        }
    }

    private fun readU16Le(offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32Le(offset: Int): Long {
        var value = 0L
        for (index in 3 downTo 0) {
            value = (value shl 8) or (bytes[offset + index].toLong() and 0xFF)
        }
        return value
    }

    private data class FitDeveloperDescription(
        val name: String,
        val baseType: FitBaseType?,
        val scale: Double?,
        val offset: Double?,
        val units: String?,
    )

    private fun FitFieldValue.scaled(scale: Double?, offset: Double?): FitFieldValue {
        val divisor = scale?.takeIf { it != 0.0 } ?: return this
        val shift = offset ?: 0.0
        return when (this) {
            is FitFieldValue.Number -> FitFieldValue.Number(value / divisor - shift)

            is FitFieldValue.Numbers -> FitFieldValue.Numbers(
                values.map { it / divisor - shift },
            )

            is FitFieldValue.Text, is FitFieldValue.Bytes -> this
        }
    }

    companion object {
        private const val TIMESTAMP_FIELD = 253
        private const val MESSAGE_FIELD_DESCRIPTION = 206
    }
}
