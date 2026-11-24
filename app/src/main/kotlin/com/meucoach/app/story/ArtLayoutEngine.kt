package com.meucoach.app.story

import android.graphics.Paint
import android.graphics.Typeface
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtLayout
import com.meucoach.core.domain.story.ArtLayoutSpec
import com.meucoach.core.domain.story.ArtOffset
import com.meucoach.core.domain.story.ArtTemplate

/** Horizontal anchoring of one measured text run. */
enum class ArtTextAlign { LEFT, RIGHT }

/** Immutable rectangle in design units; the renderer only scales it to the canvas. */
data class ArtRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(right >= left) { "Art rect must not be inverted horizontally ($left..$right)." }
        require(bottom >= top) { "Art rect must not be inverted vertically ($top..$bottom)." }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f

    fun shifted(dx: Float, dy: Float): ArtRect = copy(
        left = left + dx,
        top = top + dy,
        right = right + dx,
        bottom = bottom + dy,
    )

    fun expanded(padding: Float): ArtRect = ArtRect(
        left = left - padding,
        top = top - padding,
        right = right + padding,
        bottom = bottom + padding,
    )

    fun inset(padding: Float): ArtRect = ArtRect(
        left = left + padding,
        top = top + padding,
        right = right - padding,
        bottom = bottom - padding,
    )

    fun union(other: ArtRect): ArtRect = ArtRect(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )

    fun contains(other: ArtRect): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
}

/** One measured text run ready to be drawn at [baseline] and [x]. */
data class ArtTextLayout(
    val text: String,
    val x: Float,
    val baseline: Float,
    val size: Float,
    val align: ArtTextAlign,
    val bold: Boolean,
    val bounds: ArtRect,
) {
    init {
        require(text.isNotEmpty()) { "Art text must not be empty." }
        require(size > 0f) { "Art text size must be positive." }
    }

    fun shifted(dx: Float, dy: Float): ArtTextLayout = copy(
        x = x + dx,
        baseline = baseline + dy,
        bounds = bounds.shifted(dx, dy),
    )
}

/** Brand slot: one logo or the textual wordmark, never scaled by the text scale. */
data class ArtBrandLayout(val bounds: ArtRect, val text: ArtTextLayout?, val logoBounds: ArtRect?)

/** Distance label + number + optional unit. */
data class ArtDistanceLayout(
    val label: ArtTextLayout,
    val number: ArtTextLayout,
    val unit: ArtTextLayout?,
    val bounds: ArtRect,
) {
    fun shifted(dx: Float, dy: Float): ArtDistanceLayout = copy(
        label = label.shifted(dx, dy),
        number = number.shifted(dx, dy),
        unit = unit?.shifted(dx, dy),
        bounds = bounds.shifted(dx, dy),
    )
}

/** Pace and total time stacked against the right margin. */
data class ArtPaceTimeLayout(
    val paceLabel: ArtTextLayout,
    val paceValue: ArtTextLayout,
    val timeLabel: ArtTextLayout,
    val timeValue: ArtTextLayout,
    val bounds: ArtRect,
) {
    fun shifted(dx: Float, dy: Float): ArtPaceTimeLayout = copy(
        paceLabel = paceLabel.shifted(dx, dy),
        paceValue = paceValue.shifted(dx, dy),
        timeLabel = timeLabel.shifted(dx, dy),
        timeValue = timeValue.shifted(dx, dy),
        bounds = bounds.shifted(dx, dy),
    )
}

/** Up to two extras as one value-only line. */
data class ArtExtrasLayout(val line: ArtTextLayout, val bounds: ArtRect) {
    fun shifted(dx: Float, dy: Float): ArtExtrasLayout = copy(
        line = line.shifted(dx, dy),
        bounds = bounds.shifted(dx, dy),
    )
}

/** Square route frame plus the inset path area the normalized route maps into. */
data class ArtRouteLayout(val square: ArtRect, val path: ArtRect)

