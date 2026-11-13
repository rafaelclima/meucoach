package com.meucoach.app.story

import com.meucoach.core.domain.story.RouteData
import com.meucoach.core.domain.story.RoutePoint
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StorySegment
import com.meucoach.core.domain.story.StorySegmentType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Motion design for the story: a smooth route-following flyover.
 *
 * The camera starts with the whole route, dives into the runner's position,
 * pushes in and tilts the map plane, then pulls back for the closing frame.
 * The rotation is a slow, deterministic drift instead of a bearing snapped to
 * the route: it avoids the side-to-side flips of a heading-driven camera.
 * Every value is a pure function of time so the map background and the route
 * overlay receive the exact same camera and never drift apart.
 */
data class StoryCamera(
    val scale: Float,
    val rotationDegrees: Float,
    /** Tilt around the horizontal axis; the main "3D angle" of the flyover. */
    val pitchDegrees: Float,
    /** Tilt around the vertical axis; a small side-to-side roll. */
    val yawDegrees: Float,
    /** Point of the normalized route the camera follows, in [0,1]. */
    val focusX: Float,
    val focusY: Float,
    /** How much the camera is following the runner (0 = route overview, 1 = follow). */
    val follow: Float,
    /** Where the focus lands on the frame, as a fraction of width/height. */
    val targetX: Float,
    val targetY: Float,
)

object StoryMotion {
    const val OVERVIEW_SCALE = 1.20f
    const val FOLLOW_SCALE = 1.65f
    const val CARD_EXTRA_SCALE = 0.04f
    const val MAX_CAMERA_SCALE = FOLLOW_SCALE + CARD_EXTRA_SCALE
    const val OVERVIEW_PITCH_DEGREES = 6f
    const val FOLLOW_PITCH_DEGREES = 18f
    const val CARD_EXTRA_PITCH_DEGREES = 2f

    /** One gentle side-to-side roll during the run, never a hard bearing snap. */
    const val DRIFT_ROTATION_DEGREES = 6f
    const val FOLLOW_YAW_DEGREES = 1.5f

    /** The runner sits above the card, so the map has room to breathe. */
    const val FOLLOW_TARGET_Y = 0.47f

    /**
     * Keep the follow point away from the very edge of the route square. The
     * camera stops chasing the last 15% so the tilted snapshot still covers
     * the frame at the start/end of the run.
     */
    const val FOCUS_MARGIN = 0.35f

    private const val INTRO_SECONDS = 1.6f
    private const val OUTRO_SECONDS = 1.2f
    private const val CARD_FOCUS_RAMP_SEC = 1.4f

    fun camera(timeSec: Float, plan: StoryPlan): StoryCamera {
        val progress = progressAt(plan, timeSec)
        val follow = followIntensity(plan, timeSec)
        val card = cardFocus(plan, timeSec)
        val point = routePointAt(plan.route, progress)
        return StoryCamera(
            scale = OVERVIEW_SCALE +
                (FOLLOW_SCALE - OVERVIEW_SCALE) * follow +
                CARD_EXTRA_SCALE * card,
            rotationDegrees = sin(progress * PI * 2f).toFloat() * DRIFT_ROTATION_DEGREES * follow,
            pitchDegrees = OVERVIEW_PITCH_DEGREES +
                (FOLLOW_PITCH_DEGREES - OVERVIEW_PITCH_DEGREES) * follow +
                CARD_EXTRA_PITCH_DEGREES * card,
            yawDegrees = sin(progress * PI * 3f).toFloat() * FOLLOW_YAW_DEGREES * follow,
            focusX = point.x.coerceIn(FOCUS_MARGIN, 1f - FOCUS_MARGIN),
            focusY = point.y.coerceIn(FOCUS_MARGIN, 1f - FOCUS_MARGIN),
            follow = follow,
            targetX = 0.5f,
            targetY = 0.5f + (FOLLOW_TARGET_Y - 0.5f) * follow,
        )
    }

    /**
     * 0 while the whole route is on screen, 1 while the camera follows the
     * runner. The intro dives in over [INTRO_SECONDS]; the closing segment
     * pulls back inside its first [OUTRO_SECONDS], so the final card gets a
     * smooth overview instead of a hard cut.
     */
    fun followIntensity(plan: StoryPlan, timeSec: Float): Float {
        val finalStart = plan.segments.lastOrNull { it.type == StorySegmentType.FINAL }?.startSec
            ?: plan.durationSec
        val intro = easeInOut((timeSec / INTRO_SECONDS).coerceIn(0f, 1f))
        val outro = if (timeSec <= finalStart) {
            1f
        } else {
            1f - easeInOut(((timeSec - finalStart) / OUTRO_SECONDS).coerceIn(0f, 1f))
        }
        return min(intro, outro)
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

    /** Point of the normalized route at [progress], interpolated by path length. */
    fun routePointAt(route: RouteData, progress: Float): RoutePoint {
        val points = route.points
        if (points.isEmpty()) return RoutePoint(0.5f, 0.5f)
        if (points.size == 1) return points.first()
        val clamped = progress.coerceIn(0f, 1f)
        val lengths = FloatArray(points.size - 1) { index ->
            hypot(points[index + 1].x - points[index].x, points[index + 1].y - points[index].y)
        }
        val total = lengths.sum()
        if (total <= EPSILON) return points.first()
        var remaining = clamped * total
        lengths.forEachIndexed { index, length ->
            if (remaining <= length || index == lengths.lastIndex) {
                val fraction = if (length <= EPSILON) 0f else (remaining / length).coerceIn(0f, 1f)
                val start = points[index]
                val end = points[index + 1]
                return RoutePoint(
                    x = start.x + (end.x - start.x) * fraction,
                    y = start.y + (end.y - start.y) * fraction,
                )
            }
            remaining -= length
        }
        return points.last()
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

    private fun distanceToSegment(timeSec: Float, segment: StorySegment): Float = when {
        timeSec < segment.startSec -> segment.startSec - timeSec
        timeSec > segment.endSec -> timeSec - segment.endSec
        else -> 0f
    }

    private const val EPSILON = 1e-8f
}
