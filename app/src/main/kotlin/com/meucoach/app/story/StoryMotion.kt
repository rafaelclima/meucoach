package com.meucoach.app.story

import kotlin.math.PI
import kotlin.math.sin

/**
 * Motion design for the story: the route eases in and out of each km card and
 * the map gets a slow cinematic push (zoom + tiny rotation + drift).
 *
 * Both values are pure functions of time so the map background and the route
 * overlay can be transformed by the exact same camera without drifting apart.
 * The map is rendered with a supersampled snapshot, so a 1.08–1.13 zoom still
 * reads sharp.
 */
data class StoryCamera(
    val scale: Float,
    val rotationDegrees: Float,
    /** Horizontal drift as a fraction of the frame width. */
    val shiftX: Float,
    /** Vertical drift as a fraction of the frame height. */
    val shiftY: Float,
)

object StoryMotion {
    /** Never below 1: the snapshot must always cover the frame after rotation. */
    const val MIN_SCALE = 1.08f
    const val MAX_EXTRA_SCALE = 0.05f
    const val MAX_CAMERA_SCALE = MIN_SCALE + MAX_EXTRA_SCALE
    const val MAX_ROTATION_DEGREES = 2f
    const val MAX_SHIFT_X = 0.006f
    const val MAX_SHIFT_Y = 0.012f

    fun camera(timeSec: Float, durationSec: Float): StoryCamera {
        val progress = if (durationSec <= 0f) 0f else (timeSec / durationSec).coerceIn(0f, 1f)
        val eased = easeInOut(progress)
        return StoryCamera(
            scale = MIN_SCALE + MAX_EXTRA_SCALE * eased,
            rotationDegrees = sin(progress * PI.toFloat()) * MAX_ROTATION_DEGREES,
            shiftX = sin(progress * PI.toFloat() * 1.6f) * MAX_SHIFT_X,
            shiftY = -MAX_SHIFT_Y * eased,
        )
    }

    /**
     * Smoothstep inside a moving segment: the route slows down near a km card
     * and accelerates between cards, which reads as a speed ramp instead of a
     * constant ticker. Cards themselves stay frozen at their km.
     */
    fun progressAt(plan: StoryPlan, timeSec: Float): Float {
        val segment = segmentAt(plan, timeSec) ?: return 0f
        if (segment.type == StorySegmentType.CARD || segment.endSec <= segment.startSec) {
            return segment.progressEnd
        }
        val fraction = ((timeSec - segment.startSec) / (segment.endSec - segment.startSec)).coerceIn(0f, 1f)
        val eased = easeInOut(fraction)
        return segment.progressStart + (segment.progressEnd - segment.progressStart) * eased
    }

    fun segmentAt(plan: StoryPlan, timeSec: Float): StorySegment? =
        plan.segments.firstOrNull { timeSec >= it.startSec && timeSec <= it.endSec }
            ?: plan.segments.lastOrNull()

    fun easeInOut(fraction: Float): Float {
        val clamped = fraction.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }
}
