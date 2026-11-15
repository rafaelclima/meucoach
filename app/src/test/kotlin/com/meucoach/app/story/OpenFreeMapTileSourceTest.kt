package com.meucoach.app.story

import com.meucoach.core.domain.story.StoryMapStyle
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test

/**
 * The Protomaps seam (MAP-09): the snapshotter depends on [StoryTileSource],
 * and the MVP binds the OpenFreeMap implementation without changing the flow.
 */
class OpenFreeMapTileSourceTest {
    private val source = OpenFreeMapTileSource()

    @Test
    fun `exposes the openfreemap liberty style document`() {
        assertThat(source.name).isEqualTo("OpenFreeMap")
        assertThat(source.styleUrl).isEqualTo("https://tiles.openfreemap.org/styles/liberty")
        assertThat(source.styleUrl).isEqualTo(OpenFreeMapStyle.STYLE_URL)
    }

    @Test
    fun `delegates the autoral restyle`() {
        val json = """
            {"layers":[
              {"id":"label_city","type":"symbol","layout":{"text-field":["get","name"]}}
            ]}
        """.trimIndent()

        val city = JSONObject(source.customize(json, StoryMapStyle.MEUCOACH_DARK))
            .getJSONArray("layers").getJSONObject(0)

        assertThat(city.getJSONObject("layout").getInt("text-size")).isEqualTo(26)
    }
}
