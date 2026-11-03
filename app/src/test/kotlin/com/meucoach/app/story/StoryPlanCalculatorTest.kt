package com.meucoach.app.story

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.story.StoryVerdictTone
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.HeartRateZone
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

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
        assertThat(plan.segments.last().type).isEqualTo(StorySegmentType.FINAL)
        assertThat(plan.segments.last().startSec).isEqualTo(27f)
        assertThat(plan.segments.last().endSec).isEqualTo(30f)
        assertThat(plan.segments.last().progressStart).isEqualTo(1f)
        assertThat(plan.segments.last().progressEnd).isEqualTo(1f)
        assertThat(plan.segments.sumOf { (it.endSec - it.startSec).toDouble() })
            .isCloseTo(30.0, within(0.01))
    }

    @Test
    fun `closes a run without km splits with the final frame`() {
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
        assertThat(plan.segments).hasSize(2)
        assertThat(plan.segments.first().type).isEqualTo(StorySegmentType.MOVE)
        assertThat(plan.segments.first().endSec).isEqualTo(27f)
        assertThat(plan.segments.last().type).isEqualTo(StorySegmentType.FINAL)
        assertThat(plan.segments.last().startSec).isEqualTo(27f)
        assertThat(plan.segments.last().endSec).isEqualTo(30f)
    }

    @Test
    fun `reserves the closing frame inside shorter target durations`() {
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

        val plan = StoryPlanCalculator.calculate(activity, targetDurationSec = 10)

        assertThat(plan).isNotNull
        assertThat(plan!!.durationSec).isEqualTo(10f)
        assertThat(plan.segments.last().type).isEqualTo(StorySegmentType.FINAL)
        assertThat(plan.segments.last().startSec).isEqualTo(7f)
        assertThat(plan.segments.last().endSec).isEqualTo(10f)
        assertThat(plan.segments.sumOf { (it.endSec - it.startSec).toDouble() })
            .isCloseTo(10.0, within(0.01))
    }

    @Test
    fun `keeps a three second minimum when the target is shorter than the closing frame`() {
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

        val plan = StoryPlanCalculator.calculate(activity, targetDurationSec = 2)

        assertThat(plan).isNotNull
        assertThat(plan!!.durationSec).isEqualTo(3f)
        assertThat(plan.segments).hasSize(1)
        assertThat(plan.segments.single().type).isEqualTo(StorySegmentType.FINAL)
        assertThat(plan.segments.single().startSec).isEqualTo(0f)
        assertThat(plan.segments.single().endSec).isEqualTo(3f)
    }

    @Test
    fun `derives pace and heart rate from the run when the summary is absent`() {
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

        val plan = StoryPlanCalculator.calculate(activity)

        assertThat(plan!!.averagePace).isEqualTo(Pace.fromSecondsPerKm(360))
        assertThat(plan.averageHeartRate).isEqualTo(150)
    }

    @Test
    fun `prefers canonical summary values over sample averages`() {
        val activity = activity(
            samples = listOf(
                sample(offsetSec = 0, latitude = -5.790, longitude = -35.210, hr = 145),
                sample(offsetSec = 360, latitude = -5.781, longitude = -35.210, hr = 150),
                sample(offsetSec = 720, latitude = -5.772, longitude = -35.210, hr = 155),
            ),
            durationSec = 720,
            distanceM = 2_000.0,
            calories = 140,
            averagePace = Pace.fromSecondsPerKm(354),
            averageHeartRate = 149,
        )

        val plan = StoryPlanCalculator.calculate(activity)

        assertThat(plan!!.averagePace).isEqualTo(Pace.fromSecondsPerKm(354))
        assertThat(plan.averageHeartRate).isEqualTo(149)
    }

    @Test
    fun `keeps missing heart rate null instead of zero`() {
        val activity = activity(
            samples = listOf(
                sample(offsetSec = 0, latitude = -5.790, longitude = -35.210, hr = null),
                sample(offsetSec = 360, latitude = -5.781, longitude = -35.210, hr = null),
                sample(offsetSec = 720, latitude = -5.772, longitude = -35.210, hr = null),
            ),
            durationSec = 720,
            distanceM = 2_000.0,
            calories = 140,
        )

        val plan = StoryPlanCalculator.calculate(activity)

        assertThat(plan!!.averageHeartRate).isNull()
    }

    @Test
    fun `computes the bounding box of the gps route`() {
        val activity = activity(
            samples = listOf(
                sample(offsetSec = 0, latitude = -5.790, longitude = -35.210, hr = 145),
                sample(offsetSec = 180, latitude = -5.786, longitude = -35.206, hr = 148),
                sample(offsetSec = 360, latitude = -5.781, longitude = -35.210, hr = 150),
                sample(offsetSec = 720, latitude = -5.772, longitude = -35.214, hr = 155),
            ),
            durationSec = 720,
            distanceM = 2_000.0,
            calories = 140,
        )

        val plan = StoryPlanCalculator.calculate(activity)

        assertThat(plan!!.boundingBox).isEqualTo(
            BoundingBox(
                minLat = -5.790,
                maxLat = -5.772,
                minLon = -35.214,
                maxLon = -35.206,
            ),
        )
    }

    @Test
    fun `stamps splits and paints the route with the measured zone`() {
        val activity = activity(
            samples = listOf(
                sample(offsetSec = 0, latitude = -5.790, longitude = -35.210, hr = 150),
                sample(offsetSec = 360, latitude = -5.781, longitude = -35.210, hr = 150),
                sample(offsetSec = 720, latitude = -5.772, longitude = -35.210, hr = 150),
            ),
            durationSec = 720,
            distanceM = 2_000.0,
            calories = 140,
        )

        val plan = StoryPlanCalculator.calculate(
            activity = activity,
            lthrBpm = 180,
            session = easySession(),
        )

        assertThat(plan).isNotNull
        assertThat(plan!!.verdict?.tone).isEqualTo(StoryVerdictTone.ON_TARGET)
        assertThat(plan.splits.map { it.verdict }).containsOnly(StoryVerdictTone.ON_TARGET)
        assertThat(plan.zoneRuns).isNotEmpty()
        assertThat(plan.zoneRuns.map { it.zone }).containsOnly(HeartRateZone.Z2)
        assertThat(plan.zoneRuns.first().startProgress).isEqualTo(0f)
        assertThat(plan.zoneRuns.last().endProgress).isEqualTo(1f)
    }

    @Test
    fun `keeps the route unpainted without calibration`() {
        val activity = activity(
            samples = listOf(
                sample(offsetSec = 0, latitude = -5.790, longitude = -35.210, hr = 150),
                sample(offsetSec = 360, latitude = -5.781, longitude = -35.210, hr = 150),
            ),
            durationSec = 360,
            distanceM = 1_000.0,
            calories = 70,
        )

        val plan = StoryPlanCalculator.calculate(activity, session = easySession())

        assertThat(plan!!.zoneRuns).isEmpty()
        assertThat(plan.verdict?.tone).isNull()
        assertThat(plan.verdict?.coachLine).isEqualTo("Sem alvo definido — só o esforço.")
        assertThat(plan.splits.map { it.verdict }).containsOnlyNulls()
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

    private fun easySession() = PlannedSession(
        date = LocalDate.parse("2026-10-30"),
        type = WorkoutType.EASY,
        title = "Fácil",
        durationMin = 30,
        rationale = "Teste determinístico.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-FRIEL-2018"),
    )

    private fun activity(
        samples: List<ActivitySample>,
        durationSec: Int,
        distanceM: Double,
        calories: Int?,
        averagePace: Pace? = null,
        averageHeartRate: Int? = null,
    ) = ImportedActivity(
        source = ActivitySource.GPX,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-10-30T12:00:00Z"),
        durationSec = durationSec,
        distanceM = distanceM,
        calories = calories,
        averagePace = averagePace,
        averageHeartRate = averageHeartRate,
        samples = samples,
    )

    private fun sample(offsetSec: Int, latitude: Double?, longitude: Double?, hr: Int?) = ActivitySample(
        offsetSec = offsetSec,
        latitude = latitude,
        longitude = longitude,
        heartRate = hr,
    )
}
