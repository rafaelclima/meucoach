package com.meucoach.app.story

import com.meucoach.app.ui.activitydetail.RouteData
import com.meucoach.app.ui.activitydetail.RoutePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Motion design for the story: a route-following flyover.
 *
 * The camera starts with the whole route, dives into the runner's position,
 * keeps the direction of travel pointing up, tilts the map plane and pushes in
 * while a km card is on screen. At the closing segment it pulls back to the
 * overview. Every value is a pure function of time so the map background and
 * the route overlay receive the exact same camera and never drift apart.
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
    const val OVERVIEW_SCALE = 1.18f
    const val FOLLOW_SCALE = 1.72f
    const val CARD_EXTRA_SCALE = 0.06f
    const val MAX_CAMERA_SCALE = FOLLOW_SCALE + CARD_EXTRA_SCALE
    const val OVERVIEW_PITCH_DEGREES = 8f
    const val FOLLOW_PITCH_DEGREES = 32f
    const val CARD_EXTRA_PITCH_DEGREES = 3f
    const val FOLLOW_YAW_DEGREES = 2.5f

    /**
     * The map snapshot is a fixed top-down rectangle, so an unbounded bearing
     * would rotate its corners into the frame. 22° is enough to feel like a
     * camera turn while the 1.72× follow scale still covers the 9:16 frame.
     */
    const val MAX_ROTATION_DEGREES = 22f

    /** Keeps the route slightly diagonal instead of a perfectly vertical line. */
    const val BEARING_OFFSET_DEGREES = -8f

    /** The runner sits above the card, so the map has room to breathe. */
    const val FOLLOW_TARGET_Y = 0.43f

    private const val INTRO_SECONDS = 1.8f
    private const val OUTRO_SECONDS = 1.2f
    private const val HEADING_WINDOW = 0.03f
    private const val CARD_FOCUS_RAMP_SEC = 1.4f

    fun camera(timeSec: Float, plan: StoryPlan): StoryCamera {
        val progress = progressAt(plan, timeSec)
        val follow = followIntensity(plan, timeSec)
        val card = cardFocus(plan, timeSec)
        val point = routePointAt(plan.route, progress)
        val heading = headingDegrees(plan.route, progress)
        val turn = normalizeDegrees(heading + BEARING_OFFSET_DEGREES)
            .coerceIn(-MAX_ROTATION_DEGREES, MAX_ROTATION_DEGREES)
        return StoryCamera(
            scale = OVERVIEW_SCALE +
                (FOLLOW_SCALE - OVERVIEW_SCALE) * follow +
                CARD_EXTRA_SCALE * card,
            rotationDegrees = turn * follow,
            pitchDegrees = OVERVIEW_PITCH_DEGREES +
                (FOLLOW_PITCH_DEGREES - OVERVIEW_PITCH_DEGREES) * follow +
                CARD_EXTRA_PITCH_DEGREES * card,
            yawDegrees = sin(progress * PI * 2f).toFloat() * FOLLOW_YAW_DEGREES * follow,
            focusX = point.x,
            focusY = point.y,
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
     * Rotation that makes the current direction of travel point up, with a
     * small diagonal offset. The heading is taken over a window before and
     * after the runner, so GPS jitter and sharp turns do not spin the map.
     */
    fun headingDegrees(route: RouteData, progress: Float): Float {
        val behind = routePointAt(route, (progress - HEADING_WINDOW).coerceIn(0f, 1f))
        val ahead = routePointAt(route, (progress + HEADING_WINDOW).coerceIn(0f, 1f))
        var dx = ahead.x - behind.x
        var dy = ahead.y - behind.y
        if (dx * dx + dy * dy <= EPSILON) {
            val start = routePointAt(route, progress)
            val end = routePointAt(route, (progress + HEADING_WINDOW).coerceIn(0f, 1f))
            dx = end.x - start.x
            dy = end.y - start.y
        }
        if (dx * dx + dy * dy <= EPSILON) return 0f
        val direction = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return normalizeDegrees(-90f - direction)
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

    fun normalizeDegrees(value: Float): Float = ((value % 360f) + 540f) % 360f - 180f

    private fun distanceToSegment(timeSec: Float, segment: StorySegment): Float = when {
        timeSec < segment.startSec -> segment.startSec - timeSec
        timeSec > segment.endSec -> timeSec - segment.endSec
        else -> 0f
    }

    private const val EPSILON = 1e-8f
}
