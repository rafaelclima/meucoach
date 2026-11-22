package com.meucoach.app.story

import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.StoryExtra
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned JSON for the art recipe (ADR 0058 §2.6). Mirrors
 * [StoryRecipeCodec]: forgiving decode — unknown enum values fall back to the
 * documented default and a corrupt payload never crashes the Studio.
 *
 * The card only offers the five extras of [ArtRecipe.EXTRA_CHOICES]; a saved
 * recipe carrying video-only extras (MAX_HR, FASTEST_KM) has them skipped on
 * decode, the same way the Studio filters availability on open. Duplicated
 * names, more than two extras or a wrong version fall back to the default.
 */
object ArtRecipeCodec {
    const val VERSION = 1

    private const val KEY_VERSION = "v"
    private const val KEY_FORMAT = "format"
    private const val KEY_EXTRAS = "extras"
    private const val KEY_FONT = "font"
    private const val KEY_TEXT_COLOR = "textColor"
    private const val KEY_ROUTE_COLOR = "routeColor"

    fun encode(recipe: ArtRecipe): String = JSONObject().apply {
        put(KEY_VERSION, VERSION)
        put(KEY_FORMAT, recipe.format.name)
        put(KEY_EXTRAS, JSONArray(recipe.extras.map { it.name }))
        put(KEY_FONT, recipe.font.name)
        put(KEY_TEXT_COLOR, recipe.textColor.name)
        put(KEY_ROUTE_COLOR, recipe.routeColor.name)
    }.toString()

    fun decode(json: String?): ArtRecipe {
        if (json.isNullOrBlank()) return ArtRecipe()
        return runCatching {
            val root = JSONObject(json)
            require(root.optInt(KEY_VERSION, 0) == VERSION) { "Unsupported art recipe version" }
            ArtRecipe(
                format = enumOr(root.optString(KEY_FORMAT), ArtFormat.PORTRAIT_4_5),
                extras = decodeExtras(root),
                font = enumOr(root.optString(KEY_FONT), ArtFont.INTER),
                textColor = enumOr(root.optString(KEY_TEXT_COLOR), ArtColor.WHITE),
                routeColor = enumOr(root.optString(KEY_ROUTE_COLOR), ArtColor.LIME),
            )
        }.getOrElse { ArtRecipe() }
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
