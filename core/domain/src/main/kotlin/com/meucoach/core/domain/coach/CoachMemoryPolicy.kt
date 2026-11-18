package com.meucoach.core.domain.coach

import java.time.Duration
import java.time.Instant

/** The MVP has no free-text user turn; a message is always a validated coach reply. */
enum class CoachMessageKind { EXPLAIN, REVIEW }

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

    private val messageTtl: Duration = Duration.ofDays(MESSAGE_TTL_DAYS)
    private val summaryTtl: Duration = Duration.ofDays(SUMMARY_TTL_DAYS)

    fun messageCutoff(now: Instant): Instant = now.minus(messageTtl)

    fun summaryCutoff(now: Instant): Instant = now.minus(summaryTtl)

    /** A row exactly at the cutoff is still valid; only older rows expire. */
    fun isMessageExpired(createdAt: Instant, now: Instant): Boolean = createdAt.isBefore(messageCutoff(now))

    /** A row exactly at the cutoff is still valid; only older rows expire. */
    fun isSummaryExpired(createdAt: Instant, now: Instant): Boolean = createdAt.isBefore(summaryCutoff(now))
}
