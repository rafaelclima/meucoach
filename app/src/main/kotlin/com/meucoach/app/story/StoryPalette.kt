package com.meucoach.app.story

import androidx.compose.ui.graphics.toArgb
import com.meucoach.core.designsystem.theme.McColors

data class StoryPalette(
    val background: Int,
    val surface: Int,
    val surfaceInset: Int,
    val border: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textTertiary: Int,
    val accent: Int,
    val coral: Int,
    val onAccent: Int,
    /** Dark ring under the route so lime stays readable on the light basemap. */
    val mapCasing: Int,
    val mapBaseRoute: Int,
)

fun McColors.toStoryPalette() = StoryPalette(
    background = background.toArgb(),
    surface = surface.toArgb(),
    surfaceInset = surfaceInset.toArgb(),
    border = borderSubtle.toArgb(),
    textPrimary = textPrimary.toArgb(),
    textSecondary = textSecondary.toArgb(),
    textTertiary = textTertiary.toArgb(),
    accent = accent.toArgb(),
    coral = coral.toArgb(),
    onAccent = onAccent.toArgb(),
    // The story basemap is always light (OpenFreeMap liberty), in both app themes.
    mapCasing = 0xFF08090B.toInt(),
    mapBaseRoute = 0xFF4A545F.toInt(),
)
