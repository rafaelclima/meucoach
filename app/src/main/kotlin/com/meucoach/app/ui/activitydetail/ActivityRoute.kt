package com.meucoach.app.ui.activitydetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.ActivitySample

@Composable
internal fun ActivityRouteCard(samples: List<ActivitySample>, modifier: Modifier = Modifier) {
    val route = remember(samples) { routeData(samples) } ?: return
    val colors = McTheme.colors

    McCard(modifier = modifier) {
        Text(
            text = "Rota",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Traçado a partir do GPS. A localização não sai do aparelho.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROUTE_HEIGHT)
                .semantics { contentDescription = "Traçado da rota da corrida" },
        ) {
            drawRoundRect(
                color = colors.surfaceInset,
                cornerRadius = CornerRadius(16.dp.toPx()),
            )
            val padding = 16.dp.toPx()
            val availableWidth = size.width - padding * 2
            val availableHeight = size.height - padding * 2
            fun x(point: RoutePoint) = padding + point.x * availableWidth
            fun y(point: RoutePoint) = padding + point.y * availableHeight

            val path = Path()
            route.points.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(x(point), y(point)) else path.lineTo(x(point), y(point))
            }
            drawPath(
                path = path,
                color = colors.accent,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawCircle(
                color = colors.success,
                radius = 5.dp.toPx(),
                center = Offset(x(route.start), y(route.start)),
            )
            drawCircle(
                color = colors.coral,
                radius = 5.dp.toPx(),
                center = Offset(x(route.end), y(route.end)),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RouteLegend(color = colors.success, label = "Início")
            RouteLegend(color = colors.coral, label = "Fim")
        }
    }
}

@Composable
private fun RouteLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = McTextStyles.caption, color = McTheme.colors.textSecondary)
    }
}

private val ROUTE_HEIGHT = 200.dp
