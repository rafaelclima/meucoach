package com.meucoach.core.data.samples

import com.meucoach.core.model.ActivitySample
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Versioned columnar codec for activity samples.
 *
 * The payload is gzip-compressed and starts with a small header followed by
 * one column per signal. Optional columns have a one-byte presence flag per
 * sample, so missing values remain missing instead of becoming zero.
 *
 * Timestamps are not stored: an activity's start time plus [ActivitySample.offsetSec]
 * is the canonical time. Developer fields are preserved in a small map column.
 */
class SampleBlobCodec {
    fun encode(samples: List<ActivitySample>): ByteArray {
        require(samples.zipWithNext().all { (first, second) -> first.offsetSec <= second.offsetSec }) {
            "Samples must be ordered by offset"
        }

        val mask = columnMask(samples)
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { gzip ->
            DataOutputStream(gzip).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(samples.size)
                output.writeInt(mask)

                samples.forEach { output.writeInt(it.offsetSec) }
                writeOptionalDoubles(output, samples, mask, LATITUDE_BIT) { it.latitude }
                writeOptionalDoubles(output, samples, mask, LONGITUDE_BIT) { it.longitude }
                writeOptionalDoubles(output, samples, mask, ALTITUDE_BIT) { it.altitudeM }
                writeOptionalDoubles(output, samples, mask, SPEED_BIT) { it.speedMps }
                writeOptionalInts(output, samples, mask, HEART_RATE_BIT) { it.heartRate }
                writeOptionalInts(output, samples, mask, CADENCE_BIT) { it.cadenceSpm }
                writeOptionalInts(output, samples, mask, POWER_BIT) { it.powerW }
                writeOptionalDoubles(output, samples, mask, STEP_LENGTH_BIT) { it.stepLengthM }
                writeDeveloperFields(output, samples, mask)
            }
        }
        return compressed.toByteArray()
    }

    fun decode(bytes: ByteArray, startTime: Instant? = null): List<ActivitySample> {
        val payload = decompress(bytes)
        try {
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                val magic = input.readInt()
                if (magic != MAGIC) {
                    throw SampleBlobException("Invalid sample blob magic")
                }
                val version = input.readInt()
                if (version != VERSION) {
                    throw SampleBlobException("Unsupported sample blob version $version")
                }
                val sampleCount = input.readInt()
                if (sampleCount !in 0..MAX_SAMPLES) {
                    throw SampleBlobException("Invalid sample count $sampleCount")
                }
                val mask = input.readInt()
                if (mask and OFFSET_BIT == 0) {
                    throw SampleBlobException("Sample blob is missing the offset column")
                }
                if (mask and ALL_COLUMNS_MASK.inv() != 0) {
                    throw SampleBlobException("Sample blob contains unknown columns")
                }

                val offsets = IntArray(sampleCount) { input.readInt() }
                val latitudes = readOptionalDoubles(input, sampleCount, mask, LATITUDE_BIT)
                val longitudes = readOptionalDoubles(input, sampleCount, mask, LONGITUDE_BIT)
                val altitudes = readOptionalDoubles(input, sampleCount, mask, ALTITUDE_BIT)
                val speeds = readOptionalDoubles(input, sampleCount, mask, SPEED_BIT)
                val heartRates = readOptionalInts(input, sampleCount, mask, HEART_RATE_BIT)
                val cadences = readOptionalInts(input, sampleCount, mask, CADENCE_BIT)
                val powers = readOptionalInts(input, sampleCount, mask, POWER_BIT)
                val stepLengths = readOptionalDoubles(input, sampleCount, mask, STEP_LENGTH_BIT)
                val developerFields = readDeveloperFields(input, sampleCount, mask)

                if (input.available() != 0) {
                    throw SampleBlobException("Sample blob has trailing bytes")
                }

                return List(sampleCount) { index ->
                    ActivitySample(
                        offsetSec = offsets[index],
                        timestamp = startTime?.plusSeconds(offsets[index].toLong()),
                        latitude = latitudes?.get(index),
                        longitude = longitudes?.get(index),
                        altitudeM = altitudes?.get(index),
                        speedMps = speeds?.get(index),
                        heartRate = heartRates?.get(index),
                        cadenceSpm = cadences?.get(index),
                        powerW = powers?.get(index),
                        stepLengthM = stepLengths?.get(index),
                        developerFields = developerFields?.get(index) ?: emptyMap(),
                    )
                }
            }
        } catch (error: SampleBlobException) {
            throw error
        } catch (error: EOFException) {
            throw SampleBlobException("Truncated sample blob", error)
        } catch (error: IOException) {
            throw SampleBlobException("Invalid sample blob", error)
        } catch (error: IllegalArgumentException) {
            throw SampleBlobException("Invalid sample value", error)
        }
    }

    private fun decompress(bytes: ByteArray): ByteArray = try {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            readBounded(input, MAX_PAYLOAD_BYTES)
        }
    } catch (error: IOException) {
        throw SampleBlobException("Invalid compressed sample blob", error)
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw SampleBlobException("Sample blob exceeds $maxBytes bytes")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun columnMask(samples: List<ActivitySample>): Int {
        var mask = OFFSET_BIT
        if (samples.any { it.latitude != null }) mask = mask or LATITUDE_BIT
        if (samples.any { it.longitude != null }) mask = mask or LONGITUDE_BIT
        if (samples.any { it.altitudeM != null }) mask = mask or ALTITUDE_BIT
        if (samples.any { it.speedMps != null }) mask = mask or SPEED_BIT
        if (samples.any { it.heartRate != null }) mask = mask or HEART_RATE_BIT
        if (samples.any { it.cadenceSpm != null }) mask = mask or CADENCE_BIT
        if (samples.any { it.powerW != null }) mask = mask or POWER_BIT
        if (samples.any { it.stepLengthM != null }) mask = mask or STEP_LENGTH_BIT
        if (samples.any { it.developerFields.isNotEmpty() }) mask = mask or DEVELOPER_FIELDS_BIT
        return mask
    }

    private fun writeOptionalDoubles(
        output: DataOutputStream,
        samples: List<ActivitySample>,
        mask: Int,
        bit: Int,
        valueOf: (ActivitySample) -> Double?,
    ) {
        if (mask and bit == 0) return
        samples.forEach { sample -> output.writeByte(if (valueOf(sample) == null) 0 else 1) }
        samples.forEach { sample -> valueOf(sample)?.let(output::writeDouble) }
    }

    private fun writeOptionalInts(
        output: DataOutputStream,
        samples: List<ActivitySample>,
        mask: Int,
        bit: Int,
        valueOf: (ActivitySample) -> Int?,
    ) {
        if (mask and bit == 0) return
        samples.forEach { sample -> output.writeByte(if (valueOf(sample) == null) 0 else 1) }
        samples.forEach { sample -> valueOf(sample)?.let(output::writeInt) }
    }

    private fun writeDeveloperFields(output: DataOutputStream, samples: List<ActivitySample>, mask: Int) {
        if (mask and DEVELOPER_FIELDS_BIT == 0) return
        samples.forEach { sample -> output.writeByte(if (sample.developerFields.isEmpty()) 0 else 1) }
        samples.forEach { sample ->
            if (sample.developerFields.isEmpty()) return@forEach
            output.writeInt(sample.developerFields.size)
            sample.developerFields.forEach { (key, value) ->
                val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
                require(keyBytes.isNotEmpty() && keyBytes.size <= MAX_DEVELOPER_FIELD_NAME_BYTES) {
                    "Developer field name is invalid"
                }
                require(value.isFinite()) { "Developer field value must be finite" }
                output.writeInt(keyBytes.size)
                output.write(keyBytes)
                output.writeDouble(value)
            }
        }
    }

    private fun readOptionalDoubles(input: DataInputStream, sampleCount: Int, mask: Int, bit: Int): List<Double?>? {
        if (mask and bit == 0) return null
        val present = readPresence(input, sampleCount)
        return List(sampleCount) { index -> if (present[index]) input.readDouble() else null }
    }

    private fun readOptionalInts(input: DataInputStream, sampleCount: Int, mask: Int, bit: Int): List<Int?>? {
        if (mask and bit == 0) return null
        val present = readPresence(input, sampleCount)
        return List(sampleCount) { index -> if (present[index]) input.readInt() else null }
    }

    private fun readPresence(input: DataInputStream, sampleCount: Int): BooleanArray {
        val bytes = ByteArray(sampleCount)
        input.readFully(bytes)
        return BooleanArray(sampleCount) { index ->
            when (bytes[index].toInt()) {
                0 -> false
                1 -> true
                else -> throw SampleBlobException("Invalid presence flag")
            }
        }
    }

    private fun readDeveloperFields(input: DataInputStream, sampleCount: Int, mask: Int): List<Map<String, Double>>? {
        if (mask and DEVELOPER_FIELDS_BIT == 0) return null
        val present = readPresence(input, sampleCount)
        return List(sampleCount) { index ->
            if (!present[index]) {
                emptyMap()
            } else {
                val size = input.readInt()
                if (size !in 0..MAX_DEVELOPER_FIELDS) {
                    throw SampleBlobException("Invalid developer field count $size")
                }
                val fields = LinkedHashMap<String, Double>(size)
                repeat(size) {
                    val keyLength = input.readInt()
                    if (keyLength !in 1..MAX_DEVELOPER_FIELD_NAME_BYTES) {
                        throw SampleBlobException("Invalid developer field name length")
                    }
                    val keyBytes = ByteArray(keyLength)
                    input.readFully(keyBytes)
                    val key = String(keyBytes, StandardCharsets.UTF_8)
                    val value = input.readDouble()
                    if (key.isBlank() || !value.isFinite()) {
                        throw SampleBlobException("Invalid developer field")
                    }
                    fields[key] = value
                }
                fields
            }
        }
    }

    companion object {
        const val VERSION = 1
        const val ENCODING = "gzip-columnar-v1"

        private const val MAGIC = 0x4D435342
        private const val BUFFER_SIZE = 8 * 1024
        private const val MAX_SAMPLES = 5_000_000
        private const val MAX_PAYLOAD_BYTES = 128 * 1024 * 1024
        private const val MAX_DEVELOPER_FIELDS = 64
        private const val MAX_DEVELOPER_FIELD_NAME_BYTES = 128

        private const val OFFSET_BIT = 1 shl 0
        private const val LATITUDE_BIT = 1 shl 1
        private const val LONGITUDE_BIT = 1 shl 2
        private const val ALTITUDE_BIT = 1 shl 3
        private const val SPEED_BIT = 1 shl 4
        private const val HEART_RATE_BIT = 1 shl 5
        private const val CADENCE_BIT = 1 shl 6
        private const val POWER_BIT = 1 shl 7
        private const val STEP_LENGTH_BIT = 1 shl 8
        private const val DEVELOPER_FIELDS_BIT = 1 shl 9

        private const val ALL_COLUMNS_MASK =
            OFFSET_BIT or LATITUDE_BIT or LONGITUDE_BIT or ALTITUDE_BIT or SPEED_BIT or
                HEART_RATE_BIT or CADENCE_BIT or POWER_BIT or STEP_LENGTH_BIT or DEVELOPER_FIELDS_BIT
    }
}

class SampleBlobException(message: String, cause: Throwable? = null) : Exception(message, cause)
