package com.meucoach.app.story

import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.StoryExtra
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtRecipeCodecTest {
    @Test
    fun `round-trips every field`() {
        val recipe = ArtRecipe(
            format = ArtFormat.SQUARE,
            extras = listOf(StoryExtra.DATE, StoryExtra.AVG_HR),
            font = ArtFont.ANTON,
            textColor = ArtColor.CYAN,
            routeColor = ArtColor.CORAL,
        )

        val decoded = ArtRecipeCodec.decode(ArtRecipeCodec.encode(recipe))

        assertThat(decoded).isEqualTo(recipe)
    }

    @Test
    fun `round-trips the 9x16 story format`() {
        val recipe = ArtRecipe(format = ArtFormat.PORTRAIT_9_16)

        assertThat(ArtRecipeCodec.decode(ArtRecipeCodec.encode(recipe))).isEqualTo(recipe)
    }

    @Test
    fun `writes versioned json with the documented keys`() {
        val json = JSONObject(ArtRecipeCodec.encode(ArtRecipe()))

        assertThat(json.getInt("v")).isEqualTo(1)
        assertThat(json.getString("format")).isEqualTo("PORTRAIT_4_5")
        assertThat(json.getJSONArray("extras").length()).isZero()
        assertThat(json.getString("font")).isEqualTo("INTER")
        assertThat(json.getString("textColor")).isEqualTo("WHITE")
        assertThat(json.getString("routeColor")).isEqualTo("LIME")
    }

    @Test
    fun `defaults to the documented recipe on unknown enums`() {
        val json = JSONObject(ArtRecipeCodec.encode(ArtRecipe()))
            .put("format", "ROUTE_CROP")
            .put("font", "BARLOW")
            .put("textColor", "LIME_PRESSED")
            .put("routeColor", "NEON")
            .toString()

        assertThat(ArtRecipeCodec.decode(json)).isEqualTo(ArtRecipe())
    }

    @Test
    fun `drops extras outside the card set`() {
        val json = JSONObject(ArtRecipeCodec.encode(ArtRecipe(extras = listOf(StoryExtra.MAX_HR, StoryExtra.DATE))))
            .toString()

        assertThat(ArtRecipeCodec.decode(json).extras).containsExactly(StoryExtra.DATE)
    }

    @Test
    fun `a corrupt payload falls back to the documented recipe`() {
        assertThat(ArtRecipeCodec.decode(null)).isEqualTo(ArtRecipe())
        assertThat(ArtRecipeCodec.decode("")).isEqualTo(ArtRecipe())
        assertThat(ArtRecipeCodec.decode("não é json")).isEqualTo(ArtRecipe())

        val wrongVersion = JSONObject(ArtRecipeCodec.encode(ArtRecipe())).put("v", 99).toString()
        assertThat(ArtRecipeCodec.decode(wrongVersion)).isEqualTo(ArtRecipe())

        val threeExtras = JSONObject(ArtRecipeCodec.encode(ArtRecipe()))
            .put(
                "extras",
                org.json.JSONArray(listOf("DATE", "AVG_HR", "CADENCE")),
            )
            .toString()
        assertThat(ArtRecipeCodec.decode(threeExtras)).isEqualTo(ArtRecipe())
    }
}
