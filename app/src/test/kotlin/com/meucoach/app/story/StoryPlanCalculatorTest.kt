package com.meucoach.app.story

import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant

class StoryPlanCalculatorTest {
    @Test
    fun `builds two km cards and a thirty second timeline`() {
        val activity = activity(
            samples = listOf(
                sample(offsetSec = 0, latitude = -5.790, longitude = -35.210, hr = 145),
                sample(offsetSec = 360, latitude = -5.781, longitude = -35.210, hr = 150),
                sample(offsetSec = 720, latitude = -5.772, longitude = -35.210, hr = 155),
            ),
            durationSec = 720,
            distanceM = 2_000.0,
            calories = 140,
        )

        val plan = StoryPlanCalculator.calculate(activity, targetDurationSec = 30)

        assertThat(plan).isNotNull
        assertThat(plan!!.splits).hasSize(2)
        assertThat(plan.splits.first().pace).isEqualTo(Pace.fromSecondsPerKm(360))
        assertThat(plan.splits.first().heartRate).isEqualTo(148)
        assertThat(plan.splits.first().calories).isEqualTo(70)
        assertThat(plan.splits[1].progress).isGreaterThan(0.99f)
        assertThat(plan.segments.count { it.type == StorySegmentType.CARD }).isEqualTo(2)
        assertThat(plan.segments.first().startSec).isEqualTo(0f)
        assertThat(plan.segments.last().endSec).isEqualTo(30f)
        assertThat(plan.segments.sumOf { (it.endSec - it.startSec).toDouble() })
            .isCloseTo(30.0, org.assertj.core.api.Assertions.within(0.01))
    }

    @Test
    fun `keeps a single move segment without km splits`() {
        val activity = activity(
            samples = listOf(
                sample(offsetSec = 0, latitude = -5.790, longitude = -35.210, hr = 140),
                sample(offsetSec = 120, latitude = -5.788, longitude = -35.210, hr = 145),
            ),
            durationSec = 120,
            distanceM = 300.0,
            calories = 20,
        )

        val plan = StoryPlanCalculator.calculate(activity, targetDurationSec = 30)

        assertThat(plan).isNotNull
        assertThat(plan!!.splits).isEmpty()
        assertThat(plan.segments).hasSize(1)
        assertThat(plan.segments.single().type).isEqualTo(StorySegmentType.MOVE)
        assertThat(plan.segments.single().endSec).isEqualTo(30f)
    }

    @Test
    fun `returns null without a gps route`() {
        val activity = activity(
            samples = listOf(sample(offsetSec = 0, latitude = null, longitude = null, hr = 140)),
            durationSec = 600,
            distanceM = 1_500.0,
            calories = 90,
        )

        assertThat(StoryPlanCalculator.calculate(activity)).isNull()
    }

    private fun activity(samples: List<ActivitySample>, durationSec: Int, distanceM: Double, calories: Int?) =
        ImportedActivity(
            source = ActivitySource.GPX,
            sport = Sport.RUNNING,
            startTime = Instant.parse("2026-10-30T12:00:00Z"),
            durationSec = durationSec,
            distanceM = distanceM,
            calories = calories,
            samples = samples,
        )

    private fun sample(offsetSec: Int, latitude: Double?, longitude: Double?, hr: Int) = ActivitySample(
        offsetSec = offsetSec,
        latitude = latitude,
        longitude = longitude,
        heartRate = hr,
    )
}
