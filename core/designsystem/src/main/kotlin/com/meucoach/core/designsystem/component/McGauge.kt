package com.meucoach.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.ReadinessLevel

@Composable
fun McGauge(
    score: Int,
    level: ReadinessLevel,
    levelLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 148.dp,
    strokeWidth: Dp = 10.dp,
) {
    val colors = McTheme.colors
    val clampedScore = score.coerceIn(0, 100)
    val color = when (level) {
        ReadinessLevel.GREEN -> colors.success
        ReadinessLevel.AMBER -> colors.warning
        ReadinessLevel.RED -> colors.danger
    }
    val animatedProgress by animateFloatAsState(
        targetValue = clampedScore / 100f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "readinessGauge",
    )
    val accessibilityLabel = "Prontidão $clampedScore de 100, $levelLabel"

    Box(
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val diameter = this.size.minDimension - strokePx
            val topLeft = Offset(
                x = (this.size.width - diameter) / 2f,
                y = (this.size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)
            val startAngle = 140f
            val maxSweep = 260f

            drawArc(
                color = colors.borderSubtle,
                startAngle = startAngle,
                sweepAngle = maxSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            drawArc(
                color = color.copy(alpha = 0.14f),
                startAngle = startAngle,
                sweepAngle = maxSweep * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx + 7.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = maxSweep * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = clampedScore.toString(),
                style = McTextStyles.metricHero.copy(fontSize = 40.sp, lineHeight = 40.sp),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = levelLabel, style = McTextStyles.caption, color = color)
        }
    }
}
