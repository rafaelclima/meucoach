package com.meucoach.app.story

import com.meucoach.core.domain.story.BoundingBox
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.math.abs

class StoryMapProjectionTest {
    private val bounds = BoundingBox(
        minLat = -5.80,
        maxLat = -5.78,
        minLon = -35.22,
        maxLon = -35.20,
    )

    @Test
    fun `top-down projection matches the snapshot framing`() {
        val width = 720
        val height = 1280
        val camera = StoryMapCameraCalculator.calculate(bounds, width, height)
        val projection = StoryMapProjection(camera, width, height)
        val square = StoryLayout.routeSquare(width.toFloat(), height.toFloat())

        val topLeft = projection.project(bounds.maxLat, bounds.minLon)
        val bottomRight = projection.project(bounds.minLat, bounds.maxLon)

        assertThat(topLeft[0]).isCloseTo(square.left, within(2f))
        assertThat(topLeft[1]).isCloseTo(square.top, within(2f))
        assertThat(bottomRight[0]).isCloseTo(square.left + square.side, within(2f))
        assertThat(bottomRight[1]).isCloseTo(square.top + square.side, within(2f))
    }

    @Test
    fun `pitched projection still contains the whole route`() {
        val width = 720
        val height = 1280
        val camera = StoryMapCameraCalculator.calculateForStory(bounds, width, height, pitchDegrees = 38f)
        val projection = StoryMapProjection(camera, width, height)
        val square = StoryLayout.routeSquare(width.toFloat(), height.toFloat())
        val corners = listOf(
            bounds.maxLat to bounds.minLon,
            bounds.maxLat to bounds.maxLon,
            bounds.minLat to bounds.minLon,
            bounds.minLat to bounds.maxLon,
        ).map { projection.project(it.first, it.second) }

        corners.forEach { point ->
            assertThat(point[0]).isBetween(square.left - 1f, square.left + square.side + 1f)
            assertThat(point[1]).isBetween(square.top - 1f, square.top + square.side + 1f)
        }
        assertThat(abs(camera.pitchDegrees - 38f)).isLessThan(0.01f)
    }

    @Test
    fun `bearing rotates the projected scenery`() {
        val width = 720
        val height = 1280
        val north = StoryMapCameraCalculator.calculate(bounds, width, height)
        val rotated = north.copy(bearingDegrees = 90f)
        val plain = StoryMapProjection(north, width, height).project(bounds.maxLat, bounds.minLon)
        val turned = StoryMapProjection(rotated, width, height).project(bounds.maxLat, bounds.minLon)

        // A 90° bearing rotates the scenery around the frame center.
        val moved = kotlin.math.hypot(
            (turned[0] - plain[0]).toDouble(),
            (turned[1] - plain[1]).toDouble(),
        )
        assertThat(moved).isGreaterThan(20.0)
    }

    private fun within(value: Float) = org.assertj.core.data.Offset.offset(value)
}
