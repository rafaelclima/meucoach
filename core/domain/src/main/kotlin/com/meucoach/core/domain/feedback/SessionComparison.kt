package com.meucoach.core.domain.feedback

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Pace
import kotlin.math.roundToInt

/** What this activity meant for the plan: evidence of a session or a run of its own. */
enum class SessionComparisonKind {
    /** The run is the evidence of the day's plan; targets exist. */
    LINKED,

    /** No planned session was fulfilled by this run. No target is invented. */
    FREE_RUN,
}

/** Direction of the difference against the plan, not a judgement of quality. */
enum class ComparisonVerdict {
    WITHIN,
    ABOVE,
    BELOW,
}

/**
 * The session this activity fulfilled, reduced to what the comparison needs.
 * A rest day never produces a comparison: there is nothing to run against.
 */
data class ComparisonPlan(
    val title: String,
    val type: WorkoutType,
    val durationMin: Int?,
    val targets: PrescriptionTargets = PrescriptionTargets(),
) {
    init {
        require(title.isNotBlank()) { "Comparison plan title must not be blank" }
        require(type != WorkoutType.REST) { "A rest day cannot be compared with a run" }
        require(durationMin == null || durationMin > 0) { "Comparison duration must be positive" }
    }

    companion object {
        /** The prescription is the day's authority: what the athlete was told to do. */
        fun from(prescription: DailyPrescription): ComparisonPlan? {
            if (prescription.prescribedType == WorkoutType.REST) return null
            return ComparisonPlan(
                title = prescription.title,
                type = prescription.prescribedType,
                durationMin = prescription.prescribedDurationMin,
                targets = prescription.targets,
            )
        }

        /** Fallback for a linked day whose prescription was never persisted. */
        fun from(session: PlannedSession): ComparisonPlan? {
            if (session.type == WorkoutType.REST) return null
            return ComparisonPlan(
                title = session.title,
                type = session.type,
                durationMin = session.durationMin,
                targets = session.targets,
            )
        }
    }
}

/**
 * Deterministic planned × executed reading of one activity (FR-010).
 *
 * Every metric is compared only when both sides exist; a missing value stays
 * `null` and the UI shows "—". The calculator never judges quality, never
 * prescribes and never turns a gap into a neutral claim.
 */
data class SessionComparison(
    val kind: SessionComparisonKind,
    val plan: ComparisonPlan?,
    val actualDurationSec: Int,
    val actualDistanceM: Double,
    val actualAveragePace: Pace?,
    val actualAverageHeartRate: Int?,
    val actualRpe: Int?,
    val durationPercentOfPlan: Int?,
    val durationVerdict: ComparisonVerdict?,
    val distancePercentOfPlan: Int?,
    val distanceVerdict: ComparisonVerdict?,
    val paceVerdict: ComparisonVerdict?,
    val heartRateVerdict: ComparisonVerdict?,
    val rpeVerdict: ComparisonVerdict?,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
) {
    init {
        require(actualDurationSec > 0) { "Actual duration must be positive" }
        require(actualDistanceM.isFinite() && actualDistanceM >= 0.0) {
            "Actual distance must be finite and non-negative"
        }
        require(actualRpe == null || actualRpe in 1..10) { "RPE must be between 1 and 10" }
        require(kind != SessionComparisonKind.LINKED || plan != null) {
            "A linked comparison requires the plan it fulfilled"
        }
        require(kind != SessionComparisonKind.LINKED || ruleIds.isNotEmpty()) {
            "A linked comparison must cite a rule"
        }
        require(kind != SessionComparisonKind.LINKED || evidenceIds.isNotEmpty()) {
            "A linked comparison must cite evidence"
        }
        require(durationPercentOfPlan == null || durationPercentOfPlan > 0) {
            "Duration adherence must be positive when present"
        }
        require(distancePercentOfPlan == null || distancePercentOfPlan > 0) {
            "Distance adherence must be positive when present"
        }
        require((durationVerdict == null) == (durationPercentOfPlan == null)) {
            "Duration verdict and adherence must be present together"
        }
        require((distanceVerdict == null) == (distancePercentOfPlan == null)) {
            "Distance verdict and adherence must be present together"
        }
    }

    /** True when there is at least one metric compared against the plan. */
    val hasComparableTarget: Boolean
        get() = durationVerdict != null ||
            distanceVerdict != null ||
            paceVerdict != null ||
            heartRateVerdict != null ||
            rpeVerdict != null
}

object SessionComparisonCalculator {
    /** Product convention for volume and duration adherence (see ADR 0033). */
    const val DURATION_TOLERANCE_PERCENT = 10
    const val DISTANCE_TOLERANCE_PERCENT = 10

    /** Reported effort is subjective; one point of difference is noise. */
    const val RPE_TOLERANCE = 1

    const val RULE_ID = "R-FEEDBACK-01"
    private val EVIDENCE_IDS = listOf("EV-DANIELS-2014", "EV-FRIEL-2018")

