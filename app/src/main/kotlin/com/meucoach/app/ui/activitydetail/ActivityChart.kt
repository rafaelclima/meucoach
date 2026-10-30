package com.meucoach.app.ui.activitydetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.ActivitySample

@Composable
internal fun ActivityChartCard(samples: List<ActivitySample>, modifier: Modifier = Modifier) {
    val data = remember(samples) { chartData(samples) } ?: return
    val colors = McTheme.colors

    McCard(modifier = modifier) {
        Text(
            text = "Pace × FC × tempo",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Como o esforço evoluiu ao longo da corrida.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        LegendLine(
            color = colors.accent,
            label = "Pace",
            range = paceRangeLabel(data.paceRange),
        )
        if (data.heartRateRange != null) {
            Spacer(modifier = Modifier.height(6.dp))
            LegendLine(
                color = colors.coral,
                label = "FC",
                range = heartRateRangeLabel(data.heartRateRange),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .semantics {
                    contentDescription = "Gráfico de pace e frequência cardíaca ao longo do tempo"
                },
        ) {
            drawChartGrid(gridColor = colors.borderSubtle)
            drawPaceLine(data, colors.accent)
            if (data.heartRateRange != null) {
                drawHeartRateLine(data, colors.coral)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "0:00", style = McTextStyles.caption, color = colors.textTertiary)
            Text(
                text = formatDuration(data.durationSec),
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun LegendLine(color: Color, label: String, range: String) {
    val colors = McTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label $range",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
    }
}

private fun DrawScope.drawChartGrid(gridColor: Color) {
    repeat(GRID_LINES) { index ->
        val y = size.height * index / (GRID_LINES - 1)
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
    }
}

private fun DrawScope.drawPaceLine(data: ActivityChartData, color: Color) {
    val range = data.paceRange
    val span = (range.last - range.first).coerceAtLeast(1)
    val path = Path()
    data.points.forEachIndexed { index, point ->
        val x = xFor(point.offsetSec, data.durationSec)
        // Faster pace (smaller seconds) sits higher on the chart.
        val y = (point.paceSecPerKm - range.first).toFloat() / span * size.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private fun DrawScope.drawHeartRateLine(data: ActivityChartData, color: Color) {
    val range = data.heartRateRange ?: return
    val span = (range.last - range.first).coerceAtLeast(1)
    val stroke = Stroke(
        width = 2.dp.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    var path: Path? = null
    data.points.forEach { point ->
        val heartRate = point.heartRate
        if (heartRate == null) {
            path?.let { drawPath(path = it, color = color, style = stroke) }
            path = null
            return@forEach
        }
        val x = xFor(point.offsetSec, data.durationSec)
        // Higher heart rate sits higher on the chart.
        val y = (1f - (heartRate - range.first).toFloat() / span) * size.height
        val current = path ?: Path().also {
            it.moveTo(x, y)
            path = it
        }
        current.lineTo(x, y)
    }
    path?.let { drawPath(path = it, color = color, style = stroke) }
}

private fun DrawScope.xFor(offsetSec: Int, durationSec: Int): Float =
    if (durationSec <= 0) 0f else offsetSec.toFloat() / durationSec * size.width

private val CHART_HEIGHT = 160.dp
private const val GRID_LINES = 4
