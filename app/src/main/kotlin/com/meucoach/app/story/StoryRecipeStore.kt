package com.meucoach.app.story

import android.content.Context
import androidx.core.content.edit
import com.meucoach.core.domain.story.StoryRecipe
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last saved story recipe. Lives in its own preferences file (`meucoach_story`)
 * so "restaurar padrão" and the data reset can clear it without touching the
 * one-time map notice or any Room data.
 */
@Singleton
class StoryRecipeStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): StoryRecipe = StoryRecipeCodec.decode(prefs.getString(KEY_RECIPE, null))

    fun save(recipe: StoryRecipe) {
        prefs.edit { putString(KEY_RECIPE, StoryRecipeCodec.encode(recipe)) }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFERENCES = "meucoach_story"
        const val KEY_RECIPE = "recipe"
    }
}
