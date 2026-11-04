package com.meucoach.app.story

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/** The palette rides in WorkManager Data; the packing order is a contract. */
class StoryPaletteCodecTest {
    private val palette = StoryPalette(
        background = 0xFF08090B.toInt(),
        surface = 0xFF121418.toInt(),
        surfaceInset = 0xFF0B0D10.toInt(),
        border = 0xFF23272D.toInt(),
        textPrimary = 0xFFF4F6F7.toInt(),
        textSecondary = 0xFFA2A9B0.toInt(),
        textTertiary = 0xFF7F8895.toInt(),
        accent = 0xFFD8FF3E.toInt(),
        coral = 0xFFFF6B5E.toInt(),
        onAccent = 0xFF0A0B0D.toInt(),
        mapCasing = 0xFF08090B.toInt(),
        mapBaseRoute = 0xFF4A545F.toInt(),
        zone1 = 0xFF6C9CFF.toInt(),
        zone2 = 0xFF56D68B.toInt(),
        zone3 = 0xFFFFD166.toInt(),
        zone4 = 0xFFFF9F43.toInt(),
        zone5 = 0xFFFF5A5F.toInt(),
    )

    @Test
    fun `round trips every field`() {
        assertThat(StoryPaletteCodec.decode(StoryPaletteCodec.encode(palette))).isEqualTo(palette)
    }

    @Test
    fun `rejects payloads without the full field count`() {
        assertThat(StoryPaletteCodec.decode(IntArray(3))).isNull()
        assertThat(StoryPaletteCodec.decode(null)).isNull()
        assertThat(StoryPaletteCodec.decode(IntArray(StoryPaletteCodec.FIELD_COUNT + 1))).isNull()
    }
}
