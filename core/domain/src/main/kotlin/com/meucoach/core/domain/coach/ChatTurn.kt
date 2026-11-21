package com.meucoach.core.domain.coach

import java.time.Instant
import java.time.LocalDate

/**
 * One persisted turn of the Coach conversation (ADR 0054, Room v20).
 *
 * The memory tables (`coach_messages` + `coach_summaries`) only ever hold
 * validated Coach prose, so the unified timeline starts with Coach turns.
 * `You` (free input) and `System` (adjustment events) stay ephemeral until
 * their own slices arrive; the feed already renders those from live state.
 */
sealed interface ChatTurn {
    val id: String
    val createdAt: Instant

    data class Coach(
        override val id: String,
        val title: String,
        val body: String,
        val bullets: List<String>,
        override val createdAt: Instant,
        val origin: Origin,
    ) : ChatTurn

    enum class Origin { EXPLAIN, REVIEW, SUMMARY }
}

/**
 * Unified timeline built from the v19 memory rows.
 *
 * Visibility reuses [CoachHistory.view] so the archive keeps hiding the
 * current explanation, the week under review and the review turn already
 * covered by its summary. Sections interleave newest-first by `createdAt`;
 * ties keep summaries before messages (stable sort).
 */
object ChatTurns {
    fun fromMemory(
        messages: List<CoachMemoryMessage>,
        summaries: List<CoachMemorySummary>,
        currentExplainHash: String? = null,
        currentReviewWeek: LocalDate? = null,
    ): List<ChatTurn> {
        val view = CoachHistory.view(messages, summaries, currentExplainHash, currentReviewWeek)
        val turns = buildList {
            view.summaries.forEach { summary ->
                add(
                    ChatTurn.Coach(
                        id = "summary-${summary.weekStart}",
                        title = summary.title,
                        body = summary.body,
                        bullets = summary.bullets,
                        createdAt = summary.createdAt,
                        origin = ChatTurn.Origin.SUMMARY,
                    ),
                )
            }
            view.messages.forEach { message ->
                add(
                    ChatTurn.Coach(
                        id = "message-${message.id}",
                        title = message.title,
                        body = message.body,
                        bullets = message.bullets,
                        createdAt = message.createdAt,
                        origin = when (message.kind) {
                            CoachMessageKind.EXPLAIN -> ChatTurn.Origin.EXPLAIN
                            CoachMessageKind.REVIEW -> ChatTurn.Origin.REVIEW
                        },
                    ),
                )
            }
        }
        return turns.sortedWith(compareByDescending<ChatTurn> { it.createdAt })
    }
}
