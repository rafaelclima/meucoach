package com.meucoach.app.story.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.meucoach.app.story.StoryLayout
import com.meucoach.app.story.StoryMapScript
import com.meucoach.core.domain.story.StoryMapStyle
import kotlin.math.max

/**
 * Everything behind and around the route: scrims, gradients, attribution,
 * vignette and the opening fade. No route or card pixels here (VIS-07).
 */
internal class StoryBackdropRenderer(private val scope: StoryDrawScope) {
    private var vignettePaint: Paint? = null
    private var vignetteWidth = 0
    private var vignetteHeight = 0

    fun drawScrims(canvas: Canvas, width: Int, height: Int, scale: Float, dimmed: Boolean) {
        val palette = scope.palette
        if (dimmed) {
            val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.background
                alpha = 80
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        }
        val colors = intArrayOf(
            scope.withAlpha(palette.background, if (dimmed) 240 else 235),
            scope.withAlpha(palette.background, if (dimmed) 225 else 205),
            scope.withAlpha(palette.background, 0),
        )
        val shader = LinearGradient(
            0f,
            0f,
            0f,
            320f * scale,
            colors,
            floatArrayOf(0f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            320f * scale,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader },
        )
        drawGradient(
            canvas = canvas,
            width = width,
            // Start where the card ends so the attribution sits on the dark
            // footer instead of on a brighter band below the panel.
            top = StoryLayout.cardBottom(height.toFloat()),
            bottom = height.toFloat(),
            edgeAlpha = if (dimmed) 210 else 200,
            fadeOut = false,
        )
    }

    private fun drawGradient(canvas: Canvas, width: Int, top: Float, bottom: Float, edgeAlpha: Int, fadeOut: Boolean) {
        val palette = scope.palette
        val edge = scope.withAlpha(palette.background, edgeAlpha)
        val colors = if (fadeOut) {
            intArrayOf(edge, scope.withAlpha(palette.background, 0))
        } else {
            intArrayOf(scope.withAlpha(palette.background, 0), edge)
        }
        val shader = LinearGradient(0f, top, 0f, bottom, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(
            0f,
            top,
            width.toFloat(),
            bottom,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader },
        )
    }

    @Suppress("DEPRECATION")
    fun drawAttribution(canvas: Canvas, width: Int, height: Int, scale: Float) {
        // OSM tile policy requires attribution on every rendered map. It stays
        // above the Instagram footer and gets a small backdrop so the tiny text
        // survives the light basemap and the bottom gradient.
        val text = when (scope.palette.mapStyle) {
            StoryMapStyle.TERRAIN -> "© OpenStreetMap · OpenFreeMap · Elevation Mapzen"

            // Satellite is out of the MVP (ADR 0040 §2.4): legacy jobs render
            // the dark style, so the imagery credit never applies.
            StoryMapStyle.SATELLITE,
            StoryMapStyle.CLASSIC,
            StoryMapStyle.MEUCOACH_DARK,
            StoryMapStyle.MEUCOACH_LIGHT,
            -> "© OpenStreetMap · OpenMapTiles · OpenFreeMap"
        }
        val attribution = scope.paint(scope.palette.textSecondary, 13f * scale)
        attribution.alpha = 220
        val left = StoryLayout.attributionLeft(width.toFloat())
        val baseline = StoryLayout.attributionBaseline(height.toFloat())
        val textWidth = attribution.measureText(text)
        val paddingH = 8f * scale
        val paddingV = 5f * scale
        val backdrop = RectF(
            left - paddingH,
            baseline - attribution.textSize - paddingV,
            left + textWidth + paddingH,
            baseline + paddingV,
        )
        canvas.drawRoundRect(
            backdrop,
            8f * scale,
            8f * scale,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = scope.palette.background
                alpha = 175
            },
        )
        canvas.drawText(text, left, baseline, attribution)
    }

    fun drawVignette(canvas: Canvas, width: Int, height: Int) {
        if (vignettePaint == null || vignetteWidth != width || vignetteHeight != height) {
            vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.shader = RadialGradient(
                    width / 2f,
                    height / 2f,
                    max(width, height) * 0.75f,
                    intArrayOf(0x00000000, 0x4D000000),
                    floatArrayOf(0.58f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            vignetteWidth = width
            vignetteHeight = height
        }
        vignettePaint?.let { canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), it) }
    }

    /**
     * The opening fades from the background color while the opening dissolves
     * into the follow shot. Hiding the angle change under the fade removes the
     * "quick cut" at the start without needing a continuous 3D camera.
     */
    fun drawOpeningFade(canvas: Canvas, timeSec: Float, width: Int, height: Int) {
        val progress = (timeSec / StoryMapScript.OVERVIEW_FADE_SEC).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        val alpha = ((1f - eased) * 255f).toInt().coerceIn(0, 255)
        if (alpha <= 0) return
        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = scope.withAlpha(scope.palette.background, alpha)
            },
        )
    }
}
