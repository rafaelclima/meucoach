package com.meucoach.core.domain.story

import com.meucoach.core.model.Pace

/** Personal-best kinds the story can badge on the closing frame (DOM-09). */
enum class StoryRecordKind {
    LONGEST_DISTANCE,
    FASTEST_PACE,
}

/** Minimal facts about one run for record detection. */
data class StoryRecordCandidate(val activityId: String, val distanceM: Double?, val averagePace: Pace?)

/**
 * Deterministic personal-best detector (Sprint 3, DOM-09 fatia 1).
 *
 * - Needs at least [MIN_RUNS] runs (history includes the current one).
 * - A record must beat every comparable past run strictly; ties hold nothing.
 * - Missing data never invents a record: no current value, or no comparable
 *   past value, means no badge for that kind.
 * - Fastest pace only competes at [MIN_DISTANCE_M] or more, mirroring the
 *   personal bests in Tendências (best pace in runs of at least 3 km).
 *
 * Facts only: the badge copy and the spec/renderer wiring come in the next
 * slice, so no rule or evidence is cited here.
 */
object StoryRecordDetector {
    const val MIN_RUNS = 3
    const val MIN_DISTANCE_M = 3_000.0

    fun detect(history: List<StoryRecordCandidate>, currentId: String): List<StoryRecordKind> {
        val current = history.firstOrNull { it.activityId == currentId } ?: return emptyList()
        if (history.size < MIN_RUNS) return emptyList()
        val past = history.filterNot { it.activityId == currentId }
        return StoryRecordKind.entries.filter { kind ->
            when (kind) {
                StoryRecordKind.LONGEST_DISTANCE -> isLongest(current, past)
                StoryRecordKind.FASTEST_PACE -> isFastest(current, past)
            }
        }
    }

    private fun isLongest(current: StoryRecordCandidate, past: List<StoryRecordCandidate>): Boolean {
        val distance = current.distanceM?.takeIf { it.isFinite() && it > 0.0 } ?: return false
        val comparable = past.mapNotNull { it.distanceM?.takeIf { value -> value.isFinite() && value > 0.0 } }
        if (comparable.isEmpty()) return false
        return comparable.all { distance > it }
    }

    private fun isFastest(current: StoryRecordCandidate, past: List<StoryRecordCandidate>): Boolean {
        val distance = current.distanceM?.takeIf { it.isFinite() && it > 0.0 } ?: return false
        if (distance < MIN_DISTANCE_M) return false
        val pace = current.averagePace?.takeIf { it.secondsPerKm > 0 } ?: return false
        val comparable = past
            .filter { (it.distanceM ?: 0.0) >= MIN_DISTANCE_M }
            .mapNotNull { it.averagePace?.takeIf { value -> value.secondsPerKm > 0 } }
        if (comparable.isEmpty()) return false
        return comparable.all { pace.secondsPerKm < it.secondsPerKm }
    }
}
