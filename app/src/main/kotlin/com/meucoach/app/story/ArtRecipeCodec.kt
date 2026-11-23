package com.meucoach.app.story

import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtLayout
import com.meucoach.core.domain.story.ArtOffset
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.ArtTemplate
import com.meucoach.core.domain.story.StoryExtra
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned JSON for the art style (ADR 0058 §2.6, extended by ADR 0061 §2.3).
 *
 * v2 adds the template, the four per-block offsets and `textScalePercent`; v1
 * payloads keep their existing style and receive the new layout defaults. The
 * codec is deliberately forgiving: unknown enum values, invalid offsets and an
 * invalid text scale fall back per field, and a corrupt payload or an unknown
 * version falls back to [ArtRecipe] instead of throwing to the Studio.
 *
 * The style codec never persists the activity photo: `background` and `photo`
 * are owned by the per-activity photo store, so encoding drops both and decode
 * always returns the transparent default for them.
 */
object ArtRecipeCodec {
    const val VERSION = 2

    private const val LEGACY_VERSION = 1

    private const val KEY_VERSION = "v"
    private const val KEY_FORMAT = "format"
    private const val KEY_EXTRAS = "extras"
    private const val KEY_FONT = "font"
    private const val KEY_TEXT_COLOR = "textColor"
    private const val KEY_ROUTE_COLOR = "routeColor"
    private const val KEY_TEMPLATE = "template"
    private const val KEY_OFFSETS = "offsets"
    private const val KEY_TEXT_SCALE = "textScalePercent"
    private const val KEY_DX = "dx"
    private const val KEY_DY = "dy"

    private const val BLOCK_DISTANCE = "distance"
    private const val BLOCK_PACE_TIME = "paceTime"
    private const val BLOCK_EXTRAS = "extras"
    private const val BLOCK_ROUTE = "route"

    fun encode(recipe: ArtRecipe): String = JSONObject().apply {
        put(KEY_VERSION, VERSION)
        put(KEY_FORMAT, recipe.format.name)
        put(KEY_EXTRAS, JSONArray(recipe.extras.map { it.name }))
        put(KEY_FONT, recipe.font.name)
        put(KEY_TEXT_COLOR, recipe.textColor.name)
        put(KEY_ROUTE_COLOR, recipe.routeColor.name)
        put(KEY_TEMPLATE, recipe.layout.template.name)
        put(
            KEY_OFFSETS,
            JSONObject().apply {
                put(BLOCK_DISTANCE, offsetJson(recipe.layout.distanceOffset))
                put(BLOCK_PACE_TIME, offsetJson(recipe.layout.paceTimeOffset))
                put(BLOCK_EXTRAS, offsetJson(recipe.layout.extrasOffset))
                put(BLOCK_ROUTE, offsetJson(recipe.layout.routeOffset))
            },
        )
        put(KEY_TEXT_SCALE, recipe.layout.textScalePercent)
    }.toString()

    fun decode(json: String?): ArtRecipe {
        if (json.isNullOrBlank()) return ArtRecipe()
        return runCatching {
            val root = JSONObject(json)
            val version = root.optInt(KEY_VERSION, 0)
            require(version == VERSION || version == LEGACY_VERSION) {
                "Unsupported art recipe version"
            }
            ArtRecipe(
                format = enumOr(root.optString(KEY_FORMAT), ArtFormat.PORTRAIT_4_5),
                extras = decodeExtras(root),
                font = enumOr(root.optString(KEY_FONT), ArtFont.INTER),
                textColor = enumOr(root.optString(KEY_TEXT_COLOR), ArtColor.WHITE),
                routeColor = enumOr(root.optString(KEY_ROUTE_COLOR), ArtColor.LIME),
                layout = if (version == VERSION) decodeLayout(root) else ArtLayout(),
            )
        }.getOrElse { ArtRecipe() }
    }

    private fun offsetJson(offset: ArtOffset): JSONObject = JSONObject().apply {
        put(KEY_DX, offset.dx.toDouble())
        put(KEY_DY, offset.dy.toDouble())
    }

    private fun decodeLayout(root: JSONObject): ArtLayout {
        val offsets = root.optJSONObject(KEY_OFFSETS)
        return ArtLayout(
            template = enumOr(root.optString(KEY_TEMPLATE), ArtTemplate.TOP),
            distanceOffset = decodeOffset(offsets, BLOCK_DISTANCE),
            paceTimeOffset = decodeOffset(offsets, BLOCK_PACE_TIME),
            extrasOffset = decodeOffset(offsets, BLOCK_EXTRAS),
            routeOffset = decodeOffset(offsets, BLOCK_ROUTE),
            textScalePercent = decodeTextScale(root),
        )
    }

    private fun decodeOffset(offsets: JSONObject?, block: String): ArtOffset {
        val offset = offsets?.optJSONObject(block) ?: return ArtOffset()
        return runCatching {
            ArtOffset(
                dx = offset.optDouble(KEY_DX, 0.0).toFloat(),
                dy = offset.optDouble(KEY_DY, 0.0).toFloat(),
            )
        }.getOrElse { ArtOffset() }
    }

    private fun decodeTextScale(root: JSONObject): Int {
        val value = root.optInt(KEY_TEXT_SCALE, ArtLayout.DEFAULT_TEXT_SCALE_PERCENT)
        return value.takeIf { it in ArtLayout.MIN_TEXT_SCALE_PERCENT..ArtLayout.MAX_TEXT_SCALE_PERCENT }
            ?: ArtLayout.DEFAULT_TEXT_SCALE_PERCENT
    }

    private fun decodeExtras(root: JSONObject): List<StoryExtra> {
        val array = root.optJSONArray(KEY_EXTRAS) ?: return emptyList()
        val names = buildList {
            for (index in 0 until array.length()) add(array.optString(index, ""))
        }
        val extras = names
            .mapNotNull { name -> StoryExtra.entries.firstOrNull { it.name == name } }
            // Video-only extras are skipped, not fatal: the card never offered them.
            .filter { it in ArtRecipe.EXTRA_CHOICES }
        require(extras.size == extras.distinct().size) { "Duplicated art extra" }
        require(extras.size <= ArtRecipe.MAX_EXTRAS) { "Too many art extras" }
        return extras
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
