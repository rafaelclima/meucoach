package com.meucoach.core.domain.coach

import java.time.Instant
import java.time.LocalDate

/**
 * Read-only view of one validated Coach turn (Room v19, ADR 0052).
 *
 * The history is copy the athlete can read and erase; it never becomes input for
 * load, pace or planning, because the engine remains the only authority.
 */
data class CoachMemoryMessage(
    val id: String,
    val kind: CoachMessageKind,
    /** Motor context that produced the validated text; used for audit and de-duplication. */
    val inputsHash: String,
    val title: String,
    val body: String,
    val bullets: List<String>,
    val createdAt: Instant,
)

/** Read-only view of the validated narrative of one closed week. */
data class CoachMemorySummary(
    val weekStart: LocalDate,
    /** Motor context that produced the validated narrative. */
    val inputsHash: String,
    val title: String,
    val body: String,
    val bullets: List<String>,
    val createdAt: Instant,
)

/**
 * Visibility rules of the Coach history (PRD decisions 17/25).
 *
 * The current explanation and the closed week shown in the review card are
 * already on screen; repeating them in the archive adds noise. The `REVIEW` turn
 * is also the same narrative as its summary, so the summary replaces the turn
 * with the same `inputsHash`. `CoachHistory.view` centralizes these rules.
 */
object CoachHistory {
    fun view(
        messages: List<CoachMemoryMessage>,
        summaries: List<CoachMemorySummary>,
        currentExplainHash: String? = null,
        currentReviewWeek: LocalDate? = null,
    ): CoachHistoryView {
        val summarized = summaries.mapTo(mutableSetOf()) { it.inputsHash }
        val visibleMessages = messages.filterNot { message ->
            (message.kind == CoachMessageKind.REVIEW && message.inputsHash in summarized) ||
                (
                    message.kind == CoachMessageKind.EXPLAIN &&
                        currentExplainHash != null &&
                        message.inputsHash == currentExplainHash
                    )
        }
        val visibleSummaries = summaries.filterNot { summary ->
            currentReviewWeek != null && summary.weekStart == currentReviewWeek
        }
        return CoachHistoryView(messages = visibleMessages, summaries = visibleSummaries)
    }
}

/** The read-only slice of memory that is not already visible elsewhere on the tab. */
data class CoachHistoryView(val messages: List<CoachMemoryMessage>, val summaries: List<CoachMemorySummary>) {
    val isEmpty: Boolean get() = messages.isEmpty() && summaries.isEmpty()
}