/** Everything the renderer needs for one card: measured boxes, baselines, route and scrims. */
data class ArtCardLayout(
    val width: Float,
    val height: Float,
    val safeArea: ArtRect,
    val brand: ArtBrandLayout,
    val distance: ArtDistanceLayout,
    val paceTime: ArtPaceTimeLayout,
    val extras: ArtExtrasLayout?,
    val route: ArtRouteLayout,
    val scrims: List<ArtRect>,
)

/** All text the layout needs, already formatted by the renderer. */
data class ArtLayoutText(
    val distanceLabel: String,
    val distanceValue: String,
    val paceLabel: String,
    val paceValue: String,
    val timeLabel: String,
    val timeValue: String,
    val extras: String? = null,
)

/**
 * Design values of the photo scrim (PRD §5.4). These are not `dp`: preview and
 * export scale them by the canvas width, so the JPG does not change with the
 * device density.
 */
object ArtScrim {
    const val COLOR = 0xFF000000.toInt()
    const val ALPHA = 148
    const val PADDING = 36f
    const val RADIUS = 48f
    val argb: Int get() = (ALPHA shl 24) or (COLOR and 0x00FFFFFF)
}

/**
 * Measures text with the real [Typeface] and turns the numeric
 * [ArtLayoutSpec] into boxes in design units (1080 wide). The renderer draws
 * only what this engine returns; preview, export and the future clamp UI share
 * the same positions, so no coordinate is duplicated (ADR 0061 §2.4).
 *
 * Text scale multiplies sizes, line offsets and internal gaps of the text
 * blocks. Brand, route, markers and casing keep their own scale. Offsets are
 * applied after the template and are never multiplied by the text scale.
 */
class ArtLayoutEngine(private val typeface: Typeface) {
    fun layout(
        format: ArtFormat,
        background: ArtBackgroundMode,
        layout: ArtLayout,
        text: ArtLayoutText,
        canvasHeight: Float,
        logoAspectRatio: Float? = null,
    ): ArtCardLayout {
        require(canvasHeight > 0f) { "Canvas height must be positive." }
        val width = ArtLayoutSpec.DESIGN_WIDTH.toFloat()
        val textScale = layout.textScalePercent / 100f
        val safe = safeArea(format, background, canvasHeight)
        val brandTop = brandTop(format, background)
        val brand = brand(brandTop, logoAspectRatio)
        val paceWidth = paceWidth(text, textScale)
        val placed = when (layout.template) {
            ArtTemplate.TOP -> topTemplate(format, text, paceWidth, safe, textScale, brandTop)
            ArtTemplate.CENTER -> centerTemplate(format, text, paceWidth, safe, textScale)
            ArtTemplate.BOTTOM -> bottomTemplate(format, text, paceWidth, safe, textScale, brand)
        }
        val distance = placed.distance.shiftedBy(layout.distanceOffset, width, canvasHeight, safe)
        val paceTime = placed.paceTime.shiftedBy(layout.paceTimeOffset, width, canvasHeight, safe)
        val extras = placed.extras?.shiftedBy(layout.extrasOffset, width, canvasHeight, safe)
        val route = placed.route.shiftedBy(layout.routeOffset, width, canvasHeight, safe)
        val scrims = scrims(background, brand, distance, paceTime, extras)
        return ArtCardLayout(
            width = width,
            height = canvasHeight,
            safeArea = safe,
            brand = brand,
            distance = distance,
            paceTime = paceTime,
            extras = extras,
            route = route,
            scrims = scrims,
        )
    }

