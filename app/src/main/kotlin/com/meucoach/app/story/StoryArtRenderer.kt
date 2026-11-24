package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtLayoutSpec
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.ResolvedMetric
import com.meucoach.core.domain.story.StoryEssential
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRenderSpec
import com.meucoach.core.domain.story.StoryRenderSpecResolver
import java.io.OutputStream
import java.time.LocalDate
import kotlin.math.max

/**
 * Route card renderer (ADR 0058/0060), extended by the photo studio (ADR
 * 0061). A transparent card starts on an empty canvas — no `drawColor` — so
 * only text, route and markers are opaque. In photo mode the caller passes a
 * bitmap already cropped, upright and sized by [ArtPhotoLoader]; the renderer
 * only draws it full-bleed and never re-crops. Layout, baselines, offsets,
 * clamping and scrims come from [ArtLayoutEngine], so preview, export and the
 * Studio share one source of coordinates.
 */
class StoryArtRenderer(
    private val recipe: ArtRecipe,
    private val typeface: Typeface,
    private val logoBitmap: Bitmap? = null,
) {
    /**
     * Renders the card at the requested size — preview uses ~540 px, export
     * 1080. Returns `null` without a route or when photo mode lacks a bitmap;
     * the caller owns the returned bitmap.
     */
    fun render(plan: StoryPlan, localDate: LocalDate?, width: Int, height: Int, photoBitmap: Bitmap? = null): Bitmap? {
        if (plan.route.points.size < 2) return null
        if (recipe.background == ArtBackgroundMode.PHOTO && photoBitmap == null) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val spec = StoryRenderSpecResolver.fromPlan(plan, localDate, recipe.toStoryRecipe())
            draw(Canvas(bitmap), spec, localDate, width.toFloat(), height.toFloat(), photoBitmap)
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
        return bitmap
    }

    /** Writes the transparent card as PNG, recycling the render bitmap either way. */
    fun writePng(plan: StoryPlan, localDate: LocalDate?, width: Int, height: Int, output: OutputStream): Boolean {
        val bitmap = render(plan, localDate, width, height) ?: return false
        return try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Writes a photo card as JPEG at the recipe format's full width (1080) and
     * quality 92. Only photo mode is valid here; the transparent path keeps
     * using [writePng].
     */
    fun writeJpg(plan: StoryPlan, localDate: LocalDate?, photoBitmap: Bitmap?, output: OutputStream): Boolean {
        if (recipe.background != ArtBackgroundMode.PHOTO) return false
        val bitmap = render(
            plan = plan,
            localDate = localDate,
            width = recipe.format.width,
            height = recipe.format.height,
            photoBitmap = photoBitmap,
        ) ?: return false
        return try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPG_QUALITY, output)
        } finally {
            bitmap.recycle()
        }
    }

    private fun draw(
        canvas: Canvas,
        spec: StoryRenderSpec,
        localDate: LocalDate?,
        width: Float,
        height: Float,
        photoBitmap: Bitmap?,
    ) {
        val scale = width / ArtLayoutSpec.DESIGN_WIDTH
        val layout = ArtLayoutEngine(typeface).layout(
            format = recipe.format,
            background = recipe.background,
            layout = recipe.layout,
            text = layoutText(spec, localDate),
            canvasHeight = height / scale,
            logoAspectRatio = logoBitmap?.let { it.width.toFloat() / it.height },
        )
        if (recipe.background == ArtBackgroundMode.PHOTO) {
            photoBitmap?.let { drawPhoto(canvas, it, width, height) }
            layout.scrims.forEach { drawScrim(canvas, it, scale) }
        }
        drawBrand(canvas, layout.brand, scale)
        drawText(canvas, layout.distance.label, withAlpha(recipe.textColor.argb, LABEL_ALPHA), scale)
        drawText(canvas, layout.distance.number, recipe.textColor.argb, scale)
        layout.distance.unit?.let { drawText(canvas, it, recipe.textColor.argb, scale) }
        drawText(canvas, layout.paceTime.paceLabel, withAlpha(recipe.textColor.argb, LABEL_ALPHA), scale)
        drawText(canvas, layout.paceTime.paceValue, recipe.textColor.argb, scale)
        drawText(canvas, layout.paceTime.timeLabel, withAlpha(recipe.textColor.argb, LABEL_ALPHA), scale)
        drawText(canvas, layout.paceTime.timeValue, recipe.textColor.argb, scale)
        layout.extras?.let { drawText(canvas, it.line, withAlpha(recipe.textColor.argb, LABEL_ALPHA), scale) }
        drawRoute(canvas, spec.plan, layout.route, recipe.routeColor.argb, scale)
    }

    private fun layoutText(spec: StoryRenderSpec, localDate: LocalDate?): ArtLayoutText {
        val distance = metric(spec, StoryEssential.DISTANCE)
        val pace = metric(spec, StoryEssential.AVG_PACE)
        val time = metric(spec, StoryEssential.TOTAL_TIME)
        val values = recipe.extras.mapNotNull { extra -> extraValue(spec, extra, localDate) }
        return ArtLayoutText(
            distanceLabel = distance.label,
            distanceValue = distance.value,
            paceLabel = pace.label,
            paceValue = pace.value,
            timeLabel = time.label,
            timeValue = time.value,
            extras = values.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        )
    }

    private fun drawBrand(canvas: Canvas, brand: ArtBrandLayout, scale: Float) {
        val logo = logoBitmap
        val logoBounds = brand.logoBounds
        if (logo != null && logoBounds != null) {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(recipe.textColor.argb, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(logo, null, logoBounds.toRectF(scale), paint)
        } else {
            brand.text?.let { drawText(canvas, it, recipe.textColor.argb, scale) }
        }
    }

    private fun drawText(canvas: Canvas, layout: ArtTextLayout, color: Int, scale: Float) {
        val paint = storyTextPaint(color, layout.size * scale, typeface, layout.bold).apply {
            textAlign = when (layout.align) {
                ArtTextAlign.LEFT -> Paint.Align.LEFT
                ArtTextAlign.RIGHT -> Paint.Align.RIGHT
            }
        }
        canvas.drawText(layout.text, layout.x * scale, layout.baseline * scale, paint)
    }

    /** Full-bleed cover: the loader already cropped to the format, this only guards rounding. */
    private fun drawPhoto(canvas: Canvas, bitmap: Bitmap, width: Float, height: Float) {
        val cover = max(width / bitmap.width, height / bitmap.height)
        val sourceWidth = width / cover
        val sourceHeight = height / cover
        val sourceLeft = (bitmap.width - sourceWidth) / 2f
        val sourceTop = (bitmap.height - sourceHeight) / 2f
        val source = Rect(
            sourceLeft.toInt(),
            sourceTop.toInt(),
            (sourceLeft + sourceWidth).toInt(),
            (sourceTop + sourceHeight).toInt(),
        )
        val destination = RectF(0f, 0f, width, height)
        canvas.drawBitmap(bitmap, source, destination, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawScrim(canvas: Canvas, scrim: ArtRect, scale: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ArtScrim.argb }
        val radius = ArtScrim.RADIUS * scale
        canvas.drawRoundRect(scrim.toRectF(scale), radius, radius, paint)
    }

    private fun extraValue(spec: StoryRenderSpec, extra: StoryExtra, localDate: LocalDate?): String? {
        val resolved = spec.closing.firstOrNull { it.id == extra.name && it.isAvailable } ?: return null
        // The art keeps its own date copy; the catalog one belongs to the video.
        return if (extra == StoryExtra.DATE) ArtDateFormatter.format(localDate) else resolved.value
    }

    private fun drawRoute(canvas: Canvas, plan: StoryPlan, route: ArtRouteLayout, routeColor: Int, scale: Float) {
        val area = route.path.toRectF(scale)
        val path = Path()
        plan.route.points.forEachIndexed { index, point ->
            val x = area.left + point.x * area.width()
            val y = area.top + point.y * area.height()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        canvas.drawPath(path, routePaint(withAlpha(ROUTE_CASING, ROUTE_CASING_ALPHA), 30f * scale))
        canvas.drawPath(path, routePaint(routeColor, 14f * scale))
        drawMarker(canvas, path, atStart = true, color = recipe.textColor.argb, scale = scale)
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
        it.id == essential.name
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun ArtRect.toRectF(scale: Float): RectF = RectF(
        left * scale,
        top * scale,
        right * scale,
        bottom * scale,
    )

    companion object {
        /** Fixed dark shell under the route so it reads on any photo (ADR 0058 §5.2). */
        const val ROUTE_CASING = 0xFF08090B.toInt()
        internal const val ROUTE_CASING_ALPHA = 178
        private const val LABEL_ALPHA = 178
        private const val JPG_QUALITY = 92
    }
}
