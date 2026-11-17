package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.race.RacePrediction
import com.meucoach.core.domain.race.RacePredictionResult
import com.meucoach.core.domain.race.RacePredictionUnavailable
import com.meucoach.core.domain.race.RaceTimePredictor
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** How the declared goal compares to the current prediction (FR-411). */
enum class GoalAlignment {
    /** The goal is more than 5% faster than the prediction. */
    AMBITIOUS,

    /** The goal is within 5% of the prediction. */
    ALIGNED,

    /** The goal is more than 5% slower than the prediction. */
    CONSERVATIVE,
}

/**
 * Explicit gap between the declared goal and the current prediction.
 *
 * [percent] is positive when the goal is slower than the prediction and
 * negative when it is faster. It is only a reference: the prescribed race pace
 * always comes from the prediction (ADR 0047 §2.6).
 */
data class GoalGap(val goalSec: Int, val predictedSec: Int, val percent: Int, val alignment: GoalAlignment) {
    init {
        require(goalSec > 0) { "Goal time must be positive" }
        require(predictedSec > 0) { "Predicted time must be positive" }
    }
}

/**
 * Race-pace prescription for the `PEAK` (FR-411, ADR 0047 §2.6).
 *
 * The block target and the objective pace come from the current
 * [RaceTimePredictor] output, never from the declared goal. Without a recent
 * prediction the session degrades to [rpe] instead of prescribing an
 * undemonstrated pace. [note] explains the gap and the fallback in the
 * rationale.
 */
data class RacePacePlan(
    val distance: RaceDistance,
    val paceTarget: PaceTarget?,
    /** Label the executable `RACE_PACE` block shows. */
    val blockTarget: String,
    /** Perceived effort used when there is no prediction. */
    val rpe: Int?,
    val goalGap: GoalGap?,
    val prediction: RacePrediction?,
    val note: String,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
) {
    init {
        require(blockTarget.isNotBlank()) { "Race-pace block target must not be blank" }
        require(note.isNotBlank()) { "Race-pace note must not be blank" }
        require((paceTarget == null) != (rpe == null)) {
            "A race-pace plan carries either an objective pace or an RPE fallback"
        }
    }
}

/**
 * Resolves the current race pace from the calibrated VDOT (FR-411).
 *
 * Deterministic and pure: the same profile, goal and day produce the same
 * plan. The declared goal is a reference only; an ambitious goal never becomes
 * the prescribed pace.
 */
object RacePaceResolver {
    /** Above this difference the copy calls the goal ambitious (ADR 0047 §2.6). */
    const val AMBITIOUS_GOAL_PERCENT = 5

    /** Fallback effort for race-pace work without a recent prediction. */
    const val RACE_PACE_RPE = 8

    private const val RULE_ID = "R-PER-04"
    private const val PREDICTION_RULE_ID = RaceTimePredictor.RULE_ID
    private val EVIDENCE = listOf("EV-DANIELS-2014", "EV-RIEGEL-1981")

    fun resolve(profile: AthleteProfile, race: RacePlan, goalSec: Int?, today: LocalDate): RacePacePlan =
        when (val result = RaceTimePredictor.predict(profile, race.distance, today)) {
            is RacePredictionResult.Available -> {
                val prediction = result.prediction
                RacePacePlan(
                    distance = race.distance,
                    paceTarget = paceTarget(prediction),
                    blockTarget = pointPace(prediction).label(),
                    rpe = null,
                    goalGap = goalGap(goalSec, prediction),
                    prediction = prediction,
                    note = predictionNote(race.distance, prediction, goalSec),
                    ruleIds = listOf(RULE_ID, PREDICTION_RULE_ID),
                    evidenceIds = if (prediction.usedRiegel) EVIDENCE else listOf(EVIDENCE.first()),
                )
            }

            is RacePredictionResult.Unavailable -> RacePacePlan(
                distance = race.distance,
                paceTarget = null,
                blockTarget = "RPE $RACE_PACE_RPE",
                rpe = RACE_PACE_RPE,
                goalGap = null,
                prediction = null,
                note = unavailableNote(race.distance, result.reason),
                ruleIds = listOf(RULE_ID, PREDICTION_RULE_ID),
                evidenceIds = listOf(EVIDENCE.first()),
            )
        }

