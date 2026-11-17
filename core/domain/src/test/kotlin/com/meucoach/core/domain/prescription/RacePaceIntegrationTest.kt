package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.calibration.CalibrationCalculator
import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.race.RacePredictionResult
import com.meucoach.core.domain.race.RaceTimePredictor
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * FR-411: the PEAK session reads the current prediction, shows the goal gap and
 * falls back to RPE when there is no recent prediction.
 */
class RacePaceIntegrationTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val race = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(14))
    private val fiveKVdot = CalibrationCalculator.vdot(5_000.0, 1_859)!!
    private val generator = WeeklyPlanGenerator()

    @Test
    fun `peak uses the current prediction and names an ambitious goal`() {
        val profile = profile(fiveKVdot)
        val prediction = prediction(profile)
        val goalSec = (prediction.predictedSec * 0.90).roundToInt()
        val plan = generator.generate(input(profile, goalSec = goalSec))

        val quality = plan.sessions.single { it.type == WorkoutType.QUALITY }
        assertThat(quality.qualityKind).isEqualTo(QualityKind.RACE_PACE)
        assertThat(quality.targets.primary).isEqualTo(TargetKind.PACE)
        assertThat(quality.targets.pace).isNotNull
        assertThat(quality.targets.rpe).isNull()
        val pace = RacePaceResolver.pointPace(prediction)
        assertThat(quality.structure!!.blocks.single { it.kind == BlockKind.RACE_PACE }.target)
            .isEqualTo("${pace.format()}/km")
        assertThat(quality.rationale).contains("ambiciosa", "previsão atual", "nunca a meta")
        val goalPaceSec = goalSec / 5
        val goalPaceLabel = "%d:%02d/km".format(goalPaceSec / 60, goalPaceSec % 60)
        assertThat(quality.rationale).doesNotContain(goalPaceLabel)
    }

    @Test
    fun `a new calibration changes the peak target`() {
        val fast = generator.generate(input(profile(fiveKVdot), goalSec = null))
        val slow = generator.generate(
            input(profile(CalibrationCalculator.vdot(5_000.0, 1_700)!!), goalSec = null),
        )

        val fastBlock = fast.sessions.single { it.type == WorkoutType.QUALITY }
            .structure!!.blocks.single { it.kind == BlockKind.RACE_PACE }.target
        val slowBlock = slow.sessions.single { it.type == WorkoutType.QUALITY }
            .structure!!.blocks.single { it.kind == BlockKind.RACE_PACE }.target

        assertThat(fastBlock).isNotEqualTo(slowBlock)
    }

    @Test
    fun `without a prediction the peak falls back to rpe`() {
        val plan = generator.generate(input(profile(vdot = null), goalSec = 1_700))
        val quality = plan.sessions.single { it.type == WorkoutType.QUALITY }

        assertThat(quality.targets.primary).isEqualTo(TargetKind.RPE)
        assertThat(quality.targets.pace).isNull()
        assertThat(quality.targets.rpe).isEqualTo(RacePaceResolver.RACE_PACE_RPE)
        assertThat(quality.structure!!.blocks.single { it.kind == BlockKind.RACE_PACE }.target)
            .isEqualTo("RPE ${RacePaceResolver.RACE_PACE_RPE}")
        assertThat(quality.rationale).contains("Sem previsão recente", "não prescreve um pace")
    }

    private fun prediction(profile: AthleteProfile) =
        (RaceTimePredictor.predict(profile, RaceDistance.FIVE_K, monday) as RacePredictionResult.Available)
            .prediction

    private fun input(profile: AthleteProfile, goalSec: Int?) = WeeklyPlanInput(
        profile = profile,
        readiness = ReadinessResult(
            score = 80,
            level = ReadinessLevel.GREEN,
            confidence = DataConfidence.HIGH,
            components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
            missing = emptySet(),
            flags = emptySet(),
        ),
        load = LoadSnapshot(
            daily = emptyList(),
            ctlSeries = emptyList(),
            atlSeries = emptyList(),
            ctl = 0.0,
            atl = 0.0,
            tsb = 0.0,
            acuteAverage7d = 0.0,
            chronicAverage28d = 0.0,
            acwr = 1.0,
            monotony = 1.2,
            missingDays = 16,
            coverage = LoadCoverage(
                daysInWindow = 28,
                daysWithLoad = 12,
                daysWithoutActivity = 16,
                daysWithoutData = 0,
                lastActivityAt = java.time.Instant.parse("2026-10-30T10:00:00Z"),
                sourceFresh = true,
            ),
            strain = 400.0,
        ),
        weekStart = monday,
        today = monday,
        race = race,
        raceTargetTimeSec = goalSec,
    )

    private fun profile(vdot: Double?) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        vdot = vdot,
        vdotSource = if (vdot == null) null else com.meucoach.core.model.VdotSource.TRAINING_ESTIMATE,
        vdotCalibratedAt = if (vdot == null) null else monday.minusDays(2),
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 90,
            ),
        ),
    )
}
