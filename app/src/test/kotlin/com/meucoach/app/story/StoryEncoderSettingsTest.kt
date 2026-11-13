package com.meucoach.app.story

import android.media.MediaCodecInfo
import android.media.MediaFormat
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryEncoderSettingsTest {
    @Test
    fun `declares bt709 sdr limited color and sixteen megabits`() {
        val format = videoFormat()

        format.applyStoryEncoderSettings(supportsHighProfile41 = true, supportsVbr = true)

        assertThat(format.getInteger(MediaFormat.KEY_COLOR_STANDARD))
            .isEqualTo(MediaFormat.COLOR_STANDARD_BT709)
        assertThat(format.getInteger(MediaFormat.KEY_COLOR_TRANSFER))
            .isEqualTo(MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        assertThat(format.getInteger(MediaFormat.KEY_COLOR_RANGE))
            .isEqualTo(MediaFormat.COLOR_RANGE_LIMITED)
        assertThat(format.getInteger(MediaFormat.KEY_BIT_RATE)).isEqualTo(16_000_000)
    }

    @Test
    fun `declares high profile level 4_1 when the encoder supports it`() {
        val format = videoFormat()

        format.applyStoryEncoderSettings(supportsHighProfile41 = true, supportsVbr = true)

        assertThat(format.getInteger(MediaFormat.KEY_PROFILE))
            .isEqualTo(MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
        assertThat(format.getInteger(MediaFormat.KEY_LEVEL))
            .isEqualTo(MediaCodecInfo.CodecProfileLevel.AVCLevel41)
    }

    @Test
    fun `declares vbr when the encoder supports it`() {
        val format = videoFormat()

        format.applyStoryEncoderSettings(supportsHighProfile41 = true, supportsVbr = true)

        assertThat(format.getInteger(MediaFormat.KEY_BITRATE_MODE))
            .isEqualTo(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
    }

    @Test
    fun `keeps encoder defaults when profile and vbr are unsupported`() {
        val format = videoFormat()

        format.applyStoryEncoderSettings(supportsHighProfile41 = false, supportsVbr = false)

        assertThat(format.containsKey(MediaFormat.KEY_PROFILE)).isFalse()
        assertThat(format.containsKey(MediaFormat.KEY_LEVEL)).isFalse()
        assertThat(format.containsKey(MediaFormat.KEY_BITRATE_MODE)).isFalse()
        // Color and bitrate do not depend on optional capabilities.
        assertThat(format.getInteger(MediaFormat.KEY_COLOR_STANDARD))
            .isEqualTo(MediaFormat.COLOR_STANDARD_BT709)
        assertThat(format.getInteger(MediaFormat.KEY_BIT_RATE)).isEqualTo(16_000_000)
    }

    private fun videoFormat(): MediaFormat = MediaFormat.createVideoFormat("video/avc", 1080, 1920)
}
