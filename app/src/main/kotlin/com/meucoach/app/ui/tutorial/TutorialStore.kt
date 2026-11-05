package com.meucoach.app.ui.tutorial

import android.content.Context
import androidx.core.content.edit

/**
 * Marks whether the user already completed the intro tutorial. It is a UI
 * preference, not athlete data: it lives in SharedPreferences (same pattern as
 * HealthSyncStore) so a Room migration is never needed for it.
 */
class TutorialStore(context: Context, prefsName: String = PREFS_NAME) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun isIntroDone(): Boolean = prefs.getBoolean(KEY_INTRO_DONE, false)

    fun markIntroDone() {
        prefs.edit { putBoolean(KEY_INTRO_DONE, true) }
    }

    fun isChecklistDismissed(nowMillis: Long = System.currentTimeMillis()): Boolean =
        prefs.getLong(KEY_CHECKLIST_DISMISSED_UNTIL, 0L) > nowMillis

    fun dismissChecklist(days: Long = 7, nowMillis: Long = System.currentTimeMillis()) {
        require(days > 0) { "Dismissal must last at least one day" }
        prefs.edit {
            putLong(KEY_CHECKLIST_DISMISSED_UNTIL, nowMillis + days * MILLIS_PER_DAY)
        }
    }

    companion object {
        const val PREFS_NAME = "meucoach_tutorial"
        private const val KEY_INTRO_DONE = "introDone"
        private const val KEY_CHECKLIST_DISMISSED_UNTIL = "checklistDismissedUntil"
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
}
