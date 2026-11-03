package com.meucoach.app.story

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
