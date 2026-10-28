package com.meucoach.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class McColors(
    val isDark: Boolean,
    val background: Color,
    val backgroundSubtle: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceInset: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentText: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val coral: Color,
    val coralSoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val lavender: Color,
    val cyan: Color,
    val zone1: Color,
    val zone2: Color,
    val zone3: Color,
    val zone4: Color,
    val zone5: Color,
)

internal val McDarkColors =
    McColors(
        isDark = true,
        background = Color(0xFF08090B),
        backgroundSubtle = Color(0xFF0E1013),
        surface = Color(0xFF121418),
        surfaceElevated = Color(0xFF191C21),
        surfaceInset = Color(0xFF0B0D10),
        borderSubtle = Color(0xFF23272D),
        borderStrong = Color(0xFF343A42),
        textPrimary = Color(0xFFF4F6F7),
        textSecondary = Color(0xFFA2A9B0),
        // Ajustado de #6E767F para atingir 4.5:1 também em surface/elevated.
        textTertiary = Color(0xFF7F8895),
        accent = Color(0xFFD8FF3E),
        accentText = Color(0xFFD8FF3E),
        accentPressed = Color(0xFFC2E82F),
        accentSoft = Color(0xFF2A3310),
        onAccent = Color(0xFF0A0B0D),
        coral = Color(0xFFFF6B5E),
        coralSoft = Color(0xFF2A1513),
        success = Color(0xFF4ADE80),
        warning = Color(0xFFFFB454),
        danger = Color(0xFFFF5A5F),
        lavender = Color(0xFFB9A6FF),
        cyan = Color(0xFF5BD0E0),
        zone1 = Color(0xFF6C9CFF),
        zone2 = Color(0xFF56D68B),
        zone3 = Color(0xFFFFD166),
        zone4 = Color(0xFFFF9F43),
        zone5 = Color(0xFFFF5A5F),
    )

internal val McLightColors =
    McColors(
        isDark = false,
        background = Color(0xFFF6F7F8),
        backgroundSubtle = Color(0xFFEEF1F3),
        surface = Color(0xFFFFFFFF),
        surfaceElevated = Color(0xFFFFFFFF),
        surfaceInset = Color(0xFFF1F3F5),
        borderSubtle = Color(0xFFE3E6E9),
        borderStrong = Color(0xFFC9CFD5),
        textPrimary = Color(0xFF0A0B0D),
        textSecondary = Color(0xFF5C646D),
        // Ajustado de #8A929B para cumprir 4.5:1 no claro.
        textTertiary = Color(0xFF626C5A),
        accent = Color(0xFFD8FF3E),
        accentText = Color(0xFF4F6600),
        accentPressed = Color(0xFFC2E82F),
        accentSoft = Color(0xFFF1FBCB),
        onAccent = Color(0xFF0A0B0D),
        // Ajustado de #D94A3D para cumprir 4.5:1 no claro.
        coral = Color(0xFFB33A32),
        coralSoft = Color(0xFFFFE7E4),
        success = Color(0xFF1F7A4D),
        // Ajustado de #A86400 para cumprir 4.5:1 no claro.
        warning = Color(0xFF8A5200),
        danger = Color(0xFFC62828),
        lavender = Color(0xFF6E54C8),
        cyan = Color(0xFF0E7C8C),
        zone1 = Color(0xFF2E5BCC),
        zone2 = Color(0xFF1F7A4D),
        zone3 = Color(0xFF8A5A00),
        zone4 = Color(0xFFA35A00),
        zone5 = Color(0xFFC62828),
    )
