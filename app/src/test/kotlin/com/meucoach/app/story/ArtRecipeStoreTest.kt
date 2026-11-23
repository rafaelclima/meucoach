package com.meucoach.app.story

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtLayout
import com.meucoach.core.domain.story.ArtOffset
import com.meucoach.core.domain.story.ArtPhotoCrop
import com.meucoach.core.domain.story.ArtPhotoRef
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.ArtTemplate
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryRecipe
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
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
    fun `saves and reloads the style across instances`() {
        val recipe = ArtRecipe(
            format = ArtFormat.SQUARE,
            extras = listOf(StoryExtra.DATE),
            font = ArtFont.OSWALD,
            textColor = ArtColor.BLACK,
            routeColor = ArtColor.CYAN,
            layout = ArtLayout(
                template = ArtTemplate.CENTER,
                distanceOffset = ArtOffset(0.25f, -0.5f),
                routeOffset = ArtOffset(-0.125f, 0.5f),
                textScalePercent = 115,
            ),
        )

        ArtRecipeStore(context).save(recipe)

        assertThat(ArtRecipeStore(context).load()).isEqualTo(recipe)
    }

    @Test
    fun `save normalizes background and photo out of the style store`() {
        val photo = ArtPhotoRef(
            photoId = "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
            activityId = "activity-1",
            crop = ArtPhotoCrop(focusX = 0.25f, focusY = 0.75f, zoom = 2f),
        )
        val recipe = ArtRecipe(
            background = ArtBackgroundMode.PHOTO,
            layout = ArtLayout(template = ArtTemplate.BOTTOM, textScalePercent = 110),
            photo = photo,
        )

        val store = ArtRecipeStore(context)
        store.save(recipe)

        val loaded = store.load()
        assertThat(loaded.background).isEqualTo(ArtBackgroundMode.TRANSPARENT)
        assertThat(loaded.photo).isNull()
        assertThat(loaded.layout).isEqualTo(recipe.layout)

        val raw = context.getSharedPreferences(ArtRecipeStore.PREFERENCES, Context.MODE_PRIVATE)
            .getString(ArtRecipeStore.KEY_RECIPE, null)
        assertThat(raw).isNotNull()
        assertThat(raw).doesNotContain("background")
        assertThat(raw).doesNotContain("photo")
    }

    @Test
    fun `load migrates a version 1 payload with layout defaults`() {
        val v1 = JSONObject().apply {
            put("v", 1)
            put("format", "SQUARE")
            put("extras", JSONArray(listOf("DATE")))
            put("font", "ANTON")
            put("textColor", "CYAN")
            put("routeColor", "CORAL")
        }
        context.getSharedPreferences(ArtRecipeStore.PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(ArtRecipeStore.KEY_RECIPE, v1.toString())
            .commit()

        val loaded = ArtRecipeStore(context).load()

        assertThat(loaded.format).isEqualTo(ArtFormat.SQUARE)
        assertThat(loaded.extras).containsExactly(StoryExtra.DATE)
        assertThat(loaded.font).isEqualTo(ArtFont.ANTON)
        assertThat(loaded.layout).isEqualTo(ArtLayout())
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
