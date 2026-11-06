package com.meucoach.core.domain.prescription

import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Sport
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

/**
 * Suggests imported runs that may correspond to a planned session (ADR 0024).
 *
 * A suggestion is never a link: the user always confirms it. Compatibility
 * uses the local date in the profile timezone, running/treadmill sport and
 * explicit tolerances on duration and distance.
 */
class SessionMatcher {
    fun suggest(
        session: PlannedSession,
        activities: List<ActivitySummary>,
        zoneId: ZoneId,
        excludedActivityIds: Set<String> = emptySet(),
    ): List<ActivitySummary> = activities
        .filter { it.id !in excludedActivityIds }
        .filter { matches(session, it, zoneId) }
        .sortedBy { activity -> abs(activity.durationSec / SECONDS_PER_MINUTE - session.durationMin.orZero()) }

    fun matches(session: PlannedSession, activity: ActivitySummary, zoneId: ZoneId): Boolean {
        if (activity.sport != Sport.RUNNING && activity.sport != Sport.TREADMILL) return false
        val localDate = activity.startTime.atZone(zoneId).toLocalDate()
        if (localDate != session.date) return false
        if (!durationMatches(session, activity)) return false
        if (!distanceMatches(session, activity)) return false
        return true
    }

    private fun durationMatches(session: PlannedSession, activity: ActivitySummary): Boolean {
        val planned = session.durationMin ?: return true
        val tolerance = max(planned * DURATION_TOLERANCE_RATIO, MIN_DURATION_TOLERANCE_MIN)
        val actual = activity.durationSec / SECONDS_PER_MINUTE
        return abs(actual - planned) <= tolerance
    }

    private fun distanceMatches(session: PlannedSession, activity: ActivitySummary): Boolean {
        val planned = session.targets.distanceKm ?: return true
        val tolerance = max(planned * DISTANCE_TOLERANCE_RATIO, MIN_DISTANCE_TOLERANCE_KM)
        return abs(activity.distance.kilometers - planned) <= tolerance
    }

    private fun Int?.orZero(): Double = this?.toDouble() ?: 0.0

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
        const val DURATION_TOLERANCE_RATIO = 0.20
        const val MIN_DURATION_TOLERANCE_MIN = 10.0
        const val DISTANCE_TOLERANCE_RATIO = 0.15
        const val MIN_DISTANCE_TOLERANCE_KM = 1.0
    }
}
