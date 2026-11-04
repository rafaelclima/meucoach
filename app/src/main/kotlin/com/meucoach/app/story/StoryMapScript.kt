package com.meucoach.app.story

/**
 * Camera script for the story: a wide, higher-pitch overview that dissolves
 * into the closer follow shot. The second snapshot is rendered by the worker,
 * so the video can change real 3D pitch without paying for it in the preview.
 */
object StoryMapScript {
    const val OVERVIEW_PITCH_DEGREES = 22f
    const val FOLLOW_PITCH_DEGREES = 42f

    /** Overview is shown until this time, then dissolves into the follow shot. */
    const val OVERVIEW_UNTIL_SEC = 2.2f
    const val OVERVIEW_FADE_SEC = 0.25f

    /** The overview is wider, so it can be rendered smaller than the follow map. */
    const val OVERVIEW_SUPERSAMPLE = 1.3f

    fun overviewWidth(): Int = (StoryVideoExporter.WIDTH * OVERVIEW_SUPERSAMPLE).toInt()
    fun overviewHeight(): Int = (StoryVideoExporter.HEIGHT * OVERVIEW_SUPERSAMPLE).toInt()

    /** 0 = overview, 1 = follow; smoothstep across the dissolve window. */
    fun followBlend(timeSec: Float): Float {
        val start = OVERVIEW_UNTIL_SEC
        val raw = ((timeSec - start) / OVERVIEW_FADE_SEC).coerceIn(0f, 1f)
        return raw * raw * (3f - 2f * raw)
    }
}
