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
    private const val PITCH_VERTICAL_STRENGTH = 0.12f
    private const val YAW_STRENGTH = 0.25f

    /** Extra safety scale so the rotated/tilted snapshot always covers the frame. */
    const val COVERAGE_SCALE = 1.15f

    /**
     * Full map/route transform: perspective, then the camera translation,
     * rotation, scale and focus. Kept in one place so the renderer and the
     * coverage test use the exact same matrix.
     */
    fun cameraMatrix(
        camera: StoryCamera,
        square: StoryLayout.RouteSquare,
        width: Float,
        height: Float,
        nativeTilt: Boolean = false,
    ): Matrix {
        val routeFocusX = square.left + camera.focusX * square.side
        val routeFocusY = square.top + camera.focusY * square.side
        // The focus easing is quartic: the camera zooms in before it starts
        // chasing the runner, which keeps the map edges covered during the dive.
        val focusBlend = camera.follow * camera.follow * camera.follow * camera.follow
        val focusX = square.centerX + (routeFocusX - square.centerX) * focusBlend
        val focusY = square.centerY + (routeFocusY - square.centerY) * focusBlend
        val matrix = Matrix()
        if (!nativeTilt) {
            matrixFor(width, height, camera.pitchDegrees, camera.yawDegrees)?.let { matrix.preConcat(it) }
        }
        matrix.preTranslate(camera.targetX * width, camera.targetY * height)
        matrix.preRotate(camera.rotationDegrees)
        matrix.preScale(camera.scale * COVERAGE_SCALE, camera.scale * COVERAGE_SCALE)
        matrix.preTranslate(-focusX, -focusY)
        return matrix
    }

    fun matrix(width: Float, height: Float, pitchDegrees: Float, yawDegrees: Float): Matrix? =
        matrixFor(width, height, pitchDegrees, yawDegrees)

    private fun matrixFor(width: Float, height: Float, pitchDegrees: Float, yawDegrees: Float): Matrix? {
        if (width <= 0f || height <= 0f) return null
        if (pitchDegrees == 0f && yawDegrees == 0f) return null

        val pitch = width * sin(Math.toRadians(pitchDegrees.toDouble())).toFloat() * PITCH_STRENGTH
        val horizon = height * sin(Math.toRadians(pitchDegrees.toDouble())).toFloat() * PITCH_VERTICAL_STRENGTH
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
        // Top edge narrows and drops toward the horizon (pitch); the left side
        // compresses and the right side stretches (yaw).
        val target = floatArrayOf(
            pitch,
            horizon + yaw,
            width - pitch,
            horizon - yaw,
            width + pitch,
            height + yaw,
            -pitch,
            height - yaw,
        )
        val matrix = Matrix()
        return if (matrix.setPolyToPoly(source, 0, target, 0, 4)) matrix else null
    }
}
