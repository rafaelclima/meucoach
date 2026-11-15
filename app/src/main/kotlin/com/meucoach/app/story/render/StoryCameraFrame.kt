package com.meucoach.app.story.render

import android.graphics.Matrix
import com.meucoach.app.story.StoryLayout

/**
 * Camera-space frame shared by the map and the route: both are drawn under
 * the same [cameraMatrix], so the route never drifts from the snapshot.
 */
internal data class StoryCameraFrame(
    val cameraMatrix: Matrix,
    val square: StoryLayout.RouteSquare,
    val overlayScale: Float,
    val width: Int,
    val height: Int,
)