    private fun topTemplate(
        format: ArtFormat,
        text: ArtLayoutText,
        paceWidth: Float,
        safe: ArtRect,
        textScale: Float,
        brandTop: Float,
    ): PlacedBlocks {
        val labelBaseline = legacyLabelBaseline(format, brandTop)
        val distance = distance(format, text, paceWidth, safe, textScale, labelBaseline)
        val paceTime = paceTime(text, safe, textScale, labelBaseline)
        val baseBaseline = maxOf(distance.number.baseline, paceTime.timeValue.baseline)
        val extrasText = extrasMeasure(text.extras, safe, textScale)
        val gap = extrasGap(
            bottomFromBase = maxOf(distance.bounds.bottom, paceTime.bounds.bottom) - baseBaseline,
            extrasAscent = extrasText?.ascent ?: 0f,
            textScale = textScale,
        )
        val extras = extrasText?.let { extras(it, baseBaseline + gap) }
        val contentBaseline = extras?.line?.baseline ?: baseBaseline
        val routeTop = contentBaseline + ROUTE_TOP_GAP * textScale
        val route = route(safe, routeTop, safe.bottom, centerVertically = true)
        return PlacedBlocks(distance, paceTime, extras, route)
    }

    private fun centerTemplate(
        format: ArtFormat,
        text: ArtLayoutText,
        paceWidth: Float,
        safe: ArtRect,
        textScale: Float,
    ): PlacedBlocks {
        val metrics = groupMetrics(format, text, paceWidth, safe, textScale)
        val groupTop = (safe.top + (safe.height - metrics.height) / 2f).coerceAtLeast(safe.top)
        val labelBaseline = groupTop - metrics.labelAscent
        val distance = distance(format, text, paceWidth, safe, textScale, labelBaseline)
        val paceTime = paceTime(text, safe, textScale, labelBaseline)
        val extras = extrasMeasure(text.extras, safe, textScale)
            ?.let { extras(it, metrics.extrasBaseline + labelBaseline) }
        val groupBottom = maxOf(
            distance.bounds.bottom,
            paceTime.bounds.bottom,
            extras?.bounds?.bottom ?: Float.NEGATIVE_INFINITY,
        )
        val routeTop = groupBottom + ArtLayoutSpec.ROUTE_GAP * textScale
        val route = route(safe, routeTop, safe.bottom, centerVertically = false)
        return PlacedBlocks(distance, paceTime, extras, route)
    }

    private fun bottomTemplate(
        format: ArtFormat,
        text: ArtLayoutText,
        paceWidth: Float,
        safe: ArtRect,
        textScale: Float,
        brand: ArtBrandLayout,
    ): PlacedBlocks {
        val metrics = groupMetrics(format, text, paceWidth, safe, textScale)
        val labelBaseline = safe.bottom - metrics.bottomFromBaseline
        val distance = distance(format, text, paceWidth, safe, textScale, labelBaseline)
        val paceTime = paceTime(text, safe, textScale, labelBaseline)
        val extras = extrasMeasure(text.extras, safe, textScale)
            ?.let { extras(it, metrics.extrasBaseline + labelBaseline) }
        val groupTop = minOf(
            distance.bounds.top,
            paceTime.bounds.top,
            extras?.bounds?.top ?: Float.POSITIVE_INFINITY,
        )
        val routeTop = brand.bounds.bottom + ArtLayoutSpec.BRAND_GAP
        val routeBottom = groupTop - ArtLayoutSpec.ROUTE_GAP * textScale
        val route = route(safe, routeTop, routeBottom, centerVertically = false)
        return PlacedBlocks(distance, paceTime, extras, route)
    }

    private fun legacyLabelBaseline(format: ArtFormat, brandTop: Float): Float {
        val base = if (format == ArtFormat.SQUARE) SQUARE_LABEL_BASELINE else LABEL_BASELINE
        return base + (brandTop - ArtLayoutSpec.BRAND_TOP)
    }

