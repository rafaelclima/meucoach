package com.meucoach.app.story.render

import android.graphics.Canvas
import android.graphics.RectF
import com.meucoach.app.story.StoryLayout
import com.meucoach.core.domain.story.ResolvedMetric
import com.meucoach.core.domain.story.StoryRenderSpec

/**
 * Header band: logo/wordmark, activity label, distance and duration.
 * The values come from the resolved spec; the renderer never formats raw
 * activity fields (ADR 0040 §2.1).
 */
internal class StoryHeaderRenderer(private val scope: StoryDrawScope) {
    fun drawHeader(canvas: Canvas, spec: StoryRenderSpec, width: Int, height: Int, scale: Float) {
        val palette = scope.palette
        val overline = scope.paint(palette.textTertiary, 18f * scale, bold = true)
        val metric = scope.paint(palette.textPrimary, 46f * scale, bold = true).apply {
            setShadowLayer(8f * scale, 0f, 2f * scale, 0xB3000000.toInt())
        }
        val unit = scope.paint(palette.textPrimary, 20f * scale).apply {
            setShadowLayer(6f * scale, 0f, 2f * scale, 0xB3000000.toInt())
        }
        val margin = StoryLayout.margin(width.toFloat())
        val overlineBaseline = StoryLayout.overlineBaseline(height.toFloat())

        val logo = scope.logoBitmap
        if (logo != null) {
            val logoHeight = StoryLayout.logoHeight(height.toFloat())
            val logoWidth = logoHeight * logo.width / logo.height
            val top = StoryLayout.logoTop(height.toFloat())
            canvas.drawBitmap(
                logo,
                null,
                RectF(margin, top, margin + logoWidth, top + logoHeight),
                null,
            )
        } else {
            canvas.drawText("MEUCOACH", margin, overlineBaseline, overline)
        }
        val activityLabel = "CORRIDA"
        canvas.drawText(
            activityLabel,
            width - margin - overline.measureText(activityLabel),
            overlineBaseline,
            overline,
        )

        // Units stay inline with the numbers: they read as "3,14 km" instead of
        // a floating caption over the map. The values come from the resolved
        // metric spec; the renderer does not format or invent anything.
        val metricBaseline = StoryLayout.metricBaseline(height.toFloat())
        val distance = headerNumber(spec.header[0]) ?: "—"
        canvas.drawText(distance, margin, metricBaseline, metric)
        canvas.drawText("km", margin + metric.measureText(distance) + 8f * scale, metricBaseline, unit)
        val duration = headerNumber(spec.header[1]) ?: "—"
        val durationWidth = metric.measureText(duration)
        val tempoWidth = unit.measureText("tempo")
        canvas.drawText(duration, width - margin - tempoWidth - 8f * scale - durationWidth, metricBaseline, metric)
        canvas.drawText("tempo", width - margin - tempoWidth, metricBaseline, unit)
    }

    private fun headerNumber(metric: ResolvedMetric): String? =
        metric.value.substringBeforeLast(" ").takeIf { it.isNotBlank() } ?: metric.value.takeIf { metric.isAvailable }
}
