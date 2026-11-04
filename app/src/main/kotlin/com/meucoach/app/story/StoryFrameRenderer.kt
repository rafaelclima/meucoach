package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.core.model.Pace
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val PT_BR = Locale.forLanguageTag("pt-BR")

/**
 * Draws one 9:16 frame with a route, progress marker and the current km card.
 * Kept independent of MediaCodec so the same drawing can feed a preview later.
 *
 * When [logoBitmap] is provided it replaces the text wordmark in the header;
 * the footer now carries only the map attribution (ADR 0015 §2.8).
 *
 * Phase A of the story v2 (`docs/STORY_V2.md`): Instagram safe areas, a slow
 * camera push shared by the map and the route, colour grade and vignette. The
 * temporal grain is applied by [StoryFilm] in the exporter.
 */
class StoryFrameRenderer(
    private val palette: StoryPalette,
    private val typeface: Typeface,
    private val logoBitmap: Bitmap? = null,
    private val mapBackground: Bitmap? = null,
) {
    private val mapPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        // The colour grade used to run per frame; keeping the source style as
        // is saves ~10 ms/frame on software Canvas. Re-enable only with a
        // once-per-export bake.
        colorFilter = null
    }

    private var vignettePaint: Paint? = null
    private var vignetteWidth = 0
    private var vignetteHeight = 0

    fun render(plan: StoryPlan, timeSec: Float, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        render(Canvas(bitmap), plan, timeSec, width, height)
        return bitmap
    }

    fun render(canvas: Canvas, plan: StoryPlan, timeSec: Float, width: Int, height: Int) {
        val scale = width / StoryLayout.FRAME_WIDTH
        val segment = StoryMotion.segmentAt(plan, timeSec)
        val isFinal = segment?.type == StorySegmentType.FINAL
        val square = StoryLayout.routeSquare(width.toFloat(), height.toFloat())
        val camera = StoryMotion.camera(timeSec, plan)
        val highlights = StoryHighlightsCalculator.calculate(plan)

        canvas.drawColor(palette.background)
        withCamera(canvas, camera, square, width, height) {
            drawMapBackground(canvas, width, height)
        }
        withCamera(canvas, camera, square, width, height) {
            drawRoute(
                canvas,
                plan,
                timeSec,
                width,
                height,
                scale,
                square,
                camera.scale * StoryPerspective.COVERAGE_SCALE,
            )
        }
        drawScrims(canvas, width, height, scale, isFinal)
        drawHeader(canvas, plan, width, height, scale)
        when {
            segment?.type == StorySegmentType.CARD && segment.split != null ->
                drawCard(canvas, plan, segment, timeSec, width, height, scale, highlights)

            isFinal ->
                drawFinal(canvas, plan, width, height, scale, highlights)
        }
        if (mapBackground != null) drawAttribution(canvas, width, height, scale)
        drawVignette(canvas, width, height)
    }

    private inline fun withCamera(
        canvas: Canvas,
        camera: StoryCamera,
        square: StoryLayout.RouteSquare,
        width: Int,
        height: Int,
        draw: () -> Unit,
    ) {
        canvas.save()
        canvas.concat(StoryPerspective.cameraMatrix(camera, square, width.toFloat(), height.toFloat()))
        draw()
        canvas.restore()
    }

    private fun drawMapBackground(canvas: Canvas, width: Int, height: Int) {
        val map = mapBackground ?: return
        canvas.drawBitmap(map, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), mapPaint)
    }

    private fun drawScrims(canvas: Canvas, width: Int, height: Int, scale: Float, dimmed: Boolean) {
        if (dimmed) {
            val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.background
                alpha = 80
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        }
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
        drawGradient(
            canvas = canvas,
            width = width,
            top = height * 0.84f,
            bottom = height.toFloat(),
            edgeAlpha = if (dimmed) 210 else 200,
            fadeOut = false,
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

    private fun drawHeader(canvas: Canvas, plan: StoryPlan, width: Int, height: Int, scale: Float) {
        val overline = paint(palette.textTertiary, 18f * scale, bold = true)
        val metric = paint(palette.textPrimary, 46f * scale, bold = true).apply {
            setShadowLayer(8f * scale, 0f, 2f * scale, 0xB3000000.toInt())
        }
        val unit = paint(palette.textPrimary, 20f * scale).apply {
            setShadowLayer(6f * scale, 0f, 2f * scale, 0xB3000000.toInt())
        }
        val margin = StoryLayout.margin(width.toFloat())
        val overlineBaseline = StoryLayout.overlineBaseline(height.toFloat())

        val logo = logoBitmap
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
        // a floating caption over the map.
        val metricBaseline = StoryLayout.metricBaseline(height.toFloat())
        val distance = String.format(PT_BR, "%.2f", plan.distanceKm)
        canvas.drawText(distance, margin, metricBaseline, metric)
        canvas.drawText("km", margin + metric.measureText(distance) + 8f * scale, metricBaseline, unit)
        val duration = formatDuration(plan.durationTotalSec)
        val durationWidth = metric.measureText(duration)
        val tempoWidth = unit.measureText("tempo")
        canvas.drawText(duration, width - margin - tempoWidth - 8f * scale - durationWidth, metricBaseline, metric)
        canvas.drawText("tempo", width - margin - tempoWidth, metricBaseline, unit)
    }

    private fun drawRoute(
        canvas: Canvas,
        plan: StoryPlan,
        timeSec: Float,
        width: Int,
        height: Int,
        scale: Float,
        square: StoryLayout.RouteSquare,
        cameraScale: Float,
    ) {
        val side = square.side
        // Keep the route, markers and glow at a stable screen size while the
        // map itself flies in and out under the camera.
        val overlayScale = scale / cameraScale.coerceAtLeast(0.01f)
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
        val measure = PathMeasure(routePath, false)
        val progress = StoryMotion.progressAt(plan, timeSec)
        val position = FloatArray(2)
        measure.getPosTan(measure.length * progress, position, null)
        // Directional light around the runner: the map recedes and the eye
        // follows the route instead of scanning the whole snapshot.
        drawSpotlight(canvas, position[0], position[1], side)
        if (mapBackground != null) {
            val casing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 19f * overlayScale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = palette.mapCasing
                alpha = 210
            }
            canvas.drawPath(routePath, casing)
        }
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f * overlayScale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = if (mapBackground != null) palette.mapBaseRoute else palette.border
            alpha = if (mapBackground != null) 240 else 255
        }
        canvas.drawPath(routePath, base)

        drawTraveledRoute(canvas, plan, measure, progress, overlayScale)
        drawKmTicks(canvas, plan, measure, overlayScale)

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            alpha = 60
        }
        canvas.drawCircle(position[0], position[1], 24f * overlayScale, glow)
        canvas.drawCircle(position[0], position[1], 9f * overlayScale, paint(palette.accent, 0f))
        val startX = projectX(plan.route.start.x)
        val startY = projectY(plan.route.start.y)
        val endX = projectX(plan.route.end.x)
        val endY = projectY(plan.route.end.y)
        if (mapBackground != null) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.mapCasing
                alpha = 210
            }
            canvas.drawCircle(startX, startY, 9f * overlayScale, ring)
            canvas.drawCircle(endX, endY, 9f * overlayScale, ring)
        }
        canvas.drawCircle(startX, startY, 6f * overlayScale, paint(palette.textPrimary, 0f))
        canvas.drawCircle(endX, endY, 6f * overlayScale, paint(palette.coral, 0f))
    }

    /** Paints the traveled part of the route with the zone measured on that stretch. */
    private fun drawTraveledRoute(
        canvas: Canvas,
        plan: StoryPlan,
        measure: PathMeasure,
        progress: Float,
        scale: Float,
    ) {
        if (measure.length <= 0f || progress <= 0f) return
        if (plan.zoneRuns.isEmpty()) {
            val traveled = Path()
            measure.getSegment(0f, measure.length * progress, traveled, true)
            canvas.drawPath(traveled, routePaint(palette.accent, scale))
            return
        }
        val segment = Path()
        val paint = routePaint(palette.accent, scale)
        plan.zoneRuns.forEach { run ->
            val endProgress = minOf(run.endProgress, progress)
            if (endProgress <= run.startProgress) return@forEach
            segment.reset()
            measure.getSegment(
                measure.length * run.startProgress,
                measure.length * endProgress,
                segment,
                true,
            )
            paint.color = palette.zoneColor(run.zone)
            canvas.drawPath(segment, paint)
        }
    }

    /**
     * Small ticks at each finished kilometre. They turn the line into a scale:
     * the viewer can see "how many km until the card" without reading numbers.
     */
    private fun drawKmTicks(canvas: Canvas, plan: StoryPlan, measure: PathMeasure, scale: Float) {
        if (measure.length <= 0f || plan.splits.size < 2) return
        val point = FloatArray(2)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.mapCasing
            alpha = 160
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            alpha = 190
        }
        plan.splits.drop(1).forEach { split ->
            measure.getPosTan(measure.length * split.progress, point, null)
            canvas.drawCircle(point[0], point[1], 5.5f * scale, ring)
            canvas.drawCircle(point[0], point[1], 3f * scale, dot)
        }
    }

    /** Soft accent halo under the route, centred on the current position. */
    private fun drawSpotlight(canvas: Canvas, x: Float, y: Float, side: Float) {
        if (mapBackground == null) return
        val radius = side * 0.55f
        val shader = RadialGradient(
            x,
            y,
            radius,
            intArrayOf(withAlpha(palette.accent, 26), withAlpha(palette.accent, 0)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
    }

    private fun routePaint(color: Int, scale: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 11f * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    /**
     * Km card with the verdict stamp, a split bar and a short counter roll.
     * Numbers climb during the first [COUNTER_SECONDS] so the card enters like a
     * HUD instead of a static panel (`docs/STORY_V2.md` §7, phase B).
     */
    private fun drawCard(
        canvas: Canvas,
        plan: StoryPlan,
        segment: StorySegment,
        timeSec: Float,
        width: Int,
        height: Int,
        scale: Float,
        highlights: StoryHighlights,
    ) {
        val split = segment.split ?: return
        val card = cardBounds(width, height)
        drawPanel(canvas, card, scale)
        val reveal = StoryMotion.easeInOut(
            ((timeSec - segment.startSec) / COUNTER_SECONDS).coerceIn(0f, 1f),
        )

        val kmPaint = paint(palette.accent, 52f * scale, bold = true)
        canvas.drawText("KM ${split.km}", card.left + 28f * scale, card.top + 64f * scale, kmPaint)
        drawFastestStamp(canvas, card, split, highlights, plan.splits.size, scale)

        val label = paint(palette.textTertiary, 16f * scale, bold = true)
        val value = paint(palette.textPrimary, 30f * scale, bold = true)
        val rowTop = card.top + 92f * scale
        val column = (card.width() - 56f * scale) / 3f
        drawMetric(
            canvas,
            card.left + 28f * scale,
            rowTop,
            "RITMO",
            animatedPace(split.pace, reveal),
            label,
            value,
        )
        drawMetric(
            canvas,
            card.left + 28f * scale + column,
            rowTop,
            "FC MÉDIA",
            split.heartRate?.let { "${(it * reveal).roundToInt()} bpm" } ?: "—",
            label,
            value,
        )
        drawMetric(
            canvas,
            card.left + 28f * scale + column * 2,
            rowTop,
            "CALORIAS",
            split.calories?.let { "${(it * reveal).roundToInt()}" } ?: "—",
            label,
            value,
        )
        drawSplitBar(canvas, plan.splits, split.km, card, scale)
        // Legenda simples para o usuário entender rapidamente.
        val legend = paint(palette.textTertiary, 11f * scale)
        canvas.drawText(
            "Ritmo = tempo por km · FC = frequência cardíaca · Calorias = energia gasta",
            card.left + 28f * scale,
            card.bottom - 16f * scale,
            legend,
        )
    }

    private fun animatedPace(pace: Pace, reveal: Float): String {
        val seconds = if (reveal >= 1f) {
            pace.secondsPerKm
        } else {
            (pace.secondsPerKm * reveal).roundToInt().coerceAtLeast(1)
        }
        return String.format(PT_BR, "%d:%02d /km", seconds / 60, seconds % 60)
    }

    /**
     * The fastest kilometre is the only badge a public viewer can understand
     * without knowing the prescribed session. On short runs (one or two kms)
     * it says nothing, so it is omitted.
     */
    private fun drawFastestStamp(
        canvas: Canvas,
        card: RectF,
        split: StorySplit,
        highlights: StoryHighlights,
        splitCount: Int,
        scale: Float,
    ) {
        if (splitCount < 3 || highlights.fastestKm != split.km) return
        val text = "KM MAIS RÁPIDO"
        val stampPaint = paint(palette.onAccent, 13f * scale, bold = true)
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
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent },
        )
        val baseline = rect.centerY() - (stampPaint.descent() + stampPaint.ascent()) / 2f
        canvas.drawText(text, rect.left + paddingH, baseline, stampPaint)
    }

    /** One segment per km; the current km lights up in the brand accent. */
    private fun drawSplitBar(canvas: Canvas, splits: List<StorySplit>, currentKm: Int, card: RectF, scale: Float) {
        if (splits.isEmpty()) return
        val left = card.left + 28f * scale
        val right = card.right - 28f * scale
        val top = card.bottom - 44f * scale
        val height = 5f * scale
        val gap = 3f * scale
        val segmentWidth = (right - left - gap * (splits.size - 1)) / splits.size
        if (segmentWidth <= 0f) return
        splits.forEachIndexed { index, split ->
            val x = left + index * (segmentWidth + gap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (split.km == currentKm) palette.accent else palette.border
                alpha = if (split.km == currentKm) 255 else 200
            }
            canvas.drawRoundRect(
                RectF(x, top, x + segmentWidth, top + height),
                height / 2f,
                height / 2f,
                paint,
            )
        }
    }

    /**
     * Closing frame (ADR 0015 §2.4). The verdict comes from the planned session
     * (`StoryVerdictCalculator`); without it the frame falls back to the plain
     * summary. Distance and duration stay in the header so the cut from the
     * animated route has no layout jump.
     */
    private fun drawFinal(
        canvas: Canvas,
        plan: StoryPlan,
        width: Int,
        height: Int,
        scale: Float,
        highlights: StoryHighlights,
    ) {
        val card = cardBounds(width, height)
        drawPanel(canvas, card, scale)

        val label = paint(palette.textTertiary, 16f * scale, bold = true)
        val value = paint(palette.textPrimary, 30f * scale, bold = true)
        val overline = paint(palette.textTertiary, 12f * scale, bold = true)
        canvas.drawText("CORRIDA CONCLUÍDA", card.left + 28f * scale, card.top + 30f * scale, overline)
        val headline = paint(palette.accent, 27f * scale, bold = true)
        canvas.drawText(
            highlights.headline.uppercase(PT_BR),
            card.left + 28f * scale,
            card.top + 62f * scale,
            headline,
        )
        highlights.proof?.let { proof ->
            val proofPaint = paint(palette.textSecondary, 15f * scale, bold = true)
            canvas.drawText(proof, card.left + 28f * scale, card.top + 84f * scale, proofPaint)
        }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        canvas.drawRect(
            card.left + 28f * scale,
            card.top + 94f * scale,
            card.left + 68f * scale,
            card.top + 98f * scale,
            rule,
        )
        val rowTop = card.top + 124f * scale

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

        val closing = paint(palette.textSecondary, 13f * scale, bold = true)
        canvas.drawText(
            highlights.closingLine,
            card.left + 28f * scale,
            card.bottom - 46f * scale,
            closing,
        )

        // Closing brand stays inside the card, above the Instagram footer.
        val tagline = paint(palette.textTertiary, 11f * scale)
        canvas.drawText(
            "Seu treino, com contexto.",
            card.left + 28f * scale,
            card.bottom - 16f * scale,
            tagline,
        )

        val closingLogo = logoBitmap
        if (closingLogo != null) {
            val logoHeight = 20f * scale
            val logoWidth = logoHeight * closingLogo.width / closingLogo.height
            canvas.drawBitmap(
                closingLogo,
                null,
                RectF(
                    card.right - 28f * scale - logoWidth,
                    card.bottom - 14f * scale - logoHeight,
                    card.right - 28f * scale,
                    card.bottom - 14f * scale,
                ),
                null,
            )
        }
    }

    private fun cardBounds(width: Int, height: Int): RectF {
        val margin = StoryLayout.margin(width.toFloat())
        return RectF(
            margin,
            StoryLayout.cardTop(height.toFloat()),
            width - margin,
            StoryLayout.cardBottom(height.toFloat()),
        )
    }

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

    private fun drawAttribution(canvas: Canvas, width: Int, height: Int, scale: Float) {
        // OSM tile policy requires attribution on every rendered map. It stays
        // above the Instagram footer and gets a small backdrop so the tiny text
        // survives the light basemap and the bottom gradient.
        val text = when (palette.mapStyle) {
            StoryMapStyle.TERRAIN -> "© OpenStreetMap · OpenFreeMap · Elevation Mapzen"
            StoryMapStyle.SATELLITE -> "Imagery © Esri · Data © OpenStreetMap"
            StoryMapStyle.CLASSIC -> "© OpenStreetMap · OpenMapTiles · OpenFreeMap"
        }
        val attribution = paint(palette.textSecondary, 13f * scale)
        attribution.alpha = 220
        val margin = StoryLayout.margin(width.toFloat())
        val baseline = StoryLayout.attributionBaseline(height.toFloat())
        val textWidth = attribution.measureText(text)
        val paddingH = 8f * scale
        val paddingV = 5f * scale
        val backdrop = RectF(
            margin - paddingH,
            baseline - attribution.textSize - paddingV,
            margin + textWidth + paddingH,
            baseline + paddingV,
        )
        canvas.drawRoundRect(
            backdrop,
            8f * scale,
            8f * scale,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.background
                alpha = 175
            },
        )
        canvas.drawText(text, margin, baseline, attribution)
    }

    private fun drawVignette(canvas: Canvas, width: Int, height: Int) {
        if (vignettePaint == null || vignetteWidth != width || vignetteHeight != height) {
            vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.shader = RadialGradient(
                    width / 2f,
                    height / 2f,
                    max(width, height) * 0.75f,
                    intArrayOf(0x00000000, 0x4D000000),
                    floatArrayOf(0.58f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            vignetteWidth = width
            vignetteHeight = height
        }
        vignettePaint?.let { canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), it) }
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

    private companion object {
        /** Counter roll at the start of each km card, in seconds. */
        const val COUNTER_SECONDS = 0.45f
    }
}
