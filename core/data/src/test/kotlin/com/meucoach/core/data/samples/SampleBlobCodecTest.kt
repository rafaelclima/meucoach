package com.meucoach.core.data.samples

import com.meucoach.core.model.ActivitySample
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.Instant
import java.util.zip.GZIPOutputStream

class SampleBlobCodecTest {
    private val codec = SampleBlobCodec()
    private val startTime = Instant.parse("2023-11-14T10:00:00Z")

    @Test
    fun `round trips every column and developer fields`() {
        val samples =
            listOf(
                ActivitySample(
                    offsetSec = 0,
                    timestamp = startTime,
                    latitude = -5.79,
                    longitude = -35.21,
                    altitudeM = 30.0,
                    speedMps = 2.8,
                    heartRate = 140,
                    cadenceSpm = 160,
                    powerW = 220,
                    stepLengthM = 1.1,
                    developerFields = mapOf("temperature" to 27.5),
                ),
                ActivitySample(
                    offsetSec = 1,
                    timestamp = startTime.plusSeconds(1),
                    speedMps = 2.9,
                    cadenceSpm = 161,
                ),
                ActivitySample(
                    offsetSec = 2,
                    timestamp = startTime.plusSeconds(2),
                    latitude = -5.7901,
                    longitude = -35.2101,
                    altitudeM = 31.0,
                    heartRate = 145,
                    powerW = 230,
                    developerFields = mapOf("temperature" to 27.8, "vertical" to 0.2),
                ),
            )

        val decoded = codec.decode(codec.encode(samples), startTime)

        assertThat(decoded).isEqualTo(samples)
    }

    @Test
    fun `round trips an empty sample list`() {
        val decoded = codec.decode(codec.encode(emptyList()), startTime)

        assertThat(decoded).isEmpty()
    }

    @Test
    fun `reconstructs timestamps from the activity start time`() {
        val sample = ActivitySample(offsetSec = 42, heartRate = 150)

        val decoded = codec.decode(codec.encode(listOf(sample)), startTime)

        assertThat(decoded.single().timestamp).isEqualTo(startTime.plusSeconds(42))
        assertThat(decoded.single().heartRate).isEqualTo(150)
    }

    @Test
    fun `compresses repetitive samples`() {
        val samples =
            List(2_000) { index ->
                ActivitySample(
                    offsetSec = index,
                    speedMps = 3.0,
                    heartRate = 150,
                    cadenceSpm = 170,
                )
            }

        val encoded = codec.encode(samples)

        assertThat(encoded.size).isLessThan(10_000)
        assertThat(codec.decode(encoded, startTime)).hasSize(2_000)
    }

    @Test
    fun `rejects invalid gzip input`() {
        assertThatThrownBy { codec.decode(byteArrayOf(1, 2, 3), startTime) }
            .isInstanceOf(SampleBlobException::class.java)
            .hasMessageContaining("compressed")
    }

    @Test
    fun `rejects a truncated blob`() {
        val encoded = codec.encode(listOf(ActivitySample(offsetSec = 0, heartRate = 150)))

        assertThatThrownBy { codec.decode(encoded.copyOf(encoded.size - 8), startTime) }
            .isInstanceOf(SampleBlobException::class.java)
    }

    @Test
    fun `rejects an unsupported version`() {
        val encoded = gzip(payload(version = 99))

        assertThatThrownBy { codec.decode(encoded, startTime) }
            .isInstanceOf(SampleBlobException::class.java)
            .hasMessageContaining("Unsupported")
    }

    @Test
    fun `rejects a blob without the offset column`() {
        val encoded = gzip(payload(mask = 0))

        assertThatThrownBy { codec.decode(encoded, startTime) }
            .isInstanceOf(SampleBlobException::class.java)
            .hasMessageContaining("offset")
    }

    @Test
    fun `rejects an absurd sample count before allocating`() {
        val encoded = gzip(payload(sampleCount = Int.MAX_VALUE))

        assertThatThrownBy { codec.decode(encoded, startTime) }
            .isInstanceOf(SampleBlobException::class.java)
            .hasMessageContaining("sample count")
    }

    @Test
    fun `rejects trailing bytes`() {
        val encoded = gzip(payload(trailing = byteArrayOf(7)))

        assertThatThrownBy { codec.decode(encoded, startTime) }
            .isInstanceOf(SampleBlobException::class.java)
            .hasMessageContaining("trailing")
    }

    @Test
    fun `rejects samples out of order`() {
        val samples =
            listOf(
                ActivitySample(offsetSec = 1, heartRate = 150),
                ActivitySample(offsetSec = 0, heartRate = 151),
            )

        assertThatThrownBy { codec.encode(samples) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ordered")
    }

    private fun gzip(payload: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(payload) }
        return output.toByteArray()
    }

    private fun payload(
        version: Int = SampleBlobCodec.VERSION,
        sampleCount: Int = 0,
        mask: Int = 1,
        trailing: ByteArray = byteArrayOf(),
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(0x4D435342)
            data.writeInt(version)
            data.writeInt(sampleCount)
            data.writeInt(mask)
            data.write(trailing)
        }
        return output.toByteArray()
    }
}
