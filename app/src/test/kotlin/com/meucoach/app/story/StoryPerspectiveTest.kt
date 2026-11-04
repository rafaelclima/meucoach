package com.meucoach.app.story

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryPerspectiveTest {
    private val width = 1_080f
    private val height = 1_920f

    @Test
    fun `no transform at rest`() {
        assertThat(StoryPerspective.matrix(width, height, pitchDegrees = 0f, yawDegrees = 0f)).isNull()
    }

    @Test
    fun `pitch narrows the top and widens the bottom`() {
        val points = corners()
        StoryPerspective.matrix(width, height, pitchDegrees = 3f, yawDegrees = 0f)!!.mapPoints(points)

        assertThat(points[0]).isGreaterThan(0f)
        assertThat(points[2]).isLessThan(width)
        assertThat(points[4]).isGreaterThan(width)
        assertThat(points[6]).isLessThan(0f)
    }

    @Test
    fun `pitch drops the top edge toward the horizon`() {
        val points = corners()
        StoryPerspective.matrix(width, height, pitchDegrees = 3f, yawDegrees = 0f)!!.mapPoints(points)

        assertThat(points[1]).isGreaterThan(0f)
        assertThat(points[3]).isGreaterThan(0f)
    }

    @Test
    fun `yaw compresses the left and stretches the right`() {
        val points = corners()
        StoryPerspective.matrix(width, height, pitchDegrees = 0f, yawDegrees = 2f)!!.mapPoints(points)

        assertThat(points[1]).isGreaterThan(0f)
        assertThat(points[3]).isLessThan(0f)
        assertThat(points[7]).isLessThan(height)
        assertThat(points[5]).isGreaterThan(height)
    }

    private fun corners() = floatArrayOf(
        0f,
        0f,
        width,
        0f,
        width,
        height,
        0f,
        height,
    )
}
