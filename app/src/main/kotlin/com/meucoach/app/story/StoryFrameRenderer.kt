package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.meucoach.app.story.render.StoryBackdropRenderer
import com.meucoach.app.story.render.StoryCameraFrame
import com.meucoach.app.story.render.StoryCardRenderer
import com.meucoach.app.story.render.StoryDrawScope
import com.meucoach.app.story.render.StoryFinalRenderer
import com.meucoach.app.story.render.StoryHeaderRenderer
import com.meucoach.app.story.render.StoryRouteRenderer
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRecipe
import com.meucoach.core.domain.story.StoryRenderSpec
import com.meucoach.core.domain.story.StoryRenderSpecResolver
import com.meucoach.core.domain.story.StorySegmentType

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

/** Shrinks the paint so [text] fits [maxWidth]; never grows past [preferred]. */
internal fun fittedTextSize(paint: Paint, text: String, maxWidth: Float, preferred: Float): Float {
    paint.textSize = preferred
    val measured = paint.measureText(text)
    if (measured <= maxWidth || measured <= 0f) return preferred
    return (preferred * maxWidth / measured).coerceAtLeast(8f)
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
 *
 * The drawing itself lives in `story/render` (VIS-07): header, card, final,
 * route and backdrop renderers. This class only resolves the spec, picks the
 * active keyframe snapshot and dispatches; it decides no metric.
 */
class StoryFrameRenderer(
    private val palette: StoryPalette,
    private val typeface: Typeface,
    private val logoBitmap: Bitmap? = null,
    private val mapBackground: StoryMapSnapshot? = null,
    private val avatarBitmap: Bitmap? = null,
    private val openingMap: StoryMapSnapshot? = null,
    private val finalMap: StoryMapSnapshot? = null,
    private val recipe: StoryRecipe = StoryRecipe(),
) {
    private val scope = StoryDrawScope(palette, typeface, logoBitmap)
    private val header = StoryHeaderRenderer(scope)
    private val cards = StoryCardRenderer(scope)
    private val closing = StoryFinalRenderer(scope)
    private val route = StoryRouteRenderer(scope, avatarBitmap, mapBackground)
    private val backdrop = StoryBackdropRenderer(scope)

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
        val spec = specFor(plan)
        val highlights = spec.highlights
        // One active keyframe per frame: the opening bookend dissolves into the
        // follow shot, then hard cuts on card/final boundaries hide the angle
        // change. Every keyframe draws its own native pixels, so the route
        // never jumps even though the pitch does.
        val opening = openingMap ?: mapBackground
        val closingMap = finalMap ?: mapBackground
        val active = when (StoryMapScript.keyframeAt(plan, timeSec)) {
            StoryKeyframe.OPENING -> opening
            StoryKeyframe.FOLLOW -> mapBackground
            StoryKeyframe.FINAL -> closingMap
        }
        val followBlend = if (openingMap != null && mapBackground != null) {
            StoryMapScript.followBlend(timeSec)
        } else {
            1f
        }

        canvas.drawColor(palette.background)
        val marker = route.drawCoverage(
            canvas,
            StoryCameraFrame(cameraMatrix, square, overlayScale, width, height),
            plan,
            timeSec,
            opening,
            active,
            followBlend,
        )
        // The avatar pin is screen-space on purpose: it must stay upright and
        // legible while the map rotates/zooms under the camera.
        if (avatarBitmap != null && marker != null) {
            val screen = floatArrayOf(marker[0], marker[1])
            cameraMatrix.mapPoints(screen)
            route.drawAvatarPin(canvas, screen[0], screen[1], scale)
        }
        backdrop.drawScrims(canvas, width, height, scale, isFinal)
        header.drawHeader(canvas, spec, width, height, scale)
        when {
            segment?.type == StorySegmentType.CARD && segment.split != null ->
                cards.drawCard(canvas, spec, segment, width, height, scale)

            isFinal ->
                closing.drawFinal(canvas, spec, width, height, scale, highlights)
        }
        if (mapBackground != null || openingMap != null || finalMap != null) {
            backdrop.drawAttribution(canvas, width, height, scale)
        }
        backdrop.drawVignette(canvas, width, height)
        backdrop.drawOpeningFade(canvas, timeSec, width, height)
    }
}
