package com.meucoach.app.story

import com.meucoach.core.domain.story.BoundingBox
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

/**
 * Camera for the MapLibre snapshot that sits behind the story route.
 *
 * The route is normalized into a centered square inside the 9:16 frame. This
 * camera maps the same [BoundingBox] to that square, so the animated line lands
 * on the streets it was recorded on (ADR 0015 §2.5). Pure math on purpose: the
 * alignment can be unit-tested without loading MapLibre native code.
 */
data class StoryMapCamera(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val pitchDegrees: Float = 0f,
    val bearingDegrees: Float = 0f,
)

object StoryMapCameraCalculator {
    /** MapLibre renders a 512 px world tile grid at zoom 0. */
    // The map bounds fill the route square; the cinematic push in
    // StoryMotion is what brings the map closer during the video. The layout
    // already reserves room for that push, so no extra zoom is applied here.
    const val MAP_ZOOM_FACTOR = 1f

    private const val TILE_SIZE_PX = 512.0
    private const val MIN_SPAN = 1e-9
    private const val MAX_MERCATOR_LAT = 85.05112878

    data class RouteSquare(val left: Float, val top: Float, val side: Float) {
        val centerX: Float get() = left + side / 2f
        val centerY: Float get() = top + side / 2f
    }

    /** Same geometry [StoryFrameRenderer.drawRoute] uses to draw the route. */
    fun routeSquare(width: Float, height: Float): RouteSquare {
        val square = StoryLayout.routeSquare(width, height)
        return RouteSquare(left = square.left, top = square.top, side = square.side)
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

    /**
     * Same framing as [calculate], but on a pitched/bearing camera. The bounds
     * are fitted iteratively against the real projection so the tilted snapshot
     * still contains the whole route.
     */
    fun calculateForStory(
        bounds: BoundingBox,
        width: Int = 720,
        height: Int = 1280,
        pitchDegrees: Float = 38f,
        bearingDegrees: Float = 0f,
    ): StoryMapCamera {
        if (pitchDegrees == 0f && bearingDegrees == 0f) return calculate(bounds, width, height)
        val square = routeSquare(width.toFloat(), height.toFloat())
        val base = calculate(bounds, width, height)
        var targetX = mercatorX(base.longitude)
        var targetY = mercatorY(base.latitude)
        var zoom = base.zoom
        val corners = listOf(
            bounds.minLat to bounds.minLon,
            bounds.minLat to bounds.maxLon,
            bounds.maxLat to bounds.minLon,
            bounds.maxLat to bounds.maxLon,
        )
        repeat(8) {
            val camera = StoryMapCamera(
                latitude = inverseMercatorY(targetY),
                longitude = inverseMercatorX(targetX),
                zoom = zoom,
                pitchDegrees = pitchDegrees,
                bearingDegrees = bearingDegrees,
            )
            val points = corners.map { StoryMapFit.project(camera, width, height, it.first, it.second) }
            val minX = points.minOf { it[0] }
            val maxX = points.maxOf { it[0] }
            val minY = points.minOf { it[1] }
            val maxY = points.maxOf { it[1] }
            val spanX = (maxX - minX).coerceAtLeast(1f)
            val spanY = (maxY - minY).coerceAtLeast(1f)
            val factor = minOf(square.side / spanX, square.side / spanY)
            zoom += ln(factor.toDouble()) / ln(2.0)
            val worldSize = TILE_SIZE_PX * 2.0.pow(zoom)
            targetX -= (square.centerX - (minX + maxX) / 2f) / worldSize
            targetY -= (square.centerY - (minY + maxY) / 2f) / worldSize
        }
        return StoryMapCamera(
            latitude = inverseMercatorY(targetY),
            longitude = inverseMercatorX(targetX),
            zoom = zoom,
            pitchDegrees = pitchDegrees,
            bearingDegrees = bearingDegrees,
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
