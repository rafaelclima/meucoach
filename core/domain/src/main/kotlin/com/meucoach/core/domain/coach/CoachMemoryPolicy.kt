package com.meucoach.core.domain.coach

import java.time.Duration
import java.time.Instant

/** Kind of a persisted Coach turn: the two motor narratives plus free answers (ADR 0056). */
enum class CoachMessageKind { EXPLAIN, REVIEW, ANSWER }

/**
 * Retention policy of the local Coach memory (Room v19, ADR 0052).
 *
 * TTL is product policy, not a training rule: it only bounds how long the app
 * keeps the copy the AI produced. Messages are the visible conversation;
 * summaries are the compact longitudinal memory. Expired rows are deleted
 * lazily by the data layer and never become input for load, pace or planning.
 */
object CoachMemoryPolicy {
    const val MESSAGE_TTL_DAYS: Long = 90
    const val SUMMARY_TTL_DAYS: Long = 180

    /**
     * Retention of the athlete's own typed turns (Room v22, ADR 0056).
     * Same 90 d as explanations: it covers the training cycle without
     * turning health-adjacent free text into a dead archive.
     */
    const val YOU_TTL_DAYS: Long = 90

    private val messageTtl: Duration = Duration.ofDays(MESSAGE_TTL_DAYS)
    private val summaryTtl: Duration = Duration.ofDays(SUMMARY_TTL_DAYS)

    fun messageCutoff(now: Instant): Instant = now.minus(messageTtl)

    fun summaryCutoff(now: Instant): Instant = now.minus(summaryTtl)

    /** A row exactly at the cutoff is still valid; only older rows expire. */
    fun isMessageExpired(createdAt: Instant, now: Instant): Boolean = createdAt.isBefore(messageCutoff(now))

    /** A row exactly at the cutoff is still valid; only older rows expire. */
    fun isSummaryExpired(createdAt: Instant, now: Instant): Boolean = createdAt.isBefore(summaryCutoff(now))

    /** Typed turns expire like explanations: absence shortens the file, never the reading. */
    fun youExpiresAt(createdAt: Instant): Instant = createdAt.plus(Duration.ofDays(YOU_TTL_DAYS))

    /**
     * When a turn leaves the device (Room v20, ADR 0054): explanation/review
     * turns live 90 d, summaries 180 d — the same TTL as the tables they unify.
     * Free answers (ADR 0056) are Coach turns with their own hash and live
     * 90 d like explanations.
     */
    fun turnExpiresAt(createdAt: Instant, origin: ChatTurn.Origin): Instant = createdAt.plus(
        when (origin) {
            ChatTurn.Origin.SUMMARY -> summaryTtl
            ChatTurn.Origin.EXPLAIN, ChatTurn.Origin.REVIEW, ChatTurn.Origin.ANSWER -> messageTtl
        },
    )
}
