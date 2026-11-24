package com.meucoach.core.domain.story

/**
 * Visual stroke of the route line. The same value feeds the video preview, the
 * encoded video, the transparent card, the photo card and the poster, so the
 * five outputs cannot drift (ADR 0062).
 *
 * The colors stay owned by their surfaces: the video keeps the brand accent and
 * the heart-rate zones, while the art keeps the independent route swatch.
 */
enum class StoryRouteStroke {
    /** Continuous line with a dark shell; the most legible over a busy map. */
    SOLID,

    /** Continuous line with a soft halo; reads as neon on dark maps and photos. */
    NEON,

    /** Dashes with a dark shell; editorial and lighter over a photo. */
    DASHED,

    /** Round dots with a dark shell; minimal and decorative. */
    DOTTED,
}
