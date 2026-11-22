package com.meucoach.core.domain.coach

import java.time.Instant
import java.time.LocalDate

/**
 * One persisted turn of the Coach conversation (ADR 0054, Room v20; single table since v21/ADR 0055).
 *
 * The table holds validated Coach prose plus the athlete's own typed turns
 * (`You`, Room v22/ADR 0056): the question next to the answer keeps the file
 * a conversation instead of an archive. `System` (adjustment events) stays
 * ephemeral — those events already have canonical sources.
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
        /** Only set for `SUMMARY` turns; the week the narrative covers. */
        val weekStart: LocalDate? = null,
        /**
         * Motor context that produced the validated text. `null` means the
         * context is unknown (legacy row the migration could not match); a
         * missing hash never hides a turn, it only skips the dedupe filters.
         */
        val inputsHash: String? = null,
    ) : ChatTurn

    /**
     * A typed question or report from the athlete (ADR 0056).
     *
     * The persisted body is the parser-sanitized text (trimmed, capped),
     * never the raw keystrokes, and it never travels to the proxy: only
     * `intent + slots` leave the device. Visibility rules never hide it.
     */
    data class You(override val id: String, val body: String, override val createdAt: Instant) : ChatTurn

    enum class Origin { EXPLAIN, REVIEW, SUMMARY }
}

/**
 * Visibility rules of the Coach history (PRD decisions 17/25), applied to the
 * persisted turns.
 *
 * The current explanation and the closed week shown in the review card are
 * already on screen; repeating them in the archive adds noise. The `REVIEW`
 * turn is also the same narrative as its summary, so the summary hides the
 * turn with the same `inputsHash`. Newest first; ties keep summaries before
 * messages, as the archive did before the single table.
 */
object ChatTurns {
    fun visibleTurns(
        turns: List<ChatTurn>,
        currentExplainHash: String? = null,
        currentReviewWeek: LocalDate? = null,
    ): List<ChatTurn> {
        val summarizedHashes = turns
            .filterIsInstance<ChatTurn.Coach>()
            .filter { it.origin == ChatTurn.Origin.SUMMARY }
            .mapNotNull { it.inputsHash }
            .toSet()
        return turns
            .filterNot { turn ->
                turn is ChatTurn.Coach && hides(turn, summarizedHashes, currentExplainHash, currentReviewWeek)
            }
            .sortedWith(
                compareByDescending<ChatTurn> { it.createdAt }
                    .thenBy { if (it is ChatTurn.Coach && it.origin == ChatTurn.Origin.SUMMARY) 0 else 1 },
            )
    }

    private fun hides(
        turn: ChatTurn.Coach,
        summarizedHashes: Set<String>,
        currentExplainHash: String?,
        currentReviewWeek: LocalDate?,
    ): Boolean = when (turn.origin) {
        ChatTurn.Origin.EXPLAIN ->
            currentExplainHash != null && turn.inputsHash == currentExplainHash

        ChatTurn.Origin.REVIEW ->
            turn.inputsHash != null && turn.inputsHash in summarizedHashes

        ChatTurn.Origin.SUMMARY ->
            currentReviewWeek != null && turn.weekStart == currentReviewWeek
    }
}