    private fun distance(
        format: ArtFormat,
        text: ArtLayoutText,
        paceWidth: Float,
        safe: ArtRect,
        textScale: Float,
        labelBaseline: Float,
    ): ArtDistanceLayout {
        val label = measured(text.distanceLabel, ArtLayoutSpec.DISTANCE_LABEL_SIZE * textScale, bold = true)
            .layout(safe.left, labelBaseline, ArtTextAlign.LEFT)
        val number = distanceNumber(format, text, paceWidth, safe, textScale)
        val unitText = distanceUnit(text)
        val unit = unitText?.let { measured(it, UNIT_SIZE * textScale, bold = true) }
        val distanceBaseline = labelBaseline + number.size + DISTANCE_LINE_GAP * textScale
        val numberLayout = number.layout(safe.left, distanceBaseline, ArtTextAlign.LEFT)
        val unitLayout = unit?.layout(
            x = safe.left + number.width + UNIT_GAP * textScale,
            baseline = distanceBaseline,
            align = ArtTextAlign.LEFT,
        )
        var bounds = label.bounds.union(numberLayout.bounds)
        if (unitLayout != null) bounds = bounds.union(unitLayout.bounds)
        return ArtDistanceLayout(label, numberLayout, unitLayout, bounds)
    }

    private fun distanceNumber(
        format: ArtFormat,
        text: ArtLayoutText,
        paceWidth: Float,
        safe: ArtRect,
        textScale: Float,
    ): MeasuredText {
        val unitWidth = distanceUnit(text)
            ?.let { measured(it, UNIT_SIZE * textScale, bold = true).width } ?: 0f
        val reserve = paceWidth + ArtLayoutSpec.BLOCK_GAP * textScale
        val maxNumberWidth = (safe.width - unitWidth - UNIT_GAP * textScale - reserve)
            .coerceAtLeast(MIN_TEXT_SIZE)
        val preferred = distanceSize(format) * textScale
        return fitted(distanceNumberText(text), preferred, bold = true, maxWidth = maxNumberWidth)
    }

    private fun distanceSize(format: ArtFormat): Float = when (format) {
        ArtFormat.PORTRAIT_4_5 -> ArtLayoutSpec.DISTANCE_SIZE_4_5.toFloat()
        ArtFormat.SQUARE -> ArtLayoutSpec.DISTANCE_SIZE_SQUARE.toFloat()
        ArtFormat.PORTRAIT_9_16 -> ArtLayoutSpec.DISTANCE_SIZE_9_16.toFloat()
    }

    private fun distanceNumberText(text: ArtLayoutText): String =
        text.distanceValue.substringBeforeLast(" ").ifBlank { text.distanceValue }

    private fun distanceUnit(text: ArtLayoutText): String? {
        val number = distanceNumberText(text)
        return text.distanceValue.substringAfterLast(" ", "")
            .takeIf { it.isNotBlank() && number != text.distanceValue }
    }

    private fun paceTime(
        text: ArtLayoutText,
        safe: ArtRect,
        textScale: Float,
        labelBaseline: Float,
    ): ArtPaceTimeLayout {
        val labelSize = ArtLayoutSpec.PACE_TIME_LABEL_SIZE * textScale
        val valueSize = ArtLayoutSpec.PACE_TIME_VALUE_SIZE * textScale
        val paceLabel = measured(text.paceLabel, labelSize, bold = true)
            .layout(safe.right, labelBaseline, ArtTextAlign.RIGHT)
        val paceValue = measured(text.paceValue, valueSize, bold = true)
            .layout(safe.right, labelBaseline + PACE_VALUE_OFFSET * textScale, ArtTextAlign.RIGHT)
        val timeLabel = measured(text.timeLabel, labelSize, bold = true)
            .layout(safe.right, labelBaseline + TIME_LABEL_OFFSET * textScale, ArtTextAlign.RIGHT)
        val timeValue = measured(text.timeValue, valueSize, bold = true)
            .layout(safe.right, labelBaseline + TIME_VALUE_OFFSET * textScale, ArtTextAlign.RIGHT)
        val bounds = paceLabel.bounds
            .union(paceValue.bounds)
            .union(timeLabel.bounds)
            .union(timeValue.bounds)
        return ArtPaceTimeLayout(paceLabel, paceValue, timeLabel, timeValue, bounds)
    }

