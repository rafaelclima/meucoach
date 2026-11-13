package com.meucoach.app.story

import android.graphics.Matrix
import com.meucoach.core.domain.story.RouteData
import com.meucoach.core.domain.story.RoutePoint
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StorySegment
import com.meucoach.core.domain.story.StorySegmentType
import com.meucoach.core.domain.story.StorySplit
import com.meucoach.core.domain.story.StorySplitBarCalculator
import com.meucoach.core.model.Pace
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The map is a static bitmap drawn into the full frame and then transformed.
 * If its inverse transform pushes a screen corner outside [0,width]×[0,height],
 * the frame shows the app background (the black borders users saw). This test
 * sweeps the whole timeline for representative routes and frame sizes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryCameraCoverageTest {
    @Test
    fun `camera never exposes the frame`() {
        val plans = listOf(
            plan(route = listOf(RoutePoint(0.1f, 0.9f), RoutePoint(0.9f, 0.1f))),
            plan(
                route = listOf(
                    RoutePoint(0.1f, 0.5f),
                    RoutePoint(0.5f, 0.1f),
                    RoutePoint(0.9f, 0.5f),
                    RoutePoint(0.5f, 0.9f),
                ),
            ),
            plan(
                route = listOf(
                    RoutePoint(0f, 0f),
                    RoutePoint(1f, 0f),
                    RoutePoint(1f, 1f),
                ),
            ),
        )
        val sizes = listOf(720f to 1280f, 1080f to 1920f)

        sizes.forEach { (width, height) ->
            val square = StoryLayout.routeSquare(width, height)
            plans.forEach { storyPlan ->
                var time = 0f
                while (time <= storyPlan.durationSec) {
                    assertCovers(
                        camera = StoryMotion.camera(time, storyPlan),
                        square = square,
                        width = width,
                        height = height,
                        label = "time=$time",
                    )
                    time += 0.25f
                }
            }
        }
    }

    private fun assertCovers(
        camera: StoryCamera,
        square: StoryLayout.RouteSquare,
        width: Float,
        height: Float,
        label: String,
    ) {
        val matrix = StoryPerspective.cameraMatrix(camera, square, width, height)
        val inverse = Matrix()
        assertThat(matrix.invert(inverse)).describedAs("invertible at $label").isTrue()
        val corners = floatArrayOf(0f, 0f, width, 0f, width, height, 0f, height)
        inverse.mapPoints(corners)
        for (corner in 0 until 4) {
            assertThat(corners[corner * 2]).describedAs("x at $label corner $corner").isBetween(0f, width)
            assertThat(corners[corner * 2 + 1])
                .describedAs("y at $label corner $corner")
                .isBetween(0f, height)
        }
    }

    private fun plan(route: List<RoutePoint>): StoryPlan {
        val split = StorySplit(
            km = 1,
            pace = Pace.fromSecondsPerKm(360),
            heartRate = 145,
            progress = 1f,
        )
        return StoryPlan(
            route = RouteData(route),
            splits = listOf(split),
            allSplits = listOf(split),
            splitBar = StorySplitBarCalculator.calculate(listOf(split), setOf(1)),
            segments = listOf(
                StorySegment(0f, 10f, StorySegmentType.MOVE, 0f, 1f),
                StorySegment(10f, 12f, StorySegmentType.CARD, 1f, 1f),
                StorySegment(12f, 15f, StorySegmentType.FINAL, 1f, 1f),
            ),
            durationSec = 15f,
            distanceKm = 1.0,
            durationTotalSec = 900,
            calories = 60,
            averagePace = Pace.fromSecondsPerKm(360),
            averageHeartRate = 145,
            boundingBox = null,
        )
    }
}
