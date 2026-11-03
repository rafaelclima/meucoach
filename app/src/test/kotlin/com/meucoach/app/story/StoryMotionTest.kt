package com.meucoach.app.story

import com.meucoach.app.ui.activitydetail.RouteData
import com.meucoach.app.ui.activitydetail.RoutePoint
import com.meucoach.core.model.Pace
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test

class StoryMotionTest {
    @Test
    fun `camera starts still and pushes in without exposing the frame`() {
        val start = StoryMotion.camera(0f, 30f)
        val middle = StoryMotion.camera(15f, 30f)
        val end = StoryMotion.camera(30f, 30f)

        assertThat(start.scale).isEqualTo(StoryMotion.MIN_SCALE)
        assertThat(start.rotationDegrees).isCloseTo(0f, within(1e-4f))
        assertThat(start.pitchDegrees).isCloseTo(0f, within(1e-4f))
        assertThat(start.yawDegrees).isCloseTo(0f, within(1e-4f))
        assertThat(start.shiftX).isCloseTo(0f, within(1e-4f))
        assertThat(middle.scale).isGreaterThan(start.scale)
        assertThat(end.scale).isEqualTo(StoryMotion.MIN_SCALE + StoryMotion.MAX_EXTRA_SCALE)
        assertThat(end.rotationDegrees).isCloseTo(0f, within(1e-3f))
        assertThat(middle.rotationDegrees).isCloseTo(StoryMotion.MAX_ROTATION_DEGREES, within(0.001f))
        assertThat(middle.pitchDegrees).isCloseTo(StoryMotion.MAX_PITCH_DEGREES, within(0.001f))
        assertThat(middle.yawDegrees).isCloseTo(0f, within(1e-4f))
        assertThat(StoryMotion.camera(7.5f, 30f).yawDegrees)
            .isCloseTo(StoryMotion.MAX_YAW_DEGREES, within(0.001f))
    }

    @Test
    fun `camera pushes in while a km card is on screen`() {
        val plan = plan()
        val withCard = StoryMotion.camera(11f, plan)
        val withoutCard = StoryMotion.camera(11f, plan.durationSec)

        assertThat(StoryMotion.cardFocus(plan, 11f)).isEqualTo(1f)
        assertThat(withCard.scale).isGreaterThan(withoutCard.scale)
        assertThat(StoryMotion.cardFocus(plan, 0f)).isEqualTo(0f)
    }

    @Test
    fun `camera scale never exceeds the layout allowance`() {
        val plan = plan()

        plan.segments.forEach { segment ->
            val time = (segment.startSec + segment.endSec) / 2f
            assertThat(StoryMotion.camera(time, plan).scale)
                .isLessThanOrEqualTo(StoryMotion.MAX_CAMERA_SCALE)
        }
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
