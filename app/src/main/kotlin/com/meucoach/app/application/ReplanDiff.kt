package com.meucoach.app.application

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import java.time.LocalDate

/** How a future session changed between two versions of the week. */
enum class ReplanChangeKind {
    ADDED,
    REMOVED,
    MODIFIED,
}

/**
 * A single future session difference. [before] is null for an added session and
 * [after] is null for a removed one; protected sessions never appear here.
 */
data class ReplanChange(
    val date: LocalDate,
    val kind: ReplanChangeKind,
    val before: PlannedSession?,
    val after: PlannedSession?,
)

/**
 * Compares only the strictly future `PLANNED` sessions of two versions, which
 * is exactly the surface a revision may touch. It is a presentation diff, not a
 * training rule: no content is generated here.
 */
fun replanDiff(current: WeeklyPlan, candidate: WeeklyPlan, today: LocalDate): List<ReplanChange> =
    diffFuturePlannedSessions(
        before = current.sessions.futurePlanned(today),
        after = candidate.sessions.futurePlanned(today),
    )

fun diffFuturePlannedSessions(before: List<PlannedSession>, after: List<PlannedSession>): List<ReplanChange> {
    val beforeByDate = before.associateBy { it.date }
    val afterByDate = after.associateBy { it.date }
    val dates = (beforeByDate.keys + afterByDate.keys).sorted()
    return dates.mapNotNull { date ->
        val previous = beforeByDate[date]
        val next = afterByDate[date]
        when {
            previous == null && next != null -> ReplanChange(date, ReplanChangeKind.ADDED, null, next)

            previous != null && next == null -> ReplanChange(date, ReplanChangeKind.REMOVED, previous, null)

            previous != null && next != null && !previous.sameFutureIntentAs(next) ->
                ReplanChange(date, ReplanChangeKind.MODIFIED, previous, next)

            else -> null
        }
    }
}

private fun List<PlannedSession>.futurePlanned(today: LocalDate): List<PlannedSession> =
    filter { it.date.isAfter(today) && it.status == SessionStatus.PLANNED }

/** Mirrors the semantic equality used by `PlanReplanner` for the future projection. */
private fun PlannedSession.sameFutureIntentAs(other: PlannedSession): Boolean = type == other.type &&
    title == other.title &&
    durationMin == other.durationMin &&
    targets == other.targets &&
    ruleIds == other.ruleIds &&
    evidenceIds == other.evidenceIds
