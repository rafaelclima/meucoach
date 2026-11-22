package com.meucoach.app.story

import android.content.Context
import androidx.core.content.edit
import com.meucoach.core.domain.story.ArtRecipe
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last saved art recipe, in its own preferences file (`meucoach_art`) so
 * "restaurar padrão" and the data reset clear it without touching the video
 * recipe (`meucoach_story`) or any Room data (ADR 0058 §2.6).
 */
@Singleton
class ArtRecipeStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): ArtRecipe = ArtRecipeCodec.decode(prefs.getString(KEY_RECIPE, null))

    fun save(recipe: ArtRecipe) {
        prefs.edit { putString(KEY_RECIPE, ArtRecipeCodec.encode(recipe)) }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFERENCES = "meucoach_art"
        const val KEY_RECIPE = "recipe"
    }
}
