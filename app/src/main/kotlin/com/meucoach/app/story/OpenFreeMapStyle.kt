package com.meucoach.app.story

import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenFreeMap vector basemap (OSM data) trimmed for a 9:16 story.
 *
 * The stock MapLibre styles render place/street labels at 10–14 px, which is
 * unreadable in a 1080×1920 video, and sprinkle POI/transit icons that fight
 * with the route. We keep the geometry/colors of the "liberty" style, hide the
 * clutter and bump the text sizes/contrast so the region is recognizable at
 * 1080×1920. No API key and no per-view limits (openfreemap.org).
 */
internal object OpenFreeMapStyle {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    fun customize(json: String): String {
        val root = JSONObject(json)
        val source = root.optJSONArray("layers") ?: return json
        val layers = JSONArray()
        for (index in 0 until source.length()) {
            val layer = source.optJSONObject(index) ?: continue
            if (isClutter(layer.optString("id"))) continue
            if (layer.optString("type") == "symbol") {
                val layout = layer.optJSONObject("layout")
                if (layout != null && layout.has("text-field")) {
                    labelFor(layer.optString("id"))?.let { label ->
                        layout.put("text-size", label.size)
                        val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
                        label.color?.let { paint.put("text-color", it) }
                        paint.put("text-halo-color", "#FFFFFF")
                        paint.put("text-halo-width", 1.8)
                    }
                }
            }
            layers.put(layer)
        }
        root.put("layers", layers)
        return root.toString()
    }

    /**
     * POI, transit and airport icons add noise over a 1080 px-wide route, and
     * their labels are too small to read after the Instagram re-encode.
     */
    private fun isClutter(id: String): Boolean = id.startsWith("poi_") ||
        id.startsWith("transit_") ||
        id == "airport" ||
        id.startsWith("airport_")

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
        id.startsWith("highway-shield") || id.startsWith("road_shield") -> Label(14, null)
        else -> null
    }
}
