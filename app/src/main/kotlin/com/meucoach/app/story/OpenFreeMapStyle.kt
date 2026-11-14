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

    /** Autoral dark: grafite de fundo e água azul-petróleo (MAP-04). */
    const val DARK_BACKGROUND = "#101418"
    const val DARK_WATER = "#184A5E"
    const val DARK_WATER_LINE = "#1E5A74"
    const val DARK_PARK = "#1E3329"
    const val DARK_PARK_OUTLINE = "#2A4A3A"
    const val DARK_LABEL = "#F4F6F7"
    const val DARK_HALO = "#0B0D10"

    /** Autoral light: fundo quente claro, mesma hierarquia do dark (MAP-04). */
    const val LIGHT_BACKGROUND = "#F2EFE8"

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
            if (isClutter(layer.optString("id"), style)) continue
            val type = layer.optString("type")
            // Satellite imagery replaces the vector base; labels stay on top.
            if (satellite && type != "symbol") continue
            if (type == "symbol") customizeLabel(layer, style)
            if (style == StoryMapStyle.MEUCOACH_DARK) applyDarkPaint(layer)
            if (style == StoryMapStyle.MEUCOACH_LIGHT) applyLightPaint(layer)
            layers.put(layer)
        }
        val result = styledLayers(style, root, layers)
        root.put("layers", result)
        return root.toString()
    }

    private fun customizeLabel(layer: JSONObject, style: StoryMapStyle) {
        val layout = layer.optJSONObject("layout") ?: return
        if (!layout.has("text-field")) return
        val label = labelFor(layer.optString("id")) ?: return
        layout.put("text-size", label.size)
        val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
        if (style == StoryMapStyle.SATELLITE || style == StoryMapStyle.MEUCOACH_DARK) {
            paint.put("text-color", DARK_LABEL)
            paint.put("text-halo-color", DARK_HALO)
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

        StoryMapStyle.MEUCOACH_DARK -> withMeuCoachDark(layers)

        StoryMapStyle.MEUCOACH_LIGHT -> withMeuCoachLight(layers)

        // Legacy liberty: trimmed geometry only.
        StoryMapStyle.CLASSIC,
        -> layers
    }

    /**
     * MeuCoach Dark: grafite ao fundo, água azul-petróleo, parques verde
     * dessaturado e vias principais finas e claras. Os layers já chegam sem
     * POI/transporte/aeroway/vias menores ([isClutter]); aqui só remapeamos
     * as cores que o liberty desenha claras.
     */
    private fun withMeuCoachDark(layers: JSONArray): JSONArray = layers

    /**
     * MeuCoach Light: mesma hierarquia do dark sobre base clara, com o
     * contraste AA do liberty (texto escuro sobre fundo claro). O fundo ganha
     * um tom quente autoral; o restante mantém as cores do liberty.
     */
    private fun withMeuCoachLight(layers: JSONArray): JSONArray = layers

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
     * Grafite, água azul-petróleo e parques dessaturados. As cores do liberty
     * são claras por padrão; cada fill/line conhecido é remapeado para a
     * paleta autoral sem tocar em expressões de zoom (só cor/opacidade).
     */
    private fun applyDarkPaint(layer: JSONObject) {
        val id = layer.optString("id")
        if (id == "background") {
            layer.optJSONObject("paint")?.put("background-color", DARK_BACKGROUND)
            return
        }
        if (id == "natural_earth") {
            layer.optJSONObject("paint")?.put("raster-opacity", 0.12)
            return
        }
        val paint = layer.optJSONObject("paint") ?: return
        if (paintDarkFill(id, paint)) return
        paintDarkLine(layer, paint)
    }

    /** Fill layers; true when [id] was handled. */
    private fun paintDarkFill(id: String, paint: JSONObject): Boolean {
        when (id) {
            "park" -> {
                paint.put("fill-color", DARK_PARK)
                paint.put("fill-outline-color", DARK_PARK_OUTLINE)
            }

            "park_outline" -> paint.put("line-color", DARK_PARK_OUTLINE)

            "landuse_residential" -> paint.put("fill-color", "#161A1F")

            "landcover_wood", "landcover_grass" -> {
                paint.put("fill-color", "#1C2F26")
                paint.put("fill-opacity", 0.85)
            }

            "landcover_ice" -> paint.put("fill-color", "#1B242E")

            "landcover_sand" -> paint.put("fill-color", "#232830")

            "landuse_pitch", "landuse_track" -> paint.put("fill-color", "#1D2A24")

            "landuse_cemetery" -> paint.put("fill-color", "#1A2620")

            "landuse_hospital" -> paint.put("fill-color", "#251E22")

            "landuse_school" -> paint.put("fill-color", "#1F251B")

            "landcover_wetland" -> paint.put("fill-opacity", 0.3)

            "water" -> paint.put("fill-color", DARK_WATER)

            "building" -> {
                paint.put("fill-color", "#1E242B")
                paint.put("fill-outline-color", "#2A323B")
            }

            else -> return false
        }
        return true
    }

    /** Line layers: waterways, boundaries and the arterial road network. */
    private fun paintDarkLine(layer: JSONObject, paint: JSONObject) {
        val id = layer.optString("id")
        val type = layer.optString("type")
        val sourceLayer = layer.optString("source-layer")
        if (sourceLayer == "waterway" && type == "line") {
            paint.put("line-color", DARK_WATER_LINE)
            return
        }
        if (id.startsWith("boundary")) {
            paint.put("line-color", "#5A6169")
            return
        }
        if (sourceLayer != "transportation" || type != "line") return
        if (id.contains("casing")) {
            paint.put("line-color", DARK_HALO)
            return
        }
        paint.put("line-color", roadColor(id))
    }

    private fun roadColor(id: String): String = when {
        id.contains("motorway") || id.contains("trunk") || id.contains("primary") -> "#CBD2D9"
        id.contains("secondary") || id.contains("tertiary") -> "#9AA3AD"
        else -> "#7D868F"
    }

    private fun applyLightPaint(layer: JSONObject) {
        if (layer.optString("id") == "background") {
            layer.optJSONObject("paint")?.put("background-color", LIGHT_BACKGROUND)
        }
    }

    /**
     * POI, transit and airport icons add noise over a 1080 px-wide route, and
     * their labels are too small to read after the Instagram re-encode. The
     * autoral styles also hide rail/transit geometry, aerodromes and minor
     * streets/links: o mapa mantém cidade, bairro, parques, água, avenidas e
     * trilhas (path), sem a malha fina que compete com a rota lima.
     */
    private fun isClutter(id: String, style: StoryMapStyle = StoryMapStyle.MEUCOACH_DARK): Boolean {
        if (id.startsWith("poi_") || id.startsWith("transit_") || id.startsWith("airport_")) return true
        if (id in BASE_CLUTTER) return true
        if (style != StoryMapStyle.MEUCOACH_DARK && style != StoryMapStyle.MEUCOACH_LIGHT) return false
        if (id in MINOR_EXACT_CLUTTER) return true
        return MINOR_PART_CLUTTER.any { part -> id.contains(part) }
    }

    private val BASE_CLUTTER = setOf("poi_transit", "airport")
    private val MINOR_EXACT_CLUTTER = setOf(
        "road_area_pattern",
        "building-3d",
        "highway-name-minor",
    )
    private val MINOR_PART_CLUTTER = listOf(
        "aeroway_",
        "rail",
        "service_track",
        "_minor",
        "_street",
        "_link",
    )

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
