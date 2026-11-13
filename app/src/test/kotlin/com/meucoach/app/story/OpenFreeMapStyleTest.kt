package com.meucoach.app.story

import com.meucoach.core.domain.story.StoryMapStyle
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test

class OpenFreeMapStyleTest {
    @Test
    fun `enlarges and brightens place labels`() {
        val json = """
            {"layers":[
              {"id":"label_city","type":"symbol","layout":{"text-field":["get","name"]},"paint":{"text-color":"#000"}},
              {"id":"place_not_in_liberty","type":"symbol","layout":{"text-field":["get","name"]}},
              {"id":"roads","type":"line","paint":{"line-color":"#fff"}}
            ]}
        """.trimIndent()

        val layers = JSONObject(OpenFreeMapStyle.customize(json)).getJSONArray("layers")

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
            {"layers":[{"id":"highway-name-minor","type":"symbol","layout":{"text-field":["get","name"]}}]}
        """.trimIndent()

        val layer = JSONObject(OpenFreeMapStyle.customize(json)).getJSONArray("layers").getJSONObject(0)

        assertThat(layer.getJSONObject("layout").getInt("text-size")).isEqualTo(18)
        assertThat(layer.getJSONObject("paint").getString("text-color")).isEqualTo("#2B3036")
    }

    @Test
    fun `leaves a style without layers unchanged`() {
        val json = """{"version":8}"""

        assertThat(OpenFreeMapStyle.customize(json)).isEqualTo(json)
    }
}
