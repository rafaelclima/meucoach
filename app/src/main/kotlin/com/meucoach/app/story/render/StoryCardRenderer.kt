package com.meucoach.app.story.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.meucoach.core.domain.story.ResolvedMetric
import com.meucoach.core.domain.story.StoryCardSpec
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRenderSpec
import com.meucoach.core.domain.story.StorySegment

/**
 * Km card with the resolved slots: pace plus up to two per-km extras, and
 * the fastest badge only when the recipe selected FASTEST_KM. The renderer
 * draws whatever the spec resolved; it never checks raw activity fields.
 */
internal class StoryCardRenderer(private val scope: StoryDrawScope) {
    fun drawCard(canvas: Canvas, spec: StoryRenderSpec, segment: StorySegment, width: Int, height: Int, scale: Float) {
        val split = segment.split ?: return
        val palette = scope.palette
        val card = scope.cardBounds(width, height)
        scope.drawPanel(canvas, card, scale)
        val cardSpec = spec.cards.firstOrNull { it.split.km == split.km }
        val metrics = cardSpec?.metrics ?: listOf(
            ResolvedMetric(
                id = "AVG_PACE",
                label = "RITMO",
                value = String.format(
                    storyPtBr,
                    "%d:%02d /km",
                    split.pace.secondsPerKm / 60,
                    split.pace.secondsPerKm % 60,
                ),
            ),
        )

        val kmPaint = scope.paint(palette.accent, 52f * scale, bold = true)
        canvas.drawText("KM ${split.km}", card.left + 28f * scale, card.top + 64f * scale, kmPaint)
        if (cardSpec?.showFastestBadge == true) {
            drawFastestStamp(canvas, card, scale)
        }

        val label = scope.paint(palette.textTertiary, 16f * scale, bold = true)
        val value = scope.paint(palette.textPrimary, 30f * scale, bold = true)
        val rowTop = card.top + 92f * scale
        val column = (card.width() - 56f * scale) / metrics.size.coerceAtLeast(1)
        metrics.forEachIndexed { index, metric ->
            scope.drawMetric(
                canvas,
                card.left + 28f * scale + column * index,
                rowTop,
                metric.label,
                metric.value,
                label,
                value,
            )
        }
        drawSplitBar(canvas, spec.plan, split.km, card, scale)
    }

    /**
     * The fastest kilometre badge is controlled by the recipe: without the
     * FASTEST_KM extra the spec never sets [StoryCardSpec.showFastestBadge].
     */
    private fun drawFastestStamp(canvas: Canvas, card: RectF, scale: Float) {
        val scope = this.scope
        val text = "KM MAIS RÁPIDO"
        val stampPaint = scope.paint(scope.palette.onAccent, 13f * scale, bold = true)
        val paddingH = 10f * scale
        val paddingV = 5f * scale
        val height = stampPaint.textSize + paddingV * 2f
        val width = stampPaint.measureText(text) + paddingH * 2f
        val right = card.right - 28f * scale
        val top = card.top + 26f * scale
        val rect = RectF(right - width, top, right, top + height)
        canvas.drawRoundRect(
            rect,
            height / 2f,
            height / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scope.palette.accent },
        )
        val baseline = rect.centerY() - (stampPaint.descent() + stampPaint.ascent()) / 2f
        canvas.drawText(text, rect.left + paddingH, baseline, stampPaint)
    }

    /**
     * One segment per completed km (grouped for long runs), not one per card.
     * A 10 km run with 8 cards still shows ten segments; the kms with a card
     * are brighter and the current km lights up in the brand accent.
     */
    private fun drawSplitBar(canvas: Canvas, plan: StoryPlan, currentKm: Int, card: RectF, scale: Float) {
        val palette = scope.palette
        val segments = plan.splitBar.segments
        if (segments.isEmpty()) return
        val left = card.left + 28f * scale
        val right = card.right - 28f * scale
        val top = card.bottom - 28f * scale
        val height = 5f * scale
        val gap = 3f * scale
        val segmentWidth = (right - left - gap * (segments.size - 1)) / segments.size
        if (segmentWidth <= 0f) return
        segments.forEachIndexed { index, segment ->
            val current = currentKm in segment.startKm..segment.endKm
            val x = left + index * (segmentWidth + gap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (current) palette.accent else palette.border
                alpha = when {
                    current -> 255
                    segment.hasCard -> 220
                    else -> 120
                }
            }
            canvas.drawRoundRect(
                RectF(x, top, x + segmentWidth, top + height),
                height / 2f,
                height / 2f,
                paint,
            )
        }
    }
}
