package com.meucoach.core.domain.race

import com.meucoach.core.domain.calibration.CalibrationCalculator
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.VdotSource
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.roundToInt

/** Reference performance behind a Tier A calibration, used only for the Riegel check. */
data class RaceReference(val distanceM: Double, val durationSec: Int) {
    init {
        require(distanceM.isFinite() && distanceM > 0.0) { "Reference distance must be positive" }
        require(durationSec > 0) { "Reference duration must be positive" }
    }
}

/**
 * Deterministic race time prediction (ADR 0036, `R-RACE-03`).
 *
 * [predictedSec] comes from inverting Daniels' VDOT equation for [distance].
 * [rangeSec] is a product convention around the point (±3% for a performance
 * calibration, ±5% for a training estimate) widened to include the Riegel
 * estimate when a Tier A reference performance exists. It is a reference, never
 * a prescription or a promise.
 */
data class RacePrediction(
    val distance: RaceDistance,
    val predictedSec: Int,
    val rangeSec: IntRange,
    val confidence: DataConfidence,
    val usedRiegel: Boolean,
    val ruleId: String = RaceTimePredictor.RULE_ID,
    val evidenceIds: List<String> = RaceTimePredictor.EVIDENCE,
) {
    init {
        require(predictedSec > 0) { "Predicted time must be positive" }
        require(rangeSec.first >= 1 && rangeSec.last >= rangeSec.first) {
            "Prediction range must be positive and ordered"
        }
        require(predictedSec in rangeSec) { "Prediction range must include the predicted time" }
        require(confidence != DataConfidence.LOW) { "Low confidence does not produce a prediction" }
        require(ruleId == RaceTimePredictor.RULE_ID) { "Only the race prediction rule is allowed here" }
    }
}

/** Why a prediction is absent; the UI shows the reason instead of inventing a number. */
enum class RacePredictionUnavailable {
    NO_VDOT,
    STALE_CALIBRATION,
    LOW_CONFIDENCE,
}

sealed interface RacePredictionResult {
    data class Available(val prediction: RacePrediction) : RacePredictionResult

    data class Unavailable(val reason: RacePredictionUnavailable) : RacePredictionResult
}

/**
 * Predicts race times from the calibrated VDOT (ADR 0036).
 *
 * The public [predict] overload maps the profile provenance to confidence:
 * `PERFORMANCE` → `HIGH`, `TRAINING_ESTIMATE` → `MEDIUM`, legacy/unknown →
 * no prediction. A calibration older than 56 days is stale, matching the
 * calibration window. Riegel is only a range check for 5k–half and never
 * extrapolates a marathon from a short reference.
 */
object RaceTimePredictor {
    const val RULE_ID = "R-RACE-03"
    const val RECENT_DAYS = 56L
    const val HIGH_RANGE_PERCENT = 3
    const val MEDIUM_RANGE_PERCENT = 5
    const val RIEGEL_EXPONENT = 1.06
    val EVIDENCE = listOf("EV-DANIELS-2014", "EV-RIEGEL-1981")

    private const val MIN_SEC = 60
    private const val MAX_SEC = 24 * 3_600

    fun predict(profile: AthleteProfile, distance: RaceDistance, today: LocalDate): RacePredictionResult {
        val confidence = when (profile.vdotSource) {
            VdotSource.PERFORMANCE -> DataConfidence.HIGH
            VdotSource.TRAINING_ESTIMATE -> DataConfidence.MEDIUM
            null -> DataConfidence.LOW
        }
        val reference = if (profile.vdotSource == VdotSource.PERFORMANCE) {
            val referenceDistance = profile.vdotReferenceDistanceM
            val referenceDuration = profile.vdotReferenceDurationSec
            if (referenceDistance != null && referenceDuration != null) {
                RaceReference(referenceDistance, referenceDuration)
            } else {
                null
            }
        } else {
            null
        }
        return predict(
            vdot = profile.vdot,
            confidence = confidence,
            calibratedAt = profile.vdotCalibratedAt,
            reference = reference,
            distance = distance,
            today = today,
        )
    }

