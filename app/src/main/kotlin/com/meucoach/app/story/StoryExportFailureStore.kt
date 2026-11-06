package com.meucoach.app.story

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager does not persist `outputData` for a `FAILED` work, so the
 * failure message of a story export would be lost between the worker and the
 * screen. This tiny store bridges that gap until the next export replaces it
 * (ADR 0018).
 */
@Singleton
class StoryExportFailureStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(activityId: String, message: String) {
        prefs.edit { putString(activityId, message) }
    }

    fun messageFor(activityId: String): String? = prefs.getString(activityId, null)

    fun clear(activityId: String) {
        prefs.edit { remove(activityId) }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES = "meucoach_story_export"
    }
}
