package com.meucoach.app.story

import com.meucoach.core.domain.story.StoryMapStyle
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

    private const val ELEVATION_TILES =
        "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"

    private const val SATELLITE_TILES =
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"

    @Suppress("DEPRECATION")
    fun customize(json: String, style: StoryMapStyle = StoryMapStyle.MEUCOACH_DARK): String {
        val root = JSONObject(json)
        val source = root.optJSONArray("layers") ?: return json
        val satellite = style == StoryMapStyle.SATELLITE
        val layers = JSONArray()
        for (index in 0 until source.length()) {
            val layer = source.optJSONObject(index) ?: continue
            if (isClutter(layer.optString("id"))) continue
            val type = layer.optString("type")
            // Satellite imagery replaces the vector base; labels stay on top.
            if (satellite && type != "symbol") continue
            if (type == "symbol") customizeLabel(layer, satellite)
            layers.put(layer)
        }
        val result = styledLayers(style, root, layers)
        root.put("layers", result)
        return root.toString()
    }

    private fun customizeLabel(layer: JSONObject, satellite: Boolean) {
        val layout = layer.optJSONObject("layout") ?: return
        if (!layout.has("text-field")) return
        val label = labelFor(layer.optString("id")) ?: return
        layout.put("text-size", label.size)
        val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
        if (satellite) {
            paint.put("text-color", "#F4F6F7")
            paint.put("text-halo-color", "#0B0D10")
        } else {
            label.color?.let { paint.put("text-color", it) }
            paint.put("text-halo-color", "#FFFFFF")
        }
        paint.put("text-halo-width", 1.8)
    }

    @Suppress("DEPRECATION")
    private fun styledLayers(style: StoryMapStyle, root: JSONObject, layers: JSONArray): JSONArray = when (style) {
        StoryMapStyle.SATELLITE -> withSatellite(root, layers)

        StoryMapStyle.TERRAIN -> withTerrain(root, layers)

        // The autoral dark/light styles arrive in Sprint B (MAP-04); until
        // then they use the trimmed liberty geometry.
        StoryMapStyle.CLASSIC,
        StoryMapStyle.MEUCOACH_DARK,
        StoryMapStyle.MEUCOACH_LIGHT,
        -> layers
    }

    /**
     * Satellite imagery with the same vector labels, muted and veiled so the
     * lime route and the cards stay legible. Imagery: Esri World Imagery
     * (attribution required).
     */
    private fun withSatellite(root: JSONObject, labels: JSONArray): JSONArray {
        val sources = root.optJSONObject("sources") ?: JSONObject().also { root.put("sources", it) }
        sources.put(
            SATELLITE_SOURCE,
            JSONObject(
                """
                {
                  "type": "raster",
                  "tiles": ["$SATELLITE_TILES"],
                  "tileSize": 256,
                  "maxzoom": 19,
                  "attribution": "Imagery: Esri, Maxar, Earthstar Geographics"
                }
                """.trimIndent(),
            ),
        )
        val result = JSONArray()
        result.put(
            JSONObject(
                """
                {
                  "id": "satellite",
                  "type": "raster",
                  "source": "$SATELLITE_SOURCE",
                  "paint": {
                    "raster-opacity": 1.0,
                    "raster-brightness-max": 0.92,
                    "raster-contrast": 0.15,
                    "raster-saturation": -0.2
                  }
                }
                """.trimIndent(),
            ),
        )
        // Dark veil above the imagery, below the labels: keeps the route and
        // the safe-area gradients readable on bright satellite photos.
        result.put(
            JSONObject(
                """
                {
                  "id": "satellite-veil",
                  "type": "background",
                  "paint": {
                    "background-color": "#0B0D10",
                    "background-opacity": 0.18
                  }
                }
                """.trimIndent(),
            ),
        )
        for (index in 0 until labels.length()) result.put(labels.getJSONObject(index))
        return result
    }

    /**
     * Hillshade over the vector basemap: cheap relief that reads as "terrain"
     * even on the top-down snapshot. Tiles come from the public Mapzen terrain
     * set hosted on AWS; attribution is required and rendered on the frame.
     */
    private fun withTerrain(root: JSONObject, layers: JSONArray): JSONArray {
        val sources = root.optJSONObject("sources") ?: JSONObject().also { root.put("sources", it) }
        sources.put(
            TERRAIN_SOURCE,
            JSONObject(
                """
                {
                  "type": "raster-dem",
                  "tiles": ["$ELEVATION_TILES"],
                  "encoding": "terrarium",
                  "tileSize": 256,
                  "attribution": "Elevation: Mapzen"
                }
                """.trimIndent(),
            ),
        )
        val hillshade = JSONObject(
            """
            {
              "id": "terrain-hillshade",
              "type": "hillshade",
              "source": "$TERRAIN_SOURCE",
              "paint": {
                "hillshade-exaggeration": 0.35,
                "hillshade-shadow-color": "#3A3226",
                "hillshade-highlight-color": "#FFF6E5",
                "hillshade-accent-color": "#6B5A45"
              }
            }
            """.trimIndent(),
        )
        // Drawn under every label so street names stay readable.
        val result = JSONArray()
        var inserted = false
        for (index in 0 until layers.length()) {
            val layer = layers.getJSONObject(index)
            if (!inserted && layer.optString("type") == "symbol") {
                result.put(hillshade)
                inserted = true
            }
            result.put(layer)
        }
        if (!inserted) result.put(hillshade)
        return result
    }

    private const val TERRAIN_SOURCE = "terrain-dem"
    private const val SATELLITE_SOURCE = "satellite"

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
