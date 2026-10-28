package com.meucoach.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.meucoach.core.designsystem.R

private fun variableFamily(resourceId: Int): FontFamily = FontFamily(
    Font(
        resId = resourceId,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = resourceId,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = resourceId,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = resourceId,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

val GeistFamily: FontFamily = variableFamily(R.font.geist_variable)
val InterFamily: FontFamily = variableFamily(R.font.inter_variable)

object McTextStyles {
    val metricHero =
        TextStyle(
            fontFamily = GeistFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 56.sp,
            lineHeight = 56.sp,
            letterSpacing = (-1.8).sp,
            fontFeatureSettings = "tnum",
        )

    val metric =
        TextStyle(
            fontFamily = GeistFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.6).sp,
            fontFeatureSettings = "tnum",
        )

    val metricSmall =
        TextStyle(
            fontFamily = GeistFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.2).sp,
            fontFeatureSettings = "tnum",
        )

    val overline =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.1.sp,
            fontFeatureSettings = "tnum",
        )

    val caption =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp,
        )
}

internal val McTypography =
    Typography(
        displayLarge =
        TextStyle(
            fontFamily = GeistFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 52.sp,
            lineHeight = 56.sp,
            letterSpacing = (-1.6).sp,
            fontFeatureSettings = "tnum",
        ),
        displayMedium =
        TextStyle(
            fontFamily = GeistFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            letterSpacing = (-1.2).sp,
            fontFeatureSettings = "tnum",
        ),
        displaySmall =
        TextStyle(
            fontFamily = GeistFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.8).sp,
            fontFeatureSettings = "tnum",
        ),
        headlineLarge =
        TextStyle(
            fontFamily = GeistFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.6).sp,
        ),
        headlineMedium =
        TextStyle(
            fontFamily = GeistFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.4).sp,
        ),
        headlineSmall =
        TextStyle(
            fontFamily = GeistFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.2).sp,
        ),
        titleLarge =
        TextStyle(
            fontFamily = GeistFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
        ),
        titleSmall =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        bodyLarge =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
        ),
        bodyMedium =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        bodySmall =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp,
        ),
        labelLarge =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        labelMedium =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.2.sp,
        ),
        labelSmall =
        TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.2.sp,
        ),
    )
