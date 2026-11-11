package com.meucoach.core.domain.race

import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * One projected passage of the declared race goal.
 *
 * [distanceMeters] is the cumulative distance at the split; the final item uses
 * the exact race distance, so a half marathon ends at 21.097,5 m and is labelled
 * "Chegada" instead of pretending there is a 22nd kilometre.
 */
data class RaceSplitProjection(val distanceMeters: Int, val cumulativeSec: Int, val isFinish: Boolean) {
    init {
        require(distanceMeters > 0) { "Split distance must be positive" }
        require(cumulativeSec > 0) { "Split time must be positive" }
    }
}

/**
 * Arithmetic reference derived from a declared race target (ADR 0032).
 *
 * This is not a prescription: it never enters `DailyPrescription`,
 * `PlannedSession` or the weekly plan. [confidence] describes the declared input
 * and the deterministic arithmetic, never the chance of achieving the goal.
 */
data class RaceTargetProjection(
    val targetPaceSecPerKm: Int,
    val paceRangeSecPerKm: IntRange,
    val splits: List<RaceSplitProjection>,
    val finishSec: Int,
    val confidence: DataConfidence = DataConfidence.HIGH,
    val ruleIds: List<String> = listOf(RULE_ID),
) {
    init {
        require(targetPaceSecPerKm > 0) { "Target pace must be positive" }
        require(paceRangeSecPerKm.first >= 1) { "Pace range must start at one second or more" }
        require(paceRangeSecPerKm.first <= targetPaceSecPerKm) {
            "Pace range must include the target pace"
        }
        require(paceRangeSecPerKm.last >= targetPaceSecPerKm) {
            "Pace range must include the target pace"
        }
        require(finishSec > 0) { "Finish time must be positive" }
        require(splits.isNotEmpty()) { "At least the finish split is required" }
        require(splits.last().isFinish) { "The final split must be the finish" }
        require(splits.last().cumulativeSec == finishSec) { "The finish split must match the target time" }
        require(splits.zipWithNext().all { (first, second) -> second.distanceMeters > first.distanceMeters }) {
            "Split distances must increase"
        }
        require(splits.zipWithNext().all { (first, second) -> second.cumulativeSec > first.cumulativeSec }) {
            "Split times must increase"
        }
        require(ruleIds == listOf(RULE_ID)) { "Only the arithmetic reference rule is allowed here" }
    }

    companion object {
        const val RULE_ID = "R-RACE-02"
    }
}

/**
 * Projects pace, range and cumulative splits from a declared target time.
 *
 * The pace is rounded for display; the splits and the finish use the exact target
 * time so the last passage always equals the declared goal. A missing target
 * returns `null`; the app never invents one.
 */
object RaceTargetProjector {
    /** Approved product convention: ±3% around the target pace (ADR 0032/D5). */
    const val RANGE_PERCENT = 3

    fun project(
        distance: RaceDistance,
        targetTimeSec: Int?,
        rangePercent: Int = RANGE_PERCENT,
    ): RaceTargetProjection? {
        if (targetTimeSec == null || targetTimeSec <= 0) return null
        require(rangePercent in 0..50) { "Range percent must be between 0 and 50" }

        val kilometers = distance.meters / METERS_PER_KILOMETER
        val pace = (targetTimeSec / kilometers).roundToInt().coerceAtLeast(1)
        val delta = (pace * (rangePercent / 100.0)).roundToInt()
        val range = (pace - delta).coerceAtLeast(1)..(pace + delta)

        val fullKilometers = floor(kilometers).toInt()
        val splits = buildList {
            for (kilometer in 1..fullKilometers) {
                add(
                    RaceSplitProjection(
                        distanceMeters = kilometer * METERS_PER_KILOMETER.toInt(),
                        cumulativeSec = cumulativeSeconds(targetTimeSec, kilometer, kilometers),
                        isFinish = kilometer == fullKilometers && kilometers == fullKilometers.toDouble(),
                    ),
                )
            }
            if (kilometers > fullKilometers) {
                add(
                    RaceSplitProjection(
                        distanceMeters = distance.meters.roundToInt(),
                        cumulativeSec = targetTimeSec,
                        isFinish = true,
                    ),
                )
            }
        }

        return RaceTargetProjection(
            targetPaceSecPerKm = pace,
            paceRangeSecPerKm = range,
            splits = splits,
            finishSec = targetTimeSec,
        )
    }

    private fun cumulativeSeconds(targetTimeSec: Int, kilometer: Int, kilometers: Double): Int =
        (targetTimeSec.toDouble() * kilometer / kilometers).roundToInt()

    private const val METERS_PER_KILOMETER = 1_000.0
}
