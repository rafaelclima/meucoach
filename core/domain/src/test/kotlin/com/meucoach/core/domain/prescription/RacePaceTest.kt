package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.calibration.CalibrationCalculator
import com.meucoach.core.domain.race.RacePrediction
import com.meucoach.core.domain.race.RacePredictionResult
import com.meucoach.core.domain.race.RaceReference
import com.meucoach.core.domain.race.RaceTimePredictor
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.Sex
import com.meucoach.core.model.VdotSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * FR-411 / ADR 0047 §2.6: the PEAK pace is the current prediction, the goal is
 * a reference with an explicit gap, and a missing prediction degrades to RPE.
 */
class RacePaceTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val race = RacePlan("race-1", RaceDistance.FIVE_K, today.plusDays(14))
    private val fiveKVdot = CalibrationCalculator.vdot(5_000.0, 1_859)!!

    @Test
    fun `uses the current prediction for the block target and pace range`() {
        val profile = profile(fiveKVdot)
        val prediction = prediction(profile)
        val plan = RacePaceResolver.resolve(profile, race, goalSec = null, today = today)

        assertThat(plan.prediction).isEqualTo(prediction)
        assertThat(plan.paceTarget).isNotNull
        assertThat(plan.paceTarget!!.fast.secondsPerKm).isLessThanOrEqualTo(
            RacePaceResolver.pointPace(prediction).secondsPerKm,
        )
        assertThat(plan.paceTarget!!.slow.secondsPerKm).isGreaterThanOrEqualTo(
            RacePaceResolver.pointPace(prediction).secondsPerKm,
        )
        assertThat(plan.blockTarget).isEqualTo("${RacePaceResolver.pointPace(prediction).format()}/km")
        assertThat(plan.rpe).isNull()
        assertThat(plan.note).contains("previsão atual", "nunca a meta")
        assertThat(plan.ruleIds).contains("R-PER-04", "R-RACE-03")
        assertThat(plan.evidenceIds).contains("EV-DANIELS-2014")
    }

    @Test
    fun `an ambitious goal is named and never becomes the pace`() {
        val profile = profile(fiveKVdot)
        val prediction = prediction(profile)
        val goalSec = (prediction.predictedSec * 0.90).roundToInt()
        val plan = RacePaceResolver.resolve(profile, race, goalSec, today)

        assertThat(plan.goalGap!!.alignment).isEqualTo(GoalAlignment.AMBITIOUS)
        assertThat(plan.goalGap!!.percent).isNegative()
        assertThat(plan.note).contains("ambiciosa", "previsão atual")
        // The goal pace would be ~10% faster; the prescribed band stays around
        // the current prediction instead.
        val goalPace = (goalSec / 5.0).roundToInt()
        assertThat(plan.paceTarget!!.fast.secondsPerKm).isGreaterThan(goalPace)
        assertThat(plan.blockTarget).isEqualTo("${RacePaceResolver.pointPace(prediction).format()}/km")
    }

    @Test
    fun `a goal inside five percent is a reference, not a prescription`() {
        val profile = profile(fiveKVdot)
        val prediction = prediction(profile)
        val goalSec = (prediction.predictedSec * 0.98).roundToInt()
        val plan = RacePaceResolver.resolve(profile, race, goalSec, today)

        assertThat(plan.goalGap!!.alignment).isEqualTo(GoalAlignment.ALIGNED)
        assertThat(plan.note).contains("referência", "previsão atual")
        assertThat(plan.blockTarget).isEqualTo("${RacePaceResolver.pointPace(prediction).format()}/km")
    }

    @Test
    fun `an identical goal is aligned and says so`() {
        val profile = profile(fiveKVdot)
        val prediction = prediction(profile)
        val plan = RacePaceResolver.resolve(profile, race, prediction.predictedSec, today)

        assertThat(plan.goalGap!!.percent).isZero()
        assertThat(plan.goalGap!!.alignment).isEqualTo(GoalAlignment.ALIGNED)
        assertThat(plan.note).contains("está alinhada")
    }

    @Test
    fun `a slightly slower goal stays aligned and says slower`() {
        val profile = profile(fiveKVdot)
        val prediction = prediction(profile)
        val goalSec = (prediction.predictedSec * 1.03).roundToInt()
        val plan = RacePaceResolver.resolve(profile, race, goalSec, today)

        assertThat(plan.goalGap!!.alignment).isEqualTo(GoalAlignment.ALIGNED)
        assertThat(plan.goalGap!!.percent).isPositive()
        assertThat(plan.note).contains("mais lenta", "ainda dentro da faixa")
    }

    @Test
    fun `a non-positive goal is treated as absent`() {
        val prediction = prediction(profile(fiveKVdot))

        assertThat(RacePaceResolver.goalGap(null, prediction)).isNull()
        assertThat(RacePaceResolver.goalGap(0, prediction)).isNull()
        assertThat(RacePaceResolver.goalGap(-1, prediction)).isNull()
    }

    @Test
    fun `a slower goal stays a reference and does not slow the prescription`() {
        val profile = profile(fiveKVdot)
        val prediction = prediction(profile)
        val goalSec = (prediction.predictedSec * 1.10).roundToInt()
        val plan = RacePaceResolver.resolve(profile, race, goalSec, today)

        assertThat(plan.goalGap!!.alignment).isEqualTo(GoalAlignment.CONSERVATIVE)
        assertThat(plan.goalGap!!.percent).isPositive()
        assertThat(plan.note).contains("mais lenta", "previsão atual")
        assertThat(plan.blockTarget).isEqualTo("${RacePaceResolver.pointPace(prediction).format()}/km")
    }

    @Test
    fun `the five percent boundary is exclusive on the ambitious side`() {
        val prediction = RacePrediction(
            distance = RaceDistance.FIVE_K,
            predictedSec = 1_000,
            rangeSec = 900..1_100,
            confidence = com.meucoach.core.model.DataConfidence.HIGH,
            usedRiegel = false,
        )

        assertThat(RacePaceResolver.goalGap(950, prediction)!!.alignment)
            .isEqualTo(GoalAlignment.ALIGNED)
        assertThat(RacePaceResolver.goalGap(949, prediction)!!.alignment)
            .isEqualTo(GoalAlignment.AMBITIOUS)
    }

    @Test
    fun `without a prediction the peak degrades to rpe`() {
        val profile = profile(vdot = null)
        val plan = RacePaceResolver.resolve(profile, race, goalSec = 1_700, today = today)

        assertThat(plan.prediction).isNull()
        assertThat(plan.paceTarget).isNull()
        assertThat(plan.rpe).isEqualTo(RacePaceResolver.RACE_PACE_RPE)
        assertThat(plan.blockTarget).isEqualTo("RPE ${RacePaceResolver.RACE_PACE_RPE}")
        assertThat(plan.goalGap).isNull()
        assertThat(plan.note).contains("Sem previsão recente", "sem inventar ritmo")
    }

    @Test
    fun `a stale calibration also degrades to rpe`() {
        val profile = profile(fiveKVdot).copy(vdotCalibratedAt = today.minusDays(70))
        val plan = RacePaceResolver.resolve(profile, race, goalSec = null, today = today)

        assertThat(plan.paceTarget).isNull()
        assertThat(plan.rpe).isEqualTo(RacePaceResolver.RACE_PACE_RPE)
        assertThat(plan.note).contains("calibração vencida", "Recalibre")
    }

    @Test
    fun `a legacy calibration without provenance degrades to rpe`() {
        val profile = profile(fiveKVdot).copy(vdotSource = null)
        val plan = RacePaceResolver.resolve(profile, race, goalSec = null, today = today)

        assertThat(plan.paceTarget).isNull()
        assertThat(plan.rpe).isEqualTo(RacePaceResolver.RACE_PACE_RPE)
        assertThat(plan.note).contains("sem origem confiável")
    }

    @Test
    fun `every race distance labels the prediction and marathon uses hours`() {
        val profile = profile(fiveKVdot)
        val labels = mapOf(
            RaceDistance.FIVE_K to "5K",
            RaceDistance.TEN_K to "10K",
            RaceDistance.HALF_MARATHON to "meia maratona",
            RaceDistance.MARATHON to "maratona",
        )
        labels.forEach { (distance, label) ->
            val plan = RacePaceResolver.resolve(
                profile,
                RacePlan("race-${distance.name}", distance, today.plusDays(30)),
                goalSec = null,
                today = today,
            )
            assertThat(plan.paceTarget).describedAs("$distance pace").isNotNull
            assertThat(plan.blockTarget).describedAs("$distance target").endsWith("/km")
            assertThat(plan.note).describedAs("$distance note").contains("previsão atual de $label")
        }

        val marathon = RacePaceResolver.resolve(
            profile,
            RacePlan("race-1", RaceDistance.MARATHON, today.plusDays(30)),
            goalSec = null,
            today = today,
        )
        val prediction = RaceTimePredictor.predict(profile, RaceDistance.MARATHON, today)
        val seconds = (prediction as RacePredictionResult.Available).prediction.predictedSec
        assertThat(RacePaceResolver.timeLabel(seconds)).matches("\\d+:\\d{2}:\\d{2}")
        assertThat(seconds).isGreaterThan(3_600)
    }

    @Test
    fun `a performance reference also cites the Riegel check`() {
        val reference = RaceReference(distanceM = 5_000.0, durationSec = 1_859)
        val profile = profile(fiveKVdot).copy(
            vdotSource = VdotSource.PERFORMANCE,
            vdotReferenceDistanceM = reference.distanceM,
            vdotReferenceDurationSec = reference.durationSec,
        )
        val plan = RacePaceResolver.resolve(
            profile,
            RacePlan("race-1", RaceDistance.TEN_K, today.plusDays(30)),
            goalSec = null,
            today = today,
        )

        assertThat(plan.prediction?.usedRiegel).isTrue()
        assertThat(plan.evidenceIds).contains("EV-RIEGEL-1981")
    }

    @Test
    fun `a race-pace plan cannot carry both targets or neither`() {
        assertThatThrownBy {
            RacePacePlan(
                distance = RaceDistance.FIVE_K,
                paceTarget = null,
                blockTarget = "RPE 8",
                rpe = null,
                goalGap = null,
                prediction = null,
                note = "nota",
                ruleIds = listOf("R-PER-04"),
                evidenceIds = listOf("EV-DANIELS-2014"),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a new calibration changes the peak pace`() {
        val first = RacePaceResolver.resolve(profile(fiveKVdot), race, null, today)
        val second = RacePaceResolver.resolve(
            profile(CalibrationCalculator.vdot(5_000.0, 1_700)!!),
            race,
            null,
            today,
        )

        assertThat(second.blockTarget).isNotEqualTo(first.blockTarget)
        assertThat(second.paceTarget!!.fast.secondsPerKm)
            .isNotEqualTo(first.paceTarget!!.fast.secondsPerKm)
    }

    private fun prediction(profile: AthleteProfile): RacePrediction =
        (RaceTimePredictor.predict(profile, RaceDistance.FIVE_K, today) as RacePredictionResult.Available)
            .prediction

    private fun profile(vdot: Double?) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        vdot = vdot,
        vdotSource = if (vdot == null) null else VdotSource.TRAINING_ESTIMATE,
        vdotCalibratedAt = if (vdot == null) null else today.minusDays(2),
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
    )
}