    /**
     * Gap of [goalSec] against the current [prediction]; `null` when no goal was
     * declared. The 5% band is compared on the raw times, before rounding the
     * display percentage.
     */
    fun goalGap(goalSec: Int?, prediction: RacePrediction): GoalGap? {
        if (goalSec == null || goalSec <= 0) return null
        val rawPercent = (goalSec - prediction.predictedSec) * 100.0 / prediction.predictedSec
        val alignment = when {
            goalSec < prediction.predictedSec * (1.0 - AMBITIOUS_GOAL_PERCENT / 100.0) ->
                GoalAlignment.AMBITIOUS

            goalSec > prediction.predictedSec * (1.0 + AMBITIOUS_GOAL_PERCENT / 100.0) ->
                GoalAlignment.CONSERVATIVE

            else -> GoalAlignment.ALIGNED
        }
        return GoalGap(
            goalSec = goalSec,
            predictedSec = prediction.predictedSec,
            percent = rawPercent.roundToInt(),
            alignment = alignment,
        )
    }

    /** Point pace of the current prediction, in seconds per km. */
    fun pointPace(prediction: RacePrediction): Pace {
        val kilometers = prediction.distance.meters / METERS_PER_KILOMETER
        return Pace.fromSecondsPerKm((prediction.predictedSec / kilometers).roundToInt().coerceAtLeast(1))
    }

    private fun paceTarget(prediction: RacePrediction): PaceTarget {
        val kilometers = prediction.distance.meters / METERS_PER_KILOMETER
        val fast = Pace.fromSecondsPerKm((prediction.rangeSec.first / kilometers).roundToInt().coerceAtLeast(1))
        val slow = Pace.fromSecondsPerKm((prediction.rangeSec.last / kilometers).roundToInt().coerceAtLeast(1))
        return PaceTarget(fast = fast, slow = slow)
    }

    private fun predictionNote(distance: RaceDistance, prediction: RacePrediction, goalSec: Int?): String {
        val gap = goalGap(goalSec, prediction)
        val pace = pointPace(prediction).label()
        val current = "O pace do PEAK usa a previsão atual de ${distance.label()} ($pace), nunca a meta."
        if (gap == null) return "Sem meta de tempo informada. $current"
        val goal = timeLabel(gap.goalSec)
        val predicted = timeLabel(gap.predictedSec)
        val percent = abs(gap.percent)
        return when (gap.alignment) {
            GoalAlignment.AMBITIOUS ->
                "Sua meta de $goal é $percent% mais rápida que a previsão atual ($predicted): " +
                    "ela é ambiciosa. $current"

            GoalAlignment.ALIGNED ->
                if (percent == 0) {
                    "Sua meta de $goal está alinhada com a previsão atual ($predicted): " +
                        "ela é referência. $current"
                } else if (gap.percent < 0) {
                    "Sua meta de $goal é $percent% mais rápida que a previsão atual ($predicted), " +
                        "dentro da faixa: ela é referência. $current"
                } else {
                    "Sua meta de $goal é $percent% mais lenta que a previsão atual ($predicted), " +
                        "ainda dentro da faixa: ela é referência. $current"
                }

            GoalAlignment.CONSERVATIVE ->
                "Sua meta de $goal é $percent% mais lenta que a previsão atual ($predicted): " +
                    "ela segue como referência. $current"
        }
    }

    private fun unavailableNote(distance: RaceDistance, reason: RacePredictionUnavailable): String {
        val why = when (reason) {
            RacePredictionUnavailable.NO_VDOT -> "sem VDOT calibrado"
            RacePredictionUnavailable.STALE_CALIBRATION -> "calibração vencida"
            RacePredictionUnavailable.LOW_CONFIDENCE -> "calibração sem origem confiável"
        }
        return "Sem previsão recente de ${distance.label()} ($why): o PEAK usa o esforço " +
            "$RACE_PACE_RPE de 10; o app não prescreve um pace não demonstrado. " +
            "Recalibre com prova, teste de 30 min ou esforço sustentado."
    }

    /** `m:ss` under an hour, `h:mm:ss` above it, matching the race card labels. */
    internal fun timeLabel(seconds: Int): String {
        val hours = seconds / HOUR_SECONDS
        val minutes = (seconds % HOUR_SECONDS) / MINUTE_SECONDS
        val rest = seconds % MINUTE_SECONDS
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, rest)
        }
    }

    private fun Pace.label(): String = "${format()}/km"

    private fun RaceDistance.label(): String = when (this) {
        RaceDistance.FIVE_K -> "5K"
        RaceDistance.TEN_K -> "10K"
        RaceDistance.HALF_MARATHON -> "meia maratona"
        RaceDistance.MARATHON -> "maratona"
    }

    private const val METERS_PER_KILOMETER = 1_000.0
    private const val MINUTE_SECONDS = 60
    private const val HOUR_SECONDS = 3_600
}
