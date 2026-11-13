package com.meucoach.app.story

import com.meucoach.core.domain.story.BoundingBox
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

class StoryMapCameraTest {
    private val bounds = BoundingBox(
        minLat = -5.790,
        maxLat = -5.772,
        minLon = -35.210,
        maxLon = -35.206,
    )

    @Test
    fun `projects the bounds into the route square`() {
        val width = 720
        val height = 1280
        val camera = StoryMapCameraCalculator.calculate(bounds, width, height)
        val square = StoryMapCameraCalculator.routeSquare(width.toFloat(), height.toFloat())
        val worldSize = 512.0 * 2.0.pow(camera.zoom)

        val centerX = (mercatorX(bounds.minLon) + mercatorX(bounds.maxLon)) / 2.0
        val centerY = (mercatorY(bounds.maxLat) + mercatorY(bounds.minLat)) / 2.0
        val pixelX = width / 2.0 + (centerX - mercatorX(camera.longitude)) * worldSize
        val pixelY = height / 2.0 + (centerY - mercatorY(camera.latitude)) * worldSize

        assertThat(pixelX).isCloseTo(square.centerX.toDouble(), within(0.5))
        assertThat(pixelY).isCloseTo(square.centerY.toDouble(), within(0.5))
    }

    @Test
    fun `fills the route square with the bounds`() {
        val width = 720
        val height = 1280
        val camera = StoryMapCameraCalculator.calculate(bounds, width, height)
        val square = StoryMapCameraCalculator.routeSquare(width.toFloat(), height.toFloat())
        val worldSize = 512.0 * 2.0.pow(camera.zoom)

        val left = width / 2.0 + (mercatorX(bounds.minLon) - mercatorX(camera.longitude)) * worldSize
        val right = width / 2.0 + (mercatorX(bounds.maxLon) - mercatorX(camera.longitude)) * worldSize
        val top = height / 2.0 + (mercatorY(bounds.maxLat) - mercatorY(camera.latitude)) * worldSize
        val bottom = height / 2.0 + (mercatorY(bounds.minLat) - mercatorY(camera.latitude)) * worldSize
        val expected = square.side * StoryMapCameraCalculator.MAP_ZOOM_FACTOR.toDouble()

        assertThat(maxOf(right - left, bottom - top)).isCloseTo(expected, within(1.0))
        assertThat(left).isGreaterThanOrEqualTo(0.0)
        assertThat(right).isLessThanOrEqualTo(width.toDouble())
        assertThat(top).isGreaterThanOrEqualTo(0.0)
        assertThat(bottom).isLessThanOrEqualTo(height.toDouble())
    }

    @Test
    fun `zooms closer for a shorter route`() {
        val longRun = StoryMapCameraCalculator.calculate(
            BoundingBox(minLat = -5.80, maxLat = -5.76, minLon = -35.22, maxLon = -35.18),
        )
        val shortRun = StoryMapCameraCalculator.calculate(
            BoundingBox(minLat = -5.786, maxLat = -5.784, minLon = -35.211, maxLon = -35.209),
        )

        assertThat(shortRun.zoom).isGreaterThan(longRun.zoom)
        assertThat(longRun.zoom).isBetween(12.0, 16.0)
    }

    @Test
    fun `keeps the route square proportional at preview size`() {
        val square = StoryMapCameraCalculator.routeSquare(360f, 640f)

        assertThat(square.side).isCloseTo(263.7f, within(0.1f))
        assertThat(square.centerX).isCloseTo(180f, within(0.1f))
        assertThat(square.centerY).isCloseTo(328.2f, within(0.1f))
        assertThat(square.left).isCloseTo(48.2f, within(0.1f))
        assertThat(square.top).isCloseTo(196.4f, within(0.1f))
    }

    @Test
    fun `centres the map on the route square`() {
        val width = 720
        val height = 1280
        val camera = StoryMapCameraCalculator.calculate(bounds, width, height)
        val square = StoryMapCameraCalculator.routeSquare(width.toFloat(), height.toFloat())

        // The camera target moves opposite to the square offset so the bounds
        // land on the square, whether it sits above or below the frame centre.
        val boundsCenterLat = (bounds.minLat + bounds.maxLat) / 2.0
        if (square.centerY < height / 2f) {
            assertThat(camera.latitude).isLessThan(boundsCenterLat)
        } else {
            assertThat(camera.latitude).isGreaterThan(boundsCenterLat)
        }
        assertThat(abs(camera.longitude - (bounds.minLon + bounds.maxLon) / 2.0)).isLessThan(1e-9)
    }

    private fun mercatorX(longitude: Double): Double = (longitude + 180.0) / 360.0

    private fun mercatorY(latitude: Double): Double =
        0.5 - ln(tan(PI / 4.0 + Math.toRadians(latitude) / 2.0)) / (2.0 * PI)
}
