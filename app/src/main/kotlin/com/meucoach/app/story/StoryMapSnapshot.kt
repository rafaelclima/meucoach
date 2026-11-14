package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.PointF
import com.meucoach.core.domain.story.StoryGeoPoint

/**
 * Bitmap rendered by MapLibre plus the route projected by the same native
 * camera. [routePixels] are in bitmap pixels: the renderer scales them to the
 * video frame instead of reprojecting the geography with the Kotlin port.
 *
 * Production snapshots come from [MapBackgroundRenderer], which calls
 * `MapSnapshot.pixelForLatLng`; [fromFallback] exists only for tests and for a
 * documented emergency fallback (MAP-02/MAP-03).
 */
data class StoryMapSnapshot(
    val bitmap: Bitmap,
    val routePixels: List<PointF>,
    val startPixel: PointF? = routePixels.firstOrNull(),
    val endPixel: PointF? = routePixels.lastOrNull(),
) {
    init {
        require(bitmap.width > 0 && bitmap.height > 0) { "O snapshot precisa ter dimensões positivas." }
        require(routePixels.all { it.x.isFinite() && it.y.isFinite() }) {
            "Os pixels da rota precisam ser finitos."
        }
    }

    companion object {
        /**
         * Emergency fallback: projects the route with the pure Kotlin port.
         * Never used by the production renderer, which requires the exact
         * pixels returned by MapLibre.
         */
        fun fromFallback(bitmap: Bitmap, camera: StoryMapCamera, geoPoints: List<StoryGeoPoint>): StoryMapSnapshot {
            val projection = StoryMapProjection(camera, bitmap.width, bitmap.height)
            val points = geoPoints.map { point ->
                val projected = projection.project(point.latitude, point.longitude)
                PointF(projected[0], projected[1])
            }
            return StoryMapSnapshot(bitmap = bitmap, routePixels = points)
        }
    }
}
