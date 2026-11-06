package com.meucoach.core.domain.retention

import java.time.Duration
import java.time.Instant

/**
 * Retention rule for the activity trash (ADR 0025). The rule is pure and
 * receives both timestamps so the caller decides which clock and zone apply.
 */
object TrashPolicy {
    const val RETENTION_DAYS: Long = 7L
    private val RETENTION: Duration = Duration.ofDays(RETENTION_DAYS)
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

    fun expiresAt(deletedAt: Instant): Instant = deletedAt.plus(RETENTION)

    fun purgeable(deletedAt: Instant, now: Instant): Boolean = !expiresAt(deletedAt).isAfter(now)

    /** Whole days until expiry; 0 means the item is ready (or due) for purge. */
    fun daysRemaining(deletedAt: Instant, now: Instant): Int {
        val remainingMillis = Duration.between(now, expiresAt(deletedAt)).toMillis()
        if (remainingMillis <= 0L) return 0
        return ((remainingMillis + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY).toInt()
    }
}
