package com.meucoach.app.story

import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StorySegmentType

/**
 * Camera script for the story: three native MapLibre keyframes (MAP-05).
 *
 * - Opening: wide 30° pitch while the title fades in.
 * - Follow: close 42° chase for the body of the run.
 * - Final: 35° pullback for the closing card.
 *
 * Cuts happen on card/final boundaries, where the opaque panel hides the
 * angle change; the route never jumps because every keyframe carries its own
 * `pixelForLatLng` pixels. The renderer draws a single active snapshot per
 * frame (two only during the 0.6 s opening dissolve), so at most two bitmaps
 * are sampled at once.
 */
enum class StoryKeyframe {
    OPENING,
    FOLLOW,
    FINAL,
}

object StoryMapScript {
    const val OPENING_PITCH_DEGREES = 30f
    const val FOLLOW_PITCH_DEGREES = 42f
    const val FINAL_PITCH_DEGREES = 35f

    /** Opening is shown only while the video fades in; no visible cut later. */
    const val OVERVIEW_UNTIL_SEC = 0f
    const val OVERVIEW_FADE_SEC = 0.6f

    /** Bookends render smaller than the follow shot (MAP-05). */
    const val OPENING_SUPERSAMPLE = 1.75f
    const val FINAL_SUPERSAMPLE = 1.75f

    /** Legacy alias: the overview shot is now the opening keyframe. */
    const val OVERVIEW_PITCH_DEGREES = OPENING_PITCH_DEGREES
    const val OVERVIEW_SUPERSAMPLE = OPENING_SUPERSAMPLE

    fun openingWidth(): Int = (StoryVideoExporter.WIDTH * OPENING_SUPERSAMPLE).toInt()

    fun openingHeight(): Int = (StoryVideoExporter.HEIGHT * OPENING_SUPERSAMPLE).toInt()

    fun finalWidth(): Int = (StoryVideoExporter.WIDTH * FINAL_SUPERSAMPLE).toInt()

    fun finalHeight(): Int = (StoryVideoExporter.HEIGHT * FINAL_SUPERSAMPLE).toInt()

    fun overviewWidth(): Int = openingWidth()

    fun overviewHeight(): Int = openingHeight()

    /** 0 = opening, 1 = follow; smoothstep across the dissolve window. */
    fun followBlend(timeSec: Float): Float {
        val start = OVERVIEW_UNTIL_SEC
        val raw = ((timeSec - start) / OVERVIEW_FADE_SEC).coerceIn(0f, 1f)
        return raw * raw * (3f - 2f * raw)
    }

    /**
     * Active keyframe at [timeSec]: opening before the first card, final from
     * the closing segment on, follow in between. The cuts land on segment
     * boundaries, where the card panel covers the angle change.
     */
    fun keyframeAt(plan: StoryPlan, timeSec: Float): StoryKeyframe {
        val finalStart = plan.segments.lastOrNull { it.type == StorySegmentType.FINAL }?.startSec
            ?: plan.durationSec
        if (timeSec >= finalStart) return StoryKeyframe.FINAL
        val firstCardStart = plan.segments.firstOrNull { it.type == StorySegmentType.CARD }?.startSec
        if (firstCardStart != null && timeSec < firstCardStart) return StoryKeyframe.OPENING
        return StoryKeyframe.FOLLOW
    }
}
