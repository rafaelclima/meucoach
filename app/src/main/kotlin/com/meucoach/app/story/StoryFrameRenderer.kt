package com.meucoach.app.story

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import com.meucoach.app.ui.activities.formatDuration
import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")

/**
 * Draws one 9:16 frame with a route, progress marker and the current km card.
 * Kept independent of MediaCodec so the same drawing can feed a preview later.
 */
class StoryFrameRenderer(private val palette: StoryPalette, private val typeface: Typeface) {
    fun render(plan: StoryPlan, timeSec: Float, width: Int, height: Int): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        render(Canvas(bitmap), plan, timeSec, width, height)
        return bitmap
    }

    fun render(canvas: Canvas, plan: StoryPlan, timeSec: Float, width: Int, height: Int) {
        val scale = width / 720f
        canvas.drawColor(palette.background)
        drawHeader(canvas, plan, width, height, scale)
        drawRoute(canvas, plan, timeSec, width, height, scale)
        val segment = segmentAt(plan, timeSec)
        if (segment?.type == StorySegmentType.CARD && segment.split != null) {
            drawCard(canvas, segment.split, width, height, scale)
        }
        drawFooter(canvas, width, height, scale)
    }

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
        val area = RectF(56f * scale, 300f * scale, width - 56f * scale, height * 0.62f)
        val side = minOf(area.width(), area.height())
        val left = area.centerX() - side / 2f
        val top = area.centerY() - side / 2f
        val routePath = Path()
        plan.route.points.forEachIndexed { index, point ->
            val x = left + point.x * side
            val y = top + point.y * side
            if (index == 0) routePath.moveTo(x, y) else routePath.lineTo(x, y)
        }
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = palette.border
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
        canvas.drawCircle(
            left + plan.route.start.x * side,
            top + plan.route.start.y * side,
            6f * scale,
            paint(palette.textPrimary, 0f),
        )
        canvas.drawCircle(
            left + plan.route.end.x * side,
            top + plan.route.end.y * side,
            6f * scale,
            paint(palette.coral, 0f),
        )
    }

    private fun drawCard(canvas: Canvas, split: StorySplit, width: Int, height: Int, scale: Float) {
        val card = RectF(56f * scale, height * 0.66f, width - 56f * scale, height * 0.86f)
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

        val kmPaint = paint(palette.accent, 52f * scale, bold = true)
        canvas.drawText("KM ${split.km}", card.left + 28f * scale, card.top + 64f * scale, kmPaint)
        val label = paint(palette.textTertiary, 16f * scale, bold = true)
        val value = paint(palette.textPrimary, 30f * scale, bold = true)
        val rowTop = card.top + 92f * scale
        val column = (card.width() - 56f * scale) / 3f
        drawMetric(canvas, card.left + 28f * scale, rowTop, "PACE", "${split.pace.format()} /km", label, value)
        drawMetric(
            canvas,
            card.left + 28f * scale + column,
            rowTop,
            "FC",
            split.heartRate?.let { "$it bpm" } ?: "—",
            label,
            value,
        )
        drawMetric(
            canvas,
            card.left + 28f * scale + column * 2,
            rowTop,
            "KCAL",
            split.calories?.toString() ?: "—",
            label,
            value,
        )
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

    private fun drawFooter(canvas: Canvas, width: Int, height: Int, scale: Float) {
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
