package com.meucoach.core.domain.parser.fit

import java.nio.ByteOrder

internal class FitCursor(private val bytes: ByteArray, var offset: Int, var limit: Int) {
    fun readU8(): Int {
        ensure(1)
        return bytes[offset++].toInt() and 0xFF
    }

    fun readBytes(size: Int): ByteArray {
        ensure(size)
        val result = bytes.copyOfRange(offset, offset + size)
        offset += size
        return result
    }

    fun readU16(order: ByteOrder): Int {
        ensure(2)
        val first = bytes[offset].toInt() and 0xFF
        val second = bytes[offset + 1].toInt() and 0xFF
        offset += 2
        return if (order == ByteOrder.LITTLE_ENDIAN) {
            first or (second shl 8)
        } else {
            (first shl 8) or second
        }
    }

    private fun ensure(size: Int) {
        if (size < 0 || offset + size > limit) {
            throw FitParseException(
                FitErrorCode.TRUNCATED,
                "Need $size byte(s) at $offset, limit $limit",
            )
        }
    }
}
