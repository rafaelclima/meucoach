package com.meucoach.core.data.health

import android.content.Context
import java.time.LocalDate

/**
 * Persists the last successful Health Connect sync date. This is not health
 * data itself; it only lets the next read start from the previous day.
 */
class HealthSyncStore(context: Context, prefsName: String = PREFS_NAME) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun lastSyncedDate(): LocalDate? = prefs
        .getString(KEY_LAST_SYNC, null)
        ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }

    fun markSynced(date: LocalDate) {
        prefs.edit().putString(KEY_LAST_SYNC, date.toString()).apply()
    }

    /** Forgets the watermark so the next sync reads the full initial window again. */
    fun reset() {
        prefs.edit().remove(KEY_LAST_SYNC).apply()
    }

    companion object {
        const val PREFS_NAME = "meucoach_health_sync"
        private const val KEY_LAST_SYNC = "lastSyncDate"
    }
}
