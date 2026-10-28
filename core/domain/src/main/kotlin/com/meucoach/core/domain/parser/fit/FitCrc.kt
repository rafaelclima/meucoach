package com.meucoach.core.domain.parser.fit

internal object FitCrc {
    private val table = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400,
        0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401,
        0x5000, 0x9C01, 0x8801, 0x4400,
    )

    fun compute(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): Int {
        var crc = 0
        for (index in from until to) {
            val byte = bytes[index].toInt() and 0xFF
            var tmp = table[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor table[byte and 0xF]
            tmp = table[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor table[(byte shr 4) and 0xF]
        }
        return crc
    }
}
