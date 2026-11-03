package com.meucoach.app.story

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Motion design for the story: the route eases in and out of each km card and
 * the map gets a slow cinematic camera move (zoom, rotation, pitch and yaw).
 *
 * Both values are pure functions of time so the map background and the route
 * overlay can be transformed by the exact same camera without drifting apart.
 * The map is rendered with a supersampled snapshot, so a 1.08–1.16 zoom still
 * reads sharp.
 */
data class StoryCamera(
    val scale: Float,
    val rotationDegrees: Float,
    /** Tilt around the horizontal axis; gives the map a subtle 3D angle. */
    val pitchDegrees: Float,
    /** Tilt around the vertical axis; adds a slow side-to-side angle change. */
    val yawDegrees: Float,
    /** Horizontal drift as a fraction of the frame width. */
    val shiftX: Float,
    /** Vertical drift as a fraction of the frame height. */
    val shiftY: Float,
)

object StoryMotion {
    /** Never below 1: the snapshot must always cover the frame after rotation. */
    const val MIN_SCALE = 1.08f
    const val MAX_EXTRA_SCALE = 0.04f

    /** Extra push while a km card is on screen. */
    const val CARD_EXTRA_SCALE = 0.04f
    const val MAX_CAMERA_SCALE = MIN_SCALE + MAX_EXTRA_SCALE + CARD_EXTRA_SCALE
    const val MAX_ROTATION_DEGREES = 3f
    const val MAX_PITCH_DEGREES = 4f
    const val MAX_YAW_DEGREES = 2f
    const val MAX_SHIFT_X = 0.007f
    const val MAX_SHIFT_Y = 0.012f
    private const val CARD_FOCUS_RAMP_SEC = 1.4f

    /** Camera without a timeline, useful for tests and the cover frame. */
    fun camera(timeSec: Float, durationSec: Float): StoryCamera = camera(timeSec, durationSec, cardFocus = 0f)

    /**
     * Camera synced with the story timeline: the map pushes in while a km card
     * is being read and relaxes between cards, so the video breathes with the
     * splits instead of holding a constant zoom.
     */
    fun camera(timeSec: Float, plan: StoryPlan): StoryCamera =
        camera(timeSec, plan.durationSec, cardFocus = cardFocus(plan, timeSec))

    private fun camera(timeSec: Float, durationSec: Float, cardFocus: Float): StoryCamera {
        val progress = if (durationSec <= 0f) 0f else (timeSec / durationSec).coerceIn(0f, 1f)
        val eased = easeInOut(progress)
        val wave = sin(progress * PI).toFloat()
        return StoryCamera(
            scale = MIN_SCALE + MAX_EXTRA_SCALE * eased + CARD_EXTRA_SCALE * cardFocus,
            rotationDegrees = wave * MAX_ROTATION_DEGREES,
            pitchDegrees = wave * MAX_PITCH_DEGREES,
            yawDegrees = sin(progress * PI * 2f).toFloat() * MAX_YAW_DEGREES,
            shiftX = sin(progress * PI * 1.6f).toFloat() * MAX_SHIFT_X,
            shiftY = -MAX_SHIFT_Y * eased,
        )
    }

    /**
     * 1 while the camera is on a km card, fading to 0 [CARD_FOCUS_RAMP_SEC]
     * after the card ends. Cards shorter than the ramp still get a smooth
     * entry/exit instead of a hard zoom cut.
     */
    fun cardFocus(plan: StoryPlan, timeSec: Float): Float {
        val card = plan.segments
            .filter { it.type == StorySegmentType.CARD && it.endSec > it.startSec }
            .minByOrNull { distanceToSegment(timeSec, it) }
            ?: return 0f
        val center = (card.startSec + card.endSec) / 2f
        val half = (card.endSec - card.startSec) / 2f + CARD_FOCUS_RAMP_SEC
        val distance = abs(timeSec - center)
        if (distance >= half) return 0f
        return (0.5f + 0.5f * cos(PI * distance / half)).toFloat()
    }

    private fun distanceToSegment(timeSec: Float, segment: StorySegment): Float = when {
        timeSec < segment.startSec -> segment.startSec - timeSec
        timeSec > segment.endSec -> timeSec - segment.endSec
        else -> 0f
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
