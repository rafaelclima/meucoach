package com.meucoach.app.story

import com.meucoach.core.domain.story.StoryMapStyle

/**
 * Packs [StoryPalette] into the flat forms WorkManager input Data supports
 * (ints only — no Android types survive into the work payload).
 *
 * The field order is part of the contract with [decode]; add new fields only
 * at the end and bump [FIELD_COUNT] together.
 */
object StoryPaletteCodec {
    const val KEY = "storyPalette"
    internal const val FIELD_COUNT = 18

    fun encode(palette: StoryPalette): IntArray = intArrayOf(
        palette.background,
        palette.surface,
        palette.surfaceInset,
        palette.border,
        palette.textPrimary,
        palette.textSecondary,
        palette.textTertiary,
        palette.accent,
        palette.coral,
        palette.onAccent,
        palette.mapCasing,
        palette.mapBaseRoute,
        palette.zone1,
        palette.zone2,
        palette.zone3,
        palette.zone4,
        palette.zone5,
        palette.mapStyle.ordinal,
    )

    fun decode(values: IntArray?): StoryPalette? {
        if (values == null || values.size != FIELD_COUNT) return null
        return StoryPalette(
            background = values[0],
            surface = values[1],
            surfaceInset = values[2],
            border = values[3],
            textPrimary = values[4],
            textSecondary = values[5],
            textTertiary = values[6],
            accent = values[7],
            coral = values[8],
            onAccent = values[9],
            mapCasing = values[10],
            mapBaseRoute = values[11],
            zone1 = values[12],
            zone2 = values[13],
            zone3 = values[14],
            zone4 = values[15],
            zone5 = values[16],
            mapStyle = StoryMapStyle.entries.getOrNull(values[17]) ?: StoryMapStyle.MEUCOACH_DARK,
        )
    }
}
