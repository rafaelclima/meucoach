package com.meucoach.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McPillShape
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.DataConfidence

@Composable
fun McConfidenceBadge(
    confidence: DataConfidence,
    modifier: Modifier = Modifier,
    /** Optional owner of the confidence (e.g. "Plano"), so it is not read as readiness. */
    owner: String? = null,
) {
    val colors = McTheme.colors
    val (label, color) = when (confidence) {
        DataConfidence.HIGH -> "Confiança alta" to colors.success
        DataConfidence.MEDIUM -> "Confiança média" to colors.warning
        DataConfidence.LOW -> "Confiança baixa" to colors.coral
    }

    Row(
        modifier = modifier
            .background(color = color.copy(alpha = 0.12f), shape = McPillShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(6.dp)
                .background(color = color, shape = CircleShape),
        )
        Text(
            text = owner?.let { "$it · ${label.lowercase()}" } ?: label,
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
fun McSignalDot(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(7.dp)
            .background(color = color, shape = CircleShape),
    )
}
