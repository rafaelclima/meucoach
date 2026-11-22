package com.meucoach.app.story

import com.meucoach.core.domain.story.ArtColor

/**
 * WCAG contrast ratio between two opaque sRGB colors. The preview cannot
 * promise legibility over an unknown photo (ADR 0058 §2.4), but it must warn
 * when the current recipe already fails against the checkerboard it is shown
 * on: text needs 4.5:1 (AA), the route stroke needs 3:1 (AA graphics).
 */
object ArtContrast {
    /** Lighter cell of the dark checkerboard — the worst case against it. */
    const val DARK_CHECKER_WORST = 0xFF16191E.toInt()
    const val DARK_CHECKER = 0xFF0E1013.toInt()

    /** Darker cell of the light checkerboard — the worst case against it. */
    const val LIGHT_CHECKER_WORST = 0xFFE3E6E9.toInt()
    const val LIGHT_CHECKER = 0xFFF1F3F5.toInt()

    fun ratio(foreground: Int, background: Int): Double {
        val luminanceA = relativeLuminance(foreground)
        val luminanceB = relativeLuminance(background)
        val lighter = maxOf(luminanceA, luminanceB)
        val darker = minOf(luminanceA, luminanceB)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** Why the preview warns, or null when both colors pass on this checker. */
    fun warning(text: ArtColor, route: ArtColor, darkPreview: Boolean): String? {
        val background = if (darkPreview) DARK_CHECKER_WORST else LIGHT_CHECKER_WORST
        val backgroundName = if (darkPreview) "escuro" else "claro"
        if (ratio(text.argb, background) < TEXT_THRESHOLD) {
            return "Texto com pouco contraste no fundo $backgroundName — confira sobre a foto."
        }
        if (ratio(route.argb, background) < ROUTE_THRESHOLD) {
            return "Traçado com pouco contraste no fundo $backgroundName — confira sobre a foto."
        }
        return null
    }

    private const val TEXT_THRESHOLD = 4.5
    private const val ROUTE_THRESHOLD = 3.0

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
