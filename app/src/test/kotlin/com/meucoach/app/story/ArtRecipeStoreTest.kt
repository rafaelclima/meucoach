package com.meucoach.app.story

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtRecipe
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
class ArtRecipeStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(ArtRecipeStore.PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `defaults to the documented recipe when nothing was saved`() {
        assertThat(ArtRecipeStore(context).load()).isEqualTo(ArtRecipe())
    }

    @Test
    fun `saves and reloads the recipe across instances`() {
        val recipe = ArtRecipe(
            format = ArtFormat.SQUARE,
            extras = listOf(StoryExtra.DATE),
            font = ArtFont.OSWALD,
            textColor = ArtColor.BLACK,
            routeColor = ArtColor.CYAN,
        )

        ArtRecipeStore(context).save(recipe)

        assertThat(ArtRecipeStore(context).load()).isEqualTo(recipe)
    }

    @Test
    fun `reset restores the documented recipe without touching the story store`() {
        val store = ArtRecipeStore(context)
        store.save(ArtRecipe(font = ArtFont.ANTON))

        store.reset()

        assertThat(store.load()).isEqualTo(ArtRecipe())
        assertThat(
            context.getSharedPreferences(ArtRecipeStore.PREFERENCES, Context.MODE_PRIVATE)
                .all,
        ).isEmpty()
        assertThat(
            context.getSharedPreferences(StoryRecipeStore.PREFERENCES, Context.MODE_PRIVATE)
                .all,
        ).isEmpty()
    }
}
