package com.meucoach.core.domain.importer

import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Sport
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Removes duplicates that arrive from more than one import format.
 *
 * Matching follows the data-model tolerance: start within one second, duration
 * within two seconds and distance within one percent (at least one meter).
 * When two adapters describe the same run, FIT wins over TCX, which wins over
 * GPX. Within one source the candidate with more samples is kept.
 */
class ActivityDeduplicator {
    fun deduplicate(activities: List<ImportedActivity>): List<ImportedActivity> {
        val groups = mutableListOf<MutableList<ImportedActivity>>()
        for (activity in activities) {
            val matching = groups.firstOrNull { group -> group.any { sameActivity(it, activity) } }
            if (matching == null) {
                groups += mutableListOf(activity)
            } else {
                matching += activity
            }
        }
        return groups
            .map { candidates ->
                candidates.maxWith(
                    compareBy<ImportedActivity> { sourcePriority(it.source) }
                        .thenBy { it.samples.size }
                        .thenBy { it.parserVersion },
                )
            }
            .sortedBy { it.startTime }
    }

    fun keyOf(activity: ImportedActivity): ActivityDeduplicationKey = ActivityDeduplicationKey(
        startEpochSecond = activity.startTime.epochSecond,
        sport = activity.sport,
        durationSec = activity.durationSec,
        distanceM = activity.distanceM.roundToInt(),
    )

    private fun sameActivity(first: ImportedActivity, second: ImportedActivity): Boolean {
        if (first.sport != second.sport) return false
        if (abs(first.startTime.epochSecond - second.startTime.epochSecond) > 1) return false
        if (abs(first.durationSec - second.durationSec) > 2) return false
        val distanceTolerance = max(1.0, max(first.distanceM, second.distanceM) * 0.01)
        return abs(first.distanceM - second.distanceM) <= distanceTolerance
    }

    private fun sourcePriority(source: ActivitySource): Int = when (source) {
        ActivitySource.FIT -> 5
        ActivitySource.TCX -> 4
        ActivitySource.GPX -> 3
        ActivitySource.HEALTH_CONNECT -> 2
        ActivitySource.MANUAL -> 1
    }
}

data class ActivityDeduplicationKey(
    val startEpochSecond: Long,
    val sport: Sport,
    val durationSec: Int,
    val distanceM: Int,
)
