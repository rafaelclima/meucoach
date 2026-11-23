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
 * Transparent route card (ADR 0058, extended by ADR 0060). Unlike
 * [StoryPosterRenderer] the canvas starts empty — no `drawColor` — so the
 * background is transparent by construction and only text, route and markers
 * are opaque. The layout is computed per format from scaled design units
 * (1080 wide): the brand sits on top, the distance on the left, pace/time
 * stacked against the right margin, extras right below, and the route fills
 * the remaining square. There is no footer/tagline; the 9:16 card reuses the
 * top block and centers a larger square route in the extra height.
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
        val square = recipe.format == ArtFormat.SQUARE
        val distanceBaseline = (if (square) 400f else 460f) * scale
        val labelBaseline = distanceBaseline - (if (square) 135f else 153f) * scale

        drawBrand(canvas, margin, style)

        val distance = metric(spec, StoryEssential.DISTANCE)
        drawLabel(canvas, distance.label, margin, labelBaseline, style)
        drawDistance(canvas, distance, margin, distanceBaseline, style)

        val lastMetricBaseline = drawPaceTimeBlock(canvas, spec, width, labelBaseline, style)
        val extrasBaseline = drawExtras(
            canvas,
            spec,
            localDate,
            margin,
            maxOf(distanceBaseline, lastMetricBaseline),
            style,
        )

        drawRoute(canvas, spec.plan, width, designHeight, extrasBaseline, style, recipe.routeColor.argb)
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

    private fun drawDistance(
        canvas: Canvas,
        distance: ResolvedMetric,
        margin: Float,
        baseline: Float,
        style: ArtStyle,
    ) {
        val scale = style.scale
        val value = distance.value
        val number = value.substringBeforeLast(" ").ifBlank { value }
        val unit = value.substringAfterLast(" ", "").takeIf { it.isNotBlank() && number != value }
        val size = (if (recipe.format == ArtFormat.SQUARE) SQUARE_DISTANCE_SIZE else DISTANCE_SIZE) * scale
        val numberPaint = storyTextPaint(style.text, size, typeface, bold = true)
        val unitPaint = storyTextPaint(style.text, 48f * scale, typeface, bold = true)
        val unitWidth = unit?.let { unitPaint.measureText(it) + 14f * scale } ?: 0f
        // Reserve the right column so a long distance never collides with
        // pace/time; the number shrinks before it invades the other margin.
        numberPaint.textSize = fittedTextSize(
            numberPaint,
            number,
            DESIGN_WIDTH * scale - 2f * margin - unitWidth - PACE_TIME_RESERVE * scale,
            size,
        )
        canvas.drawText(number, margin, baseline, numberPaint)
        unit?.let {
            canvas.drawText(it, margin + numberPaint.measureText(number) + 14f * scale, baseline, unitPaint)
        }
    }

    /**
     * Pace and time stacked against the right margin, one block below the
     * other (ADR 0060). Labels sit close to their values; both are right
     * aligned so the column shares the card's right edge.
     */
    private fun drawPaceTimeBlock(
        canvas: Canvas,
        spec: StoryRenderSpec,
        width: Float,
        labelBaseline: Float,
        style: ArtStyle,
    ): Float {
        val scale = style.scale
        val x = width - MARGIN * scale
        val labelPaint = storyTextPaint(style.label, 22f * scale, typeface, bold = true).apply {
            textAlign = Paint.Align.RIGHT
        }
        val valuePaint = storyTextPaint(style.text, 52f * scale, typeface, bold = true).apply {
            textAlign = Paint.Align.RIGHT
        }
        val pace = metric(spec, StoryEssential.AVG_PACE)
        val time = metric(spec, StoryEssential.TOTAL_TIME)

        canvas.drawText(pace.label, x, labelBaseline, labelPaint)
        canvas.drawText(pace.value, x, labelBaseline + 46f * scale, valuePaint)
        canvas.drawText(time.label, x, labelBaseline + 98f * scale, labelPaint)
        val timeBaseline = labelBaseline + 144f * scale
        canvas.drawText(time.value, x, timeBaseline, valuePaint)
        return timeBaseline
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
        // Immediately below the distance/pace-time block (ADR 0060).
        val baseline = lastMetricBaseline + 65f * scale
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
        val top = contentBaseline + 70f * scale
        val bottom = designHeight * scale - margin
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

    private fun routePaint(color: Int, strokeWidth: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    private fun metric(spec: StoryRenderSpec, essential: StoryEssential): ResolvedMetric = spec.closing.first {
        it.id ==
            essential.name
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    companion object {
        const val DESIGN_WIDTH = 1080f

        /** Fixed dark shell under the route so it reads on any photo (ADR 0058 §5.2). */
        const val ROUTE_CASING = 0xFF08090B.toInt()
        internal const val ROUTE_CASING_ALPHA = 178
        private const val LABEL_ALPHA = 178
        private const val MARGIN = 88f

        /** Distance size in design units (~10% below the original 150/130). */
        private const val DISTANCE_SIZE = 135f
        private const val SQUARE_DISTANCE_SIZE = 117f

        /** Right column kept free for the pace/time block so the distance never collides. */
        private const val PACE_TIME_RESERVE = 300f
    }
}
