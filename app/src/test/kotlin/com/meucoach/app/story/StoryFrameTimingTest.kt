package com.meucoach.app.story

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test

/**
 * The recipe quality owns the encoder frame rate (ENC-02): 1080p30 today, 60
 * fps prepared but unexposed until DEV-04 validates it on device.
 */
class StoryFrameTimingTest {
    @Test
    fun `thirty fps covers a thirty second story`() {
        assertThat(storyFrameCount(30f, 30)).isEqualTo(900)
        assertThat(storyFrameTimeSec(450, 30)).isCloseTo(15f, within(1e-6f))
        assertThat(storyFrameTimeUs(30, 30)).isEqualTo(1_000_000L)
        assertThat(storyFrameTimeNs(30, 30)).isEqualTo(1_000_000_000L)
    }

    @Test
    fun `sixty fps doubles the frames with matching timestamps`() {
        assertThat(storyFrameCount(30f, 60)).isEqualTo(1_800)
        assertThat(storyFrameTimeSec(900, 60)).isCloseTo(15f, within(1e-6f))
        assertThat(storyFrameTimeUs(60, 60)).isEqualTo(1_000_000L)
        assertThat(storyFrameTimeNs(60, 60)).isEqualTo(1_000_000_000L)
    }

    @Test
    fun `a zero length plan still encodes one frame`() {
        assertThat(storyFrameCount(0f, 30)).isEqualTo(1)
        assertThat(storyFrameCount(0f, 60)).isEqualTo(1)
    }
}
