package com.meucoach.core.model

import java.time.Instant

/**
 * How a planned session was actually completed (ADR 0024).
 *
 * Only [ACTIVITY] and [GUIDED] are strong evidence: they can unlock progression.
 * [MANUAL] counts for adherence with low confidence and never feeds volume,
 * load or zones. [LEGACY_UNVERIFIED] is read-only history migrated from the old
 * "DONE by click" model.
 */
enum class CompletionEvidence {
    ACTIVITY,
    GUIDED,
    MANUAL,
    LEGACY_UNVERIFIED,
    ;

    val isStrong: Boolean get() = this == ACTIVITY || this == GUIDED
}

/** What produced a [SessionExecution]. */
enum class ExecutionSource {
    ACTIVITY,
    GUIDED,
    MANUAL,
}

/** A paused interval inside a guided execution. */
data class ExecutionPause(val startedAt: Instant, val endedAt: Instant) {
    init {
        require(!endedAt.isBefore(startedAt)) { "A pause cannot end before it starts" }
    }
}

/**
 * One execution of a planned session.
 *
 * A [MANUAL] execution is an explicit "I did it without a record": it carries
 * duration and RPE, never distance or an activity id, so it cannot create
 * physiological metrics out of thin air.
 */
data class SessionExecution(
    val id: String,
    val plannedSessionId: String?,
    val activityId: String?,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val pauses: List<ExecutionPause> = emptyList(),
    val durationSec: Int,
    val distanceM: Double? = null,
    val rpe: Int? = null,
    val source: ExecutionSource,
    val createdAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Execution id must not be blank" }
        require(durationSec > 0) { "Execution duration must be positive" }
        require(rpe == null || rpe in 1..10) { "RPE must be between 1 and 10" }
        require(distanceM == null || distanceM >= 0.0) { "Distance must not be negative" }
        require(startedAt == null || endedAt == null || !endedAt.isBefore(startedAt)) {
            "Execution cannot end before it starts"
        }
        when (source) {
            ExecutionSource.ACTIVITY -> require(activityId != null) {
                "An activity execution must reference an activity"
            }

            ExecutionSource.GUIDED -> require(activityId == null) {
                "A guided execution does not reference an imported activity"
            }

            ExecutionSource.MANUAL -> {
                require(activityId == null && distanceM == null) {
                    "A manual execution must not invent activity data"
                }
                require(rpe != null) { "A manual execution requires RPE" }
            }
        }
    }
}
