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
import com.meucoach.core.domain.story.ResolvedMetric
import com.meucoach.core.domain.story.StoryCardSpec
import com.meucoach.core.domain.story.StoryHighlights
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRecipe
import com.meucoach.core.domain.story.StoryRenderSpec
import com.meucoach.core.domain.story.StoryRenderSpecResolver
import com.meucoach.core.domain.story.StorySegment
import com.meucoach.core.domain.story.StorySegmentType
import java.util.Locale
import kotlin.math.max

private val PT_BR = Locale.forLanguageTag("pt-BR")

/**
 * Story text always uses tabular figures: pace, heart rate and time change
 * every frame, and proportional digits make the numbers look like they wobble.
 */
internal fun storyTextPaint(color: Int, textSize: Float, typeface: Typeface, bold: Boolean = false): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = textSize
        this.typeface = if (bold) Typeface.create(typeface, Typeface.BOLD) else typeface
        fontFeatureSettings = "tnum"
    }

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
    private val mapBackground: StoryMapSnapshot? = null,
    private val avatarBitmap: Bitmap? = null,
    private val overviewMap: StoryMapSnapshot? = null,
    private val recipe: StoryRecipe = StoryRecipe(),
) {
    private var cachedSpec: StoryRenderSpec? = null
    private var cachedSpecPlan: StoryPlan? = null
    private var cachedSpecRecipe: StoryRecipe? = null

    private fun specFor(plan: StoryPlan): StoryRenderSpec {
        val cached = cachedSpec
        if (cached != null && cachedSpecPlan === plan && cachedSpecRecipe == recipe) return cached
        val spec = StoryRenderSpecResolver.fromPlan(plan, plan.localDate, recipe)
        cachedSpec = spec
        cachedSpecPlan = plan
        cachedSpecRecipe = recipe
        return spec
    }
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
        val cameraMatrix = StoryPerspective.cameraMatrix(
            camera,
            square,
            width.toFloat(),
            height.toFloat(),
            nativeTilt = true,
        )
        val overlayScale = scale / (camera.scale * StoryPerspective.COVERAGE_SCALE).coerceAtLeast(0.01f)
        val followRoute = routeSource(plan, mapBackground, width, height, square, mapBackground != null)
        val spec = specFor(plan)
        val highlights = spec.highlights
        val followBlend = if (overviewMap != null) StoryMapScript.followBlend(timeSec) else 1f

        canvas.drawColor(palette.background)
        var overviewPosition: FloatArray? = null
        if (overviewMap != null && followBlend < 1f) {
            val overviewRoute = routeSource(plan, overviewMap, width, height, square, hasMap = true)
            withCamera(canvas, cameraMatrix) {
                drawMapLayer(canvas, width, height, overviewMap.bitmap, alpha = 1f)
                overviewPosition = drawRoute(
                    canvas,
                    plan,
                    timeSec,
                    square,
                    overlayScale,
                    overviewRoute,
                    alpha = 1f,
                )
            }
        }
        var followPosition: FloatArray? = null
        if (followBlend >= 1f) {
            withCamera(canvas, cameraMatrix) {
                drawMapLayer(canvas, width, height, mapBackground?.bitmap, alpha = 1f)
                followPosition = drawRoute(
                    canvas,
                    plan,
                    timeSec,
                    square,
                    overlayScale,
                    followRoute,
                    alpha = 1f,
                )
            }
        } else if (followBlend > 0f) {
            // Radial reveal around the runner: the follow shot opens over the
            // overview without the double exposure of a map crossfade.
            val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            withCamera(canvas, cameraMatrix) {
                drawMapLayer(canvas, width, height, mapBackground?.bitmap, alpha = 1f)
                followPosition = drawRoute(
                    canvas,
                    plan,
                    timeSec,
                    square,
                    overlayScale,
                    followRoute,
                    alpha = 1f,
                )
            }
            val center = followPosition?.copyOf() ?: floatArrayOf(width / 2f, height / 2f)
            cameraMatrix.mapPoints(center)
            val maxRadius = kotlin.math.hypot(
                maxOf(center[0], width - center[0]).toDouble(),
                maxOf(center[1], height - center[1]).toDouble(),
            ).toFloat()
            val radius = (maxRadius * followBlend).coerceAtLeast(1f)
            val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    center[0],
                    center[1],
                    radius,
                    intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF),
                    floatArrayOf(0f, 0.82f, 1f),
                    Shader.TileMode.CLAMP,
                )
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mask)
            canvas.restoreToCount(layer)
        }
        // The avatar pin is screen-space on purpose: it must stay upright and
        // legible while the map rotates/zooms under the camera.
        val overview = overviewPosition
        val follow = followPosition
        val marker = when {
            overview != null && follow != null -> floatArrayOf(
                overview[0] + (follow[0] - overview[0]) * followBlend,
                overview[1] + (follow[1] - overview[1]) * followBlend,
            )

            overview != null -> overview

            else -> follow
        }
        if (avatarBitmap != null && marker != null) {
            val screen = floatArrayOf(marker[0], marker[1])
            cameraMatrix.mapPoints(screen)
            drawAvatarPin(canvas, screen[0], screen[1], scale)
        }
        drawScrims(canvas, width, height, scale, isFinal)
        drawHeader(canvas, spec, width, height, scale)
        when {
            segment?.type == StorySegmentType.CARD && segment.split != null ->
                drawCard(canvas, spec, segment, width, height, scale)

            isFinal ->
                drawFinal(canvas, spec, width, height, scale, highlights)
        }
        if (mapBackground != null) drawAttribution(canvas, width, height, scale)
        drawVignette(canvas, width, height)
        drawOpeningFade(canvas, timeSec, width, height)
    }

    /**
     * The opening fades from the background color while the overview dissolves
     * into the follow shot. Hiding the angle change under the fade removes the
     * "quick cut" at the start without needing a continuous 3D camera.
     */
    private fun drawOpeningFade(canvas: Canvas, timeSec: Float, width: Int, height: Int) {
        val progress = (timeSec / StoryMapScript.OVERVIEW_FADE_SEC).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        val alpha = ((1f - eased) * 255f).toInt().coerceIn(0, 255)
        if (alpha <= 0) return
        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(palette.background, alpha)
            },
        )
    }

    private inline fun withCamera(canvas: Canvas, cameraMatrix: android.graphics.Matrix, draw: () -> Unit) {
        canvas.save()
        canvas.concat(cameraMatrix)
        draw()
        canvas.restore()
    }

    private fun drawMapLayer(canvas: Canvas, width: Int, height: Int, map: Bitmap?, alpha: Float) {
        if (map == null || alpha <= 0f) return
        val paint = Paint(mapPaint).apply { this.alpha = (255 * alpha).toInt().coerceIn(0, 255) }
        canvas.drawBitmap(map, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
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
            // Start where the card ends so the attribution sits on the dark
            // footer instead of on a brighter band below the panel.
            top = StoryLayout.cardBottom(height.toFloat()),
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

    private fun drawHeader(canvas: Canvas, spec: StoryRenderSpec, width: Int, height: Int, scale: Float) {
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

    private fun drawRoute(
        canvas: Canvas,
        plan: StoryPlan,
        timeSec: Float,
        square: StoryLayout.RouteSquare,
        overlayScale: Float,
        route: StoryRouteSource,
        alpha: Float,
    ): FloatArray? {
        if (alpha <= 0f) return null
        fun a(base: Int): Int = (base * alpha).toInt().coerceIn(0, 255)
        val side = square.side
        val routePath = Path()
        for (index in 0 until route.size) {
            val point = route.pointAt(index)
            if (index == 0) routePath.moveTo(point[0], point[1]) else routePath.lineTo(point[0], point[1])
        }
        val measure = PathMeasure(routePath, false)
        val progress = StoryMotion.progressAt(plan, timeSec)
        val position = FloatArray(2)
        measure.getPosTan(measure.length * progress, position, null)
        // Directional light around the runner: the map recedes and the eye
        // follows the route instead of scanning the whole snapshot.
        if (route.hasMap) drawSpotlight(canvas, position[0], position[1], side, alpha)
        if (route.hasMap) {
            val casing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 19f * overlayScale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = palette.mapCasing
                this.alpha = a(210)
            }
            canvas.drawPath(routePath, casing)
        }
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f * overlayScale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = if (route.hasMap) palette.mapBaseRoute else palette.border
            this.alpha = a(if (route.hasMap) 240 else 255)
        }
        canvas.drawPath(routePath, base)

        drawTraveledRoute(canvas, plan, measure, progress, overlayScale, alpha)
        drawKmTicks(canvas, plan, measure, overlayScale, alpha)

        if (avatarBitmap == null) {
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.accent
                this.alpha = a(60)
            }
            canvas.drawCircle(position[0], position[1], 24f * overlayScale, glow)
            canvas.drawCircle(
                position[0],
                position[1],
                9f * overlayScale,
                paint(palette.accent, 0f).apply { this.alpha = a(255) },
            )
        }

        val start = route.start
        val end = route.end
        if (route.hasMap) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.mapCasing
                this.alpha = a(210)
            }
            canvas.drawCircle(start[0], start[1], 9f * overlayScale, ring)
            canvas.drawCircle(end[0], end[1], 9f * overlayScale, ring)
        }
        canvas.drawCircle(
            start[0],
            start[1],
            6f * overlayScale,
            paint(palette.textPrimary, 0f).apply { this.alpha = a(255) },
        )
        canvas.drawCircle(
            end[0],
            end[1],
            6f * overlayScale,
            paint(palette.coral, 0f).apply { this.alpha = a(255) },
        )

        return floatArrayOf(position[0], position[1])
    }

    /**
     * Route vertices already in frame coordinates. A real snapshot supplies the
     * native `pixelForLatLng` pixels; the pure projection is only used by the
     * map-less preview/test fallback (MAP-02/MAP-03).
     */
    private fun routeSource(
        plan: StoryPlan,
        snapshot: StoryMapSnapshot?,
        width: Int,
        height: Int,
        square: StoryLayout.RouteSquare,
        hasMap: Boolean,
    ): StoryRouteSource {
        val routePixels = snapshot?.routePixels?.takeIf { it.size >= 2 }
        // The pure port is only reachable when the renderer has no snapshot;
        // a production snapshot never falls back to it (MAP-03).
        val fallbackProjection = if (snapshot == null) fallbackProjection(plan, width, height) else null
        val scaleX = if (routePixels != null) width.toFloat() / snapshot.bitmap.width else 1f
        val scaleY = if (routePixels != null) height.toFloat() / snapshot.bitmap.height else 1f
        fun normalizedX(x: Float): Float = if (!hasMap) {
            square.left + x * square.side
        } else {
            square.centerX + (x - 0.5f) * square.side
        }
        fun normalizedY(y: Float): Float = if (!hasMap) {
            square.top + y * square.side
        } else {
            square.centerY + (y - 0.5f) * square.side
        }
        fun pointAt(index: Int): FloatArray = when {
            routePixels != null -> {
                val point = routePixels[index]
                floatArrayOf(point.x * scaleX, point.y * scaleY)
            }

            fallbackProjection != null -> {
                val point = plan.geoPoints[index]
                fallbackProjection.project(point.latitude, point.longitude)
            }

            else -> {
                val point = plan.route.points[index]
                floatArrayOf(normalizedX(point.x), normalizedY(point.y))
            }
        }
        val size = when {
            routePixels != null -> routePixels.size
            fallbackProjection != null -> plan.geoPoints.size
            else -> plan.route.points.size
        }
        require(size > 0) { "A rota do story precisa de pelo menos um ponto." }
        val start = snapshot?.startPixel?.let { floatArrayOf(it.x * scaleX, it.y * scaleY) } ?: pointAt(0)
        val end = snapshot?.endPixel?.let { floatArrayOf(it.x * scaleX, it.y * scaleY) } ?: pointAt(size - 1)
        return StoryRouteSource(
            size = size,
            hasMap = hasMap,
            start = start,
            end = end,
            point = ::pointAt,
        )
    }

    private fun fallbackProjection(plan: StoryPlan, width: Int, height: Int): StoryMapProjection? {
        val bounds = plan.boundingBox ?: return null
        if (plan.geoPoints.size < 2) return null
        return StoryMapProjection(
            StoryMapCameraCalculator.calculateForStory(
                bounds,
                width,
                height,
                pitchDegrees = StoryMapScript.FOLLOW_PITCH_DEGREES,
            ),
            width,
            height,
        )
    }

    private class StoryRouteSource(
        val size: Int,
        val hasMap: Boolean,
        val start: FloatArray,
        val end: FloatArray,
        private val point: (Int) -> FloatArray,
    ) {
        fun pointAt(index: Int): FloatArray = point(index)
    }

    /** Paints the traveled part of the route with the zone measured on that stretch. */
    private fun drawTraveledRoute(
        canvas: Canvas,
        plan: StoryPlan,
        measure: PathMeasure,
        progress: Float,
        scale: Float,
        alpha: Float,
    ) {
        if (measure.length <= 0f || progress <= 0f) return
        if (plan.zoneRuns.isEmpty()) {
            val traveled = Path()
            measure.getSegment(0f, measure.length * progress, traveled, true)
            canvas.drawPath(traveled, routePaint(palette.accent, scale, alpha))
            return
        }
        val segment = Path()
        val paint = routePaint(palette.accent, scale, alpha)
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
    private fun drawKmTicks(canvas: Canvas, plan: StoryPlan, measure: PathMeasure, scale: Float, alpha: Float) {
        val splits = plan.allSplits.ifEmpty { plan.splits }
        if (measure.length <= 0f || splits.size < 2) return
        val point = FloatArray(2)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.mapCasing
            this.alpha = (160 * alpha).toInt().coerceIn(0, 255)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            this.alpha = (190 * alpha).toInt().coerceIn(0, 255)
        }
        splits.drop(1).forEach { split ->
            measure.getPosTan(measure.length * split.progress, point, null)
            canvas.drawCircle(point[0], point[1], 5.5f * scale, ring)
            canvas.drawCircle(point[0], point[1], 3f * scale, dot)
        }
    }

    /** Soft accent halo under the route, centred on the current position. */
    private fun drawSpotlight(canvas: Canvas, x: Float, y: Float, side: Float, alpha: Float) {
        if (mapBackground == null) return
        val radius = side * 0.55f
        val shader = RadialGradient(
            x,
            y,
            radius,
            intArrayOf(
                withAlpha(palette.accent, (26 * alpha).toInt().coerceIn(0, 255)),
                withAlpha(palette.accent, 0),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
    }

    /**
     * Profile photo as the moving pin. A circular badge with an accent ring and
     * a small pointer keeps the runner visible at 1080×1920 without covering
     * the route; the size is kept in screen space by [scale].
     */
    private fun drawAvatarPin(canvas: Canvas, x: Float, y: Float, scale: Float) {
        val avatar = avatarBitmap ?: return
        val radius = 38f * scale
        val centerY = y - radius - 9f * scale

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            alpha = 55
        }
        canvas.drawCircle(x, centerY, radius + 14f * scale, glow)

        val pointer = Path().apply {
            moveTo(x - 9f * scale, centerY + radius - 2f * scale)
            lineTo(x + 9f * scale, centerY + radius - 2f * scale)
            lineTo(x, y)
            close()
        }
        canvas.drawPath(
            pointer,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.mapCasing
                alpha = 225
            },
        )
        canvas.drawCircle(
            x,
            centerY,
            radius + 5f * scale,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent },
        )

        canvas.save()
        canvas.clipPath(
            Path().apply { addCircle(x, centerY, radius, Path.Direction.CW) },
        )
        canvas.drawBitmap(
            avatar,
            null,
            RectF(x - radius, centerY - radius, x + radius, centerY + radius),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        canvas.restore()
    }

    private fun routePaint(color: Int, scale: Float, alpha: Float = 1f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 11f * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
        this.alpha = (255 * alpha).toInt().coerceIn(0, 255)
    }

    /**
     * Km card with the resolved slots: pace plus up to two per-km extras, and
     * the fastest badge only when the recipe selected FASTEST_KM. The renderer
     * draws whatever the spec resolved; it never checks raw activity fields.
     */
    private fun drawCard(
        canvas: Canvas,
        spec: StoryRenderSpec,
        segment: StorySegment,
        width: Int,
        height: Int,
        scale: Float,
    ) {
        val split = segment.split ?: return
        val card = cardBounds(width, height)
        drawPanel(canvas, card, scale)
        val cardSpec = spec.cards.firstOrNull { it.split.km == split.km }
        val metrics = cardSpec?.metrics ?: listOf(
            ResolvedMetric(
                id = "AVG_PACE",
                label = "RITMO",
                value = String.format(PT_BR, "%d:%02d /km", split.pace.secondsPerKm / 60, split.pace.secondsPerKm % 60),
            ),
        )

        val kmPaint = paint(palette.accent, 52f * scale, bold = true)
        canvas.drawText("KM ${split.km}", card.left + 28f * scale, card.top + 64f * scale, kmPaint)
        if (cardSpec?.showFastestBadge == true) {
            drawFastestStamp(canvas, card, scale)
        }

        val label = paint(palette.textTertiary, 16f * scale, bold = true)
        val value = paint(palette.textPrimary, 30f * scale, bold = true)
        val rowTop = card.top + 92f * scale
        val column = (card.width() - 56f * scale) / metrics.size.coerceAtLeast(1)
        metrics.forEachIndexed { index, metric ->
            drawMetric(
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

    /**
     * One segment per completed km (grouped for long runs), not one per card.
     * A 10 km run with 8 cards still shows ten segments; the kms with a card
     * are brighter and the current km lights up in the brand accent.
     */
    private fun drawSplitBar(canvas: Canvas, plan: StoryPlan, currentKm: Int, card: RectF, scale: Float) {
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

    /**
     * Closing frame (ADR 0015 §2.4). The verdict comes from the planned session
     * (`StoryVerdictCalculator`); without it the frame falls back to the plain
     * summary. Distance and duration stay in the header so the cut from the
     * animated route has no layout jump.
     */
    private fun drawFinal(
        canvas: Canvas,
        spec: StoryRenderSpec,
        width: Int,
        height: Int,
        scale: Float,
        highlights: StoryHighlights,
    ) {
        val card = cardBounds(width, height)
        drawPanel(canvas, card, scale)
        val compact = spec.closing.size > 3

        val overline = paint(palette.textTertiary, (if (compact) 10f else 12f) * scale, bold = true)
        val headline = paint(palette.accent, (if (compact) 22f else 27f) * scale, bold = true)
        val overlineY = card.top + (if (compact) 20f else 26f) * scale
        canvas.drawText("CORRIDA CONCLUÍDA", card.left + 28f * scale, overlineY, overline)
        canvas.drawText(
            highlights.headline.uppercase(PT_BR),
            card.left + 28f * scale,
            card.top + (if (compact) 42f else 56f) * scale,
            headline,
        )
        highlights.proof?.let { proof ->
            val proofPaint = paint(palette.textSecondary, (if (compact) 13f else 15f) * scale, bold = true)
            canvas.drawText(
                proof,
                card.left + 28f * scale,
                card.top + (if (compact) 58f else 76f) * scale,
                proofPaint,
            )
        }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        val ruleTop = card.top + (if (compact) 66f else 84f) * scale
        canvas.drawRect(
            card.left + 28f * scale,
            ruleTop,
            card.left + 68f * scale,
            ruleTop + 4f * scale,
            rule,
        )

        if (compact) {
            val label = paint(palette.textTertiary, 12f * scale, bold = true)
            val value = paint(palette.textPrimary, 22f * scale, bold = true)
            drawMetricRow(canvas, spec.closing.take(3), card, card.top + 80f * scale, scale, label, value)
            val extras = spec.closing.drop(3)
            if (extras.isNotEmpty()) {
                val extraLabel = paint(palette.textTertiary, 11f * scale, bold = true)
                val extraValue = paint(palette.textPrimary, 19f * scale, bold = true)
                drawMetricRow(canvas, extras, card, card.top + 120f * scale, scale, extraLabel, extraValue)
            }
        } else {
            val label = paint(palette.textTertiary, 16f * scale, bold = true)
            val value = paint(palette.textPrimary, 30f * scale, bold = true)
            drawMetricRow(canvas, spec.closing, card, card.top + 110f * scale, scale, label, value)
        }

        val closing = paint(palette.textSecondary, 13f * scale, bold = true)
        val closingBottom = if (compact) 26f else 34f
        canvas.drawText(
            highlights.closingLine,
            card.left + 28f * scale,
            card.bottom - closingBottom * scale,
            closing,
        )

        // Closing brand stays inside the card, above the Instagram footer.
        val tagline = paint(palette.textTertiary, 11f * scale)
        val taglineBottom = if (compact) 10f else 14f
        canvas.drawText(
            "Seu treino, com contexto.",
            card.left + 28f * scale,
            card.bottom - taglineBottom * scale,
            tagline,
        )

        val closingLogo = logoBitmap
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

    private fun drawMetricRow(
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

    @Suppress("DEPRECATION")
    private fun drawAttribution(canvas: Canvas, width: Int, height: Int, scale: Float) {
        // OSM tile policy requires attribution on every rendered map. It stays
        // above the Instagram footer and gets a small backdrop so the tiny text
        // survives the light basemap and the bottom gradient.
        val text = when (palette.mapStyle) {
            StoryMapStyle.TERRAIN -> "© OpenStreetMap · OpenFreeMap · Elevation Mapzen"

            StoryMapStyle.SATELLITE -> "Imagery © Esri · Data © OpenStreetMap"

            StoryMapStyle.CLASSIC,
            StoryMapStyle.MEUCOACH_DARK,
            StoryMapStyle.MEUCOACH_LIGHT,
            -> "© OpenStreetMap · OpenMapTiles · OpenFreeMap"
        }
        val attribution = paint(palette.textSecondary, 13f * scale)
        attribution.alpha = 220
        val left = StoryLayout.attributionLeft(width.toFloat())
        val baseline = StoryLayout.attributionBaseline(height.toFloat())
        val textWidth = attribution.measureText(text)
        val paddingH = 8f * scale
        val paddingV = 5f * scale
        val backdrop = RectF(
            left - paddingH,
            baseline - attribution.textSize - paddingV,
            left + textWidth + paddingH,
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
        canvas.drawText(text, left, baseline, attribution)
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

    private fun paint(color: Int, textSize: Float, bold: Boolean = false): Paint =
        storyTextPaint(color, textSize, typeface, bold)
}
