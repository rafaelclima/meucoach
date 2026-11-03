package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.meucoach.app.ui.activities.formatDuration
import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")

/**
 * Draws one 9:16 frame with a route, progress marker and the current km card.
 * Kept independent of MediaCodec so the same drawing can feed a preview later.
 *
 * When [logoBitmap] is provided it replaces the text footer; without it the
 * frame keeps the plain "Gerado no aparelho · MeuCoach" caption (ADR 0015 §2.8).
 */
class StoryFrameRenderer(
    private val palette: StoryPalette,
    private val typeface: Typeface,
    private val logoBitmap: Bitmap? = null,
    private val mapBackground: Bitmap? = null,
) {
    private val mapPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        // OpenFreeMap liberty is already close to the brand; keep a faint tone.
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.85f) })
    }

    fun render(plan: StoryPlan, timeSec: Float, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        render(Canvas(bitmap), plan, timeSec, width, height)
        return bitmap
    }

    fun render(canvas: Canvas, plan: StoryPlan, timeSec: Float, width: Int, height: Int) {
        val scale = width / 720f
        val segment = segmentAt(plan, timeSec)
        val isFinal = segment?.type == StorySegmentType.FINAL
        drawBackground(canvas, width, height, isFinal)
        drawHeader(canvas, plan, width, height, scale)
        drawRoute(canvas, plan, timeSec, width, height, scale)
        when {
            segment?.type == StorySegmentType.CARD && segment.split != null ->
                drawCard(canvas, segment.split, width, height, scale)

            isFinal ->
                drawFinal(canvas, plan, width, height, scale)
        }
        drawFooter(canvas, width, height, scale)
        if (mapBackground != null) drawMapAttribution(canvas, height, scale)
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int, dimmed: Boolean) {
        canvas.drawColor(palette.background)
        val map = mapBackground ?: return
        canvas.drawBitmap(map, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), mapPaint)
        // The basemap stays vivid; only the closing frame dims it to focus the summary.
        if (dimmed) {
            val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.background
                alpha = 80
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        }
        val scale = width / 720f
        drawTopGradient(canvas, width, scale, dimmed)
        drawGradient(
            canvas = canvas,
            width = width,
            top = height * 0.86f,
            bottom = height.toFloat(),
            edgeAlpha = if (dimmed) 210 else 200,
            fadeOut = false,
        )
    }

    private fun drawTopGradient(canvas: Canvas, width: Int, scale: Float, dimmed: Boolean) {
        // A darker header band keeps the overline and units readable without dimming the whole map.
        val colors = intArrayOf(
            withAlpha(palette.background, if (dimmed) 240 else 235),
            withAlpha(palette.background, if (dimmed) 225 else 205),
            withAlpha(palette.background, 0),
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
    }

    private fun drawGradient(canvas: Canvas, width: Int, top: Float, bottom: Float, edgeAlpha: Int, fadeOut: Boolean) {
        val edge = withAlpha(palette.background, edgeAlpha)
        val colors = if (fadeOut) {
            intArrayOf(edge, withAlpha(palette.background, 0))
        } else {
            intArrayOf(withAlpha(palette.background, 0), edge)
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

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun segmentAt(plan: StoryPlan, timeSec: Float): StorySegment? =
        plan.segments.firstOrNull { timeSec >= it.startSec && timeSec <= it.endSec }
            ?: plan.segments.lastOrNull()

    private fun progressAt(plan: StoryPlan, timeSec: Float): Float {
        val segment = segmentAt(plan, timeSec) ?: return 0f
        if (segment.type == StorySegmentType.CARD || segment.endSec <= segment.startSec) {
            return segment.progressEnd
        }
        val fraction = ((timeSec - segment.startSec) / (segment.endSec - segment.startSec)).coerceIn(0f, 1f)
        return segment.progressStart + (segment.progressEnd - segment.progressStart) * fraction
    }

    private fun drawHeader(canvas: Canvas, plan: StoryPlan, width: Int, height: Int, scale: Float) {
        val overline = paint(palette.textTertiary, 18f * scale, bold = true)
        val title = paint(palette.textPrimary, 34f * scale, bold = true)
        val metric = paint(palette.textPrimary, 46f * scale, bold = true)
        val unit = paint(palette.textSecondary, 20f * scale)

        canvas.drawText("MEUCOACH", 56f * scale, 96f * scale, overline)
        canvas.drawText("Corrida", 56f * scale, 148f * scale, title)
        val distance = String.format(PT_BR, "%.2f", plan.distanceKm)
        canvas.drawText(distance, 56f * scale, 214f * scale, metric)
        canvas.drawText("km", 56f * scale + metric.measureText(distance) + 8f * scale, 214f * scale, unit)
        val duration = formatDuration(plan.durationTotalSec)
        canvas.drawText(duration, width - 56f * scale - metric.measureText(duration), 214f * scale, metric)
        canvas.drawText(
            "tempo",
            width - 56f * scale - unit.measureText("tempo"),
            240f * scale,
            unit,
        )
    }

    private fun drawRoute(canvas: Canvas, plan: StoryPlan, timeSec: Float, width: Int, height: Int, scale: Float) {
        val square = StoryMapCameraCalculator.routeSquare(width.toFloat(), height.toFloat())
        val side = square.side
        // The map snapshot uses the same zoom factor, keeping the line on the streets.
        val zoom = if (mapBackground != null) StoryMapCameraCalculator.MAP_ZOOM_FACTOR else 1f
        fun projectX(x: Float): Float = if (mapBackground == null) {
            square.left + x * side
        } else {
            square.centerX + (x - 0.5f) * side * zoom
        }
        fun projectY(y: Float): Float = if (mapBackground == null) {
            square.top + y * side
        } else {
            square.centerY + (y - 0.5f) * side * zoom
        }
        val routePath = Path()
        plan.route.points.forEachIndexed { index, point ->
            val x = projectX(point.x)
            val y = projectY(point.y)
            if (index == 0) routePath.moveTo(x, y) else routePath.lineTo(x, y)
        }
        if (mapBackground != null) {
            val casing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 19f * scale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = palette.mapCasing
                alpha = 210
            }
            canvas.drawPath(routePath, casing)
        }
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = if (mapBackground != null) palette.mapBaseRoute else palette.border
            alpha = if (mapBackground != null) 240 else 255
        }
        canvas.drawPath(routePath, base)

        val measure = PathMeasure(routePath, false)
        val traveled = Path()
        val progress = progressAt(plan, timeSec)
        measure.getSegment(0f, measure.length * progress, traveled, true)
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 11f * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = palette.accent
        }
        canvas.drawPath(traveled, accent)

        val position = FloatArray(2)
        measure.getPosTan(measure.length * progress, position, null)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            alpha = 60
        }
        canvas.drawCircle(position[0], position[1], 24f * scale, glow)
        canvas.drawCircle(position[0], position[1], 9f * scale, paint(palette.accent, 0f))
        val startX = projectX(plan.route.start.x)
        val startY = projectY(plan.route.start.y)
        val endX = projectX(plan.route.end.x)
        val endY = projectY(plan.route.end.y)
        if (mapBackground != null) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.mapCasing
                alpha = 210
            }
            canvas.drawCircle(startX, startY, 9f * scale, ring)
            canvas.drawCircle(endX, endY, 9f * scale, ring)
        }
        canvas.drawCircle(startX, startY, 6f * scale, paint(palette.textPrimary, 0f))
        canvas.drawCircle(endX, endY, 6f * scale, paint(palette.coral, 0f))
    }

    private fun drawCard(canvas: Canvas, split: StorySplit, width: Int, height: Int, scale: Float) {
        val card = cardBounds(width, height, scale)
        drawPanel(canvas, card, scale)

        val kmPaint = paint(palette.accent, 52f * scale, bold = true)
        canvas.drawText("KM ${split.km}", card.left + 28f * scale, card.top + 64f * scale, kmPaint)
        val label = paint(palette.textTertiary, 16f * scale, bold = true)
        val value = paint(palette.textPrimary, 30f * scale, bold = true)
        val rowTop = card.top + 92f * scale
        val column = (card.width() - 56f * scale) / 3f
        drawMetric(canvas, card.left + 28f * scale, rowTop, "RITMO", "${split.pace.format()} /km", label, value)
        drawMetric(
            canvas,
            card.left + 28f * scale + column,
            rowTop,
            "FC MÉDIA",
            split.heartRate?.let { "$it bpm" } ?: "—",
            label,
            value,
        )
        drawMetric(
            canvas,
            card.left + 28f * scale + column * 2,
            rowTop,
            "CALORIAS",
            split.calories?.toString() ?: "—",
            label,
            value,
        )
        // Legenda simples para o usuário entender rapidamente.
        val legend = paint(palette.textTertiary, 11f * scale)
        canvas.drawText(
            "Ritmo = tempo por km · FC = frequência cardíaca · Calorias = energia gasta",
            card.left + 28f * scale,
            card.bottom - 16f * scale,
            legend,
        )
    }

    /**
     * Closing frame (ADR 0015 §2.4). Distance and duration stay in the header so
     * the cut from the animated route to the summary has no layout jump; the
     * panel adds the remaining numbers and the footer carries the brand.
     */
    private fun drawFinal(canvas: Canvas, plan: StoryPlan, width: Int, height: Int, scale: Float) {
        val card = cardBounds(width, height, scale)
        drawPanel(canvas, card, scale)

        val title = paint(palette.accent, 20f * scale, bold = true)
        canvas.drawText("TREINO CONCLUÍDO", card.left + 28f * scale, card.top + 52f * scale, title)
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        canvas.drawRect(
            card.left + 28f * scale,
            card.top + 66f * scale,
            card.left + 68f * scale,
            card.top + 70f * scale,
            rule,
        )

        val label = paint(palette.textTertiary, 16f * scale, bold = true)
        val value = paint(palette.textPrimary, 30f * scale, bold = true)
        val rowTop = card.top + 120f * scale
        val column = (card.width() - 56f * scale) / 3f
        drawMetric(
            canvas,
            card.left + 28f * scale,
            rowTop,
            "RITMO MÉDIO",
            plan.averagePace?.let { "${it.format()} /km" } ?: "—",
            label,
            value,
        )
        drawMetric(
            canvas,
            card.left + 28f * scale + column,
            rowTop,
            "FC MÉDIA",
            plan.averageHeartRate?.let { "$it bpm" } ?: "—",
            label,
            value,
        )
        drawMetric(
            canvas,
            card.left + 28f * scale + column * 2,
            rowTop,
            "CALORIAS",
            plan.calories?.toString() ?: "—",
            label,
            value,
        )
    }

    private fun cardBounds(width: Int, height: Int, scale: Float): RectF =
        RectF(56f * scale, height * 0.66f, width - 56f * scale, height * 0.86f)

    private fun drawPanel(canvas: Canvas, card: RectF, scale: Float) {
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.surface
            alpha = 245
        }
        canvas.drawRoundRect(card, 28f * scale, 28f * scale, background)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * scale
            color = palette.border
        }
        canvas.drawRoundRect(card, 28f * scale, 28f * scale, border)
    }

    private fun drawMetric(
        canvas: Canvas,
        x: Float,
        y: Float,
        label: String,
        value: String,
        labelPaint: Paint,
        valuePaint: Paint,
    ) {
        canvas.drawText(label, x, y, labelPaint)
        canvas.drawText(value, x, y + valuePaint.textSize * 1.2f, valuePaint)
    }

    private fun drawMapAttribution(canvas: Canvas, height: Int, scale: Float) {
        // OSM tile policy requires attribution on every rendered map.
        val attribution = paint(palette.textSecondary, 13f * scale)
        attribution.alpha = 210
        canvas.drawText("© OpenStreetMap · OpenMapTiles · OpenFreeMap", 56f * scale, height - 16f * scale, attribution)
    }

    private fun drawFooter(canvas: Canvas, width: Int, height: Int, scale: Float) {
        if (logoBitmap == null) {
            drawTextFooter(canvas, width, height, scale)
            return
        }
        // The wordmark is wide (≈3.8:1); scale by height and keep the aspect ratio.
        val logoHeight = 56f * scale
        val logoWidth = logoHeight * logoBitmap.width / logoBitmap.height
        val destination = RectF(
            (width - logoWidth) / 2f,
            height - 40f * scale - logoHeight,
            (width + logoWidth) / 2f,
            height - 40f * scale,
        )
        canvas.drawBitmap(logoBitmap, null, destination, null)
    }

    private fun drawTextFooter(canvas: Canvas, width: Int, height: Int, scale: Float) {
        val footer = paint(palette.textTertiary, 16f * scale)
        canvas.drawText(
            "Gerado no aparelho · MeuCoach",
            56f * scale,
            height - 42f * scale,
            footer,
        )
        val accentLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        canvas.drawRect(
            width - 56f * scale - 42f * scale,
            height - 58f * scale,
            width - 56f * scale,
            height - 55f * scale,
            accentLine,
        )
    }

    private fun paint(color: Int, textSize: Float, bold: Boolean = false): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = textSize
        this.typeface = if (bold) {
            Typeface.create(typeface, Typeface.BOLD)
        } else {
            typeface
        }
    }
}
