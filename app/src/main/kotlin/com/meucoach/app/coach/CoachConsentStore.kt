package com.meucoach.app.coach

import android.content.Context

class CoachConsentStore(context: Context, prefsName: String = PREFS_NAME) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /**
     * The AI now also answers typed questions, so v1 consent (summary only)
     * must not silently cover the new payload. An older grant stays stored but
     * is not valid until the athlete accepts the current copy again.
     */
    fun isConsented(): Boolean =
        prefs.getBoolean(KEY_CONSENTED, false) && prefs.getInt(KEY_VERSION, 0) >= CONSENT_VERSION

    fun grant() {
        prefs.edit()
            .putBoolean(KEY_CONSENTED, true)
            .putInt(KEY_VERSION, CONSENT_VERSION)
            .apply()
    }

    fun revoke() {
        prefs.edit().putBoolean(KEY_CONSENTED, false).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "meucoach_coach"
        const val CONSENT_VERSION = 2
        private const val KEY_CONSENTED = "consented"
        private const val KEY_VERSION = "consentVersion"
    }
}
