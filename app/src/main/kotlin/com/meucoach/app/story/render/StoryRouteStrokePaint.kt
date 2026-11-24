package com.meucoach.app.story.render

import android.graphics.DashPathEffect
import android.graphics.Paint
import com.meucoach.core.domain.story.StoryRouteStroke

/**
 * Applies a [StoryRouteStroke] to a route paint. Periods are expressed in
 * design pixels and scaled with the frame, so the pattern keeps the same
 * optical rhythm in the 540 px preview and the 1080 px export.
 *
 * [phase] is the distance already travelled along the whole route when the
 * painted path is a segment (a traveled or zone run). Passing it keeps dashes
 * continuous instead of restarting at every zone boundary.
 */
internal fun Paint.applyRouteStroke(stroke: StoryRouteStroke, scale: Float, phase: Float = 0f): Paint = apply {
    pathEffect = when (stroke) {
        StoryRouteStroke.SOLID, StoryRouteStroke.NEON -> null

        StoryRouteStroke.DASHED -> DashPathEffect(
            floatArrayOf(DASH_ON * scale, DASH_OFF * scale),
            phase,
        )

        StoryRouteStroke.DOTTED -> DashPathEffect(
            floatArrayOf(DOT_ON * scale, DOT_OFF * scale),
            phase,
        )
    }
}

/** Phase that continues the dash pattern at [distance] along the full route. */
internal fun routeStrokePhase(stroke: StoryRouteStroke, scale: Float, distance: Float): Float {
    val period = when (stroke) {
        StoryRouteStroke.SOLID, StoryRouteStroke.NEON -> return 0f
        StoryRouteStroke.DASHED -> (DASH_ON + DASH_OFF) * scale
        StoryRouteStroke.DOTTED -> (DOT_ON + DOT_OFF) * scale
    }
    if (period <= 0f) return 0f
    val offset = distance % period
    return if (offset < 0f) offset + period else offset
}

private const val DASH_ON = 26f
private const val DASH_OFF = 18f

/**
 * A near-zero dash with a round cap becomes a circle whose diameter is the
 * stroke width; the long gap is what makes it a dot instead of a dash.
 */
private const val DOT_ON = 0.1f
private const val DOT_OFF = 18f
