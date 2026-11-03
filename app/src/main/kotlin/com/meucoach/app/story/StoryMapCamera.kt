package com.meucoach.app.story

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

/**
 * Camera for the MapLibre snapshot that sits behind the story route.
 *
 * The route is normalized into a centered square inside the 9:16 frame. This
 * camera maps the same [BoundingBox] to that square, so the animated line lands
 * on the streets it was recorded on (ADR 0015 §2.5). Pure math on purpose: the
 * alignment can be unit-tested without loading MapLibre native code.
 */
data class StoryMapCamera(val latitude: Double, val longitude: Double, val zoom: Double)

object StoryMapCameraCalculator {
    /** MapLibre renders a 512 px world tile grid at zoom 0. */
    // Extra zoom over the route square: a closer, less distant map (ADR 0015).
    const val MAP_ZOOM_FACTOR = 1.2f

    private const val TILE_SIZE_PX = 512.0
    private const val MIN_SPAN = 1e-9
    private const val MAX_MERCATOR_LAT = 85.05112878
    private const val ROUTE_MARGIN_DP = 56f
    private const val ROUTE_TOP_DP = 300f
    private const val ROUTE_BOTTOM_FRACTION = 0.62f
    private const val FRAME_WIDTH = 720f

    data class RouteSquare(val left: Float, val top: Float, val side: Float) {
        val centerX: Float get() = left + side / 2f
        val centerY: Float get() = top + side / 2f
    }

    /** Same geometry [StoryFrameRenderer.drawRoute] uses to draw the route. */
    fun routeSquare(width: Float, height: Float): RouteSquare {
        val margin = ROUTE_MARGIN_DP * (width / FRAME_WIDTH)
        val top = ROUTE_TOP_DP * (width / FRAME_WIDTH)
        val bottom = height * ROUTE_BOTTOM_FRACTION
        val side = minOf(width - 2f * margin, bottom - top)
        val centerY = (top + bottom) / 2f
        return RouteSquare(
            left = width / 2f - side / 2f,
            top = centerY - side / 2f,
            side = side,
        )
    }

    fun calculate(bounds: BoundingBox, width: Int = 720, height: Int = 1280): StoryMapCamera {
        val minX = mercatorX(bounds.minLon)
        val maxX = mercatorX(bounds.maxLon)
        val minY = mercatorY(bounds.maxLat)
        val maxY = mercatorY(bounds.minLat)
        val spanX = max(maxX - minX, MIN_SPAN)
        val spanY = max(maxY - minY, MIN_SPAN)
        val square = routeSquare(width.toFloat(), height.toFloat())
        val scale = square.side * MAP_ZOOM_FACTOR / max(spanX, spanY)
        val centerX = (minX + maxX) / 2.0
        val centerY = (minY + maxY) / 2.0
        // The camera target renders at the frame center; offset it so the route
        // square (above the frame center) receives the bounds instead.
        val targetX = centerX - (square.centerX - width / 2.0) / scale
        val targetY = centerY - (square.centerY - height / 2.0) / scale
        return StoryMapCamera(
            latitude = inverseMercatorY(targetY),
            longitude = inverseMercatorX(targetX),
            zoom = ln(scale / TILE_SIZE_PX) / ln(2.0),
        )
    }

    private fun mercatorX(longitude: Double): Double = (longitude + 180.0) / 360.0

    private fun mercatorY(latitude: Double): Double {
        val clamped = latitude.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val radians = Math.toRadians(clamped)
        return 0.5 - ln(tan(PI / 4.0 + radians / 2.0)) / (2.0 * PI)
    }

    private fun inverseMercatorX(x: Double): Double = x * 360.0 - 180.0

    private fun inverseMercatorY(y: Double): Double = Math.toDegrees(2.0 * atan(exp(PI * (1.0 - 2.0 * y))) - PI / 2.0)
}
