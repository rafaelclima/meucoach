package com.meucoach.app.ui.activitydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.insights.ActivityInsight
import com.meucoach.core.domain.insights.ActivityInsightTone

@Composable
internal fun ActivityInsightsCard(insights: List<ActivityInsight>, modifier: Modifier = Modifier) {
    if (insights.isEmpty()) return
    val colors = McTheme.colors

    McCard(modifier = modifier) {
        Text(
            text = "O que aprendemos",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        insights.forEachIndexed { index, insight ->
            if (index > 0) Spacer(modifier = Modifier.height(14.dp))
            Row {
                Spacer(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(toneColor(insight.tone)),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = insight.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(insight.ruleId)
                            append(" · ")
                            append(insight.evidenceIds.joinToString(", "))
                            append(" · ")
                            append(confidenceLabel(insight.confidence))
                        },
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun toneColor(tone: ActivityInsightTone): Color {
    val colors = McTheme.colors
    return when (tone) {
        ActivityInsightTone.POSITIVE -> colors.success
        ActivityInsightTone.NEUTRAL -> colors.cyan
        ActivityInsightTone.WARNING -> colors.warning
    }
}
