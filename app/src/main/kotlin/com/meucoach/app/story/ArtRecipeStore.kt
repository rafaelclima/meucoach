package com.meucoach.app.story

import android.content.Context
import androidx.core.content.edit
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtRecipe
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last saved art **style**, in its own preferences file (`meucoach_art`) so
 * "restaurar padrão" and the data reset clear it without touching the video
 * recipe (`meucoach_story`) or any Room data (ADR 0058 §2.6).
 *
 * The style is shared by every activity; the photo and the effective background
 * belong to the per-activity photo store (ADR 0061 §2.3). Saving therefore
 * normalizes `background=TRANSPARENT` and `photo=null`.
 */
@Singleton
class ArtRecipeStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): ArtRecipe = ArtRecipeCodec.decode(prefs.getString(KEY_RECIPE, null))

    fun save(recipe: ArtRecipe) {
        val style = recipe.copy(background = ArtBackgroundMode.TRANSPARENT, photo = null)
        prefs.edit { putString(KEY_RECIPE, ArtRecipeCodec.encode(style)) }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFERENCES = "meucoach_art"
        const val KEY_RECIPE = "recipe"
    }
}
