package com.meucoach.app.story

import com.meucoach.core.domain.story.StoryMapStyle

/**
 * Tile source seam for the story map (MAP-09, ADR 0040 §2.4).
 *
 * The MVP renders OpenFreeMap vector tiles with an autoral restyle
 * ([OpenFreeMapTileSource]). A future Protomaps/PMTiles source implements this
 * interface — a style document URL plus a JSON restyle — and is bound in
 * `StoryModule`; the snapshotter, the frame renderer and the disk cache keep
 * working untouched because they only see this contract.
 *
 * The contract is deliberately narrow: the snapshotter needs a style URL, and
 * the only per-style work is rewriting the downloaded JSON before the
 * snapshot. Anything source-specific (API keys, PMTiles archives, custom
 * relief sets) stays inside the implementation.
 */
interface StoryTileSource {
    /** Human label for logs; never drawn in the video. */
    val name: String

    /** Vector style document rendered by the MapLibre snapshotter. */
    val styleUrl: String

    /**
     * Restyles the downloaded style JSON into the autoral treatment for
     * [style]: hides clutter, enlarges labels, applies the dark/light paint.
     * Pure and unit-tested; never touches the network.
     */
    fun customize(json: String, style: StoryMapStyle): String
}
