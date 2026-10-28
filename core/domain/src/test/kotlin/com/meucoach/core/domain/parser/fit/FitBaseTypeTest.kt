package com.meucoach.core.domain.parser.fit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FitBaseTypeTest {
    @Test
    fun `decodes numeric base types and invalid sentinels`() {
        assertThat(decode(FitBaseType.ENUM, byteArrayOf(2))?.asNumber()).isEqualTo(2.0)
        assertThat(decode(FitBaseType.ENUM, byteArrayOf(0xFF.toByte()))).isNull()
        assertThat(decode(FitBaseType.SINT8, byteArrayOf(0xFF.toByte()))?.asNumber()).isEqualTo(-1.0)
        assertThat(decode(FitBaseType.SINT8, byteArrayOf(0x7F))).isNull()
        assertThat(decode(FitBaseType.UINT8, byteArrayOf(42))?.asNumber()).isEqualTo(42.0)
        assertThat(decode(FitBaseType.UINT8, byteArrayOf(0xFF.toByte()))).isNull()
        assertThat(decode(FitBaseType.UINT8Z, byteArrayOf(0))).isNull()
        assertThat(decode(FitBaseType.UINT8Z, byteArrayOf(1))?.asNumber()).isEqualTo(1.0)

        assertThat(decode(FitBaseType.SINT16, int16(-2, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN)?.asNumber())
            .isEqualTo(-2.0)
        assertThat(decode(FitBaseType.SINT16, int16(-2, ByteOrder.BIG_ENDIAN), ByteOrder.BIG_ENDIAN)?.asNumber())
            .isEqualTo(-2.0)
        assertThat(decode(FitBaseType.SINT16, int16(0x7FFF, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN))
            .isNull()
        assertThat(
            decode(FitBaseType.UINT16, int16(60_000, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN)?.asNumber(),
        )
            .isEqualTo(60_000.0)
        assertThat(decode(FitBaseType.UINT16, int16(0xFFFF, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN))
            .isNull()
        assertThat(decode(FitBaseType.UINT16Z, int16(0, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN))
            .isNull()
        assertThat(decode(FitBaseType.UINT16Z, int16(1, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN)?.asNumber())
            .isEqualTo(1.0)

        assertThat(decode(FitBaseType.SINT32, int32(-3, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN)?.asNumber())
            .isEqualTo(-3.0)
        assertThat(
            decode(FitBaseType.SINT32, int32(0x7FFFFFFF, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN),
        ).isNull()
        assertThat(
            decode(FitBaseType.UINT32, int32(4_000_000_000L, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN)
                ?.asNumber(),
        ).isEqualTo(4_000_000_000.0)
        assertThat(
            decode(FitBaseType.UINT32, int32(0xFFFFFFFFL, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN),
        ).isNull()
        assertThat(
            decode(FitBaseType.UINT32Z, int32(0, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN),
        ).isNull()
        assertThat(
            decode(FitBaseType.UINT32Z, int32(7, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN)?.asNumber(),
        ).isEqualTo(7.0)

        assertThat(
            decode(
                FitBaseType.FLOAT32,
                int32(1.5f.toRawBits().toLong(), ByteOrder.LITTLE_ENDIAN),
                ByteOrder.LITTLE_ENDIAN,
            )
                ?.asNumber(),
        ).isEqualTo(1.5)
        assertThat(
            decode(
                FitBaseType.FLOAT32,
                int32(Float.NaN.toRawBits().toLong(), ByteOrder.LITTLE_ENDIAN),
                ByteOrder.LITTLE_ENDIAN,
            ),
        ).isNull()
        assertThat(decode(FitBaseType.FLOAT64, float64(2.5, ByteOrder.BIG_ENDIAN), ByteOrder.BIG_ENDIAN)?.asNumber())
            .isEqualTo(2.5)
        assertThat(
            decode(FitBaseType.FLOAT64, float64(Double.NaN, ByteOrder.BIG_ENDIAN), ByteOrder.BIG_ENDIAN),
        ).isNull()

        assertThat(decode(FitBaseType.SINT64, int64(-5, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN)?.asNumber())
            .isEqualTo(-5.0)
        assertThat(decode(FitBaseType.SINT64, int64(Long.MAX_VALUE, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN))
            .isNull()
        assertThat(decode(FitBaseType.UINT64, int64(5, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN)?.asNumber())
            .isEqualTo(5.0)
        assertThat(decode(FitBaseType.UINT64, int64(-1, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN))
            .isNull()
        assertThat(decode(FitBaseType.UINT64Z, int64(0, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN))
            .isNull()
    }

    @Test
    fun `decodes strings bytes and arrays`() {
        assertThat(decode(FitBaseType.STRING, "hello\u0000ignored".toByteArray())?.asText())
            .isEqualTo("hello")
        assertThat(decode(FitBaseType.STRING, "without-null".toByteArray())?.asText())
            .isEqualTo("without-null")
        assertThat(decode(FitBaseType.STRING, byteArrayOf(0))?.asText()).isEmpty()
        assertThat(decode(FitBaseType.BYTE, byteArrayOf(1, 2))?.asText()).isNull()
        assertThat(decode(FitBaseType.UINT8, byteArrayOf(1, 2, 3)))
            .isEqualTo(FitFieldValue.Numbers(listOf(1.0, 2.0, 3.0)))
        assertThat(decode(FitBaseType.UINT8, byteArrayOf(0xFF.toByte(), 0xFF.toByte()))).isNull()
        assertThat(decode(FitBaseType.UINT8, byteArrayOf(0xFF.toByte(), 2)))
            .isEqualTo(FitFieldValue.Number(2.0))
        assertThat(FitBaseType.fromCode(0xEE)).isNull()
    }

    @Test
    fun `rejects malformed field sizes`() {
        assertThatThrownBy { decode(FitBaseType.UINT16, byteArrayOf(1)) }
            .isInstanceOf(FitParseException::class.java)
            .extracting("code")
            .isEqualTo(FitErrorCode.INVALID_DEFINITION)
    }

    @Test
    fun `computes the FIT CRC`() {
        assertThat(FitCrc.compute("123456789".toByteArray())).isEqualTo(0xBB3D)
        assertThat(FitCrc.compute(byteArrayOf(1, 2, 3, 4), 1, 3)).isEqualTo(FitCrc.compute(byteArrayOf(2, 3)))
    }

    @Test
    fun `reads cursor values and rejects truncation`() {
        val cursor = FitCursor(byteArrayOf(1, 0x02, 0x03, 0x04), 0, 4)
        assertThat(cursor.readU8()).isEqualTo(1)
        assertThat(cursor.readU16(ByteOrder.BIG_ENDIAN)).isEqualTo(0x0203)
        assertThat(cursor.readBytes(1)).containsExactly(0x04.toByte())
        assertThatThrownBy { cursor.readU8() }
            .isInstanceOf(FitParseException::class.java)
            .extracting("code")
            .isEqualTo(FitErrorCode.TRUNCATED)
        assertThatThrownBy { cursor.readBytes(-1) }
            .isInstanceOf(FitParseException::class.java)
            .extracting("code")
            .isEqualTo(FitErrorCode.TRUNCATED)

        val little = FitCursor(byteArrayOf(0x02, 0x03), 0, 2)
        assertThat(little.readU16(ByteOrder.LITTLE_ENDIAN)).isEqualTo(0x0302)
    }

    private fun decode(
        type: FitBaseType,
        bytes: ByteArray,
        order: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    ): FitFieldValue? = type.decode(bytes, order)

    private fun int16(value: Int, order: ByteOrder): ByteArray =
        ByteBuffer.allocate(2).order(order).putShort(value.toShort()).array()

    private fun int32(value: Long, order: ByteOrder): ByteArray =
        ByteBuffer.allocate(4).order(order).putInt(value.toInt()).array()

    private fun int32(value: Int, order: ByteOrder): ByteArray = int32(value.toLong(), order)

    private fun int64(value: Long, order: ByteOrder): ByteArray =
        ByteBuffer.allocate(8).order(order).putLong(value).array()

    private fun int64(value: Int, order: ByteOrder): ByteArray = int64(value.toLong(), order)

    private fun float64(value: Double, order: ByteOrder): ByteArray =
        ByteBuffer.allocate(8).order(order).putDouble(value).array()
}
