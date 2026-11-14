package com.meucoach.app.story

import com.meucoach.core.domain.story.StoryMapStyle
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test

class OpenFreeMapStyleTest {
    @Test
    fun `light enlarges place labels with dark text`() {
        val json = """
            {"layers":[
              {"id":"label_city","type":"symbol","layout":{"text-field":["get","name"]},"paint":{"text-color":"#000"}},
              {"id":"place_not_in_liberty","type":"symbol","layout":{"text-field":["get","name"]}},
              {"id":"roads","type":"line","paint":{"line-color":"#fff"}}
            ]}
        """.trimIndent()

        val layers = JSONObject(OpenFreeMapStyle.customize(json, StoryMapStyle.MEUCOACH_LIGHT)).getJSONArray("layers")

        val city = layers.getJSONObject(0)
        assertThat(city.getJSONObject("layout").getInt("text-size")).isEqualTo(26)
        assertThat(city.getJSONObject("paint").getString("text-color")).isEqualTo("#0B0D10")
        assertThat(city.getJSONObject("paint").getString("text-halo-color")).isEqualTo("#FFFFFF")

        val unknown = layers.getJSONObject(1)
        assertThat(unknown.getJSONObject("layout").has("text-size")).isFalse()

        val road = layers.getJSONObject(2)
        assertThat(road.getJSONObject("paint").getString("line-color")).isEqualTo("#fff")
    }

    @Test
    fun `dark uses white labels with dark halo`() {
        val json = """
            {"layers":[
              {"id":"label_city","type":"symbol","layout":{"text-field":["get","name"]},"paint":{"text-color":"#000"}}
            ]}
        """.trimIndent()

        val city = JSONObject(OpenFreeMapStyle.customize(json, StoryMapStyle.MEUCOACH_DARK))
            .getJSONArray("layers").getJSONObject(0)

        assertThat(city.getJSONObject("layout").getInt("text-size")).isEqualTo(26)
        assertThat(city.getJSONObject("paint").getString("text-color")).isEqualTo(OpenFreeMapStyle.DARK_LABEL)
        assertThat(city.getJSONObject("paint").getString("text-halo-color")).isEqualTo(OpenFreeMapStyle.DARK_HALO)
    }

    @Test
    fun `dark paints graphite background with petrol water and muted parks`() {
        val json = """
            {"layers":[
              {"id":"background","type":"background","paint":{"background-color":"#f8f4f0"}},
              {"id":"water","type":"fill","source":"openmaptiles","source-layer":"water","paint":{"fill-color":"rgb(158,189,255)"}},
              {"id":"park","type":"fill","source":"openmaptiles","source-layer":"park","paint":{"fill-color":"#d8e8c8"}},
              {"id":"road_trunk_primary","type":"line","source":"openmaptiles","source-layer":"transportation","paint":{"line-color":"#fea"}},
              {"id":"road_trunk_primary_casing","type":"line","source":"openmaptiles","source-layer":"transportation","paint":{"line-color":"#e9ac77"}}
            ]}
        """.trimIndent()

        val layers = JSONObject(OpenFreeMapStyle.customize(json, StoryMapStyle.MEUCOACH_DARK)).getJSONArray("layers")
        fun byId(id: String) = (0 until layers.length()).map { layers.getJSONObject(it) }.first {
            it.getString("id") ==
                id
        }

        assertThat(byId("background").getJSONObject("paint").getString("background-color"))
            .isEqualTo(OpenFreeMapStyle.DARK_BACKGROUND)
        assertThat(byId("water").getJSONObject("paint").getString("fill-color"))
            .isEqualTo(OpenFreeMapStyle.DARK_WATER)
        assertThat(byId("park").getJSONObject("paint").getString("fill-color"))
            .isEqualTo(OpenFreeMapStyle.DARK_PARK)
        assertThat(byId("road_trunk_primary").getJSONObject("paint").getString("line-color"))
            .isEqualTo("#CBD2D9")
        assertThat(byId("road_trunk_primary_casing").getJSONObject("paint").getString("line-color"))
            .isEqualTo(OpenFreeMapStyle.DARK_HALO)
    }

    @Test
    fun `light keeps a warm background and dark labels`() {
        val json = """
            {"layers":[
              {"id":"background","type":"background","paint":{"background-color":"#f8f4f0"}},
              {"id":"label_city","type":"symbol","layout":{"text-field":["get","name"]}}
            ]}
        """.trimIndent()

        val layers = JSONObject(OpenFreeMapStyle.customize(json, StoryMapStyle.MEUCOACH_LIGHT)).getJSONArray("layers")
        fun byId(id: String) = (0 until layers.length()).map { layers.getJSONObject(it) }.first {
            it.getString("id") ==
                id
        }

        assertThat(byId("background").getJSONObject("paint").getString("background-color"))
            .isEqualTo(OpenFreeMapStyle.LIGHT_BACKGROUND)
        assertThat(byId("label_city").getJSONObject("paint").getString("text-color")).isEqualTo("#0B0D10")
    }

    @Test
    fun `drops POI, transit and airport clutter`() {
        val json = """
            {"layers":[
              {"id":"poi_z14","type":"symbol","layout":{"text-field":["get","name"]}},
              {"id":"transit_stop","type":"circle"},
              {"id":"airport","type":"symbol","layout":{"text-field":["get","name"]}},
              {"id":"label_city","type":"symbol","layout":{"text-field":["get","name"]}},
              {"id":"roads","type":"line","paint":{"line-color":"#fff"}}
            ]}
        """.trimIndent()

        val layers = JSONObject(OpenFreeMapStyle.customize(json)).getJSONArray("layers")

        assertThat(layers.length()).isEqualTo(2)
        assertThat(layers.getJSONObject(0).getString("id")).isEqualTo("label_city")
        assertThat(layers.getJSONObject(1).getString("id")).isEqualTo("roads")
    }

