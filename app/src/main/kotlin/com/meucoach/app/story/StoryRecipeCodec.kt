package com.meucoach.app.story

import com.meucoach.core.domain.story.StoryDuration
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.domain.story.StoryPreset
import com.meucoach.core.domain.story.StoryQuality
import com.meucoach.core.domain.story.StoryRecipe
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned JSON for the story recipe. The same payload is persisted in
 * SharedPreferences and carried by the WorkManager input (≤ 2 KB, no bitmap).
 *
 * Decoding is deliberately forgiving: an old version, a corrupt number of
 * extras or an unknown enum value falls back to [StoryRecipe], and unknown
 * keys are ignored. A bad preference must never fail a background export
 * (ADR 0040 §4.5).
 */
object StoryRecipeCodec {
    const val VERSION = 1
    const val MAX_PAYLOAD_BYTES = 2_048

    private const val KEY_VERSION = "v"
    private const val KEY_PRESET = "preset"
    private const val KEY_EXTRAS = "extras"
    private const val KEY_DURATION = "duration"
    private const val KEY_MAP_STYLE = "mapStyle"
    private const val KEY_QUALITY = "quality"

    fun encode(recipe: StoryRecipe): String = JSONObject().apply {
        put(KEY_VERSION, VERSION)
        put(KEY_PRESET, recipe.preset.name)
        put(KEY_EXTRAS, JSONArray(recipe.extras.map { it.name }))
        put(KEY_DURATION, recipe.duration.name)
        put(KEY_MAP_STYLE, recipe.mapStyle.name)
        put(KEY_QUALITY, recipe.quality.name)
    }.toString()

    fun decode(json: String?): StoryRecipe {
        if (json.isNullOrBlank()) return StoryRecipe()
        return runCatching {
            val root = JSONObject(json)
            require(root.optInt(KEY_VERSION, 0) == VERSION) { "Unsupported story recipe version" }
            val extras = decodeExtras(root)
            StoryRecipe(
                preset = enumOr(root.optString(KEY_PRESET), StoryPreset.ESSENTIAL),
                extras = extras,
                duration = enumOr(root.optString(KEY_DURATION), StoryDuration.AUTO),
                mapStyle = enumOr(root.optString(KEY_MAP_STYLE), StoryMapStyle.MEUCOACH_DARK),
                quality = enumOr(root.optString(KEY_QUALITY), StoryQuality.STANDARD_1080P30),
            )
        }.getOrElse { StoryRecipe() }
    }

    private fun decodeExtras(root: JSONObject): List<StoryExtra> {
        val array = root.optJSONArray(KEY_EXTRAS) ?: return emptyList()
        val extras = mutableListOf<StoryExtra>()
        for (index in 0 until array.length()) {
            val name = array.optString(index, "")
            val extra = StoryExtra.entries.firstOrNull { it.name == name } ?: continue
            require(extra !in extras) { "Duplicated story extra" }
            extras += extra
        }
        require(extras.size <= StoryRecipe.MAX_EXTRAS) { "Too many story extras" }
        return extras
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
