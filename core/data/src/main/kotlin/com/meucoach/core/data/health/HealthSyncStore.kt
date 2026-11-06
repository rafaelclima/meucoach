package com.meucoach.core.data.health

import android.content.Context
import java.time.Instant
import java.time.LocalDate

/**
 * Persists the last successful Health Connect sync and the last attempt. This
 * is not health data itself; it only lets the next read start from the previous
 * day and tells the app when the data is fresh enough.
 */
class HealthSyncStore(context: Context, prefsName: String = PREFS_NAME) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun lastSyncedDate(): LocalDate? = prefs
        .getString(KEY_LAST_SYNC, null)
        ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }

    fun lastSyncedAt(): Instant? = prefs
        .getLong(KEY_LAST_SYNC_AT, MISSING)
        .takeIf { it != MISSING }
        ?.let(Instant::ofEpochMilli)

    fun lastAttemptAt(): Instant? = prefs
        .getLong(KEY_LAST_ATTEMPT_AT, MISSING)
        .takeIf { it != MISSING }
        ?.let(Instant::ofEpochMilli)

    fun markSynced(at: Instant, date: LocalDate) {
        prefs.edit()
            .putString(KEY_LAST_SYNC, date.toString())
            .putLong(KEY_LAST_SYNC_AT, at.toEpochMilli())
            .apply()
    }

    fun markAttempt(at: Instant) {
        prefs.edit().putLong(KEY_LAST_ATTEMPT_AT, at.toEpochMilli()).apply()
    }

    /** Forgets the watermark so the next sync reads the full initial window again. */
    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "meucoach_health_sync"
        private const val KEY_LAST_SYNC = "lastSyncDate"
        private const val KEY_LAST_SYNC_AT = "lastSyncedAt"
        private const val KEY_LAST_ATTEMPT_AT = "lastAttemptAt"
        private const val MISSING = Long.MIN_VALUE
    }
}
