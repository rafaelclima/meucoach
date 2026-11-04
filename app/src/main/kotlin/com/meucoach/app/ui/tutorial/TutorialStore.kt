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

    companion object {
        const val PREFS_NAME = "meucoach_tutorial"
        private const val KEY_INTRO_DONE = "introDone"
    }
}
