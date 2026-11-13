package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.meucoach.core.domain.story.ResolvedMetric
import com.meucoach.core.domain.story.StoryEssential
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRecipe
import com.meucoach.core.domain.story.StoryRenderSpec
import com.meucoach.core.domain.story.StoryRenderSpecResolver
import java.io.OutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")
private val POSTER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu", PT_BR)

/**
 * 4:5 poster for the Instagram feed (VIS-05).
 *
 * The poster is a composition of its own, not a crop of the 9:16 video: the
 * route is drawn as art over a palette gradient, without tiles, labels or
 * network, and the three essentials get the large type. Values come from
 * [StoryRenderSpecResolver], so the poster cannot disagree with the preview or
 * the encoded video. A plan without a route returns `null`: the video export
 * continues without a poster instead of inventing one.
 */
class StoryPosterRenderer(
    private val palette: StoryPalette,
    private val typeface: Typeface,
    private val logoBitmap: Bitmap? = null,
) {
    /**
     * Renders the poster, or returns `null` when there is no route to draw.
     * The caller owns the returned bitmap.
     */
    fun render(plan: StoryPlan, recipe: StoryRecipe = StoryRecipe()): Bitmap? {
        if (plan.route.points.size < 2) return null
        val spec = StoryRenderSpecResolver.fromPlan(plan, plan.localDate, recipe)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            draw(Canvas(bitmap), spec)
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
        return bitmap
    }

    /**
     * Writes the poster as PNG. Returns `false` when there is no route, so the
     * exporter can skip the poster without failing the video.
     */
    fun writePng(plan: StoryPlan, recipe: StoryRecipe = StoryRecipe(), output: OutputStream): Boolean {
        val bitmap = render(plan, recipe) ?: return false
        return try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } finally {
            bitmap.recycle()
        }
    }

    private fun draw(canvas: Canvas, spec: StoryRenderSpec) {
        val width = WIDTH.toFloat()
        val height = HEIGHT.toFloat()
        canvas.drawColor(palette.background)
        drawBackdrop(canvas, width, height)
        drawHeader(canvas, spec.plan, width)
        drawMetrics(canvas, spec, width)
        drawRoute(canvas, spec.plan, width, height)
        drawFooter(canvas, width)
    }

    private fun drawBackdrop(canvas: Canvas, width: Float, height: Float) {
        val base = LinearGradient(
            0f,
            0f,
            0f,
            height,
            intArrayOf(palette.background, palette.surface, palette.background),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(
            0f,
            0f,
            width,
            height,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = base },
        )
        val glow = RadialGradient(
            width / 2f,
            height * 0.70f,
            width * 0.62f,
            intArrayOf(withAlpha(palette.accent, 24), withAlpha(palette.accent, 0)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(
            0f,
            0f,
            width,
            height,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glow },
        )
        val vignette = RadialGradient(
            width / 2f,
            height / 2f,
            maxOf(width, height) * 0.72f,
            intArrayOf(withAlpha(palette.background, 0), withAlpha(palette.background, 80)),
            floatArrayOf(0.56f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(
            0f,
            0f,
            width,
            height,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = vignette },
        )
    }

    private fun drawHeader(canvas: Canvas, plan: StoryPlan, width: Float) {
        val margin = MARGIN
        val logo = logoBitmap
        if (logo != null) {
            val logoHeight = 44f
            val logoWidth = logoHeight * logo.width / logo.height
            // The shipped wordmark is near-white; tinting with the text colour
            // keeps it legible on the light theme without shipping two assets.
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(palette.textPrimary, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(
                logo,
                null,
                RectF(margin, 84f, margin + logoWidth, 84f + logoHeight),
                paint,
            )
        } else {
            canvas.drawText(
                "MEUCOACH",
                margin,
                120f,
                storyTextPaint(palette.textPrimary, 30f, typeface, bold = true),
            )
        }
        plan.localDate?.let { date ->
            // pt-BR abbreviates the month as "out."; the dot is noise at this size.
            val text = date.format(POSTER_DATE).replace(".", "").uppercase(PT_BR)
            val paint = storyTextPaint(palette.textTertiary, 20f, typeface, bold = true)
            canvas.drawText(text, width - margin - paint.measureText(text), 116f, paint)
        }
    }

    private fun drawMetrics(canvas: Canvas, spec: StoryRenderSpec, width: Float) {
        val margin = MARGIN
        val label = storyTextPaint(palette.textTertiary, 22f, typeface, bold = true)
        val distance = metric(spec, StoryEssential.DISTANCE)
        canvas.drawText(distance.label, margin, 250f, label)
        canvas.drawRect(
            margin,
            270f,
            margin + 72f,
            274f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent },
        )

        val value = distance.value
        val number = value.substringBeforeLast(" ").ifBlank { value }
        val unit = value.substringAfterLast(" ", "").takeIf { it.isNotBlank() && number != value }
        val numberPaint = storyTextPaint(palette.textPrimary, 178f, typeface, bold = true)
        val unitPaint = storyTextPaint(palette.textPrimary, 52f, typeface, bold = true)
        val unitWidth = unit?.let { unitPaint.measureText(it) + 14f } ?: 0f
        numberPaint.textSize = fittedSize(
            numberPaint,
            number,
            width - 2f * margin - unitWidth,
            178f,
        )
        val baseline = 454f
        canvas.drawText(number, margin, baseline, numberPaint)
        unit?.let {
            canvas.drawText(it, margin + numberPaint.measureText(number) + 14f, baseline, unitPaint)
        }

        val pace = metric(spec, StoryEssential.AVG_PACE)
        val time = metric(spec, StoryEssential.TOTAL_TIME)
        drawValue(canvas, pace, margin, Paint.Align.LEFT)
        drawValue(canvas, time, width - margin, Paint.Align.RIGHT)
    }

    private fun drawValue(canvas: Canvas, metric: ResolvedMetric, x: Float, align: Paint.Align) {
        val label = storyTextPaint(palette.textTertiary, 20f, typeface, bold = true).apply {
            textAlign = align
        }
        val value = storyTextPaint(palette.textPrimary, 56f, typeface, bold = true).apply {
            textAlign = align
        }
        canvas.drawText(metric.label, x, 566f, label)
        value.textSize = fittedSize(value, metric.value, 390f, 56f)
        canvas.drawText(metric.value, x, 642f, value)
    }

    private fun drawRoute(canvas: Canvas, plan: StoryPlan, width: Float, height: Float) {
        val top = 690f
        val bottom = height - 120f
        val side = minOf(width - 2f * MARGIN, bottom - top)
        val left = width / 2f - side / 2f
        val inset = 24f
        val area = RectF(left + inset, top + inset, left + side - inset, top + side - inset)
        val points = plan.route.points
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = area.left + point.x * area.width()
            val y = area.top + point.y * area.height()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        canvas.drawPath(path, routePaint(palette.accent, 38f, 22))
        canvas.drawPath(path, routePaint(palette.border, 20f, 170))
        canvas.drawPath(path, routePaint(palette.accent, 12f, 255))
        drawTicks(canvas, plan, path)
        drawEndpoint(canvas, path, atStart = true)
        drawEndpoint(canvas, path, atStart = false)
    }

    private fun drawTicks(canvas: Canvas, plan: StoryPlan, path: Path) {
        val measure = PathMeasure(path, false)
        if (measure.length <= 0f || plan.allSplits.size < 2) return
        val point = FloatArray(2)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.mapCasing
            alpha = 190
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            alpha = 210
        }
        plan.allSplits.drop(1).forEach { split ->
            measure.getPosTan(measure.length * split.progress, point, null)
            canvas.drawCircle(point[0], point[1], 8f, ring)
            canvas.drawCircle(point[0], point[1], 4.5f, dot)
        }
    }

    private fun drawEndpoint(canvas: Canvas, path: Path, atStart: Boolean) {
        val measure = PathMeasure(path, false)
        if (measure.length <= 0f) return
        val point = FloatArray(2)
        measure.getPosTan(if (atStart) 0f else measure.length, point, null)
        val color = if (atStart) palette.textPrimary else palette.coral
        canvas.drawCircle(point[0], point[1], 13f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        canvas.drawCircle(
            point[0],
            point[1],
            6f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = palette.background },
        )
    }

    private fun drawFooter(canvas: Canvas, width: Float) {
        val baseline = HEIGHT - 58f
        canvas.drawRect(
            MARGIN,
            baseline - 30f,
            width - MARGIN,
            baseline - 29f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(palette.border, 120) },
        )
        canvas.drawText(
            "CORRIDA",
            MARGIN,
            baseline,
            storyTextPaint(palette.textTertiary, 18f, typeface, bold = true),
        )
        val tagline = storyTextPaint(palette.textTertiary, 18f, typeface)
        val text = "Seu treino, com contexto."
        canvas.drawText(text, width - MARGIN - tagline.measureText(text), baseline, tagline)
    }

    private fun routePaint(color: Int, strokeWidth: Float, alpha: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
        this.alpha = alpha
    }

    private fun metric(spec: StoryRenderSpec, essential: StoryEssential): ResolvedMetric =
        spec.closing.first { it.id == essential.name }

    private fun fittedSize(paint: Paint, text: String, maxWidth: Float, preferred: Float): Float {
        paint.textSize = preferred
        val measured = paint.measureText(text)
        if (measured <= maxWidth || measured <= 0f) return preferred
        return (preferred * maxWidth / measured).coerceAtLeast(8f)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350

        private const val MARGIN = 88f
    }
}
