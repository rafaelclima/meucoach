package com.meucoach.core.domain.story

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Encode profiles of the story video (ENC-02). 60 fps is defined but not
 * offered until the encoder is validated on device (DEV-04).
 */
class StoryQualityTest {
    @Test
    fun `standard profile is ten eighty p thirty`() {
        assertThat(StoryQuality.STANDARD_1080P30.width).isEqualTo(1_080)
        assertThat(StoryQuality.STANDARD_1080P30.height).isEqualTo(1_920)
        assertThat(StoryQuality.STANDARD_1080P30.fps).isEqualTo(30)
        assertThat(StoryQuality.STANDARD_1080P30.bitrate).isEqualTo(16_000_000)
    }

    @Test
    fun `smooth profile doubles the frame rate at the same size`() {
        assertThat(StoryQuality.SMOOTH_1080P60.width).isEqualTo(1_080)
        assertThat(StoryQuality.SMOOTH_1080P60.height).isEqualTo(1_920)
        assertThat(StoryQuality.SMOOTH_1080P60.fps).isEqualTo(60)
    }
}
