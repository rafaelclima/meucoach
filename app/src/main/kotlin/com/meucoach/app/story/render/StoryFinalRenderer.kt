package com.meucoach.app.story.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.meucoach.core.domain.story.StoryHighlights
import com.meucoach.core.domain.story.StoryRenderSpec

/**
 * Closing frame (ADR 0015 §2.4). The verdict comes from the planned session
 * (`StoryVerdictCalculator`); without it the frame falls back to the plain
 * summary. Distance and duration stay in the header so the cut from the
 * animated route has no layout jump.
 */
internal class StoryFinalRenderer(private val scope: StoryDrawScope) {
    fun drawFinal(
        canvas: Canvas,
        spec: StoryRenderSpec,
        width: Int,
        height: Int,
        scale: Float,
        highlights: StoryHighlights,
    ) {
        val palette = scope.palette
        val card = if (spec.closing.size > 3) scope.finalCardBounds(width, height) else scope.cardBounds(width, height)
        scope.drawPanel(canvas, card, scale)
        val compact = spec.closing.size > 3

        val overline = scope.paint(palette.textTertiary, (if (compact) 10f else 12f) * scale, bold = true)
        val headline = scope.paint(palette.accent, (if (compact) 22f else 27f) * scale, bold = true)
        val overlineY = card.top + (if (compact) 20f else 26f) * scale
        canvas.drawText("CORRIDA CONCLUÍDA", card.left + 28f * scale, overlineY, overline)
        canvas.drawText(
            highlights.headline.uppercase(storyPtBr),
            card.left + 28f * scale,
            card.top + (if (compact) 42f else 56f) * scale,
            headline,
        )
        highlights.proof?.let { proof ->
            val proofPaint = scope.paint(palette.textSecondary, (if (compact) 13f else 15f) * scale, bold = true)
            canvas.drawText(
                proof,
                card.left + 28f * scale,
                card.top + (if (compact) 58f else 76f) * scale,
                proofPaint,
            )
        }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        val ruleTop = card.top + (if (compact) 70f else 84f) * scale
        canvas.drawRect(
            card.left + 28f * scale,
            ruleTop,
            card.left + 68f * scale,
            ruleTop + 4f * scale,
            rule,
        )

        if (compact) {
            val label = scope.paint(palette.textTertiary, 12f * scale, bold = true)
            val value = scope.paint(palette.textPrimary, 22f * scale, bold = true)
            scope.drawMetricRow(canvas, spec.closing.take(3), card, card.top + 88f * scale, scale, label, value)
            val extras = spec.closing.drop(3)
            if (extras.isNotEmpty()) {
                val extraLabel = scope.paint(palette.textTertiary, 11f * scale, bold = true)
                val extraValue = scope.paint(palette.textPrimary, 19f * scale, bold = true)
                scope.drawMetricRow(canvas, extras, card, card.top + 140f * scale, scale, extraLabel, extraValue)
            }
        } else {
            val label = scope.paint(palette.textTertiary, 16f * scale, bold = true)
            val value = scope.paint(palette.textPrimary, 30f * scale, bold = true)
            scope.drawMetricRow(canvas, spec.closing, card, card.top + 110f * scale, scale, label, value)
        }

        val closing = scope.paint(palette.textSecondary, 13f * scale, bold = true)
        val closingBottom = if (compact) 26f else 34f
        canvas.drawText(
            highlights.closingLine,
            card.left + 28f * scale,
            card.bottom - closingBottom * scale,
            closing,
        )

        // Closing brand stays inside the card, above the Instagram footer.
        val tagline = scope.paint(palette.textTertiary, 11f * scale)
        val taglineBottom = if (compact) 10f else 14f
        canvas.drawText(
            "Seu treino, com contexto.",
            card.left + 28f * scale,
            card.bottom - taglineBottom * scale,
            tagline,
        )

        val closingLogo = scope.logoBitmap
        if (closingLogo != null) {
            val logoHeight = 20f * scale
            val logoWidth = logoHeight * closingLogo.width / closingLogo.height
            val logoBottom = if (compact) 8f else 12f
            canvas.drawBitmap(
                closingLogo,
                null,
                RectF(
                    card.right - 28f * scale - logoWidth,
                    card.bottom - logoBottom * scale - logoHeight,
                    card.right - 28f * scale,
                    card.bottom - logoBottom * scale,
                ),
                null,
            )
        }
    }
}
