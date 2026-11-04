package com.meucoach.app.story

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test

class StoryLayoutTest {
    private val width = 1080f
    private val height = 1920f

    @Test
    fun `reserves the instagram header and footer`() {
        assertThat(StoryLayout.safeTop(height)).isCloseTo(249.6f, within(0.1f))
        assertThat(StoryLayout.safeBottom(height)).isCloseTo(1670.4f, within(0.1f))
    }

    @Test
    fun `keeps the logo, metrics and card inside the safe area`() {
        assertThat(StoryLayout.logoTop(height)).isGreaterThan(StoryLayout.safeTop(height))
        assertThat(StoryLayout.metricBaseline(height)).isLessThan(StoryLayout.routeTop(height))
        assertThat(StoryLayout.cardTop(height)).isGreaterThan(StoryLayout.routeTop(height))
        // The route may slide under the opaque card; the card is drawn on top.
        assertThat(StoryLayout.routeBottom(height)).isGreaterThan(StoryLayout.cardTop(height))
        assertThat(StoryLayout.cardBottom(height)).isLessThan(StoryLayout.safeBottom(height))
        assertThat(StoryLayout.attributionBaseline(height)).isLessThan(StoryLayout.safeBottom(height))
    }

    @Test
    fun `projects the route into the square between header and card`() {
        val square = StoryLayout.routeSquare(720f, 1280f)

        assertThat(square.side).isCloseTo(483.3f, within(0.1f))
        assertThat(square.left).isCloseTo(118.3f, within(0.1f))
        assertThat(square.top).isCloseTo(388.3f, within(0.1f))
        assertThat(square.centerX).isCloseTo(360f, within(0.1f))
        assertThat(square.centerY).isCloseTo(630f, within(0.1f))
    }

    @Test
    fun `scales margins with the frame width`() {
        assertThat(StoryLayout.margin(720f)).isCloseTo(56f, within(0.1f))
        assertThat(StoryLayout.margin(1080f)).isCloseTo(84f, within(0.1f))
        assertThat(StoryLayout.margin(360f)).isCloseTo(28f, within(0.1f))
    }
}
