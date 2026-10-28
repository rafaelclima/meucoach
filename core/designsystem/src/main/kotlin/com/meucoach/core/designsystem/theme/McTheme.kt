package com.meucoach.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalMcColors = staticCompositionLocalOf { McDarkColors }

object McTheme {
    val colors: McColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMcColors.current
}

@Composable
fun McTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val mcColors = if (darkTheme) McDarkColors else McLightColors
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = mcColors.accent,
            onPrimary = mcColors.onAccent,
            primaryContainer = mcColors.accentSoft,
            onPrimaryContainer = mcColors.textPrimary,
            secondary = mcColors.lavender,
            onSecondary = mcColors.background,
            secondaryContainer = mcColors.surfaceInset,
            onSecondaryContainer = mcColors.textPrimary,
            tertiary = mcColors.coral,
            onTertiary = mcColors.background,
            tertiaryContainer = mcColors.coralSoft,
            onTertiaryContainer = mcColors.coral,
            background = mcColors.background,
            onBackground = mcColors.textPrimary,
            surface = mcColors.surface,
            onSurface = mcColors.textPrimary,
            surfaceVariant = mcColors.surfaceElevated,
            onSurfaceVariant = mcColors.textSecondary,
            outline = mcColors.borderStrong,
            outlineVariant = mcColors.borderSubtle,
            error = mcColors.danger,
            onError = mcColors.textPrimary,
            errorContainer = mcColors.coralSoft,
            onErrorContainer = mcColors.coral,
            scrim = Color.Black.copy(alpha = 0.64f),
            inverseSurface = mcColors.textPrimary,
            inverseOnSurface = mcColors.background,
            inversePrimary = mcColors.accentPressed,
            surfaceTint = mcColors.accent,
        )
    } else {
        lightColorScheme(
            primary = mcColors.accent,
            onPrimary = mcColors.onAccent,
            primaryContainer = mcColors.accentSoft,
            onPrimaryContainer = mcColors.textPrimary,
            secondary = mcColors.lavender,
            onSecondary = Color.White,
            secondaryContainer = mcColors.surfaceInset,
            onSecondaryContainer = mcColors.textPrimary,
            tertiary = mcColors.coral,
            onTertiary = Color.White,
            tertiaryContainer = mcColors.coralSoft,
            onTertiaryContainer = mcColors.coral,
            background = mcColors.background,
            onBackground = mcColors.textPrimary,
            surface = mcColors.surface,
            onSurface = mcColors.textPrimary,
            surfaceVariant = mcColors.surfaceInset,
            onSurfaceVariant = mcColors.textSecondary,
            outline = mcColors.borderStrong,
            outlineVariant = mcColors.borderSubtle,
            error = mcColors.danger,
            onError = Color.White,
            errorContainer = mcColors.coralSoft,
            onErrorContainer = mcColors.coral,
            scrim = Color.Black.copy(alpha = 0.48f),
            inverseSurface = mcColors.textPrimary,
            inverseOnSurface = mcColors.background,
            inversePrimary = mcColors.accentPressed,
            surfaceTint = mcColors.accent,
        )
    }

    CompositionLocalProvider(LocalMcColors provides mcColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = McTypography,
            shapes = McShapes,
            content = content,
        )
    }
}
