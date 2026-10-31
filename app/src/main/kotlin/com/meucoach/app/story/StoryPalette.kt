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
)
