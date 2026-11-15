package com.meucoach.app.story.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.meucoach.app.story.StoryMapSnapshot
import com.meucoach.core.domain.story.StoryPlan

/**
 * Map snapshots and the animated route. One active keyframe per frame; the
 * opening bookend dissolves into the follow shot through a radial reveal
 * around the runner (never a map crossfade). The stroke styling lives in
 * [StoryRoutePainter]; the route vertices come from [StoryRouteSource].
 */
internal class StoryRouteRenderer(
    scope: StoryDrawScope,
    avatarBitmap: Bitmap?,
    private val follow: StoryMapSnapshot?,
) {
    private val painter = StoryRoutePainter(scope, avatarBitmap, follow)

    fun drawCoverage(
        canvas: Canvas,
        frame: StoryCameraFrame,
        plan: StoryPlan,
        timeSec: Float,
        opening: StoryMapSnapshot?,
        active: StoryMapSnapshot?,
        followBlend: Float,
    ): FloatArray? {
        if (opening != null && follow != null && followBlend < 1f) {
            return drawBlend(canvas, frame, plan, timeSec, opening, followBlend)
        }
        var marker: FloatArray? = null
        withCamera(frame.cameraMatrix, canvas) {
            drawMapLayer(canvas, frame, active?.bitmap)
            val activeRoute = StoryRouteSource.of(plan, active, frame, active != null)
            marker = painter.drawRoute(canvas, plan, timeSec, frame, activeRoute)
        }
        return marker
    }

    fun drawAvatarPin(canvas: Canvas, x: Float, y: Float, scale: Float) {
        painter.drawAvatarPin(canvas, x, y, scale)
    }

    private fun drawBlend(
        canvas: Canvas,
        frame: StoryCameraFrame,
        plan: StoryPlan,
        timeSec: Float,
        opening: StoryMapSnapshot,
        followBlend: Float,
    ): FloatArray? {
        val openingRoute = StoryRouteSource.of(plan, opening, frame, hasMap = true)
        val followRoute = StoryRouteSource.of(plan, follow, frame, hasMap = true)
        var marker: FloatArray? = null
        withCamera(frame.cameraMatrix, canvas) {
            drawMapLayer(canvas, frame, opening.bitmap)
            marker = painter.drawRoute(canvas, plan, timeSec, frame, openingRoute)
        }
        if (followBlend <= 0f) {
            // First frames while the blend is still zero: opening only, drawn
            // twice. The second pass matches the original overdraw, so the
            // stroked route keeps its exact opacity.
            withCamera(frame.cameraMatrix, canvas) {
                drawMapLayer(canvas, frame, opening.bitmap)
                marker = painter.drawRoute(canvas, plan, timeSec, frame, openingRoute)
            }
            return marker
        }
        // Radial reveal around the runner: the follow shot opens over the
        // opening without the double exposure of a map crossfade.
        val layer = canvas.saveLayer(0f, 0f, frame.width.toFloat(), frame.height.toFloat(), null)
        var followPosition: FloatArray? = null
        withCamera(frame.cameraMatrix, canvas) {
            drawMapLayer(canvas, frame, follow?.bitmap)
            followPosition = painter.drawRoute(canvas, plan, timeSec, frame, followRoute)
        }
        val center = followPosition?.copyOf() ?: floatArrayOf(frame.width / 2f, frame.height / 2f)
        frame.cameraMatrix.mapPoints(center)
        val maxRadius = kotlin.math.hypot(
            maxOf(center[0], frame.width - center[0]).toDouble(),
            maxOf(center[1], frame.height - center[1]).toDouble(),
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
        canvas.drawRect(0f, 0f, frame.width.toFloat(), frame.height.toFloat(), mask)
        canvas.restoreToCount(layer)
        return followPosition
    }

    private fun withCamera(cameraMatrix: Matrix, canvas: Canvas, draw: () -> Unit) {
        canvas.save()
        canvas.concat(cameraMatrix)
        draw()
        canvas.restore()
    }

    private fun drawMapLayer(canvas: Canvas, frame: StoryCameraFrame, map: Bitmap?) {
        if (map == null) return
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            // The colour grade used to run per frame; keeping the source style as
            // is saves ~10 ms/frame on software Canvas. Re-enable only with a
            // once-per-export bake.
            colorFilter = null
        }
        canvas.drawBitmap(map, null, RectF(0f, 0f, frame.width.toFloat(), frame.height.toFloat()), paint)
    }
}
