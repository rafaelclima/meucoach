package com.meucoach.core.data.id

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Clock
import java.util.Random
import java.util.UUID

/**
 * UUID v7 generator: 48 bits of Unix milliseconds followed by random bits.
 * IDs are time-ordered, which keeps Room indexes and export ordering stable.
 */
object UuidV7 {
    private val secureRandom = SecureRandom()

    fun generate(clock: Clock = Clock.systemUTC(), random: Random = secureRandom): String {
        val millis = clock.millis()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)

        bytes[0] = (millis ushr 40).toByte()
        bytes[1] = (millis ushr 32).toByte()
        bytes[2] = (millis ushr 24).toByte()
        bytes[3] = (millis ushr 16).toByte()
        bytes[4] = (millis ushr 8).toByte()
        bytes[5] = millis.toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long).toString()
    }
}
