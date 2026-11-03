package com.meucoach.app.story

import android.graphics.Matrix
import kotlin.math.sin

/**
 * Subtle projective tilt for the map + route layer.
 *
 * The snapshot is a top-down map, so a real camera pitch would require
 * re-rendering MapLibre for every frame. Instead this maps the whole layer (map
 * and route together) to a shallow trapezoid, which keeps the line on the
 * streets while giving the video a 3D angle change. The base camera scale
 * (1.08+) is what keeps the corners covered after the projection.
 */
object StoryPerspective {
    private const val PITCH_STRENGTH = 0.35f
    private const val YAW_STRENGTH = 0.25f

    fun matrix(width: Float, height: Float, pitchDegrees: Float, yawDegrees: Float): Matrix? {
        if (width <= 0f || height <= 0f) return null
        if (pitchDegrees == 0f && yawDegrees == 0f) return null

        val pitch = width * sin(Math.toRadians(pitchDegrees.toDouble())).toFloat() * PITCH_STRENGTH
        val yaw = height * sin(Math.toRadians(yawDegrees.toDouble())).toFloat() * YAW_STRENGTH
        val source = floatArrayOf(
            0f,
            0f,
            width,
            0f,
            width,
            height,
            0f,
            height,
        )
        // Top edge narrows and bottom edge widens (pitch); the left side
        // compresses and the right side stretches (yaw).
        val target = floatArrayOf(
            pitch,
            yaw,
            width - pitch,
            -yaw,
            width + pitch,
            height + yaw,
            -pitch,
            height - yaw,
        )
        val matrix = Matrix()
        return if (matrix.setPolyToPoly(source, 0, target, 0, 4)) matrix else null
    }
}
