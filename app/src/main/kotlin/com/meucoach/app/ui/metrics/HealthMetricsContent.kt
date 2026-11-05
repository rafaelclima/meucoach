package com.meucoach.app.ui.metrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McChartReference
import com.meucoach.core.designsystem.component.McChartSeries
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McLineChart
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.health.DailyMetricsTrends
import com.meucoach.core.domain.health.HealthMetric
import com.meucoach.core.domain.health.HealthMetricPoint
import com.meucoach.core.domain.health.HealthMetricTrend
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Dashboard shown above the import sources, so the screen proves the data before asking for more. */
@Composable
internal fun HealthMetricsOverview(
    trends: DailyMetricsTrends,
    windowDays: Int,
    onWindowSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!trends.hasData) {
            HealthMetricsEmptyCard()
            return@Column
        }
        RecoveryHeroCard(trends = trends)
        WindowSelector(windowDays = windowDays, onSelect = onWindowSelected)
        HealthMetricCard(
            trend = trends.trend(HealthMetric.SLEEP),
            trends = trends,
        )
        HealthMetricCard(
            trend = trends.trend(HealthMetric.RESTING_HEART_RATE),
            trends = trends,
        )
        HealthMetricCard(
            trend = trends.trend(HealthMetric.HRV),
            trends = trends,
        )
        HealthMetricCard(
            trend = trends.trend(HealthMetric.SPO2),
            trends = trends,
        )
    }
}

