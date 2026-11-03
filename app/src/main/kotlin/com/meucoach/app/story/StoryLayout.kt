package com.meucoach.app.story

/**
 * Shared 9:16 geometry for the story renderer.
 *
 * Instagram Stories cover roughly 13% of a 1080×1920 video at the top and at
 * the bottom with its own UI. Every anchored element (header, route square,
 * card, attribution) is positioned from these fractions so the brand, the
 * numbers and the map attribution stay visible after posting.
 *
 * Positions are fractions of the frame height; horizontal margins scale with
 * the width, like the text sizes in [StoryFrameRenderer].
 */
object StoryLayout {
    const val FRAME_WIDTH = 720f
    const val REFERENCE_HEIGHT = 1280f

    /** Fraction of the height hidden by the Instagram header. */
    const val SAFE_TOP_FRACTION = 0.13f

    /** Fraction of the height hidden by the Instagram footer. */
    const val SAFE_BOTTOM_FRACTION = 0.87f

    private const val LOGO_TOP = 174f / REFERENCE_HEIGHT
    private const val LOGO_HEIGHT = 38f / REFERENCE_HEIGHT
    private const val OVERLINE_BASELINE = 202f / REFERENCE_HEIGHT
    private const val METRIC_BASELINE = 292f / REFERENCE_HEIGHT

    private const val ROUTE_EXTENT_TOP = 340f / REFERENCE_HEIGHT
    private const val ROUTE_EXTENT_BOTTOM = 920f / REFERENCE_HEIGHT
    private const val ROUTE_MARGIN = 56f

    private const val CARD_TOP = 0.66f
    private const val CARD_BOTTOM = 0.84f

    private const val ATTRIBUTION_BASELINE = 1105f / REFERENCE_HEIGHT

    fun safeTop(height: Float): Float = height * SAFE_TOP_FRACTION
    fun safeBottom(height: Float): Float = height * SAFE_BOTTOM_FRACTION

    fun logoTop(height: Float): Float = height * LOGO_TOP
    fun logoHeight(height: Float): Float = height * LOGO_HEIGHT
    fun overlineBaseline(height: Float): Float = height * OVERLINE_BASELINE
    fun metricBaseline(height: Float): Float = height * METRIC_BASELINE

    fun margin(width: Float): Float = ROUTE_MARGIN * (width / FRAME_WIDTH)

    fun routeTop(height: Float): Float = height * ROUTE_EXTENT_TOP
    fun routeBottom(height: Float): Float = height * ROUTE_EXTENT_BOTTOM

    fun cardTop(height: Float): Float = height * CARD_TOP
    fun cardBottom(height: Float): Float = height * CARD_BOTTOM

    fun attributionBaseline(height: Float): Float = height * ATTRIBUTION_BASELINE

    /** Square where the route is projected and where the map camera centres the bounds. */
    data class RouteSquare(val left: Float, val top: Float, val side: Float) {
        val centerX: Float get() = left + side / 2f
        val centerY: Float get() = top + side / 2f
    }

    /**
     * Square the route is projected into. The camera pushes up to
     * [StoryMotion.MAX_CAMERA_SCALE], so the square is pre-shrunk: after the
     * push the route fills exactly the reserved extent (top 340, bottom 920 on
     * the 720×1280 reference) and never invades the header.
     */
    fun routeSquare(width: Float, height: Float): RouteSquare {
        val margin = margin(width)
        val top = routeTop(height)
        val bottom = routeBottom(height)
        val side = minOf(width - 2f * margin, (bottom - top) / StoryMotion.MAX_CAMERA_SCALE)
        val centerY = (top + bottom) / 2f
        return RouteSquare(
            left = width / 2f - side / 2f,
            top = centerY - side / 2f,
            side = side,
        )
    }
}
