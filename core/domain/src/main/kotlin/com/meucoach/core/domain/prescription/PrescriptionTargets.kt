package com.meucoach.core.domain.prescription

import com.meucoach.core.model.HeatLevel
import com.meucoach.core.model.Pace
import kotlin.math.roundToInt

/**
 * Objective and subjective targets of a prescription.
 *
 * Pace and heart rate are only present when the profile is calibrated. Without
 * calibration the app prescribes by perceived effort ([rpe]) and leaves the
 * objective targets `null` instead of inventing numbers.
 */
data class PrescriptionTargets(
    val pace: PaceTarget? = null,
    val heartRate: HeartRateTarget? = null,
    val zone: String? = null,
    val distanceKm: Double? = null,
    /** Perceived effort on the 1–10 scale; null when pace/HR are available. */
    val rpe: Int? = null,
) {
    init {
        require(rpe == null || rpe in 1..10) { "RPE must be between 1 and 10" }
        require(distanceKm == null || (distanceKm.isFinite() && distanceKm >= 0.0)) {
            "Distance must be finite and not negative"
        }
    }

    val isEmpty: Boolean
        get() = pace == null && heartRate == null && zone == null && distanceKm == null && rpe == null

    /** Keeps the intensity but recomputes the expected distance for a shorter/longer duration. */
    fun forDuration(durationMin: Int?): PrescriptionTargets {
        if (durationMin == null || durationMin <= 0) return copy(distanceKm = null)
        val target = pace ?: return copy(distanceKm = null)
        val averageSecondsPerKm = (target.fast.secondsPerKm + target.slow.secondsPerKm) / 2.0
        val distance = durationMin * 60.0 / averageSecondsPerKm
        return copy(distanceKm = (distance * 10).roundToInt() / 10.0)
    }

    fun withHeat(heat: HeatLevel): PrescriptionTargets {
        if (heat != HeatLevel.HOT) return this
        val slowed = pace?.let { target ->
            PaceTarget(
                fast = Pace.fromSecondsPerKm((target.fast.secondsPerKm * PrescriptionPolicy.HEAT_PACE_FACTOR).toInt()),
                slow = Pace.fromSecondsPerKm((target.slow.secondsPerKm * PrescriptionPolicy.HEAT_PACE_FACTOR).toInt()),
            )
        }
        return when {
            slowed != null -> copy(pace = slowed)
            rpe != null -> copy(rpe = (rpe + PrescriptionPolicy.HEAT_RPE_STEP).coerceAtMost(10))
            else -> this
        }
    }
}

/**
 * Single source of target derivation for the weekly plan and the daily
 * prescription, so the plan intent and the day decision cannot disagree.
 */
internal object PrescriptionPolicy {
    const val ENGINE_VERSION = "daily-prescription-v1"
    const val MIN_EASY_MINUTES = 20
    const val REDUCE_FACTOR = 0.8
    const val DOWNGRADE_FACTOR = 0.7
    const val HEAT_PACE_FACTOR = 1.03
    const val HEAT_RPE_STEP = 1
    const val EASY_RPE = 4
    const val QUALITY_RPE = 7

    fun targets(
        type: WorkoutType,
        profile: com.meucoach.core.model.AthleteProfile,
        durationMin: Int?,
        heat: HeatLevel = HeatLevel.NONE,
    ): PrescriptionTargets {
        if (type == WorkoutType.REST) return PrescriptionTargets()
        val base = when (type) {
            WorkoutType.QUALITY -> qualityTargets(profile)
            WorkoutType.LONG, WorkoutType.EASY -> easyTargets(profile)
            WorkoutType.REST -> PrescriptionTargets()
        }
        return base.forDuration(durationMin).withHeat(heat)
    }

    private fun easyTargets(profile: com.meucoach.core.model.AthleteProfile): PrescriptionTargets {
        val pace = profile.thresholdPace?.let { threshold ->
            val range = com.meucoach.core.domain.zones.PaceZoneCalculator
                .ranges(threshold)
                .first { it.zone == com.meucoach.core.model.PaceZone.E }
            PaceTarget(
                fast = Pace.fromSecondsPerKm(range.minSecPerKm),
                slow = Pace.fromSecondsPerKm(range.maxSecPerKm),
            )
        }
        val heartRate = profile.lactateThresholdHeartRate?.let { lthr ->
            val range = com.meucoach.core.domain.zones.HeartRateZoneCalculator
                .ranges(lthr)
                .first { it.zone == com.meucoach.core.model.HeartRateZone.Z2 }
            HeartRateTarget(minBpm = range.minBpm, maxBpm = range.maxBpm)
        }
        return PrescriptionTargets(
            pace = pace,
            heartRate = heartRate,
            zone = "Z2",
            rpe = if (pace == null && heartRate == null) EASY_RPE else null,
        )
    }

    private fun qualityTargets(profile: com.meucoach.core.model.AthleteProfile): PrescriptionTargets {
        val pace = profile.thresholdPace?.let { threshold ->
            val range = com.meucoach.core.domain.zones.PaceZoneCalculator
                .ranges(threshold)
                .first { it.zone == com.meucoach.core.model.PaceZone.T }
            PaceTarget(
                fast = Pace.fromSecondsPerKm(range.minSecPerKm),
                slow = Pace.fromSecondsPerKm(range.maxSecPerKm),
            )
        }
        val heartRate = profile.lactateThresholdHeartRate?.let { lthr ->
            val ranges = com.meucoach.core.domain.zones.HeartRateZoneCalculator.ranges(lthr)
            val z3 = ranges.first { it.zone == com.meucoach.core.model.HeartRateZone.Z3 }
            val z4 = ranges.first { it.zone == com.meucoach.core.model.HeartRateZone.Z4 }
            HeartRateTarget(minBpm = z3.minBpm, maxBpm = z4.maxBpm)
        }
        return PrescriptionTargets(
            pace = pace,
            heartRate = heartRate,
            zone = "Z3–Z4",
            rpe = if (pace == null && heartRate == null) QUALITY_RPE else null,
        )
    }
}
