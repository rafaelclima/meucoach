package com.meucoach.app.story

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
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtRecipeCodecTest {
    @Test
    fun `round-trips every style field`() {
        val recipe = ArtRecipe(
            format = ArtFormat.SQUARE,
            extras = listOf(StoryExtra.DATE, StoryExtra.AVG_HR),
            font = ArtFont.ANTON,
            textColor = ArtColor.CYAN,
            routeColor = ArtColor.CORAL,
            layout = ArtLayout(
                template = ArtTemplate.CENTER,
                distanceOffset = ArtOffset(0.25f, -0.5f),
                paceTimeOffset = ArtOffset(-0.125f, 0.5f),
                extrasOffset = ArtOffset(0.5f, 0.25f),
                routeOffset = ArtOffset(-0.5f, -0.125f),
                textScalePercent = 115,
            ),
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
    fun `writes version 2 json with the documented style keys`() {
        val json = JSONObject(ArtRecipeCodec.encode(ArtRecipe()))

        assertThat(ArtRecipeCodec.VERSION).isEqualTo(2)
        assertThat(json.getInt("v")).isEqualTo(2)
        assertThat(json.getString("format")).isEqualTo("PORTRAIT_4_5")
        assertThat(json.getJSONArray("extras").length()).isZero()
        assertThat(json.getString("font")).isEqualTo("INTER")
        assertThat(json.getString("textColor")).isEqualTo("WHITE")
        assertThat(json.getString("routeColor")).isEqualTo("LIME")
        assertThat(json.getString("template")).isEqualTo("TOP")
        assertThat(json.getInt("textScalePercent")).isEqualTo(100)

        val offsets = json.getJSONObject("offsets")
        for (block in listOf("distance", "paceTime", "extras", "route")) {
            val offset = offsets.getJSONObject(block)
            assertThat(offset.getDouble("dx")).isZero()
            assertThat(offset.getDouble("dy")).isZero()
        }

        assertThat(json.has("background")).isFalse()
        assertThat(json.has("photo")).isFalse()
    }

    @Test
    fun `migrates a version 1 payload preserving style and adding layout defaults`() {
        val v1 = JSONObject().apply {
            put("v", 1)
            put("format", "SQUARE")
            put("extras", JSONArray(listOf("DATE", "AVG_HR")))
            put("font", "OSWALD")
            put("textColor", "BLACK")
            put("routeColor", "CYAN")
        }

        val decoded = ArtRecipeCodec.decode(v1.toString())

        assertThat(decoded.format).isEqualTo(ArtFormat.SQUARE)
        assertThat(decoded.extras).containsExactly(StoryExtra.DATE, StoryExtra.AVG_HR)
        assertThat(decoded.font).isEqualTo(ArtFont.OSWALD)
        assertThat(decoded.textColor).isEqualTo(ArtColor.BLACK)
        assertThat(decoded.routeColor).isEqualTo(ArtColor.CYAN)
        assertThat(decoded.layout).isEqualTo(ArtLayout())
        assertThat(decoded.background).isEqualTo(ArtBackgroundMode.TRANSPARENT)
        assertThat(decoded.photo).isNull()
    }

    @Test
    fun `defaults only the unknown enum fields and keeps the rest`() {
        val json = JSONObject(
            ArtRecipeCodec.encode(
                ArtRecipe(
                    extras = listOf(StoryExtra.DATE),
                    layout = ArtLayout(template = ArtTemplate.CENTER, textScalePercent = 115),
                ),
            ),
        )
            .put("format", "ROUTE_CROP")
            .put("font", "BARLOW")
            .put("textColor", "LIME_PRESSED")
            .put("routeColor", "NEON")
            .put("template", "SIDEWAYS")
            .toString()

        val decoded = ArtRecipeCodec.decode(json)

        assertThat(decoded.format).isEqualTo(ArtFormat.PORTRAIT_4_5)
        assertThat(decoded.extras).containsExactly(StoryExtra.DATE)
        assertThat(decoded.font).isEqualTo(ArtFont.INTER)
        assertThat(decoded.textColor).isEqualTo(ArtColor.WHITE)
        assertThat(decoded.routeColor).isEqualTo(ArtColor.LIME)
        assertThat(decoded.layout.template).isEqualTo(ArtTemplate.TOP)
        assertThat(decoded.layout.textScalePercent).isEqualTo(115)
    }

    @Test
    fun `an invalid offset falls back only for that block`() {
        val recipe = ArtRecipe(
            layout = ArtLayout(
                template = ArtTemplate.CENTER,
                distanceOffset = ArtOffset(0.25f, -0.5f),
                textScalePercent = 115,
            ),
        )
        val json = JSONObject(ArtRecipeCodec.encode(recipe))
        val offsets = json.getJSONObject("offsets")
        offsets.put("paceTime", JSONObject().put("dx", 0.9).put("dy", 0.0))
        offsets.put("extras", "corrupt")
        offsets.put("route", JSONObject().put("dx", "NaN").put("dy", 0.0))

        val decoded = ArtRecipeCodec.decode(json.toString())

        assertThat(decoded.layout.template).isEqualTo(ArtTemplate.CENTER)
        assertThat(decoded.layout.textScalePercent).isEqualTo(115)
        assertThat(decoded.layout.distanceOffset).isEqualTo(ArtOffset(0.25f, -0.5f))
        assertThat(decoded.layout.paceTimeOffset).isEqualTo(ArtOffset())
        assertThat(decoded.layout.extrasOffset).isEqualTo(ArtOffset())
        assertThat(decoded.layout.routeOffset).isEqualTo(ArtOffset())
    }

    @Test
    fun `missing offsets keep the template and scale`() {
        val json = JSONObject(
            ArtRecipeCodec.encode(
                ArtRecipe(layout = ArtLayout(template = ArtTemplate.BOTTOM, textScalePercent = 125)),
            ),
        ).apply { remove("offsets") }

        val decoded = ArtRecipeCodec.decode(json.toString())

        assertThat(decoded.layout.template).isEqualTo(ArtTemplate.BOTTOM)
        assertThat(decoded.layout.textScalePercent).isEqualTo(125)
        assertThat(decoded.layout.distanceOffset).isEqualTo(ArtOffset())
    }

    @Test
    fun `an invalid text scale falls back to 100 without erasing the layout`() {
        val json = JSONObject(
            ArtRecipeCodec.encode(
                ArtRecipe(
                    layout = ArtLayout(
                        template = ArtTemplate.CENTER,
                        distanceOffset = ArtOffset(0.25f, 0.5f),
                        textScalePercent = 115,
                    ),
                ),
            ),
        ).put("textScalePercent", 999)

        val decoded = ArtRecipeCodec.decode(json.toString())

        assertThat(decoded.layout.textScalePercent).isEqualTo(100)
        assertThat(decoded.layout.template).isEqualTo(ArtTemplate.CENTER)
        assertThat(decoded.layout.distanceOffset).isEqualTo(ArtOffset(0.25f, 0.5f))
    }

    @Test
    fun `style decode drops background and photo but keeps the layout`() {
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

        val decoded = ArtRecipeCodec.decode(ArtRecipeCodec.encode(recipe))

        assertThat(decoded.background).isEqualTo(ArtBackgroundMode.TRANSPARENT)
        assertThat(decoded.photo).isNull()
        assertThat(decoded.layout).isEqualTo(recipe.layout)
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
        assertThat(ArtRecipeCodec.decode("[]")).isEqualTo(ArtRecipe())

        val wrongVersion = JSONObject(ArtRecipeCodec.encode(ArtRecipe())).put("v", 99).toString()
        assertThat(ArtRecipeCodec.decode(wrongVersion)).isEqualTo(ArtRecipe())

        val missingVersion = JSONObject(ArtRecipeCodec.encode(ArtRecipe())).apply { remove("v") }.toString()
        assertThat(ArtRecipeCodec.decode(missingVersion)).isEqualTo(ArtRecipe())

        val threeExtras = JSONObject(ArtRecipeCodec.encode(ArtRecipe()))
            .put(
                "extras",
                JSONArray(listOf("DATE", "AVG_HR", "CADENCE")),
            )
            .toString()
        assertThat(ArtRecipeCodec.decode(threeExtras)).isEqualTo(ArtRecipe())
    }
}
