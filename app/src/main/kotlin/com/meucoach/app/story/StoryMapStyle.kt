package com.meucoach.app.story

/** Visual treatment of the map snapshot used by the story. */
enum class StoryMapStyle {
    /** Clean vector basemap (OpenFreeMap liberty). */
    CLASSIC,

    /** Adds a hillshade layer fed by public elevation tiles (Mapzen/AWS). */
    TERRAIN,
}
