package com.meucoach.app.story

import com.meucoach.core.domain.story.StoryDuration
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.domain.story.StoryPreset
import com.meucoach.core.domain.story.StoryQuality
import com.meucoach.core.domain.story.StoryRecipe
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryRecipeCodecTest {
    @Test
    fun `round-trips every field`() {
        val recipe = StoryRecipe(
            preset = StoryPreset.CUSTOM,
            extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE),
            duration = StoryDuration.SEC_45,
            mapStyle = StoryMapStyle.MEUCOACH_LIGHT,
            quality = StoryQuality.SMOOTH_1080P60,
        )

        val decoded = StoryRecipeCodec.decode(StoryRecipeCodec.encode(recipe))

        assertThat(decoded).isEqualTo(recipe)
    }

    @Test
    fun `writes versioned json with the documented keys`() {
        val json = JSONObject(StoryRecipeCodec.encode(StoryRecipe()))

        assertThat(json.getInt("v")).isEqualTo(1)
        assertThat(json.getString("preset")).isEqualTo("ESSENTIAL")
        assertThat(json.getJSONArray("extras").length()).isZero()
        assertThat(json.getString("duration")).isEqualTo("AUTO")
        assertThat(json.getString("mapStyle")).isEqualTo("MEUCOACH_DARK")
        assertThat(json.getString("quality")).isEqualTo("STANDARD_1080P30")
    }

    @Test
    fun `falls back to the safe default on null blank or corrupt json`() {
        assertThat(StoryRecipeCodec.decode(null)).isEqualTo(StoryRecipe())
        assertThat(StoryRecipeCodec.decode("")).isEqualTo(StoryRecipe())
        assertThat(StoryRecipeCodec.decode("{not json")).isEqualTo(StoryRecipe())
        assertThat(StoryRecipeCodec.decode("[]")).isEqualTo(StoryRecipe())
    }

    @Test
    fun `falls back on an unsupported version`() {
        val json = """{"v":2,"preset":"PERFORMANCE","extras":["DATE"]}"""

        assertThat(StoryRecipeCodec.decode(json)).isEqualTo(StoryRecipe())
    }

    @Test
    fun `ignores unknown keys`() {
        val json = """
            {
              "v": 1,
              "preset": "PHYSIOLOGICAL",
              "extras": ["AVG_HR", "CADENCE"],
              "duration": "SEC_30",
              "mapStyle": "MEUCOACH_DARK",
              "quality": "STANDARD_1080P30",
              "futureKey": {"nested": true},
              "anotherFutureKey": [1, 2, 3]
            }
        """.trimIndent()

        val recipe = StoryRecipeCodec.decode(json)

        assertThat(recipe.preset).isEqualTo(StoryPreset.PHYSIOLOGICAL)
        assertThat(recipe.extras).containsExactly(StoryExtra.AVG_HR, StoryExtra.CADENCE)
        assertThat(recipe.duration).isEqualTo(StoryDuration.SEC_30)
    }

    @Test
    fun `rejects more than two extras and duplicated extras`() {
        val three = """{"v":1,"extras":["AVG_HR","CADENCE","DATE"]}"""
        val duplicated = """{"v":1,"extras":["AVG_HR","AVG_HR"]}"""

        assertThat(StoryRecipeCodec.decode(three)).isEqualTo(StoryRecipe())
        assertThat(StoryRecipeCodec.decode(duplicated)).isEqualTo(StoryRecipe())
    }

    @Test
    fun `skips unknown extras but keeps the known ones`() {
        val json = """{"v":1,"extras":["AVG_HR","POWER","CADENCE"]}"""

        val recipe = StoryRecipeCodec.decode(json)

        assertThat(recipe.extras).containsExactly(StoryExtra.AVG_HR, StoryExtra.CADENCE)
    }

    @Test
    fun `unknown enum values fall back field by field`() {
        val json = """
            {
              "v": 1,
              "preset": "WHATEVER",
              "extras": ["DATE"],
              "duration": "FOREVER",
              "mapStyle": "PINK",
              "quality": "ULTRA"
            }
        """.trimIndent()

        val recipe = StoryRecipeCodec.decode(json)

        assertThat(recipe.preset).isEqualTo(StoryPreset.ESSENTIAL)
        assertThat(recipe.duration).isEqualTo(StoryDuration.AUTO)
        assertThat(recipe.mapStyle).isEqualTo(StoryMapStyle.MEUCOACH_DARK)
        assertThat(recipe.quality).isEqualTo(StoryQuality.STANDARD_1080P30)
        assertThat(recipe.extras).containsExactly(StoryExtra.DATE)
    }

    @Test
    fun `payload stays well below the work data limit`() {
        val recipe = StoryRecipe(
            preset = StoryPreset.CUSTOM,
            extras = listOf(StoryExtra.ELEVATION_GAIN, StoryExtra.FASTEST_KM),
            duration = StoryDuration.SEC_60,
            mapStyle = StoryMapStyle.MEUCOACH_LIGHT,
            quality = StoryQuality.SMOOTH_1080P60,
        )

        val bytes = StoryRecipeCodec.encode(recipe).toByteArray(Charsets.UTF_8)

        assertThat(bytes.size).isLessThanOrEqualTo(StoryRecipeCodec.MAX_PAYLOAD_BYTES)
    }
}
