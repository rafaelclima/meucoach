package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import kotlin.random.Random

/**
 * Film grain applied by the exporter after [StoryFrameRenderer] draws the frame.
 *
 * It lives outside the renderer on purpose: the grain is temporal (it shifts
 * every frame) and random, so including it in the Roborazzi goldens would bloat
 * the PNGs. The preview stays clean; the exported video gets the texture.
 */
object StoryFilm {
    private const val NOISE_SIZE = 160
    private const val GRAIN_ALPHA = 26
    private const val GRAIN_COVERAGE = 22

    private val noise: Bitmap by lazy {
        val random = Random(7)
        // Sparse black/white specks read as grain and keep the tile tiny.
        val pixels = IntArray(NOISE_SIZE * NOISE_SIZE) {
            if (random.nextInt(100) < GRAIN_COVERAGE) {
                val value = if (random.nextBoolean()) 255 else 0
                (GRAIN_ALPHA shl 24) or (value shl 16) or (value shl 8) or value
            } else {
                0
            }
        }
        Bitmap.createBitmap(pixels, NOISE_SIZE, NOISE_SIZE, Bitmap.Config.ARGB_8888)
    }

    private val shader: BitmapShader by lazy {
        BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val matrix = Matrix()

    fun drawGrain(canvas: Canvas, width: Int, height: Int, timeSec: Float) {
        matrix.setTranslate((timeSec * 43f) % NOISE_SIZE, (timeSec * 31f) % NOISE_SIZE)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
