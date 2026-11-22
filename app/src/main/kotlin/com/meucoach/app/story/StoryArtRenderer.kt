package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.ResolvedMetric
import com.meucoach.core.domain.story.StoryEssential
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRenderSpec
import com.meucoach.core.domain.story.StoryRenderSpecResolver
import java.io.OutputStream
import java.time.LocalDate

/**
 * Transparent route card (ADR 0058 §2.2). Unlike [StoryPosterRenderer] the
 * canvas starts empty — no `drawColor` — so the background is transparent by
 * construction and only text, route and markers are opaque. The layout is
 * computed per format from scaled design units (1080 wide): the 4:5 card is
 * poster-like with pace/time columns and a tagline footer, the 1:1 is compact
 * with pace and time on a single line and no tagline. Nothing here copies the
 * poster's fixed coordinates, and the route always maps into a square area so
 * the normalized projection is never stretched.
 */
class StoryArtRenderer(
    private val recipe: ArtRecipe,
    private val typeface: Typeface,
    private val logoBitmap: Bitmap? = null,
) {
    /**
     * Renders the card at the requested size — preview uses ~540 px, export
     * 1080. Returns `null` when there is no route to draw; the caller owns the
     * returned bitmap.
     */
    fun render(plan: StoryPlan, localDate: LocalDate?, width: Int, height: Int): Bitmap? {
        if (plan.route.points.size < 2) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val spec = StoryRenderSpecResolver.fromPlan(plan, localDate, recipe.toStoryRecipe())
            draw(Canvas(bitmap), spec, localDate, width.toFloat(), height.toFloat())
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
        return bitmap
    }

    /** Writes the card as PNG, recycling the render bitmap either way. */
    fun writePng(plan: StoryPlan, localDate: LocalDate?, width: Int, height: Int, output: OutputStream): Boolean {
        val bitmap = render(plan, localDate, width, height) ?: return false
        return try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } finally {
            bitmap.recycle()
        }
    }

    private fun draw(canvas: Canvas, spec: StoryRenderSpec, localDate: LocalDate?, width: Float, height: Float) {
        val scale = width / DESIGN_WIDTH
        val designHeight = height / scale
        val style =
            ArtStyle(text = recipe.textColor.argb, label = withAlpha(recipe.textColor.argb, LABEL_ALPHA), scale = scale)
        val margin = MARGIN * scale

        drawBrand(canvas, margin, style)

        val distance = metric(spec, StoryEssential.DISTANCE)
        drawLabel(canvas, distance.label, margin, 250f * scale, style)
        drawDistance(canvas, distance, margin, style)

        val lastMetricBaseline = if (recipe.format == ArtFormat.SQUARE) {
            drawPaceTimeLine(canvas, spec, margin, style)
        } else {
            drawPaceTimeColumns(canvas, spec, width, style)
        }
        val extrasBaseline = drawExtras(canvas, spec, localDate, margin, lastMetricBaseline, style)

        drawRoute(canvas, spec.plan, width, designHeight, extrasBaseline, style, recipe.routeColor.argb)

        if (recipe.format != ArtFormat.SQUARE) drawFooter(canvas, width, designHeight, style)
    }

    /** Colors and scale of one card draw; labels use the text color at 70%. */
    private data class ArtStyle(val text: Int, val label: Int, val scale: Float)

    private fun drawBrand(canvas: Canvas, margin: Float, style: ArtStyle) {
        val scale = style.scale
        val logo = logoBitmap
        if (logo != null) {
            val logoHeight = 44f * scale
            val logoWidth = logoHeight * logo.width / logo.height
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(style.text, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(
                logo,
                null,
                RectF(margin, 84f * scale, margin + logoWidth, 84f * scale + logoHeight),
                paint,
            )
        } else {
            val text = storyTextPaint(style.text, 30f * scale, typeface, bold = true)
            canvas.drawText("MEUCOACH", margin, 120f * scale, text)
        }
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, baseline: Float, style: ArtStyle) {
        val paint = storyTextPaint(style.label, 24f * style.scale, typeface, bold = true)
        canvas.drawText(text, x, baseline, paint)
    }

    private fun drawDistance(canvas: Canvas, distance: ResolvedMetric, margin: Float, style: ArtStyle) {
        val scale = style.scale
        val value = distance.value
        val number = value.substringBeforeLast(" ").ifBlank { value }
        val unit = value.substringAfterLast(" ", "").takeIf { it.isNotBlank() && number != value }
        val numberPaint = storyTextPaint(style.text, preferredNumberSize(scale), typeface, bold = true)
        val unitPaint = storyTextPaint(style.text, 52f * scale, typeface, bold = true)
        val unitWidth = unit?.let { unitPaint.measureText(it) + 14f * scale } ?: 0f
        numberPaint.textSize = fittedTextSize(
            numberPaint,
            number,
            DESIGN_WIDTH * scale - 2f * margin - unitWidth,
            preferredNumberSize(scale),
        )
        val baseline = (if (recipe.format == ArtFormat.SQUARE) 400f else 460f) * scale
        canvas.drawText(number, margin, baseline, numberPaint)
        unit?.let {
            canvas.drawText(it, margin + numberPaint.measureText(number) + 14f * scale, baseline, unitPaint)
        }
    }

    /** One aligned metric column of the 4:5 card. */
    private data class MetricSlot(
        val x: Float,
        val align: Paint.Align,
        val maxWidth: Float,
        val labelBaseline: Float,
        val valueBaseline: Float,
    )

    /** 4:5: the two values in aligned columns, like the poster. */
    private fun drawPaceTimeColumns(canvas: Canvas, spec: StoryRenderSpec, width: Float, style: ArtStyle): Float {
        val scale = style.scale
        val columnWidth = (DESIGN_WIDTH - 2f * MARGIN) / 2f * scale
        val slot = MetricSlot(
            x = width - MARGIN * scale,
            align = Paint.Align.RIGHT,
            maxWidth = columnWidth,
            labelBaseline = 585f * scale,
            valueBaseline = 665f * scale,
        )
        drawMetricColumn(
            canvas,
            metric(spec, StoryEssential.AVG_PACE),
            slot.copy(x = MARGIN * scale, align = Paint.Align.LEFT),
            style,
        )
        drawMetricColumn(canvas, metric(spec, StoryEssential.TOTAL_TIME), slot, style)
        return slot.valueBaseline
    }

    private fun drawMetricColumn(canvas: Canvas, metric: ResolvedMetric, slot: MetricSlot, style: ArtStyle) {
        val label = storyTextPaint(style.label, 22f * style.scale, typeface, bold = true).apply {
            textAlign = slot.align
        }
        canvas.drawText(metric.label, slot.x, slot.labelBaseline, label)
        val value = storyTextPaint(style.text, 56f * style.scale, typeface, bold = true).apply {
            textAlign = slot.align
        }
        value.textSize = fittedTextSize(value, metric.value, slot.maxWidth, 56f * style.scale)
        canvas.drawText(metric.value, slot.x, slot.valueBaseline, value)
    }

    /** 1:1: pace and time share one fitted line, no labels (compact card). */
    private fun drawPaceTimeLine(canvas: Canvas, spec: StoryRenderSpec, margin: Float, style: ArtStyle): Float {
        val scale = style.scale
        val line = listOf(
            metric(spec, StoryEssential.AVG_PACE).value,
            metric(spec, StoryEssential.TOTAL_TIME).value,
        ).joinToString(" · ")
        val paint = storyTextPaint(style.text, 52f * scale, typeface, bold = true)
        paint.textSize = fittedTextSize(paint, line, DESIGN_WIDTH * scale - 2f * margin, 52f * scale)
        val baseline = 510f * scale
        canvas.drawText(line, margin, baseline, paint)
        return baseline
    }

    /** Up to two extras as a value-only row; unavailable extras are omitted. */
    private fun drawExtras(
        canvas: Canvas,
        spec: StoryRenderSpec,
        localDate: LocalDate?,
        margin: Float,
        lastMetricBaseline: Float,
        style: ArtStyle,
    ): Float {
        val values = recipe.extras.mapNotNull { extra -> extraValue(spec, extra, localDate) }
        if (values.isEmpty()) return lastMetricBaseline
        val scale = style.scale
        val baseline = lastMetricBaseline + 95f * scale
        val line = values.joinToString(" · ")
        val paint = storyTextPaint(style.label, 34f * scale, typeface)
        paint.textSize = fittedTextSize(paint, line, DESIGN_WIDTH * scale - 2f * margin, 34f * scale)
        canvas.drawText(line, margin, baseline, paint)
        return baseline
    }

    private fun extraValue(spec: StoryRenderSpec, extra: StoryExtra, localDate: LocalDate?): String? {
        val resolved = spec.closing.firstOrNull { it.id == extra.name && it.isAvailable } ?: return null
        // The art keeps its own date copy; the catalog one belongs to the video.
        return if (extra == StoryExtra.DATE) ArtDateFormatter.format(localDate) else resolved.value
    }

    private fun drawRoute(
        canvas: Canvas,
        plan: StoryPlan,
        width: Float,
        designHeight: Float,
        contentBaseline: Float,
        style: ArtStyle,
        routeColor: Int,
    ) {
        val scale = style.scale
        val margin = MARGIN * scale
        val footerReserve = if (recipe.format == ArtFormat.SQUARE) MARGIN else FOOTER_RESERVE
        val top = contentBaseline + 70f * scale
        val bottom = designHeight * scale - footerReserve * scale
        val side = minOf(width - 2f * margin, bottom - top)
        val left = width / 2f - side / 2f
        val areaTop = top + ((bottom - top - side).coerceAtLeast(0f) / 2f)
        val inset = 24f * scale
        val area = RectF(
            left + inset,
            areaTop + inset,
            left + side - inset,
            areaTop + side - inset,
        )
        val path = Path()
        plan.route.points.forEachIndexed { index, point ->
            val x = area.left + point.x * area.width()
            val y = area.top + point.y * area.height()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        canvas.drawPath(path, routePaint(withAlpha(ROUTE_CASING, ROUTE_CASING_ALPHA), 30f * scale))
        canvas.drawPath(path, routePaint(routeColor, 14f * scale))
        drawMarker(canvas, path, atStart = true, color = style.text, scale = scale)
        drawMarker(canvas, path, atStart = false, color = ArtColor.CORAL.argb, scale = scale)
    }

    private fun drawMarker(canvas: Canvas, path: Path, atStart: Boolean, color: Int, scale: Float) {
        val measure = PathMeasure(path, false)
        if (measure.length <= 0f) return
        val point = FloatArray(2)
        measure.getPosTan(if (atStart) 0f else measure.length, point, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(point[0], point[1], 13f * scale, paint)
    }

    private fun drawFooter(canvas: Canvas, width: Float, designHeight: Float, style: ArtStyle) {
        val scale = style.scale
        val baseline = (designHeight - 58f) * scale
        val label = storyTextPaint(style.label, 18f * scale, typeface, bold = true)
        canvas.drawText("CORRIDA", MARGIN * scale, baseline, label)
        val tagline = storyTextPaint(style.label, 18f * scale, typeface)
        val line = "Seu treino, com contexto."
        canvas.drawText(line, width - MARGIN * scale - tagline.measureText(line), baseline, tagline)
    }

    private fun routePaint(color: Int, strokeWidth: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    private fun metric(spec: StoryRenderSpec, essential: StoryEssential): ResolvedMetric =
        spec.closing.first { it.id == essential.name }

    private fun preferredNumberSize(scale: Float): Float =
        (if (recipe.format == ArtFormat.SQUARE) 130f else 150f) * scale

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    companion object {
        const val DESIGN_WIDTH = 1080f

        /** Fixed dark shell under the route so it reads on any photo (ADR 0058 §5.2). */
        const val ROUTE_CASING = 0xFF08090B.toInt()
        private const val ROUTE_CASING_ALPHA = 178
        private const val LABEL_ALPHA = 178
        private const val MARGIN = 88f
        private const val FOOTER_RESERVE = 140f
    }
}
