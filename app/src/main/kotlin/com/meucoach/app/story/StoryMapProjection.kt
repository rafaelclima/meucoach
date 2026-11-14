package com.meucoach.app.story

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Exact screen position of one geographic point under a snapshot camera.
 * [StoryMapProjection] delegates here; [StoryMapCameraCalculator] uses it only
 * to fit the bounds while choosing the camera. Route pixels in the production
 * video come from `MapSnapshot.pixelForLatLng`, not from this pure port
 * (MAP-02/MAP-03).
 */
internal object StoryMapFit {
    val FOV_RADIANS = 2.0 * kotlin.math.atan(1.0 / 3.0)

    private const val TILE_SIZE = 512.0
    private const val MAX_MERCATOR_LAT = 85.05112878

    fun project(camera: StoryMapCamera, width: Int, height: Int, latitude: Double, longitude: Double): FloatArray {
        val matrix = mercatorToPixelMatrix(camera, width, height)
        val worldSize = TILE_SIZE * 2.0.pow(camera.zoom)
        val x = mercatorX(longitude) * worldSize
        val y = mercatorY(latitude) * worldSize
        val clip = matrix.transform(x, y, 0.0, 1.0)
        val w = if (clip[3] == 0.0) 1.0 else clip[3]
        val screenX = (clip[0] / w + 1.0) * 0.5 * width
        val screenY = (1.0 - clip[1] / w) * 0.5 * height
        return floatArrayOf(screenX.toFloat(), screenY.toFloat())
    }

    private fun mercatorToPixelMatrix(camera: StoryMapCamera, width: Int, height: Int): Mat4 {
        val worldSize = TILE_SIZE * 2.0.pow(camera.zoom)
        val centerX = mercatorX(camera.longitude) * worldSize
        val centerY = mercatorY(camera.latitude) * worldSize
        val cameraToCenterDistance = 0.5 * height / tan(FOV_RADIANS / 2.0)
        val pitch = Math.toRadians(camera.pitchDegrees.toDouble())
        val bearing = Math.toRadians(camera.bearingDegrees.toDouble())

        var m = Mat4.perspective(
            fovy = FOV_RADIANS,
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

    private fun mercatorX(longitude: Double): Double = (longitude + 180.0) / 360.0

    private fun mercatorY(latitude: Double): Double {
        val clamped = latitude.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val radians = Math.toRadians(clamped)
        return 0.5 - ln(tan(PI / 4.0 + radians / 2.0)) / (2.0 * PI)
    }
}

/**
 * Pure Kotlin port of the MapLibre snapshot projection. Kept as a documented
 * fallback and for tests; the video route uses the native `pixelForLatLng`
 * values carried by [StoryMapSnapshot].
 */
class StoryMapProjection(private val camera: StoryMapCamera, private val width: Int, private val height: Int) {
    /** Screen position (top-left origin, pixels) of a geographic point. */
    fun project(latitude: Double, longitude: Double): FloatArray =
        StoryMapFit.project(camera, width, height, latitude, longitude)
}

/** Minimal column-major 4×4 matrix (gl-matrix semantics). */
internal class Mat4(
    private val values: DoubleArray = DoubleArray(16).also {
        it[0] = 1.0
        it[5] = 1.0
        it[10] = 1.0
        it[15] = 1.0
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
