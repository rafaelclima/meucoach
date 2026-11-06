package com.meucoach.app.firstrun

import android.content.Context
import androidx.core.content.edit

/** Remembers one-time contextual tips so the same card never repeats. */
class FirstRunTipStore(context: Context, prefsName: String = PREFS_NAME) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun isShown(tipId: String): Boolean = prefs.getBoolean(tipId, false)

    fun markShown(tipId: String) {
        prefs.edit { putBoolean(tipId, true) }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "meucoach_first_run_tips"
    }
}
