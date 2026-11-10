package com.meucoach.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * Race distances supported by the v1 event form (ADR 0030).
 *
 * `CUSTOM` is not an event distance; the four values map to the existing
 * `GoalType` race options so the planning projection is stable.
 */
enum class RaceDistance(val meters: Double) {
    FIVE_K(5_000.0),
    TEN_K(10_000.0),
    HALF_MARATHON(21_097.5),
    MARATHON(42_195.0),
}

/** Explicit lifecycle of a race event; only `PLANNED` is active. */
enum class RaceStatus {
    PLANNED,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
}

/**
 * A competition, separate from `PlannedSession` and `WorkoutType` (ADR 0030).
 *
 * The event is not a training session: it has its own lifecycle, does not enter
 * volume/adherence and never infers completion from the calendar. [targetTimeSec]
 * is a declared goal, never a prescribed pace, range or prediction.
 */
data class RaceEvent(
    val id: String,
    val distance: RaceDistance,
    val targetDate: LocalDate,
    val targetTimeSec: Int? = null,
    val status: RaceStatus = RaceStatus.PLANNED,
    val activityId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Set when the status leaves [RaceStatus.PLANNED]. */
    val resolvedAt: Instant? = null,
) {
    init {
        require(id.isNotBlank()) { "Race event id must not be blank" }
        require(targetTimeSec == null || targetTimeSec > 0) { "Race target time must be positive" }
        require((status == RaceStatus.PLANNED) == (resolvedAt == null)) {
            "Only a planned race event has no resolvedAt"
        }
        require(activityId == null || status == RaceStatus.COMPLETED) {
            "Only a completed race event links an activity"
        }
        require(activityId == null || activityId.isNotBlank()) { "Race activity id must not be blank" }
    }
}