    private fun paceWidth(text: ArtLayoutText, textScale: Float): Float {
        val labelSize = ArtLayoutSpec.PACE_TIME_LABEL_SIZE * textScale
        val valueSize = ArtLayoutSpec.PACE_TIME_VALUE_SIZE * textScale
        return maxOf(
            measured(text.paceLabel, labelSize, bold = true).width,
            measured(text.paceValue, valueSize, bold = true).width,
            measured(text.timeLabel, labelSize, bold = true).width,
            measured(text.timeValue, valueSize, bold = true).width,
        )
    }

    private fun extrasMeasure(text: String?, safe: ArtRect, textScale: Float): MeasuredText? =
        text?.takeIf { it.isNotBlank() }
            ?.let { fitted(it, ArtLayoutSpec.EXTRA_SIZE * textScale, bold = false, maxWidth = safe.width) }

    private fun extras(line: MeasuredText, baseline: Float): ArtExtrasLayout {
        val layout = line.layout(
            x = ArtLayoutSpec.MARGIN.toFloat(),
            baseline = baseline,
            align = ArtTextAlign.LEFT,
        )
        return ArtExtrasLayout(layout, layout.bounds)
    }

    private fun extrasGap(bottomFromBase: Float, extrasAscent: Float, textScale: Float): Float {
        // At 100% the legacy 65 px gap is the frozen ADR 0060 baseline; other
        // scales grow the gap only when the measured descents would collide.
        if (textScale == 1f) return EXTRAS_GAP
        val required = bottomFromBase - extrasAscent
        return maxOf(EXTRAS_GAP * textScale, required)
    }

    private fun groupMetrics(
        format: ArtFormat,
        text: ArtLayoutText,
        paceWidth: Float,
        safe: ArtRect,
        textScale: Float,
    ): GroupMetrics {
        val label = measured(text.distanceLabel, ArtLayoutSpec.DISTANCE_LABEL_SIZE * textScale, bold = true)
        val number = distanceNumber(format, text, paceWidth, safe, textScale)
        val distanceLine = number.size + DISTANCE_LINE_GAP * textScale
        val distanceBottom = distanceLine + number.descent
        val valueSize = ArtLayoutSpec.PACE_TIME_VALUE_SIZE * textScale
        val paceBottom = TIME_VALUE_OFFSET * textScale + fontDescent(valueSize, bold = true)
        val baseOffset = maxOf(distanceLine, TIME_VALUE_OFFSET * textScale)
        val extrasText = extrasMeasure(text.extras, safe, textScale)
        val gap = extrasGap(
            bottomFromBase = maxOf(distanceBottom, paceBottom) - baseOffset,
            extrasAscent = extrasText?.ascent ?: 0f,
            textScale = textScale,
        )
        val extrasBaseline = baseOffset + gap
        val extrasBottom = extrasText?.let { extrasBaseline + it.descent } ?: Float.NEGATIVE_INFINITY
        val bottomFromBaseline = maxOf(distanceBottom, paceBottom, extrasBottom)
        return GroupMetrics(
            height = bottomFromBaseline - label.ascent,
            bottomFromBaseline = bottomFromBaseline,
            extrasBaseline = extrasBaseline,
            labelAscent = label.ascent,
        )
    }

    private fun route(safe: ArtRect, top: Float, bottom: Float, centerVertically: Boolean): ArtRouteLayout {
        val available = (bottom - top).coerceAtLeast(0f)
        val side = minOf(safe.width, available)
        val left = safe.centerX - side / 2f
        val areaTop = if (centerVertically) top + (available - side) / 2f else top
        val square = ArtRect(left, areaTop, left + side, areaTop + side)
        return ArtRouteLayout(square, square.inset(minOf(ArtLayoutSpec.ROUTE_INSET.toFloat(), side / 2f)))
    }