    /**
     * Compares one activity with [linkedPlan]. Pass `null` when the activity is
     * not the evidence of a planned session: the result is a free run, with
     * actual values only and no invented target.
     */
    fun compare(linkedPlan: ComparisonPlan?, activity: ActivitySummary, rpe: Int? = null): SessionComparison {
        val actualPace = activity.averagePace ?: activity.calculatedPace
        if (linkedPlan == null) {
            return SessionComparison(
                kind = SessionComparisonKind.FREE_RUN,
                plan = null,
                actualDurationSec = activity.durationSec,
                actualDistanceM = activity.distance.meters,
                actualAveragePace = actualPace,
                actualAverageHeartRate = activity.averageHeartRate,
                actualRpe = rpe,
                durationPercentOfPlan = null,
                durationVerdict = null,
                distancePercentOfPlan = null,
                distanceVerdict = null,
                paceVerdict = null,
                heartRateVerdict = null,
                rpeVerdict = null,
                ruleIds = emptyList(),
                evidenceIds = emptyList(),
                confidence = DataConfidence.LOW,
            )
        }

        val durationPercent = linkedPlan.durationMin?.let { planned ->
            percentOf(actual = activity.durationSec.toDouble(), planned = planned * 60.0)
        }
        val plannedDistanceKm = linkedPlan.targets.distanceKm
        val distancePercent = plannedDistanceKm
            ?.takeIf { it > 0.0 }
            ?.let { planned -> percentOf(actual = activity.distance.kilometers, planned = planned) }

        val durationVerdict = durationPercent?.let { verdictFor(it, DURATION_TOLERANCE_PERCENT) }
        val distanceVerdict = distancePercent?.let { verdictFor(it, DISTANCE_TOLERANCE_PERCENT) }
        val paceVerdict = paceVerdict(actualPace, linkedPlan.targets)
        val heartRateVerdict = heartRateVerdict(activity.averageHeartRate, linkedPlan.targets)
        val rpeVerdict = rpeVerdict(rpe, linkedPlan.targets.rpe)

        val signals = listOf(durationVerdict, distanceVerdict, paceVerdict, heartRateVerdict, rpeVerdict)
            .count { it != null }
        return SessionComparison(
            kind = SessionComparisonKind.LINKED,
            plan = linkedPlan,
            actualDurationSec = activity.durationSec,
            actualDistanceM = activity.distance.meters,
            actualAveragePace = actualPace,
            actualAverageHeartRate = activity.averageHeartRate,
            actualRpe = rpe,
            durationPercentOfPlan = durationPercent,
            durationVerdict = durationVerdict,
            distancePercentOfPlan = distancePercent,
            distanceVerdict = distanceVerdict,
            paceVerdict = paceVerdict,
            heartRateVerdict = heartRateVerdict,
            rpeVerdict = rpeVerdict,
            ruleIds = listOf(RULE_ID),
            evidenceIds = EVIDENCE_IDS,
            confidence = when {
                signals >= 3 -> DataConfidence.HIGH
                signals == 2 -> DataConfidence.MEDIUM
                else -> DataConfidence.LOW
            },
        )
    }

    private fun percentOf(actual: Double, planned: Double): Int = (actual * 100.0 / planned).roundToInt()

    private fun verdictFor(percent: Int, tolerancePercent: Int): ComparisonVerdict = when {
        percent > 100 + tolerancePercent -> ComparisonVerdict.ABOVE
        percent < 100 - tolerancePercent -> ComparisonVerdict.BELOW
        else -> ComparisonVerdict.WITHIN
    }

    private fun paceVerdict(actual: Pace?, targets: PrescriptionTargets): ComparisonVerdict? {
        val target = targets.pace ?: return null
        val value = actual ?: return null
        return when {
            value.secondsPerKm < target.fast.secondsPerKm -> ComparisonVerdict.ABOVE
            value.secondsPerKm > target.slow.secondsPerKm -> ComparisonVerdict.BELOW
            else -> ComparisonVerdict.WITHIN
        }
    }

    private fun heartRateVerdict(actual: Int?, targets: PrescriptionTargets): ComparisonVerdict? {
        val target = targets.heartRate ?: return null
        val value = actual ?: return null
        val max = target.maxBpm
        return when {
            value < target.minBpm -> ComparisonVerdict.BELOW
            max != null && value > max -> ComparisonVerdict.ABOVE
            else -> ComparisonVerdict.WITHIN
        }
    }

    private fun rpeVerdict(actual: Int?, planned: Int?): ComparisonVerdict? {
        if (actual == null || planned == null) return null
        return when {
            actual - planned > RPE_TOLERANCE -> ComparisonVerdict.ABOVE
            planned - actual > RPE_TOLERANCE -> ComparisonVerdict.BELOW
            else -> ComparisonVerdict.WITHIN
        }
    }
}
