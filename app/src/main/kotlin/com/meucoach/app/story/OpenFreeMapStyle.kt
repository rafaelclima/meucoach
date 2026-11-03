package com.meucoach.app.story

import org.json.JSONObject

/**
 * OpenFreeMap vector basemap (OSM data) with larger, high-contrast labels.
 *
 * The stock MapLibre styles render place/street labels at 10–14 px, which is
 * unreadable in a 9:16 video. We keep the geometry/colors of the "liberty"
 * style and bump the text sizes/contrast so the region is recognizable at
 * 1080×1920. No API key and no per-view limits (openfreemap.org).
 */
internal object OpenFreeMapStyle {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    fun customize(json: String): String {
        val root = JSONObject(json)
        val layers = root.optJSONArray("layers") ?: return json
        for (index in 0 until layers.length()) {
            val layer = layers.optJSONObject(index) ?: continue
            if (layer.optString("type") != "symbol") continue
            val layout = layer.optJSONObject("layout") ?: continue
            if (!layout.has("text-field")) continue
            val label = labelFor(layer.optString("id")) ?: continue
            layout.put("text-size", label.size)
            val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
            label.color?.let { paint.put("text-color", it) }
            paint.put("text-halo-color", "#FFFFFF")
            paint.put("text-halo-width", 1.8)
        }
        return root.toString()
    }

    private data class Label(val size: Int, val color: String?)

    private fun labelFor(id: String): Label? = when {
        id == "label_city_capital" -> Label(28, "#0B0D10")
        id == "label_city" -> Label(26, "#0B0D10")
        id.startsWith("label_country") -> Label(22, "#0B0D10")
        id.startsWith("label_town") -> Label(22, "#111418")
        id.startsWith("label_village") -> Label(20, "#111418")
        id.startsWith("label_other") -> Label(19, "#1B1F24")
        id.startsWith("label_state") -> Label(20, "#111418")
        id.startsWith("highway-name") -> Label(18, "#2B3036")
        id == "waterway_line_label" || id.startsWith("water_name") -> Label(20, "#2E5A80")
        id == "airport" -> Label(18, "#3A4048")
        id.startsWith("poi_") -> Label(16, "#3A4048")
        id.startsWith("highway-shield") || id.startsWith("road_shield") -> Label(14, null)
        else -> null
    }
}
