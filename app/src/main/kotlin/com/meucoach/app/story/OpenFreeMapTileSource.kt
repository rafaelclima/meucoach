package com.meucoach.app.story

import com.meucoach.core.domain.story.StoryMapStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MVP tile source (MAP-09): OpenFreeMap vector tiles (OSM data) with the
 * `MeuCoach Dark`/`Light` restyle applied on top of `liberty`. No API key and
 * no per-view limits (openfreemap.org).
 */
@Singleton
class OpenFreeMapTileSource @Inject constructor() : StoryTileSource {
    override val name: String = "OpenFreeMap"

    override val styleUrl: String = OpenFreeMapStyle.STYLE_URL

    override fun customize(json: String, style: StoryMapStyle): String = OpenFreeMapStyle.customize(json, style)
}
