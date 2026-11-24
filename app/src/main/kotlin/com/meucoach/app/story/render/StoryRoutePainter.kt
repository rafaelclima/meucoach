package com.meucoach.app.story.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.meucoach.app.story.StoryMapSnapshot
import com.meucoach.app.story.StoryMotion
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRouteStroke

/**
 * Stroke styling of the story route: casing, base, traveled zones, km ticks,
 * runner marker and the avatar pin. Vertex coordinates come from
 * [StoryRouteSource]; this class only paints them.
 */
internal class StoryRoutePainter(
    private val scope: StoryDrawScope,
    private val avatarBitmap: Bitmap?,
    private val mapBackground: StoryMapSnapshot?,
) {
    fun drawRoute(
        canvas: Canvas,
        plan: StoryPlan,
        timeSec: Float,
        frame: StoryCameraFrame,
        route: StoryRouteSource,
        stroke: StoryRouteStroke,
    ): FloatArray? {
        val palette = scope.palette
        val overlayScale = frame.overlayScale
        val side = frame.square.side
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
        if (route.hasMap) drawSpotlight(canvas, position[0], position[1], side)
        if (route.hasMap) {
            val casing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 19f * overlayScale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = palette.mapCasing
                alpha = 210
                applyRouteStroke(stroke, overlayScale)
            }
            canvas.drawPath(routePath, casing)
        }
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f * overlayScale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = if (route.hasMap) palette.mapBaseRoute else palette.textTertiary
            alpha = if (route.hasMap) 240 else 220
            applyRouteStroke(stroke, overlayScale)
        }
        canvas.drawPath(routePath, base)

        drawTraveledRoute(canvas, plan, measure, progress, overlayScale, stroke)
        drawKmTicks(canvas, plan, measure, overlayScale)

        if (avatarBitmap == null) {
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.accent
                alpha = 60
            }
            canvas.drawCircle(position[0], position[1], 24f * overlayScale, glow)
            canvas.drawCircle(
                position[0],
                position[1],
                9f * overlayScale,
                scope.paint(palette.accent, 0f).apply { alpha = 255 },
            )
        }

        val start = route.start
        val end = route.end
        if (route.hasMap) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.mapCasing
                alpha = 210
            }
            canvas.drawCircle(start[0], start[1], 9f * overlayScale, ring)
            canvas.drawCircle(end[0], end[1], 9f * overlayScale, ring)
        }
        canvas.drawCircle(
            start[0],
            start[1],
            6f * overlayScale,
            scope.paint(palette.textPrimary, 0f).apply { alpha = 255 },
        )
        canvas.drawCircle(
            end[0],
            end[1],
            6f * overlayScale,
            scope.paint(palette.coral, 0f).apply { alpha = 255 },
        )
        return floatArrayOf(position[0], position[1])
    }

    /**
     * Profile photo as the moving pin. A circular badge with an accent ring and
     * a small pointer keeps the runner visible at 1080×1920 without covering
     * the route; the size is kept in screen space by [scale].
     */
    fun drawAvatarPin(canvas: Canvas, x: Float, y: Float, scale: Float) {
        val avatar = avatarBitmap ?: return
        val palette = scope.palette
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

    /** Paints the traveled part of the route with the zone measured on that stretch. */
    private fun drawTraveledRoute(
        canvas: Canvas,
        plan: StoryPlan,
        measure: PathMeasure,
        progress: Float,
        overlayScale: Float,
        stroke: StoryRouteStroke,
    ) {
        if (measure.length <= 0f || progress <= 0f) return
        if (plan.zoneRuns.isEmpty()) {
            val traveled = Path()
            measure.getSegment(0f, measure.length * progress, traveled, true)
            val paint = routePaint(scope.palette.accent, overlayScale)
                .applyRouteStroke(stroke, overlayScale)
            drawTraveledGlow(canvas, traveled, scope.palette.accent, overlayScale, stroke)
            canvas.drawPath(traveled, paint)
            return
        }
        val segment = Path()
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
            val color = scope.palette.zoneColor(run.zone)
            val phase = routeStrokePhase(stroke, overlayScale, measure.length * run.startProgress)
            val paint = routePaint(color, overlayScale).applyRouteStroke(stroke, overlayScale, phase)
            drawTraveledGlow(canvas, segment, color, overlayScale, stroke)
            canvas.drawPath(segment, paint)
        }
    }

    /** Soft halo behind the traveled line; only [StoryRouteStroke.NEON] asks for it. */
    private fun drawTraveledGlow(
        canvas: Canvas,
        path: Path,
        color: Int,
        overlayScale: Float,
        stroke: StoryRouteStroke,
    ) {
        if (stroke != StoryRouteStroke.NEON) return
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 24f * overlayScale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
            alpha = 70
        }
        canvas.drawPath(path, glow)
    }

    /**
     * Small ticks at each finished kilometre. They turn the line into a scale:
     * the viewer can see "how many km until the card" without reading numbers.
     */
    private fun drawKmTicks(canvas: Canvas, plan: StoryPlan, measure: PathMeasure, overlayScale: Float) {
        val splits = plan.allSplits.ifEmpty { plan.splits }
        if (measure.length <= 0f || splits.size < 2) return
        val point = FloatArray(2)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = scope.palette.mapCasing
            alpha = 160
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = scope.palette.textPrimary
            alpha = 190
        }
        splits.drop(1).forEach { split ->
            measure.getPosTan(measure.length * split.progress, point, null)
            canvas.drawCircle(point[0], point[1], 5.5f * overlayScale, ring)
            canvas.drawCircle(point[0], point[1], 3f * overlayScale, dot)
        }
    }

    /** Soft accent halo under the route, centred on the current position. */
    private fun drawSpotlight(canvas: Canvas, x: Float, y: Float, side: Float) {
        if (mapBackground == null) return
        val palette = scope.palette
        val radius = side * 0.55f
        val shader = RadialGradient(
            x,
            y,
            radius,
            intArrayOf(
                scope.withAlpha(palette.accent, 26),
                scope.withAlpha(palette.accent, 0),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
    }

    private fun routePaint(color: Int, scale: Float, alpha: Float = 1f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 11f * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
        this.alpha = (255 * alpha).toInt().coerceIn(0, 255)
    }
}
