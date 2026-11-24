package com.meucoach.core.domain.story

/** Background of the art card (ADR 0061 §2.1); transparent remains the default. */
enum class ArtBackgroundMode { TRANSPARENT, PHOTO }

/** Independently movable groups of the art layout (ADR 0061 §2.4). */
enum class ArtBlock { DISTANCE, PACE_TIME, EXTRAS, ROUTE }

/** Preset that anchors the blocks before the per-block offsets are applied. */
enum class ArtTemplate { TOP, CENTER, BOTTOM }

/**
 * Offset of one block as a fraction of the canvas: [dx] of the width and [dy]
 * of the height. Zero means the template position; offsets are applied after
 * the template and are never multiplied by the text scale. The range reaches
 * a full canvas so narrow blocks can still touch the free-region edges; the
 * box-aware clamp in the layout engine keeps every block on-screen, and the
 * Studio persists the effective value.
 */
data class ArtOffset(val dx: Float = 0f, val dy: Float = 0f) {
    init {
        require(dx.isFinite() && dy.isFinite()) { "Art offset must be finite (dx=$dx, dy=$dy)." }
        require(dx in MIN..MAX && dy in MIN..MAX) {
            "Art offset must stay within a full canvas (dx=$dx, dy=$dy)."
        }
    }

    companion object {
        const val MIN = -1f
        const val MAX = 1f
    }
}

/**
 * Position and scale of the four art blocks. Each block keeps its own offset
 * so moving the distance never moves the route. [textScalePercent] multiplies
 * text sizes, line heights and internal paddings in the layout engine; the
 * brand, route and casing do not scale (ADR 0061 §2.4).
 */
data class ArtLayout(
    val template: ArtTemplate = ArtTemplate.TOP,
    val distanceOffset: ArtOffset = ArtOffset(),
    val paceTimeOffset: ArtOffset = ArtOffset(),
    val extrasOffset: ArtOffset = ArtOffset(),
    val routeOffset: ArtOffset = ArtOffset(),
    val textScalePercent: Int = DEFAULT_TEXT_SCALE_PERCENT,
) {
    init {
        require(textScalePercent in MIN_TEXT_SCALE_PERCENT..MAX_TEXT_SCALE_PERCENT) {
            "Text scale must be between 80 and 130 percent: $textScalePercent."
        }
    }

    fun offset(block: ArtBlock): ArtOffset = when (block) {
        ArtBlock.DISTANCE -> distanceOffset
        ArtBlock.PACE_TIME -> paceTimeOffset
        ArtBlock.EXTRAS -> extrasOffset
        ArtBlock.ROUTE -> routeOffset
    }

    fun withOffset(block: ArtBlock, offset: ArtOffset): ArtLayout = when (block) {
        ArtBlock.DISTANCE -> copy(distanceOffset = offset)
        ArtBlock.PACE_TIME -> copy(paceTimeOffset = offset)
        ArtBlock.EXTRAS -> copy(extrasOffset = offset)
        ArtBlock.ROUTE -> copy(routeOffset = offset)
    }

    companion object {
        const val MIN_TEXT_SCALE_PERCENT = 80
        const val MAX_TEXT_SCALE_PERCENT = 130
        const val DEFAULT_TEXT_SCALE_PERCENT = 100
    }
}

/**
 * Numeric layout values in design pixels of a 1080-wide canvas. The canvas
 * grows to the format height (1350/1080/1920); previews scale these values and
 * exports use them at 1080. The app measures the real text boxes and applies
 * the templates/offsets — the domain only owns the shared numbers, so preview,
 * export and clamp cannot drift (ADR 0061 §2.4).
 */
object ArtLayoutSpec {
    const val DESIGN_WIDTH = 1080

    const val MARGIN = 88
    const val BRAND_TOP = 84
    const val BRAND_HEIGHT = 44
    const val BRAND_GAP = 56
    const val BLOCK_GAP = 32
    const val ROUTE_GAP = 56
    const val ROUTE_INSET = 24
    const val MIN_ROUTE_SIDE = 280

    /**
     * Route square cap in design pixels. The square still fills the free space
     * of short cards (1:1 stays height-bound); on tall cards (4:5, 9:16) the
     * cap keeps the route from dominating the card, and the leftover space
     * becomes breathing room around it.
     */
    const val MAX_ROUTE_SIDE = 640

    /**
     * Bottom inset of the template safe area on transparent cards. Larger than
     * [MARGIN] so the text group and the route never hug the bottom edge.
     * The 9:16 photo mode keeps [PHOTO_9X16_SAFE_BOTTOM] for the native UI.
     */
    const val BOTTOM_MARGIN = 150

    /**
     * Inset of the free nudge region from the canvas edges. Templates place
     * the blocks inside the safe area, but the user may push any block out to
     * this region — positioning freedom, not a silent correction.
     */
    const val NUDGE_MARGIN = 32

    /** Native story UI reserved at the top and bottom of the 9:16 photo mode. */
    const val PHOTO_9X16_SAFE_TOP = 250
    const val PHOTO_9X16_SAFE_BOTTOM = 340

    /** Base sizes at 100%; the layout engine multiplies text by the scale. */
    const val DISTANCE_SIZE_4_5 = 135
    const val DISTANCE_SIZE_9_16 = 135
    const val DISTANCE_SIZE_SQUARE = 117
    const val DISTANCE_LABEL_SIZE = 24
    const val PACE_TIME_LABEL_SIZE = 22
    const val PACE_TIME_VALUE_SIZE = 52
    const val EXTRA_SIZE = 34
    const val BRAND_TEXT_SIZE = 30
}