    @Test
    fun `autoral styles hide minor streets and transit but keep trails parks and water`() {
        val json = """
            {"layers":[
              {"id":"road_minor","type":"line","source":"openmaptiles","source-layer":"transportation"},
              {"id":"road_service_track","type":"line","source":"openmaptiles","source-layer":"transportation"},
              {"id":"road_link","type":"line","source":"openmaptiles","source-layer":"transportation"},
              {"id":"bridge_street","type":"line","source":"openmaptiles","source-layer":"transportation"},
              {"id":"road_major_rail","type":"line","source":"openmaptiles","source-layer":"transportation"},
              {"id":"aeroway_runway","type":"line","source":"openmaptiles","source-layer":"aeroway"},
              {"id":"road_path_pedestrian","type":"line","source":"openmaptiles","source-layer":"transportation"},
              {"id":"road_trunk_primary","type":"line","source":"openmaptiles","source-layer":"transportation"},
              {"id":"water","type":"fill","source":"openmaptiles","source-layer":"water"},
              {"id":"park","type":"fill","source":"openmaptiles","source-layer":"park"},
              {"id":"label_city","type":"symbol","layout":{"text-field":["get","name"]}}
            ]}
        """.trimIndent()

        listOf(StoryMapStyle.MEUCOACH_DARK, StoryMapStyle.MEUCOACH_LIGHT).forEach { style ->
            val layers = JSONObject(OpenFreeMapStyle.customize(json, style)).getJSONArray("layers")
            val ids = (0 until layers.length()).map { layers.getJSONObject(it).getString("id") }
            assertThat(ids).doesNotContain(
                "road_minor",
                "road_service_track",
                "road_link",
                "bridge_street",
                "road_major_rail",
                "aeroway_runway",
            )
            assertThat(ids).contains(
                "road_path_pedestrian",
                "road_trunk_primary",
                "water",
                "park",
                "label_city",
            )
        }
    }

    @Test
    fun `classic keeps minor streets`() {
        val json = """
            {"layers":[
              {"id":"road_minor","type":"line","source":"openmaptiles","source-layer":"transportation"},
              {"id":"label_city","type":"symbol","layout":{"text-field":["get","name"]}}
            ]}
        """.trimIndent()

        val layers = JSONObject(OpenFreeMapStyle.customize(json, StoryMapStyle.CLASSIC)).getJSONArray("layers")
        val ids = (0 until layers.length()).map { layers.getJSONObject(it).getString("id") }
        assertThat(ids).contains("road_minor", "label_city")
    }

    @Test
    fun `adds a hillshade layer fed by elevation tiles for terrain`() {
        val json = """
            {"sources":{},"layers":[
              {"id":"label_city","type":"symbol","layout":{"text-field":["get","name"]}}
            ]}
        """.trimIndent()

        val root = JSONObject(OpenFreeMapStyle.customize(json, StoryMapStyle.TERRAIN))
        val layers = root.getJSONArray("layers")

        assertThat(layers.getJSONObject(0).getString("type")).isEqualTo("hillshade")
        assertThat(layers.getJSONObject(0).getString("source")).isEqualTo("terrain-dem")
        assertThat(layers.getJSONObject(1).getString("id")).isEqualTo("label_city")
        val terrain = root.getJSONObject("sources").getJSONObject("terrain-dem")
        assertThat(terrain.getString("type")).isEqualTo("raster-dem")
        assertThat(terrain.getString("encoding")).isEqualTo("terrarium")
        assertThat(terrain.getJSONArray("tiles").getString(0)).contains("terrarium")
    }

    @Test
    fun `satellite keeps labels over imagery with a dark veil`() {
        val json = """
            {"sources":{},"layers":[
              {"id":"water","type":"fill"},
              {"id":"roads","type":"line","paint":{"line-color":"#fff"}},
              {"id":"label_city","type":"symbol","layout":{"text-field":["get","name"]}}
            ]}
        """.trimIndent()

        val root = JSONObject(OpenFreeMapStyle.customize(json, StoryMapStyle.SATELLITE))
        val layers = root.getJSONArray("layers")

        assertThat(layers.getJSONObject(0).getString("type")).isEqualTo("raster")
        assertThat(layers.getJSONObject(0).getString("source")).isEqualTo("satellite")
        assertThat(layers.getJSONObject(1).getString("id")).isEqualTo("satellite-veil")
        assertThat(layers.getJSONObject(2).getString("id")).isEqualTo("label_city")
        assertThat(layers.length()).isEqualTo(3)
        assertThat(
            root.getJSONObject("sources").getJSONObject("satellite")
                .getJSONArray("tiles").getString(0),
        ).contains("arcgisonline")
        assertThat(
            layers.getJSONObject(2).getJSONObject("paint").getString("text-color"),
        ).isEqualTo("#F4F6F7")
    }

    @Test
    fun `adds a paint block when the label layer has none`() {
        val json = """
            {"layers":[{"id":"highway-name-major","type":"symbol","layout":{"text-field":["get","name"]}}]}
        """.trimIndent()

        val layer = JSONObject(OpenFreeMapStyle.customize(json, StoryMapStyle.MEUCOACH_LIGHT))
            .getJSONArray("layers").getJSONObject(0)

        assertThat(layer.getJSONObject("layout").getInt("text-size")).isEqualTo(18)
        assertThat(layer.getJSONObject("paint").getString("text-color")).isEqualTo("#2B3036")
    }

    @Test
    fun `leaves a style without layers unchanged`() {
        val json = """{"version":8}"""

        assertThat(OpenFreeMapStyle.customize(json)).isEqualTo(json)
    }
}
