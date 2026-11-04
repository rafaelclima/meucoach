package com.meucoach.app.story

import com.meucoach.app.ui.activitydetail.RouteData
import com.meucoach.app.ui.activitydetail.RoutePoint
import com.meucoach.core.model.Pace
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import kotlin.math.abs

class StoryMotionTest {
    @Test
    fun `camera starts on the overview and dives into follow`() {
        val plan = plan()
        val start = StoryMotion.camera(0f, plan)
        val middle = StoryMotion.camera(5f, plan)
        val end = StoryMotion.camera(15f, plan)

        assertThat(start.follow).isCloseTo(0f, within(1e-4f))
        assertThat(start.scale).isCloseTo(StoryMotion.OVERVIEW_SCALE, within(1e-4f))
        assertThat(start.rotationDegrees).isCloseTo(0f, within(1e-4f))
        assertThat(start.pitchDegrees).isCloseTo(StoryMotion.OVERVIEW_PITCH_DEGREES, within(1e-4f))

        assertThat(middle.follow).isCloseTo(1f, within(1e-4f))
        assertThat(middle.scale).isCloseTo(StoryMotion.FOLLOW_SCALE, within(1e-4f))
        assertThat(middle.pitchDegrees).isCloseTo(StoryMotion.FOLLOW_PITCH_DEGREES, within(1e-4f))

        assertThat(end.follow).isCloseTo(0f, within(1e-4f))
        assertThat(end.scale).isCloseTo(StoryMotion.OVERVIEW_SCALE, within(1e-4f))
        assertThat(end.rotationDegrees).isCloseTo(0f, within(1e-4f))
        assertThat(end.pitchDegrees).isCloseTo(StoryMotion.OVERVIEW_PITCH_DEGREES, within(1e-4f))
    }

    @Test
    fun `camera drifts smoothly instead of snapping to the route bearing`() {
        val plan = plan()

        assertThat(abs(StoryMotion.camera(2.5f, plan).rotationDegrees)).isGreaterThan(3f)
        assertThat(abs(StoryMotion.camera(5f, plan).rotationDegrees)).isLessThan(0.01f)
        assertThat(abs(StoryMotion.camera(7.5f, plan).rotationDegrees)).isGreaterThan(3f)
    }

    @Test
    fun `camera follows the runner point along the route`() {
        val plan = plan()

        assertThat(StoryMotion.camera(0f, plan).focusX).isCloseTo(StoryMotion.FOCUS_MARGIN, within(1e-4f))
        assertThat(StoryMotion.camera(0f, plan).focusY).isCloseTo(StoryMotion.FOCUS_MARGIN, within(1e-4f))
        assertThat(StoryMotion.camera(5f, plan).focusX).isCloseTo(0.5f, within(1e-4f))
        assertThat(StoryMotion.camera(5f, plan).focusY).isCloseTo(0.5f, within(1e-4f))
    }

    @Test
    fun `camera pushes in and tilts while a km card is on screen`() {
        val plan = plan()
        val withCard = StoryMotion.camera(11f, plan)

        assertThat(StoryMotion.cardFocus(plan, 11f)).isEqualTo(1f)
        assertThat(withCard.scale).isCloseTo(StoryMotion.MAX_CAMERA_SCALE, within(1e-4f))
        assertThat(withCard.pitchDegrees)
            .isCloseTo(StoryMotion.FOLLOW_PITCH_DEGREES + StoryMotion.CARD_EXTRA_PITCH_DEGREES, within(0.001f))
    }

    @Test
    fun `camera scale never exceeds the layout allowance`() {
        val plan = plan()

        var time = 0f
        while (time <= plan.durationSec) {
            assertThat(StoryMotion.camera(time, plan).scale)
                .isLessThanOrEqualTo(StoryMotion.MAX_CAMERA_SCALE)
            time += 0.25f
        }
    }

    @Test
    fun `route point is interpolated by path length`() {
        val route = RouteData(
            listOf(
                RoutePoint(0f, 0f),
                RoutePoint(1f, 0f),
                RoutePoint(1f, 1f),
            ),
        )

        assertThat(StoryMotion.routePointAt(route, 0.25f).x).isCloseTo(0.5f, within(1e-4f))
        assertThat(StoryMotion.routePointAt(route, 0.25f).y).isCloseTo(0f, within(1e-4f))
        assertThat(StoryMotion.routePointAt(route, 0.5f).x).isCloseTo(1f, within(1e-4f))
        assertThat(StoryMotion.routePointAt(route, 0.5f).y).isCloseTo(0f, within(1e-4f))
        assertThat(StoryMotion.routePointAt(route, 0.75f).x).isCloseTo(1f, within(1e-4f))
        assertThat(StoryMotion.routePointAt(route, 0.75f).y).isCloseTo(0.5f, within(1e-4f))
    }

    @Test
    fun `follow intensity dives in and pulls back inside the closing segment`() {
        val plan = plan()

        assertThat(StoryMotion.followIntensity(plan, 0f)).isCloseTo(0f, within(1e-4f))
        assertThat(StoryMotion.followIntensity(plan, 1.6f)).isCloseTo(1f, within(1e-4f))
        assertThat(StoryMotion.followIntensity(plan, 11f)).isCloseTo(1f, within(1e-4f))
        assertThat(StoryMotion.followIntensity(plan, 12.6f)).isCloseTo(0.5f, within(0.001f))
        assertThat(StoryMotion.followIntensity(plan, 15f)).isCloseTo(0f, within(1e-4f))
    }

    @Test
    fun `easing is symmetric and monotonic`() {
        assertThat(StoryMotion.easeInOut(0f)).isEqualTo(0f)
        assertThat(StoryMotion.easeInOut(0.5f)).isEqualTo(0.5f)
        assertThat(StoryMotion.easeInOut(1f)).isEqualTo(1f)
        assertThat(StoryMotion.easeInOut(0.25f)).isLessThan(0.25f)
        assertThat(StoryMotion.easeInOut(0.75f)).isGreaterThan(0.75f)
    }

    @Test
    fun `route slows near the km card and accelerates in the middle`() {
        val plan = plan()
        val linearQuarter = 0.25f

        assertThat(StoryMotion.progressAt(plan, 0f)).isEqualTo(0f)
        assertThat(StoryMotion.progressAt(plan, 2.5f)).isLessThan(linearQuarter)
        assertThat(StoryMotion.progressAt(plan, 5f)).isEqualTo(0.5f)
        assertThat(StoryMotion.progressAt(plan, 11f)).isEqualTo(1f)
        assertThat(StoryMotion.progressAt(plan, 20f)).isEqualTo(1f)
    }

    private fun plan() = StoryPlan(
        route = RouteData(listOf(RoutePoint(0f, 0f), RoutePoint(1f, 1f))),
        splits = listOf(StorySplit(1, Pace.fromSecondsPerKm(360), 145, 60, 1f)),
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
