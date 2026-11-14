package com.meucoach.app.story

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test

class StoryLayoutTest {
    private val width = 1080f
    private val height = 1920f

    @Test
    fun `reserves the reels header and footer`() {
        assertThat(StoryLayout.safeTop(height)).isCloseTo(249.6f, within(0.1f))
        // Reels covers ~420 px at the bottom of a 1920 px video: 22% safe base.
        assertThat(StoryLayout.safeBottom(height)).isCloseTo(1497.6f, within(0.1f))
    }

    @Test
    fun `keeps the logo, metrics and card inside the safe area`() {
        assertThat(StoryLayout.logoTop(height)).isGreaterThan(StoryLayout.safeTop(height))
        assertThat(StoryLayout.metricBaseline(height)).isLessThan(StoryLayout.routeTop(height))
        assertThat(StoryLayout.cardTop(height)).isGreaterThan(StoryLayout.routeTop(height))
        // The route may slide under the opaque card; the card is drawn on top.
        assertThat(StoryLayout.routeBottom(height)).isGreaterThan(StoryLayout.cardTop(height))
        assertThat(StoryLayout.cardBottom(height)).isLessThanOrEqualTo(StoryLayout.safeBottom(height))
    }

    @Test
    fun `card occupies sixty two to seventy eight percent of the frame`() {
        assertThat(StoryLayout.cardTop(height)).isCloseTo(height * 0.62f, within(0.1f))
        assertThat(StoryLayout.cardBottom(height)).isCloseTo(height * 0.78f, within(0.1f))
    }

    @Test
    fun `closing card with five metrics gets two percent more height upward`() {
        assertThat(StoryLayout.finalCardTop(height)).isCloseTo(height * 0.60f, within(0.1f))
        assertThat(StoryLayout.finalCardTop(height)).isLessThan(StoryLayout.cardTop(height))
        assertThat(StoryLayout.cardBottom(height)).isLessThanOrEqualTo(StoryLayout.safeBottom(height))
    }

    @Test
    fun `attribution is pinned to the bottom left corner`() {
        assertThat(StoryLayout.attributionBaseline(height)).isCloseTo(height - 21f, within(0.1f))
        assertThat(StoryLayout.attributionBaseline(height)).isGreaterThan(StoryLayout.safeBottom(height))
        assertThat(StoryLayout.attributionLeft(width)).isCloseTo(18f, within(0.1f))
        assertThat(StoryLayout.attributionLeft(width)).isLessThan(StoryLayout.margin(width))
    }

    @Test
    fun `projects the route into the square between header and card`() {
        val square = StoryLayout.routeSquare(720f, 1280f)

        assertThat(square.side).isCloseTo(527.3f, within(0.1f))
        assertThat(square.left).isCloseTo(96.3f, within(0.1f))
        assertThat(square.top).isCloseTo(392.7f, within(0.1f))
        assertThat(square.centerX).isCloseTo(360f, within(0.1f))
        assertThat(square.centerY).isCloseTo(656.4f, within(0.1f))
    }

    @Test
    fun `scales margins with the frame width`() {
        assertThat(StoryLayout.margin(720f)).isCloseTo(56f, within(0.1f))
        assertThat(StoryLayout.margin(1080f)).isCloseTo(84f, within(0.1f))
        assertThat(StoryLayout.margin(360f)).isCloseTo(28f, within(0.1f))
    }
}