    fun predict(
        vdot: Double?,
        confidence: DataConfidence,
        calibratedAt: LocalDate?,
        reference: RaceReference?,
        distance: RaceDistance,
        today: LocalDate,
    ): RacePredictionResult {
        if (vdot == null || !vdot.isFinite() || vdot <= 0.0) {
            return RacePredictionResult.Unavailable(RacePredictionUnavailable.NO_VDOT)
        }
        if (confidence == DataConfidence.LOW) {
            return RacePredictionResult.Unavailable(RacePredictionUnavailable.LOW_CONFIDENCE)
        }
        if (calibratedAt == null || calibratedAt.isAfter(today) ||
            ChronoUnit.DAYS.between(calibratedAt, today) > RECENT_DAYS
        ) {
            return RacePredictionResult.Unavailable(RacePredictionUnavailable.STALE_CALIBRATION)
        }

        val predictedSec = timeFor(vdot, distance.meters)
            ?: return RacePredictionResult.Unavailable(RacePredictionUnavailable.NO_VDOT)
        val percent = when (confidence) {
            DataConfidence.HIGH -> HIGH_RANGE_PERCENT
            DataConfidence.MEDIUM -> MEDIUM_RANGE_PERCENT
            DataConfidence.LOW -> return RacePredictionResult.Unavailable(RacePredictionUnavailable.LOW_CONFIDENCE)
        }
        val delta = (predictedSec * percent / 100.0).roundToInt()
        var low = (predictedSec - delta).coerceAtLeast(1)
        var high = predictedSec + delta
        var usedRiegel = false

        if (reference != null && distance.supportsRiegel() && reference.supportsRiegel()) {
            val riegel = riegelSeconds(reference, distance.meters)
            if (riegel != null) {
                low = minOf(low, riegel)
                high = maxOf(high, riegel)
                usedRiegel = true
            }
        }

        return RacePredictionResult.Available(
            RacePrediction(
                distance = distance,
                predictedSec = predictedSec,
                rangeSec = low..high,
                confidence = confidence,
                usedRiegel = usedRiegel,
            ),
        )
    }

    /**
     * Inverts the Daniels equation: the prediction is the duration whose
     * unclamped VDOT equals the calibrated one. The function is monotonic in
     * duration, so a bounded binary search is deterministic.
     */
    private fun timeFor(vdot: Double, distanceM: Double): Int? {
        var low = MIN_SEC.toDouble()
        var high = MAX_SEC.toDouble()
        val fast = CalibrationCalculator.vdotRaw(distanceM, low.toInt()) ?: return null
        val slow = CalibrationCalculator.vdotRaw(distanceM, high.toInt()) ?: return null
        if (vdot > fast || vdot < slow) return null

        repeat(80) {
            val mid = (low + high) / 2.0
            val value = CalibrationCalculator.vdotRaw(distanceM, mid.toInt()) ?: return null
            if (value > vdot) low = mid else high = mid
        }
        return ((low + high) / 2.0).roundToInt().coerceAtLeast(1)
    }

    private fun riegelSeconds(reference: RaceReference, targetMeters: Double): Int? {
        val factor = (targetMeters / reference.distanceM).pow(RIEGEL_EXPONENT)
        if (!factor.isFinite() || factor <= 0.0) return null
        return (reference.durationSec * factor).roundToInt().takeIf { it > 0 }
    }

    private fun RaceDistance.supportsRiegel(): Boolean = this == RaceDistance.FIVE_K ||
        this == RaceDistance.TEN_K ||
        this == RaceDistance.HALF_MARATHON

    private fun RaceReference.supportsRiegel(): Boolean = distanceM in 5_000.0..21_097.5
}
