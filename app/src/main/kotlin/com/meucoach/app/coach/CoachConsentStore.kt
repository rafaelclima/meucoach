package com.meucoach.app.coach

import android.content.Context

class CoachConsentStore(context: Context, prefsName: String = PREFS_NAME) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun isConsented(): Boolean = prefs.getBoolean(KEY_CONSENTED, false)

    fun grant() {
        prefs.edit().putBoolean(KEY_CONSENTED, true).apply()
    }

    fun revoke() {
        prefs.edit().putBoolean(KEY_CONSENTED, false).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "meucoach_coach"
        private const val KEY_CONSENTED = "consented"
    }
}
