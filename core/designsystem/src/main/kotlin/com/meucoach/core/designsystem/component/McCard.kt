package com.meucoach.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McCardShape
import com.meucoach.core.designsystem.theme.McTheme

enum class McCardVariant {
    DEFAULT,
    ELEVATED,
    ACCENT,
    INSET,
}

private data class McCardColors(val container: Color, val border: Color)

@Composable
fun McCard(
    modifier: Modifier = Modifier,
    variant: McCardVariant = McCardVariant.DEFAULT,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = McTheme.colors
    val cardColors = when (variant) {
        McCardVariant.DEFAULT -> McCardColors(colors.surface, colors.borderSubtle)
        McCardVariant.ELEVATED -> McCardColors(colors.surfaceElevated, colors.borderSubtle)
        McCardVariant.ACCENT -> McCardColors(colors.accentSoft, colors.accent.copy(alpha = 0.24f))
        McCardVariant.INSET -> McCardColors(colors.surfaceInset, colors.borderSubtle)
    }
    val baseModifier = modifier.fillMaxWidth()

    if (onClick == null) {
        Surface(
            modifier = baseModifier,
            shape = McCardShape,
            color = cardColors.container,
            contentColor = colors.textPrimary,
            border = BorderStroke(1.dp, cardColors.border),
        ) {
            Column(modifier = Modifier.padding(20.dp), content = content)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = baseModifier,
            shape = McCardShape,
            color = cardColors.container,
            contentColor = colors.textPrimary,
            border = BorderStroke(1.dp, cardColors.border),
        ) {
            Column(modifier = Modifier.padding(20.dp), content = content)
        }
    }
}
