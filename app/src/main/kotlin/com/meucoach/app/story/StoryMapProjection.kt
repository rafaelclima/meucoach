package com.meucoach.app.story

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

internal val STORY_MAP_FOV_RADIANS = 2.0 * kotlin.math.atan(1.0 / 3.0)

class StoryMapProjection(private val camera: StoryMapCamera, private val width: Int, private val height: Int) {
    private val matrix = mercatorToPixelMatrix(camera, width, height)
    private val worldSize = TILE_SIZE * 2.0.pow(camera.zoom)

    /** Screen position (top-left origin, pixels) of a geographic point. */
    fun project(latitude: Double, longitude: Double): FloatArray {
        val x = mercatorX(longitude) * worldSize
        val y = mercatorY(latitude) * worldSize
        val clip = matrix.transform(x, y, 0.0, 1.0)
        val w = if (clip[3] == 0.0) 1.0 else clip[3]
        val screenX = (clip[0] / w + 1.0) * 0.5 * width
        val screenY = (1.0 - clip[1] / w) * 0.5 * height
        return floatArrayOf(screenX.toFloat(), screenY.toFloat())
    }

    private companion object {
        const val TILE_SIZE = 512.0
    }
}

/**
 * Mercator → pixel matrix, matching mapbox-gl/maplibre-gl `_calcMatrices`.
 * `x`/`y` are world pixels (mercator × worldSize).
 */
private fun mercatorToPixelMatrix(camera: StoryMapCamera, width: Int, height: Int): Mat4 {
    val worldSize = 512.0 * 2.0.pow(camera.zoom)
    val centerX = mercatorX(camera.longitude) * worldSize
    val centerY = mercatorY(camera.latitude) * worldSize
    val cameraToCenterDistance = 0.5 * height / tan(STORY_MAP_FOV_RADIANS / 2.0)
    val pitch = Math.toRadians(camera.pitchDegrees.toDouble())
    val bearing = Math.toRadians(camera.bearingDegrees.toDouble())

    var m = Mat4.perspective(
        fovy = STORY_MAP_FOV_RADIANS,
        aspect = width.toDouble() / height,
        near = 1.0,
        far = 1_000_000.0,
    )
    // No center offset (edge insets are not used by the snapshot).
    m = m * Mat4.scale(1.0, -1.0, 1.0)
    m = m * Mat4.translate(0.0, 0.0, -cameraToCenterDistance)
    m = m * Mat4.rotateX(pitch)
    m = m * Mat4.rotateZ(-bearing)
    m = m * Mat4.translate(-centerX, -centerY, 0.0)
    return m
}

internal fun mercatorX(longitude: Double): Double = (longitude + 180.0) / 360.0

internal fun mercatorY(latitude: Double): Double {
    val clamped = latitude.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
    val radians = Math.toRadians(clamped)
    return 0.5 - ln(tan(PI / 4.0 + radians / 2.0)) / (2.0 * PI)
}

internal fun inverseMercatorX(x: Double): Double = x * 360.0 - 180.0

internal fun inverseMercatorY(y: Double): Double =
    Math.toDegrees(2.0 * kotlin.math.atan(kotlin.math.exp(PI * (1.0 - 2.0 * y))) - PI / 2.0)

private const val MAX_MERCATOR_LAT = 85.05112878

/** Minimal column-major 4×4 matrix (gl-matrix semantics). */
internal class Mat4(
    private val values: DoubleArray = DoubleArray(16).also {
        it[0] = 1.0
        it[5] = 1.0
        it[10] = 1.0
        it[15] =
            1.0
    },
) {
    operator fun times(other: Mat4): Mat4 {
        val out = DoubleArray(16)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0.0
                for (k in 0 until 4) {
                    sum += values[k * 4 + row] * other.values[column * 4 + k]
                }
                out[column * 4 + row] = sum
            }
        }
        return Mat4(out)
    }

    fun transform(x: Double, y: Double, z: Double, w: Double): DoubleArray {
        val out = DoubleArray(4)
        for (row in 0 until 4) {
            out[row] = values[row] * x + values[4 + row] * y + values[8 + row] * z + values[12 + row] * w
        }
        return out
    }

    companion object {
        fun translate(x: Double, y: Double, z: Double): Mat4 = Mat4(
            doubleArrayOf(
                1.0, 0.0, 0.0, 0.0,
                0.0, 1.0, 0.0, 0.0,
                0.0, 0.0, 1.0, 0.0,
                x, y, z, 1.0,
            ),
        )

        fun scale(x: Double, y: Double, z: Double): Mat4 = Mat4(
            doubleArrayOf(
                x, 0.0, 0.0, 0.0,
                0.0, y, 0.0, 0.0,
                0.0, 0.0, z, 0.0,
                0.0, 0.0, 0.0, 1.0,
            ),
        )

        fun rotateX(radians: Double): Mat4 {
            val c = cos(radians)
            val s = sin(radians)
            return Mat4(
                doubleArrayOf(
                    1.0, 0.0, 0.0, 0.0,
                    0.0, c, s, 0.0,
                    0.0, -s, c, 0.0,
                    0.0, 0.0, 0.0, 1.0,
                ),
            )
        }

        fun rotateZ(radians: Double): Mat4 {
            val c = cos(radians)
            val s = sin(radians)
            return Mat4(
                doubleArrayOf(
                    c, s, 0.0, 0.0,
                    -s, c, 0.0, 0.0,
                    0.0, 0.0, 1.0, 0.0,
                    0.0, 0.0, 0.0, 1.0,
                ),
            )
        }

        fun perspective(fovy: Double, aspect: Double, near: Double, far: Double): Mat4 {
            val f = 1.0 / tan(fovy / 2.0)
            val nf = 1.0 / (near - far)
            return Mat4(
                doubleArrayOf(
                    f / aspect, 0.0, 0.0, 0.0,
                    0.0, f, 0.0, 0.0,
                    0.0, 0.0, (far + near) * nf, -1.0,
                    0.0, 0.0, 2.0 * far * near * nf, 0.0,
                ),
            )
        }
    }
}
