package com.meucoach.core.domain.race

import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import com.meucoach.core.model.Sport
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the auto-completion rule needs to decide.
 *
 * The app fills the linked/declined sets from the repositories and from
 * `RaceAutoCompletionStore`; the rule itself is pure and has no I/O.
 */
data class RaceAutoCompletionInput(
    val event: RaceEvent,
    val activityId: String,
    val activitiesOnDate: List<ActivitySummary>,
    val zoneId: ZoneId,
    val sessionLinkedActivityIds: Set<String> = emptySet(),
    val raceLinkedActivityIds: Set<String> = emptySet(),
    val declinedActivityIds: Set<String> = emptySet(),
)

sealed interface RaceAutoCompletionDecision {
    data class Eligible(val eventId: String, val activityId: String) : RaceAutoCompletionDecision

    data class Rejected(val reason: RaceAutoCompletionRejection) : RaceAutoCompletionDecision
}

enum class RaceAutoCompletionRejection {
    EVENT_NOT_PLANNED,
    ACTIVITY_NOT_FOUND,
    WRONG_DATE,
    WRONG_SPORT,
    ACTIVITY_ALREADY_LINKED_TO_SESSION,
    ACTIVITY_ALREADY_LINKED_TO_RACE,
    PREVIOUSLY_DECLINED,
    DISTANCE_TOO_SHORT,
    DISTANCE_TOO_LONG,
    MULTIPLE_CANDIDATES,
}

/**
 * Conservative rule for inferring a completed race from an imported run (ADR 0032).
 *
 * Same local date, running/treadmill, unlinked, a planned event, a distance
 * between 90% and 110% of the race and exactly one eligible candidate. Anything
 * else is skipped; the explicit completion dialog remains available.
 */
object RaceAutoCompletionRule {
    const val MIN_DISTANCE_RATIO = 0.90
    const val MAX_DISTANCE_RATIO = 1.10

    fun evaluate(input: RaceAutoCompletionInput): RaceAutoCompletionDecision {
        val event = input.event
        if (event.status != RaceStatus.PLANNED) {
            return RaceAutoCompletionDecision.Rejected(RaceAutoCompletionRejection.EVENT_NOT_PLANNED)
        }

        val activity = input.activitiesOnDate.firstOrNull { it.id == input.activityId }
            ?: return RaceAutoCompletionDecision.Rejected(RaceAutoCompletionRejection.ACTIVITY_NOT_FOUND)

        if (!activity.isOn(event.targetDate, input.zoneId)) {
            return RaceAutoCompletionDecision.Rejected(RaceAutoCompletionRejection.WRONG_DATE)
        }
        if (!activity.isRunning) {
            return RaceAutoCompletionDecision.Rejected(RaceAutoCompletionRejection.WRONG_SPORT)
        }
        if (activity.id in input.sessionLinkedActivityIds) {
            return RaceAutoCompletionDecision.Rejected(
                RaceAutoCompletionRejection.ACTIVITY_ALREADY_LINKED_TO_SESSION,
            )
        }
        if (activity.id in input.raceLinkedActivityIds) {
            return RaceAutoCompletionDecision.Rejected(RaceAutoCompletionRejection.ACTIVITY_ALREADY_LINKED_TO_RACE)
        }
        if (activity.id in input.declinedActivityIds) {
            return RaceAutoCompletionDecision.Rejected(RaceAutoCompletionRejection.PREVIOUSLY_DECLINED)
        }

        val ratio = activity.distance.meters / event.distance.meters
        if (ratio < MIN_DISTANCE_RATIO) {
            return RaceAutoCompletionDecision.Rejected(RaceAutoCompletionRejection.DISTANCE_TOO_SHORT)
        }
        if (ratio > MAX_DISTANCE_RATIO) {
            return RaceAutoCompletionDecision.Rejected(RaceAutoCompletionRejection.DISTANCE_TOO_LONG)
        }

        val eligible = input.activitiesOnDate.count { candidate ->
            candidate.isRunning &&
                candidate.isOn(event.targetDate, input.zoneId) &&
                candidate.id !in input.sessionLinkedActivityIds &&
                candidate.id !in input.raceLinkedActivityIds &&
                candidate.id !in input.declinedActivityIds &&
                candidate.distance.meters / event.distance.meters in MIN_DISTANCE_RATIO..MAX_DISTANCE_RATIO
        }
        if (eligible != 1) {
            return RaceAutoCompletionDecision.Rejected(RaceAutoCompletionRejection.MULTIPLE_CANDIDATES)
        }

        return RaceAutoCompletionDecision.Eligible(eventId = event.id, activityId = activity.id)
    }

    private val ActivitySummary.isRunning: Boolean
        get() = sport == Sport.RUNNING || sport == Sport.TREADMILL

    private fun ActivitySummary.isOn(date: LocalDate, zoneId: ZoneId): Boolean =
        startTime.atZone(zoneId).toLocalDate() == date
}
