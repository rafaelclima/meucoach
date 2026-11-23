package com.meucoach.app.story

import com.meucoach.core.domain.story.ArtColor

/**
 * WCAG contrast ratio between two opaque sRGB colors. The preview cannot
 * promise legibility over an unknown photo (ADR 0058 §2.4), but it must warn
 * when the current recipe already fails against the two extremes it may land
 * on: text clears 4.5:1 on both checkerboards, the route clears 3:1 over the
 * casing composited on a black and a white photo.
 */
object ArtContrast {
    /** Lighter cell of the dark checkerboard — the worst case against it. */
    const val DARK_CHECKER_WORST = 0xFF16191E.toInt()
    const val DARK_CHECKER = 0xFF0E1013.toInt()

    /** Darker cell of the light checkerboard — the worst case against it. */
    const val LIGHT_CHECKER_WORST = 0xFFE3E6E9.toInt()
    const val LIGHT_CHECKER = 0xFFF1F3F5.toInt()

    /**
     * The route is drawn over the casing shell (70% black), never directly on
     * the checkerboard. These are the shell pixels at the two photo extremes.
     */
    val CASING_ON_BLACK: Int = composite(StoryArtRenderer.ROUTE_CASING, 0xFF000000.toInt())
    val CASING_ON_WHITE: Int = composite(StoryArtRenderer.ROUTE_CASING, 0xFFFFFFFF.toInt())

    fun ratio(foreground: Int, background: Int): Double {
        val luminanceA = relativeLuminance(foreground)
        val luminanceB = relativeLuminance(background)
        val lighter = maxOf(luminanceA, luminanceB)
        val darker = minOf(luminanceA, luminanceB)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Why the preview warns, or null when text and route pass on both photo
     * extremes. The checkerboard toggle no longer changes the verdict: the art
     * is composed over an unknown photo, so both extremes must pass.
     */
    fun warning(text: ArtColor, route: ArtColor): String? {
        val textMessage = textWarning(text)
        val routeMessage = routeWarning(route)
        return when {
            textMessage != null && routeMessage != null ->
                "Texto e traçado com pouco contraste — confira sobre a foto."

            else -> textMessage ?: routeMessage
        }
    }

    /**
     * The text is drawn straight on the transparent canvas, so it must clear
     * AA on both checker extremes. No palette color does; the warning is
     * consultative and tells the user to check the actual photo.
     */
    fun textWarning(text: ArtColor): String? = if (minOf(
            ratio(text.argb, DARK_CHECKER_WORST),
            ratio(text.argb, LIGHT_CHECKER_WORST),
        ) < TEXT_THRESHOLD
    ) {
        "Texto com pouco contraste — confira sobre a foto."
    } else {
        null
    }

    /** The route sits on the casing, never on the checker; check both photo shells. */
    fun routeWarning(route: ArtColor): String? = if (minOf(
            ratio(route.argb, CASING_ON_BLACK),
            ratio(route.argb, CASING_ON_WHITE),
        ) < ROUTE_THRESHOLD
    ) {
        "Traçado com pouco contraste — confira sobre a foto."
    } else {
        null
    }

    private const val TEXT_THRESHOLD = 4.5
    private const val ROUTE_THRESHOLD = 3.0

    /** Source-over blend of the casing on an opaque background. */
    private fun composite(foreground: Int, background: Int): Int {
        val alpha = StoryArtRenderer.ROUTE_CASING_ALPHA / 255.0
        fun channel(color: Int, shift: Int): Int =
            (((color shr shift) and 0xFF) * alpha + ((background shr shift) and 0xFF) * (1 - alpha))
                .toInt()
                .coerceIn(0, 255)

        return (0xFF shl 24) or
            (channel(foreground, 16) shl 16) or
            (channel(foreground, 8) shl 8) or
            channel(foreground, 0)
    }

    private fun relativeLuminance(argb: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.03928) normalized / 12.92 else Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel((argb shr 16) and 0xFF) +
            0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }
}
