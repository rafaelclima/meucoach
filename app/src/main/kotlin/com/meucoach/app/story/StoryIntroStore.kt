package com.meucoach.app.story

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One-time explanation that the first story downloads map (and relief) tiles. */
@Singleton
class StoryIntroStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun seen(): Boolean = prefs.getBoolean(KEY_SEEN, false)

    fun markSeen() {
        prefs.edit { putBoolean(KEY_SEEN, true) }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES = "meucoach_story_intro"
        const val KEY_SEEN = "map_notice_seen"
    }
}
