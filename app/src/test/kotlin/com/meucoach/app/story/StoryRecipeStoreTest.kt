package com.meucoach.app.story

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.StoryDuration
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryRecipe
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryRecipeStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearPreferences() {
        prefs().edit().clear().commit()
    }

    @Test
    fun `defaults to the essential recipe when nothing was saved`() {
        assertThat(StoryRecipeStore(context).load()).isEqualTo(StoryRecipe())
    }

    @Test
    fun `saves and reloads the recipe across instances`() {
        val recipe = StoryRecipe(
            extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE),
            duration = StoryDuration.SEC_30,
        )

        StoryRecipeStore(context).save(recipe)

        assertThat(StoryRecipeStore(context).load()).isEqualTo(recipe)
    }

    @Test
    fun `reset clears the saved recipe`() {
        val store = StoryRecipeStore(context)
        store.save(StoryRecipe(extras = listOf(StoryExtra.DATE)))

        store.reset()

        assertThat(store.load()).isEqualTo(StoryRecipe())
        assertThat(prefs().all).isEmpty()
    }

    @Test
    fun `a corrupt preference falls back to the safe default`() {
        prefs().edit().putString(StoryRecipeStore.KEY_RECIPE, "{broken").commit()

        assertThat(StoryRecipeStore(context).load()).isEqualTo(StoryRecipe())
    }

    private fun prefs() = context.getSharedPreferences(StoryRecipeStore.PREFERENCES, Context.MODE_PRIVATE)
}
