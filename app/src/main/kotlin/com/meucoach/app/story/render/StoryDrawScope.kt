package com.meucoach.app.story.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.meucoach.app.story.StoryLayout
import com.meucoach.app.story.StoryPalette
import com.meucoach.app.story.storyTextPaint
import com.meucoach.core.domain.story.ResolvedMetric
import java.util.Locale

/** Locale of every story string: decimal commas, not points. */
internal val storyPtBr: Locale = Locale.forLanguageTag("pt-BR")

/**
 * Paints, panels and metric rows shared by the header/card/final renderers.
 * Holding the palette here (instead of threading it through every draw call)
 * is what keeps each renderer file under the size budget (VIS-07).
 */
internal class StoryDrawScope(
    val palette: StoryPalette,
    private val typeface: Typeface,
    val logoBitmap: Bitmap? = null,
) {
    fun paint(color: Int, textSize: Float, bold: Boolean = false): Paint =
        storyTextPaint(color, textSize, typeface, bold)

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    fun drawPanel(canvas: Canvas, card: RectF, scale: Float) {
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

    fun drawMetric(
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

    fun drawMetricRow(
        canvas: Canvas,
        metrics: List<ResolvedMetric>,
        card: RectF,
        rowTop: Float,
        scale: Float,
        labelPaint: Paint,
        valuePaint: Paint,
    ) {
        val column = (card.width() - 56f * scale) / 3f
        metrics.forEachIndexed { index, metric ->
            drawMetric(
                canvas,
                card.left + 28f * scale + column * index,
                rowTop,
                metric.label,
                metric.value,
                labelPaint,
                valuePaint,
            )
        }
    }

    fun cardBounds(width: Int, height: Int): RectF {
        val margin = StoryLayout.margin(width.toFloat())
        return RectF(
            margin,
            StoryLayout.cardTop(height.toFloat()),
            width - margin,
            StoryLayout.cardBottom(height.toFloat()),
        )
    }

    /** Roomier closing card for 5 metrics; the bottom stays on the safe line. */
    fun finalCardBounds(width: Int, height: Int): RectF {
        val margin = StoryLayout.margin(width.toFloat())
        return RectF(
            margin,
            StoryLayout.finalCardTop(height.toFloat()),
            width - margin,
            StoryLayout.cardBottom(height.toFloat()),
        )
    }
}
