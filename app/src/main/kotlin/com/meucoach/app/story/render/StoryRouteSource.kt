package com.meucoach.app.story.render

import com.meucoach.app.story.StoryMapCameraCalculator
import com.meucoach.app.story.StoryMapProjection
import com.meucoach.app.story.StoryMapScript
import com.meucoach.app.story.StoryMapSnapshot
import com.meucoach.core.domain.story.BoundingBox
import com.meucoach.core.domain.story.StoryGeoPoint
import com.meucoach.core.domain.story.StoryPlan

/**
 * Route vertices in frame coordinates. A real snapshot supplies the native
 * `pixelForLatLng` pixels; the pure projection is only used by the map-less
 * preview/test fallback (MAP-02/MAP-03).
 */
internal class StoryRouteSource(
    val size: Int,
    val hasMap: Boolean,
    val start: FloatArray,
    val end: FloatArray,
    private val point: (Int) -> FloatArray,
) {
    fun pointAt(index: Int): FloatArray = point(index)

    companion object {
        fun of(
            plan: StoryPlan,
            snapshot: StoryMapSnapshot?,
            frame: StoryCameraFrame,
            hasMap: Boolean,
        ): StoryRouteSource {
            val square = frame.square
            val routePixels = snapshot?.routePixels?.takeIf { it.size >= 2 }
            // The pure port is only reachable when the renderer has no snapshot;
            // a production snapshot never falls back to it (MAP-03).
            val fallbackProjection = if (snapshot == null) {
                fallbackProjection(plan, frame.width, frame.height)
            } else {
                null
            }
            val scaleX = if (routePixels != null) frame.width.toFloat() / snapshot.bitmap.width else 1f
            val scaleY = if (routePixels != null) frame.height.toFloat() / snapshot.bitmap.height else 1f
            fun normalizedX(x: Float): Float = if (!hasMap) {
                square.left + x * square.side
            } else {
                square.centerX + (x - 0.5f) * square.side
            }
            fun normalizedY(y: Float): Float = if (!hasMap) {
                square.top + y * square.side
            } else {
                square.centerY + (y - 0.5f) * square.side
            }
            fun pointAt(index: Int): FloatArray = when {
                routePixels != null -> {
                    val point = routePixels[index]
                    floatArrayOf(point.x * scaleX, point.y * scaleY)
                }

                fallbackProjection != null -> {
                    val point = plan.geoPoints[index]
                    fallbackProjection.project(point.latitude, point.longitude)
                }

                else -> {
                    val point = plan.route.points[index]
                    floatArrayOf(normalizedX(point.x), normalizedY(point.y))
                }
            }
            val size = when {
                routePixels != null -> routePixels.size
                fallbackProjection != null -> plan.geoPoints.size
                else -> plan.route.points.size
            }
            require(size > 0) { "A rota do story precisa de pelo menos um ponto." }
            val start = snapshot?.startPixel?.let { floatArrayOf(it.x * scaleX, it.y * scaleY) } ?: pointAt(0)
            val end = snapshot?.endPixel?.let { floatArrayOf(it.x * scaleX, it.y * scaleY) } ?: pointAt(size - 1)
            return StoryRouteSource(
                size = size,
                hasMap = hasMap,
                start = start,
                end = end,
                point = ::pointAt,
            )
        }

        private fun fallbackProjection(plan: StoryPlan, width: Int, height: Int): StoryMapProjection? {
            val bounds: BoundingBox = plan.boundingBox ?: return null
            val geoPoints: List<StoryGeoPoint> = plan.geoPoints
            if (geoPoints.size < 2) return null
            return StoryMapProjection(
                StoryMapCameraCalculator.calculateForStory(
                    bounds,
                    width,
                    height,
                    pitchDegrees = StoryMapScript.FOLLOW_PITCH_DEGREES,
                ),
                width,
                height,
            )
        }
    }
}