    private fun safeArea(format: ArtFormat, background: ArtBackgroundMode, height: Float): ArtRect {
        val side = ArtLayoutSpec.MARGIN.toFloat()
        val photoStory = format == ArtFormat.PORTRAIT_9_16 && background == ArtBackgroundMode.PHOTO
        val top = if (photoStory) ArtLayoutSpec.PHOTO_9X16_SAFE_TOP.toFloat() else side
        val bottom = if (photoStory) {
            height - ArtLayoutSpec.PHOTO_9X16_SAFE_BOTTOM
        } else {
            height - side
        }
        return ArtRect(side, top, ArtLayoutSpec.DESIGN_WIDTH - side, bottom)
    }

    private fun brandTop(format: ArtFormat, background: ArtBackgroundMode): Float =
        if (format == ArtFormat.PORTRAIT_9_16 && background == ArtBackgroundMode.PHOTO) {
            ArtLayoutSpec.PHOTO_9X16_SAFE_TOP.toFloat()
        } else {
            ArtLayoutSpec.BRAND_TOP.toFloat()
        }

    private fun brand(top: Float, logoAspectRatio: Float?): ArtBrandLayout {
        val margin = ArtLayoutSpec.MARGIN.toFloat()
        val height = ArtLayoutSpec.BRAND_HEIGHT.toFloat()
        if (logoAspectRatio != null && logoAspectRatio > 0f) {
            val width = height * logoAspectRatio
            val bounds = ArtRect(margin, top, margin + width, top + height)
            return ArtBrandLayout(bounds, text = null, logoBounds = bounds)
        }
        val wordmark = measured("MEUCOACH", ArtLayoutSpec.BRAND_TEXT_SIZE.toFloat(), bold = true)
            .layout(margin, top + BRAND_TEXT_BASELINE, ArtTextAlign.LEFT)
        val bounds = ArtRect(margin, top, margin + maxOf(wordmark.bounds.width, 1f), top + height)
        return ArtBrandLayout(bounds, text = wordmark, logoBounds = null)
    }

    private fun scrims(
        background: ArtBackgroundMode,
        brand: ArtBrandLayout,
        distance: ArtDistanceLayout,
        paceTime: ArtPaceTimeLayout,
        extras: ArtExtrasLayout?,
    ): List<ArtRect> {
        if (background != ArtBackgroundMode.PHOTO) return emptyList()
        return buildList {
            add(brand.bounds.expanded(ArtScrim.PADDING))
            add(distance.bounds.expanded(ArtScrim.PADDING))
            add(paceTime.bounds.expanded(ArtScrim.PADDING))
            extras?.let { add(it.bounds.expanded(ArtScrim.PADDING)) }
        }
    }

    private fun measured(text: String, size: Float, bold: Boolean): MeasuredText {
        val paint = paint(size, bold)
        val metrics = paint.fontMetrics
        return MeasuredText(
            text = text,
            width = paint.measureText(text),
            ascent = metrics.ascent,
            descent = metrics.descent,
            size = size,
            bold = bold,
        )
    }

    private fun fitted(text: String, preferred: Float, bold: Boolean, maxWidth: Float): MeasuredText {
        val paint = paint(preferred, bold)
        paint.textSize = fittedTextSize(paint, text, maxWidth, preferred)
        val metrics = paint.fontMetrics
        return MeasuredText(
            text = text,
            width = paint.measureText(text),
            ascent = metrics.ascent,
            descent = metrics.descent,
            size = paint.textSize,
            bold = bold,
        )
    }

    private fun fontDescent(size: Float, bold: Boolean): Float = paint(size, bold).fontMetrics.descent

    private fun paint(size: Float, bold: Boolean): Paint = storyTextPaint(0xFFFFFFFF.toInt(), size, typeface, bold)

    private data class MeasuredText(
        val text: String,
        val width: Float,
        val ascent: Float,
        val descent: Float,
        val size: Float,
        val bold: Boolean,
    ) {
        fun layout(x: Float, baseline: Float, align: ArtTextAlign): ArtTextLayout {
            val left = if (align == ArtTextAlign.LEFT) x else x - width
            return ArtTextLayout(
                text = text,
                x = x,
                baseline = baseline,
                size = size,
                align = align,
                bold = bold,
                bounds = ArtRect(left, baseline + ascent, left + width, baseline + descent),
            )
        }
    }

