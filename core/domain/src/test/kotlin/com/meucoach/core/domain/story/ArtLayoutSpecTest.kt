package com.meucoach.core.domain.story

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ArtLayoutSpecTest {
    @Test
    fun `design canvas and margins match the documented 1080 grid`() {
        assertThat(ArtLayoutSpec.DESIGN_WIDTH).isEqualTo(1080)
        assertThat(ArtLayoutSpec.MARGIN).isEqualTo(88)
        assertThat(ArtLayoutSpec.ROUTE_INSET).isEqualTo(24)
        assertThat(ArtLayoutSpec.MIN_ROUTE_SIDE).isEqualTo(280)
    }

    @Test
    fun `brand and content gaps match the documented values`() {
        assertThat(ArtLayoutSpec.BRAND_TOP).isEqualTo(84)
        assertThat(ArtLayoutSpec.BRAND_HEIGHT).isEqualTo(44)
        assertThat(ArtLayoutSpec.BRAND_GAP).isEqualTo(56)
        assertThat(ArtLayoutSpec.BLOCK_GAP).isEqualTo(32)
        assertThat(ArtLayoutSpec.ROUTE_GAP).isEqualTo(56)
    }

    @Test
    fun `route square stays between the min side and the cap`() {
        assertThat(ArtLayoutSpec.MIN_ROUTE_SIDE).isEqualTo(280)
        assertThat(ArtLayoutSpec.MAX_ROUTE_SIDE).isEqualTo(640)
        assertThat(ArtLayoutSpec.MIN_ROUTE_SIDE).isLessThan(ArtLayoutSpec.MAX_ROUTE_SIDE)
    }

    @Test
    fun `transparent cards keep a larger bottom margin than the side margin`() {
        assertThat(ArtLayoutSpec.BOTTOM_MARGIN).isEqualTo(150)
        assertThat(ArtLayoutSpec.BOTTOM_MARGIN).isGreaterThan(ArtLayoutSpec.MARGIN)
    }

    @Test
    fun `the free nudge region stays a small inset from the canvas edges`() {
        assertThat(ArtLayoutSpec.NUDGE_MARGIN).isEqualTo(32)
        assertThat(ArtLayoutSpec.NUDGE_MARGIN).isLessThan(ArtLayoutSpec.MARGIN)
    }

    @Test
    fun `photo story safe area keeps the native story UI clear`() {
        assertThat(ArtLayoutSpec.PHOTO_9X16_SAFE_TOP).isEqualTo(250)
        assertThat(ArtLayoutSpec.PHOTO_9X16_SAFE_BOTTOM).isEqualTo(340)
    }

    @Test
    fun `base text sizes match the documented values`() {
        assertThat(ArtLayoutSpec.DISTANCE_SIZE_4_5).isEqualTo(135)
        assertThat(ArtLayoutSpec.DISTANCE_SIZE_9_16).isEqualTo(135)
        assertThat(ArtLayoutSpec.DISTANCE_SIZE_SQUARE).isEqualTo(117)
        assertThat(ArtLayoutSpec.DISTANCE_LABEL_SIZE).isEqualTo(24)
        assertThat(ArtLayoutSpec.PACE_TIME_LABEL_SIZE).isEqualTo(22)
        assertThat(ArtLayoutSpec.PACE_TIME_VALUE_SIZE).isEqualTo(52)
        assertThat(ArtLayoutSpec.EXTRA_SIZE).isEqualTo(34)
        assertThat(ArtLayoutSpec.BRAND_TEXT_SIZE).isEqualTo(30)
    }
}
