package com.meucoach.app.story

import androidx.compose.ui.graphics.toArgb
import com.meucoach.core.designsystem.theme.McColors
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.model.HeartRateZone

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
    /** Dark ring under the route so lime stays readable on both autoral basemaps. */
    val mapCasing: Int,
    val mapBaseRoute: Int,
    val zone1: Int,
    val zone2: Int,
    val zone3: Int,
    val zone4: Int,
    val zone5: Int,
    val mapStyle: StoryMapStyle = StoryMapStyle.MEUCOACH_DARK,
) {
    fun zoneColor(zone: HeartRateZone): Int = when (zone) {
        HeartRateZone.Z1 -> zone1
        HeartRateZone.Z2 -> zone2
        HeartRateZone.Z3 -> zone3
        HeartRateZone.Z4 -> zone4
        HeartRateZone.Z5 -> zone5
    }
}

fun McColors.toStoryPalette(mapStyle: StoryMapStyle = StoryMapStyle.MEUCOACH_DARK) = StoryPalette(
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
    // The autoral basemaps (MAP-04) carry their own palette in the style JSON;
    // the route casing stays dark so lime pops on both dark and light maps.
    mapCasing = 0xFF08090B.toInt(),
    mapBaseRoute = 0xFF4A545F.toInt(),
    zone1 = zone1.toArgb(),
    zone2 = zone2.toArgb(),
    zone3 = zone3.toArgb(),
    zone4 = zone4.toArgb(),
    zone5 = zone5.toArgb(),
    mapStyle = mapStyle,
)