    private data class PlacedBlocks(
        val distance: ArtDistanceLayout,
        val paceTime: ArtPaceTimeLayout,
        val extras: ArtExtrasLayout?,
        val route: ArtRouteLayout,
    )

    private data class GroupMetrics(
        val height: Float,
        val bottomFromBaseline: Float,
        val extrasBaseline: Float,
        val labelAscent: Float,
    )

    private fun ArtDistanceLayout.shiftedBy(
        offset: ArtOffset,
        width: Float,
        height: Float,
        safe: ArtRect,
    ): ArtDistanceLayout {
        val (dx, dy) = clampShift(bounds, safe, offset.dx * width, offset.dy * height)
        return shifted(dx, dy)
    }

    private fun ArtPaceTimeLayout.shiftedBy(
        offset: ArtOffset,
        width: Float,
        height: Float,
        safe: ArtRect,
    ): ArtPaceTimeLayout {
        val (dx, dy) = clampShift(bounds, safe, offset.dx * width, offset.dy * height)
        return shifted(dx, dy)
    }

    private fun ArtExtrasLayout.shiftedBy(
        offset: ArtOffset,
        width: Float,
        height: Float,
        safe: ArtRect,
    ): ArtExtrasLayout {
        val (dx, dy) = clampShift(bounds, safe, offset.dx * width, offset.dy * height)
        return shifted(dx, dy)
    }

    private fun ArtRouteLayout.shiftedBy(
        offset: ArtOffset,
        width: Float,
        height: Float,
        safe: ArtRect,
    ): ArtRouteLayout {
        val side = square.width
        val minLeft = safe.left
        val maxLeft = safe.right - side
        val minTop = safe.top
        val maxTop = safe.bottom - side
        val left = if (minLeft <= maxLeft) {
            (square.left + offset.dx * width).coerceIn(minLeft, maxLeft)
        } else {
            safe.centerX - side / 2f
        }
        val top = if (minTop <= maxTop) {
            (square.top + offset.dy * height).coerceIn(minTop, maxTop)
        } else {
            safe.top + (safe.height - side) / 2f
        }
        val shifted = ArtRect(left, top, left + side, top + side)
        return ArtRouteLayout(shifted, shifted.inset(ArtLayoutSpec.ROUTE_INSET.toFloat()))
    }

    private fun clampShift(bounds: ArtRect, safe: ArtRect, dx: Float, dy: Float): Pair<Float, Float> {
        val minDx = safe.left - bounds.left
        val maxDx = safe.right - bounds.right
        val minDy = safe.top - bounds.top
        val maxDy = safe.bottom - bounds.bottom
        val effectiveDx = if (minDx <= maxDx) dx.coerceIn(minDx, maxDx) else safe.centerX - bounds.centerX
        val effectiveDy = if (minDy <= maxDy) {
            dy.coerceIn(minDy, maxDy)
        } else {
            (safe.top + safe.bottom) / 2f - (bounds.top + bounds.bottom) / 2f
        }
        return effectiveDx to effectiveDy
    }

    private companion object {
        const val LABEL_BASELINE = 307f
        const val SQUARE_LABEL_BASELINE = 265f
        const val DISTANCE_LINE_GAP = 18f
        const val UNIT_SIZE = 48f
        const val UNIT_GAP = 14f
        const val PACE_VALUE_OFFSET = 46f
        const val TIME_LABEL_OFFSET = 98f
        const val TIME_VALUE_OFFSET = 144f
        const val EXTRAS_GAP = 65f
        const val ROUTE_TOP_GAP = 70f
        const val BRAND_TEXT_BASELINE = 36f
        const val MIN_TEXT_SIZE = 8f
    }
}
