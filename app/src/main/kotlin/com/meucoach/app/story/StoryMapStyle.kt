package com.meucoach.app.story

/** Visual treatment of the map snapshot used by the story. */
enum class StoryMapStyle {
    /** Clean vector basemap (OpenFreeMap liberty). */
    CLASSIC,

    /** Satellite imagery with vector labels (Esri World Imagery). */
    SATELLITE,

    /** Adds a hillshade layer fed by public elevation tiles (Mapzen/AWS). */
    TERRAIN,
}