@Composable
private fun HealthMetricsEmptyCard() {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.ACCENT) {
        Text(
            text = "Ainda sem dados de recuperação",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Conecte o Health Connect ou importe um CSV abaixo. Assim que houver sono, " +
                "FC de repouso, HRV ou SpO₂, os gráficos aparecem aqui e passam a explicar sua " +
                "prontidão.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun WindowSelector(windowDays: Int, onSelect: (Int) -> Unit) {
    val colors = McTheme.colors
    Column {
        Text(
            text = "Período",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WINDOWS.forEach { option ->
                FilterChip(
                    selected = option.days == windowDays,
                    onClick = { onSelect(option.days) },
                    label = { Text(text = option.label) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("health-window-${option.days}"),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.surfaceInset,
                        labelColor = colors.textSecondary,
                        selectedContainerColor = colors.accentSoft,
                        selectedLabelColor = colors.accentText,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = option.days == windowDays,
                        borderColor = colors.borderSubtle,
                        selectedBorderColor = colors.accent.copy(alpha = 0.5f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun RecoveryHeroCard(trends: DailyMetricsTrends) {
    val colors = McTheme.colors
    val sleep = trends.trend(HealthMetric.SLEEP)
    val rhr = trends.trend(HealthMetric.RESTING_HEART_RATE)
    val hrv = trends.trend(HealthMetric.HRV)
    val spo2 = trends.trend(HealthMetric.SPO2)
    McCard(variant = McCardVariant.ELEVATED) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "RECUPERAÇÃO",
                style = McTextStyles.overline,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            McPill(
                text = "${trends.daysWithAnyMetric} dias com dados",
                containerColor = colors.accentSoft,
                contentColor = colors.accentText,
                modifier = Modifier.testTag("health-coverage"),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Como seu corpo chegou",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Sono, FC de repouso, HRV e oxigenação dos últimos ${trends.windowDays} dias. " +
                "A prontidão usa esses sinais junto com o check-in.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RecoveryTile(
                label = "Sono",
                value = sleep?.latestDisplay() ?: "—",
                hint = sleep?.averageHint() ?: "sem média",
                color = colors.lavender,
                modifier = Modifier.weight(1f),
            )
            RecoveryTile(
                label = "FC de repouso",
                value = rhr?.latestDisplay() ?: "—",
                hint = rhr?.averageHint() ?: "sem média",
                color = colors.coral,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RecoveryTile(
                label = "HRV",
                value = hrv?.latestDisplay() ?: "—",
                hint = hrv?.averageHint() ?: "sem média",
                color = colors.cyan,
                modifier = Modifier.weight(1f),
            )
            RecoveryTile(
                label = "SpO₂",
                value = spo2?.latestDisplay() ?: "—",
                hint = spo2?.averageHint() ?: "sem média",
                color = colors.success,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RecoveryTile(label: String, value: String, hint: String, color: Color, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceInset)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = McTextStyles.metricSmall,
            color = colors.textPrimary,
        )
        Text(
            text = hint,
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun HealthMetricCard(trend: HealthMetricTrend?, trends: DailyMetricsTrends) {
    if (trend == null) return
    val colors = McTheme.colors
    val presentation = metricPresentation(trend.metric, colors)
    var showTable by remember(trend.metric) { mutableStateOf(false) }
    val values = alignedValues(
        points = trend.points,
        start = trends.startDate,
        days = trends.windowDays,
    ) { presentation.toDisplay(it) }
    val average = alignedValues(
        points = trend.rollingAverage7d,
        start = trends.startDate,
        days = trends.windowDays,
    ) { presentation.toDisplay(it) }
    val latest = trend.latest?.let { presentation.toDisplay(it.value) }
    val references = presentation.references(trend)
    val displayValues = trend.points.map { presentation.toDisplay(it.value) }
    val axis = axisRange(trend.metric, displayValues, presentation.minY, presentation.maxY)
    val labelSpacing = when {
        trends.windowDays <= 7 -> 1
        trends.windowDays <= 14 -> 2
        trends.windowDays <= 30 -> 5
        else -> 10
    }
    val delta = trend.change?.let { presentation.formatDelta(it) }
    val averageText = trend.average7d
        ?.let { presentation.format(presentation.toDisplay(it)) }
        .orEmpty()
    val footer = buildString {
        if (averageText.isNotBlank()) {
            append("Média 7d: ")
            append(averageText)
        }
        presentation.targetText(trend)?.let { target ->
            if (isNotEmpty()) append(" · ")
            append(target)
        }
    }

    McCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = presentation.subtitle,
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = latest?.let { presentation.format(it) } ?: "—",
                    style = McTextStyles.metricSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = presentation.unit,
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
                if (delta != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    McPill(
                        text = delta,
                        containerColor = colors.surfaceInset,
                        contentColor = colors.textSecondary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        McLineChart(
            series = listOf(
                McChartSeries(
                    label = "Por dia",
                    values = values,
                    color = presentation.color.copy(alpha = 0.42f),
                ),
                McChartSeries(
                    label = "Média 7 dias",
                    values = average,
                    color = presentation.color,
                    area = true,
                ),
            ),
            xLabels = dateLabels(trends.startDate, trends.windowDays),
            ySuffix = presentation.ySuffix,
            yDecimals = presentation.yDecimals,
            yStep = presentation.yStep,
            minY = axis.first,
            maxY = axis.second,
            references = references,
            labelSpacing = labelSpacing,
            height = 170.dp,
            contentDescription = presentation.contentDescription(trends, trend),
        )
        if (trend.metric == HealthMetric.RESTING_HEART_RATE || trend.metric == HealthMetric.HRV) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = axisNote(axis.first, axis.second, presentation.ySuffix),
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = footer,
                style = McTextStyles.caption,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            McTextButton(
                text = if (showTable) "Ocultar tabela" else "Ver tabela",
                onClick = { showTable = !showTable },
                trailingArrow = false,
                modifier = Modifier.testTag("health-table-${trend.metric.name.lowercase()}"),
            )
        }
        if (showTable) {
            Spacer(modifier = Modifier.height(8.dp))
            trend.points.takeLast(7).reversed().forEachIndexed { index, point ->
                if (index > 0) McDivider(modifier = Modifier.padding(vertical = 6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = point.date.format(DAY_FORMAT),
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = presentation.format(presentation.toDisplay(point.value)),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = presentation.unit,
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
                }
            }
            if (trend.points.isEmpty()) {
                Text(
                    text = "Nenhum dia com esta métrica no período.",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

private data class MetricPresentation(
    val title: String,
    val subtitle: String,
    val unit: String,
    val color: Color,
    val ySuffix: String,
    val yDecimals: Int,
    val yStep: Double,
    val minY: Double?,
    val maxY: Double?,
    val toDisplay: (Double) -> Double,
    val format: (Double) -> String,
    val formatDelta: (Double) -> String,
    val references: (HealthMetricTrend) -> List<McChartReference>,
    val targetText: (HealthMetricTrend) -> String?,
    val contentDescription: (DailyMetricsTrends, HealthMetricTrend) -> String,
)

private fun metricPresentation(
    metric: HealthMetric,
    colors: com.meucoach.core.designsystem.theme.McColors,
): MetricPresentation = when (metric) {
    HealthMetric.SLEEP -> MetricPresentation(
        title = "Sono",
        subtitle = "Horas dormidas por noite. A média de 7 dias suaviza noites isoladas.",
        unit = "horas",
        color = colors.lavender,
        ySuffix = " h",
        yDecimals = 1,
        yStep = 2.0,
        minY = 3.0,
        maxY = 11.0,
        toDisplay = { minutes -> minutes / 60.0 },
        format = { hours -> formatHours(hours * 60) },
        formatDelta = { change -> formatDelta(change, "min") },
        references = {
            listOf(
                McChartReference(7.0, color = colors.success.copy(alpha = 0.55f)),
                McChartReference(9.0, color = colors.success.copy(alpha = 0.55f)),
            )
        },
        targetText = { "Meta usual 7–9 h" },
        contentDescription = { trends, trend ->
            "Sono nos últimos ${trends.windowDays} dias: ${trend.points.size} noites medidas, " +
                "média de 7 dias ${trend.average7d?.let(::formatHours) ?: "sem dados"}."
        },
    )

    HealthMetric.RESTING_HEART_RATE -> MetricPresentation(
        title = "FC de repouso",
        subtitle = "Batimentos por minuto em repouso. Compare com a sua própria base.",
        unit = "bpm",
        color = colors.coral,
        ySuffix = " bpm",
        yDecimals = 0,
        yStep = 2.0,
        minY = 40.0,
        maxY = null,
        toDisplay = { it },
        format = { String.format(PT_BR, "%.0f", it) },
        formatDelta = { change -> formatDelta(change, "bpm") },
        references = { trend ->
            trend.baseline?.let {
                listOf(McChartReference(it, color = colors.coral.copy(alpha = 0.55f)))
            }.orEmpty()
        },
        targetText = { trend -> trend.baseline?.let { "Base ${String.format(PT_BR, "%.0f", it)} bpm" } },
        contentDescription = { trends, trend ->
            "FC de repouso nos últimos ${trends.windowDays} dias: ${trend.points.size} dias " +
                "medidos, média de 7 dias ${trend.average7d?.roundToInt() ?: "sem dados"} bpm."
        },
    )

    HealthMetric.HRV -> MetricPresentation(
        title = "Variabilidade cardíaca (HRV)",
        subtitle = "Variabilidade entre batimentos por noite. A tendência importa mais que um valor isolado.",
        unit = "ms",
        color = colors.cyan,
        ySuffix = " ms",
        yDecimals = 0,
        yStep = 10.0,
        minY = 0.0,
        maxY = null,
        toDisplay = { it },
        format = { String.format(PT_BR, "%.0f", it) },
        formatDelta = { change -> formatDelta(change, "ms") },
        references = { trend ->
            trend.baseline?.let {
                listOf(McChartReference(it, color = colors.cyan.copy(alpha = 0.55f)))
            }.orEmpty()
        },
        targetText = { trend -> trend.baseline?.let { "Base ${String.format(PT_BR, "%.0f", it)} ms" } },
        contentDescription = { trends, trend ->
            "HRV nos últimos ${trends.windowDays} dias: ${trend.points.size} noites medidas, " +
                "média de 7 dias ${trend.average7d?.roundToInt() ?: "sem dados"} milissegundos."
        },
    )

    HealthMetric.SPO2 -> MetricPresentation(
        title = "Oxigenação (SpO₂)",
        subtitle = "Percentual médio durante o sono. Valores isolados oscilam.",
        unit = "%",
        color = colors.success,
        ySuffix = "%",
        yDecimals = 0,
        yStep = 2.0,
        minY = 90.0,
        maxY = 100.0,
        toDisplay = { it },
        format = { String.format(PT_BR, "%.0f", it) },
        formatDelta = { change -> formatDelta(change, "pts") },
        references = {
            listOf(McChartReference(95.0, color = colors.success.copy(alpha = 0.55f)))
        },
        targetText = { "Faixa usual 95–100%" },
        contentDescription = { trends, trend ->
            "Oxigenação nos últimos ${trends.windowDays} dias: ${trend.points.size} noites " +
                "medidas, média de 7 dias ${trend.average7d?.roundToInt() ?: "sem dados"} por cento."
        },
    )
}

private fun HealthMetricTrend.latestDisplay(): String = latest?.value?.let { value ->
    when (metric) {
        HealthMetric.SLEEP -> formatHours(value)
        HealthMetric.RESTING_HEART_RATE -> "${value.roundToInt()} bpm"
        HealthMetric.HRV -> "${value.roundToInt()} ms"
        HealthMetric.SPO2 -> "${value.roundToInt()}%"
    }
} ?: "—"

private fun HealthMetricTrend.averageHint(): String = average7d?.let { value ->
    when (metric) {
        HealthMetric.SLEEP -> "7d · ${formatHours(value)}"
        HealthMetric.RESTING_HEART_RATE -> "7d · ${value.roundToInt()} bpm"
        HealthMetric.HRV -> "7d · ${value.roundToInt()} ms"
        HealthMetric.SPO2 -> "7d · ${value.roundToInt()}%"
    }
} ?: "sem média"

private fun formatHours(minutes: Double): String {
    val total = minutes.roundToInt()
    val hours = total / 60
    val rest = total % 60
    return when {
        hours == 0 -> "${rest}min"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}min"
    }
}

private fun formatDelta(change: Double, suffix: String): String {
    val rounded = change.roundToInt()
    return if (rounded == 0) {
        "0 $suffix"
    } else {
        String.format(PT_BR, "%+d %s", rounded, suffix)
    }
}

private fun axisRange(
    metric: HealthMetric,
    values: List<Double>,
    fallbackMin: Double?,
    fallbackMax: Double?,
): Pair<Double?, Double?> {
    if (values.isEmpty()) return fallbackMin to fallbackMax
    val min = values.min()
    val max = values.max()
    return when (metric) {
        HealthMetric.RESTING_HEART_RATE -> {
            val low = kotlin.math.floor(min - 1.5).coerceAtLeast(30.0)
            val high = kotlin.math.max(kotlin.math.ceil(max + 1.5), low + 6.0)
            low to high
        }

        HealthMetric.HRV -> {
            val low = kotlin.math.floor((min - 5).coerceAtLeast(0.0))
            val high = kotlin.math.max(kotlin.math.ceil(max + 5), low + 10.0)
            low to high
        }

        else -> fallbackMin to fallbackMax
    }
}

private fun axisNote(min: Double?, max: Double?, suffix: String): String {
    if (min == null || max == null) return ""
    val unit = suffix.trim()
    return String.format(
        PT_BR,
        "Eixo de %.0f a %.0f %s para mostrar a variação.",
        min,
        max,
        unit,
    )
}

private fun dateLabels(start: LocalDate, days: Int): List<String> =
    (0 until days).map { start.plusDays(it.toLong()).format(DAY_FORMAT) }

private fun alignedValues(
    points: List<HealthMetricPoint>,
    start: LocalDate,
    days: Int,
    transform: (Double) -> Double = { it },
): List<Double?> {
    val byDate = points.associate { it.date to it.value }
    return (0 until days).map { offset ->
        byDate[start.plusDays(offset.toLong())]?.let(transform)
    }
}

private data class WindowOption(val days: Int, val label: String)

private val WINDOWS = listOf(
    WindowOption(days = 7, label = "7 d"),
    WindowOption(days = 30, label = "30 d"),
    WindowOption(days = 60, label = "60 d"),
)

private val PT_BR = Locale.forLanguageTag("pt-BR")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM")
