package com.meucoach.app.importer

import android.content.Context

/**
 * Remembers imported runs that the user explicitly rejected as the race.
 *
 * Reimporting the same file must not auto-complete the event again, so the
 * refusal is durable. The explicit completion dialog still works normally; the
 * store only blocks the automatic inference (ADR 0032/D3).
 */
class RaceAutoCompletionStore(context: Context, prefsName: String = PREFS_NAME) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun declinedActivityIds(): Set<String> = synchronized(this) { read() }

    fun decline(activityId: String) {
        if (activityId.isBlank()) return
        synchronized(this) {
            persist(read() + activityId)
        }
    }

    fun reset() {
        synchronized(this) {
            prefs.edit().clear().commit()
        }
    }

    private fun read(): Set<String> = prefs.getStringSet(KEY_DECLINED, emptySet())?.toSet() ?: emptySet()

    private fun persist(ids: Set<String>) {
        // commit() so a rejected activity is never inferred again after a crash.
        prefs.edit().putStringSet(KEY_DECLINED, ids).commit()
    }

    companion object {
        const val PREFS_NAME = "meucoach_race_auto_completion"
        private const val KEY_DECLINED = "declinedActivityIds"
    }
}
