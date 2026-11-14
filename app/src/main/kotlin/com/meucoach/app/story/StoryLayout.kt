package com.meucoach.app.story

/**
 * Shared 9:16 geometry for the story renderer.
 *
 * Instagram Stories cover roughly 13% of a 1080×1920 video at the top and at
 * the bottom with its own UI, but Reels covers ~420 px (22%) at the bottom and
 * TikTok reaches ~484 px. The header, route square and card are positioned from
 * these fractions so the brand and the numbers stay visible after posting.
 * The OSM attribution is the exception: it stays pinned to the bottom-left
 * corner by product decision, even if the platform UI covers it.
 *
 * Positions are fractions of the frame height; horizontal margins scale with
 * the width, like the text sizes in [StoryFrameRenderer].
 */
object StoryLayout {
    const val FRAME_WIDTH = 720f
    const val REFERENCE_HEIGHT = 1280f

    /** Fraction of the height hidden by the Instagram header. */
    const val SAFE_TOP_FRACTION = 0.13f

    /** Fraction of the height hidden by the Reels/TikTok footer (≈420 px). */
    const val SAFE_BOTTOM_FRACTION = 0.78f

    private const val LOGO_TOP = 174f / REFERENCE_HEIGHT
    private const val LOGO_HEIGHT = 38f / REFERENCE_HEIGHT
    private const val OVERLINE_BASELINE = 202f / REFERENCE_HEIGHT
    private const val METRIC_BASELINE = 292f / REFERENCE_HEIGHT

    private const val ROUTE_EXTENT_TOP = 340f / REFERENCE_HEIGHT
    private const val ROUTE_EXTENT_BOTTOM = 0.76f
    private const val ROUTE_MARGIN = 56f

    private const val CARD_TOP = 0.62f
    private const val CARD_BOTTOM = 0.78f

    /**
     * OSM attribution is pinned to the bottom-left edge on purpose. The user 0
     * accepted that it may sit under the Reels/TikTok UI; keeping the legal
     * credit at the very corner is the product decision for this format.
     */
    private const val ATTRIBUTION_SIDE_INSET = 12f / FRAME_WIDTH
    private const val ATTRIBUTION_BOTTOM_INSET = 14f / REFERENCE_HEIGHT

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

    /** Horizontal start of the badge, tight to the left edge. */
    fun attributionLeft(width: Float): Float = width * ATTRIBUTION_SIDE_INSET

    /** Baseline of the badge, tight to the bottom edge. */
    fun attributionBaseline(height: Float): Float = height * (1f - ATTRIBUTION_BOTTOM_INSET)

    /** Square where the route is projected and where the map camera centres the bounds. */
    data class RouteSquare(val left: Float, val top: Float, val side: Float) {
        val centerX: Float get() = left + side / 2f
        val centerY: Float get() = top + side / 2f
    }

    /**
     * Square the route is projected into. It is sized for the overview camera
     * ([StoryMotion.OVERVIEW_SCALE]); the flyover pushes into this square while
     * following the runner, so the route can extend under the header/card.
     */
    fun routeSquare(width: Float, height: Float): RouteSquare {
        val margin = margin(width)
        val top = routeTop(height)
        val bottom = routeBottom(height)
        val side = minOf(width - 2f * margin, (bottom - top) / StoryMotion.OVERVIEW_SCALE)
        val centerY = (top + bottom) / 2f
        return RouteSquare(
            left = width / 2f - side / 2f,
            top = centerY - side / 2f,
            side = side,
        )
    }
}
