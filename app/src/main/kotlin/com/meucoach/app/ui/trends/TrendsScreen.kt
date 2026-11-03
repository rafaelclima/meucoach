package com.meucoach.app.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McConfidenceBadge
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McEmptyState
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McSectionHeader
import com.meucoach.core.designsystem.component.McStat
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.PlanCompletion
import com.meucoach.core.domain.readiness.ReadinessHistoryDay
import com.meucoach.core.domain.trends.ActivityTrends
import com.meucoach.core.domain.trends.ConsistencyDay
import com.meucoach.core.domain.trends.IntensityDistribution
import com.meucoach.core.domain.trends.PaceHeartRatePoint
import com.meucoach.core.domain.trends.PersonalRecords
import com.meucoach.core.domain.trends.WeeklyIntensity
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TrendsScreen(
    modifier: Modifier = Modifier,
    viewModel: TrendsViewModel = hiltViewModel(),
    historyViewModel: ReadinessHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by historyViewModel.uiState.collectAsStateWithLifecycle()
    TrendsContent(
        state = state,
        modifier = modifier,
        readinessHistory = history.days,
        onPeriodSelected = viewModel::selectPeriod,
    )
}

@Composable
internal fun TrendsContent(
    state: TrendsUiState,
    modifier: Modifier = Modifier,
    readinessHistory: List<ReadinessHistoryDay> = emptyList(),
    onPeriodSelected: (TrendsPeriod) -> Unit = {},
) {
    val colors = McTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .testTag("trends-loading"),
                color = colors.accent,
                strokeWidth = 2.dp,
            )

            state.profile == null -> McEmptyState(
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                title = "Perfil não configurado",
                description = "Complete o onboarding para calcular carga e tendências.",
                modifier = Modifier.align(Alignment.Center),
            )

            state.snapshot == null || !state.snapshot.hasData -> McEmptyState(
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                title = "Sem carga para mostrar",
                description = "Importe corridas com FC ou ritmo e calibre seu limite. " +
                    "Assim você vê carga e recuperação.",
                modifier = Modifier.align(Alignment.Center),
            )

            else -> LoadDetails(
                snapshot = state.snapshot,
                readinessHistory = readinessHistory,
                activityTrends = state.activityTrends,
                intensity = state.intensity,
                planCompletion = state.planCompletion,
                period = state.period,
                onPeriodSelected = onPeriodSelected,
                lthr = state.profile.lactateThresholdHeartRate,
            )
        }
    }
}

@Composable
private fun LoadDetails(
    snapshot: LoadSnapshot,
    readinessHistory: List<ReadinessHistoryDay>,
    activityTrends: ActivityTrends?,
    intensity: IntensityDistribution?,
    planCompletion: PlanCompletion?,
    period: TrendsPeriod,
    onPeriodSelected: (TrendsPeriod) -> Unit,
    lthr: Int?,
) {
    val colors = McTheme.colors
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("trends-list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(text = "TENDÊNCIAS", style = McTextStyles.overline, color = colors.textTertiary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Carga de treino",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Carga acumulada, carga recente e recuperação dos últimos 42 dias. " +
                        "Tendência, não prescrição.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
        item {
            McCard {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    McStat(
                        label = "Acumulada",
                        hint = "42 dias",
                        value = formatOneDecimal(snapshot.ctl),
                        modifier = Modifier.weight(1f),
                    )
                    McStat(
                        label = "Recente",
                        hint = "7 dias",
                        value = formatOneDecimal(snapshot.atl),
                        modifier = Modifier.weight(1f),
                    )
                    McStat(
                        label = "Balanço",
                        hint = "recuperação",
                        value = formatOneDecimal(snapshot.tsb),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    McStat(
                        label = "Carga",
                        hint = "soma 7 dias",
                        value = formatOneDecimal(snapshot.daily.takeLast(7).sumOf { it.load }),
                        modifier = Modifier.weight(1f),
                    )
                    McStat(
                        label = "Proporção",
                        hint = "ACWR",
                        value = snapshot.acwr?.let { formatTwoDecimals(it) } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    McStat(
                        label = "Variedade",
                        hint = "monotonia",
                        value = snapshot.monotony?.let { formatTwoDecimals(it) } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            val flag = acwrFlag(snapshot.acwr)
            McCard(variant = com.meucoach.core.designsystem.component.McCardVariant.INSET) {
                Text(
                    text = flag.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = flag.color,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A proporção (ACWR) é um sinal de atenção, nunca uma meta. " +
                        "${snapshot.missingDays} dias sem carga na janela.",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
        }
        if (activityTrends != null) {
            item { PeriodFilter(period = period, onSelect = onPeriodSelected) }
            item { McSectionHeader(title = "Volume · ${activityTrends.weeks.size} semanas") }
            item { WeeklyVolumeCard(activityTrends.weeks) }
            if (planCompletion != null && planCompletion.planned > 0) {
                item { McSectionHeader(title = "Conclusão · ${planCompletion.windowWeeks} semanas") }
                item { PlanCompletionCard(planCompletion) }
            }
            item { McSectionHeader(title = "Hábito · Dias ativos") }
            item { ConsistencyCard(activityTrends) }
            if (activityTrends.records.hasData) {
                item { McSectionHeader(title = "Seus melhores trechos") }
                item { PersonalRecordsCard(activityTrends.records) }
            }
            if (activityTrends.paceHeartRate.isNotEmpty()) {
                item { McSectionHeader(title = "Eficiência · Ritmo vs FC") }
                item { PaceHeartRateCard(activityTrends.paceHeartRate) }
            }
            item { McSectionHeader(title = "Equilíbrio · Fácil vs Forte") }
            item { IntensityCard(distribution = intensity, lthr = lthr) }
        }
        if (readinessHistory.isNotEmpty()) {
            item { McSectionHeader(title = "Prontidão · 14 dias") }
            item { ReadinessHistoryCard(readinessHistory) }
        }
        item { McSectionHeader(title = "Evolução da carga · 42 dias") }
        item { PmcCard(snapshot) }
    }
}

@Composable
private fun PeriodFilter(period: TrendsPeriod, onSelect: (TrendsPeriod) -> Unit) {
    val colors = McTheme.colors
    Column {
        Text(
            text = "Período das atividades",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendsPeriod.entries.forEach { option ->
                FilterChip(
                    selected = option == period,
                    onClick = { onSelect(option) },
                    label = { Text(text = option.label) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.surfaceInset,
                        labelColor = colors.textSecondary,
                        selectedContainerColor = colors.accentSoft,
                        selectedLabelColor = colors.accentText,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = option == period,
                        borderColor = colors.borderSubtle,
                        selectedBorderColor = colors.accent.copy(alpha = 0.5f),
                    ),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Afeta volume, consistência e eficiência. Carga e 80/20 têm janelas próprias.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun WeeklyVolumeCard(weeks: List<com.meucoach.core.domain.trends.WeeklyVolume>) {
    val colors = McTheme.colors
    val max = weeks.maxOf { it.kilometers }.coerceAtLeast(1.0)
    val total = weeks.sumOf { it.kilometers }
    McCard {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = "Volume semanal em quilômetros" },
        ) {
            val slot = size.width / weeks.size
            val barWidth = slot * 0.55f
            weeks.forEachIndexed { index, week ->
                val height = (week.kilometers / max * size.height).toFloat()
                val left = index * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = colors.accent,
                    topLeft = Offset(left, size.height - height),
                    size = androidx.compose.ui.geometry.Size(barWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = String.format(PT_BR, "%.1f km no total · média %.1f km/semana", total, total / weeks.size),
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun PlanCompletionCard(completion: PlanCompletion) {
    val colors = McTheme.colors
    McCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Adesão ao plano",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sessões passadas marcadas como concluídas.",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
            completion.completionRatio?.let { ratio ->
                Text(
                    text = String.format(PT_BR, "%.0f%%", ratio * 100),
                    style = McTextStyles.metricSmall,
                    color = colors.textPrimary,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics {
                    contentDescription = "Conclusão do plano nas últimas " +
                        "${completion.windowWeeks} semanas: ${completion.done} de " +
                        "${completion.planned} sessões concluídas e ${completion.skipped} puladas"
                },
        ) {
            val slot = size.width / completion.weeks.size
            val barWidth = slot * 0.55f
            val radius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            completion.weeks.forEachIndexed { index, week ->
                val left = index * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = colors.surfaceInset,
                    topLeft = Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                    cornerRadius = radius,
                )
                week.completionRatio?.takeIf { it > 0.0 }?.let { ratio ->
                    val height = (ratio * size.height).toFloat()
                    drawRoundRect(
                        color = colors.accent,
                        topLeft = Offset(left, size.height - height),
                        size = androidx.compose.ui.geometry.Size(barWidth, height),
                        cornerRadius = radius,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        val skipped = when (completion.skipped) {
            0 -> ""
            1 -> " · 1 pulada"
            else -> " · ${completion.skipped} puladas"
        }
        Text(
            text = "${completion.done} de ${completion.planned} sessões concluídas$skipped",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Text(
            text = "Semanas sem plano ficam vazias; a semana atual conta só até hoje.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun ConsistencyCard(trends: com.meucoach.core.domain.trends.ActivityTrends) {
    val consistency = trends.consistency
    McCard(variant = com.meucoach.core.designsystem.component.McCardVariant.INSET) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            McStat(
                label = "Dias ativos",
                value = consistency.activeDays.toString(),
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Corridas",
                value = consistency.runs.toString(),
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Última",
                value = consistency.daysSinceLastRun?.let { "há $it d" } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = String.format(
                PT_BR,
                "%.1f km no período · pace médio %s",
                trends.totalKilometers,
                trends.averagePace?.format() ?: "—",
            ),
            style = McTextStyles.caption,
            color = McTheme.colors.textSecondary,
        )
        if (trends.heatmap.isNotEmpty()) {
            McDivider(modifier = Modifier.padding(vertical = 10.dp))
            ConsistencyHeatmap(trends.heatmap)
        }
    }
}

@Composable
private fun ConsistencyHeatmap(days: List<ConsistencyDay>) {
    val colors = McTheme.colors
    val start = days.first().date
    val columns = ((days.last().date.toEpochDay() - start.toEpochDay()) / 7).toInt() + 1
    val maxKm = days.maxOf { it.kilometers }.coerceAtLeast(1.0)
    val activeDays = days.count { it.runs > 0 }
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Mapa de consistência",
                style = McTextStyles.caption,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$activeDays dias ativos",
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .semantics {
                    contentDescription = "Mapa de consistência de ${days.size} dias: " +
                        "$activeDays dias ativos"
                },
        ) {
            val gap = 3.dp.toPx()
            val cell = minOf(
                (size.width - gap * (columns - 1)) / columns,
                (size.height - gap * 6) / 7f,
            )
            val gridWidth = columns * cell + gap * (columns - 1)
            val gridHeight = 7 * cell + gap * 6
            val originX = (size.width - gridWidth) / 2f
            val originY = (size.height - gridHeight) / 2f
            days.forEach { day ->
                val column = ((day.date.toEpochDay() - start.toEpochDay()) / 7).toInt()
                val row = day.date.dayOfWeek.value - 1
                val fraction = (day.kilometers / maxKm).coerceIn(0.0, 1.0)
                val color = if (day.runs == 0) {
                    colors.surfaceInset
                } else {
                    colors.accent.copy(alpha = (0.25 + 0.65 * fraction).toFloat())
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(
                        originX + column * (cell + gap),
                        originY + row * (cell + gap),
                    ),
                    size = androidx.compose.ui.geometry.Size(cell, cell),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.2f),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Menos", style = McTextStyles.caption, color = colors.textTertiary)
            Spacer(modifier = Modifier.width(6.dp))
            listOf(0.25f, 0.55f, 0.9f).forEach { alpha ->
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = alpha)),
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "Mais", style = McTextStyles.caption, color = colors.textTertiary)
        }
    }
}

@Composable
private fun PersonalRecordsCard(records: PersonalRecords) {
    val colors = McTheme.colors
    McCard {
        Text(
            text = "Melhores esforços",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Das corridas importadas, em todo o histórico. Recorde descritivo, não prescrição.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        records.longestDistanceKm?.let { km ->
            RecordRow(
                label = "Maior distância",
                value = String.format(PT_BR, "%.1f km", km),
                hint = records.longestDistanceDate?.format(DAY_FORMAT),
            )
        }
        records.bestPace?.let { pace ->
            McDivider(modifier = Modifier.padding(vertical = 8.dp))
            RecordRow(
                label = "Melhor pace",
                value = "${pace.format()} /km",
                hint = records.bestPaceDistanceKm?.let { km ->
                    val date = records.bestPaceDate?.format(DAY_FORMAT)
                    listOfNotNull(String.format(PT_BR, "%.1f km", km), date).joinToString(" · ")
                },
            )
        }
        records.longestDurationSec?.let { seconds ->
            McDivider(modifier = Modifier.padding(vertical = 8.dp))
            RecordRow(
                label = "Mais tempo",
                value = formatDuration(seconds),
                hint = records.longestDurationDate?.format(DAY_FORMAT),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "O melhor pace considera corridas com pelo menos 3 km.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun RecordRow(label: String, value: String, hint: String?) {
    val colors = McTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun PaceHeartRateCard(points: List<PaceHeartRatePoint>) {
    val colors = McTheme.colors
    val averageEfficiency = points.map { it.efficiencyMetersPerBeat }.average()
    McCard {
        Text(
            text = "Eficiência",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Metros por batimento: proxy de economia. Compare sob condições semelhantes.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        EfficiencyChart(points)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = String.format(
                PT_BR,
                "Média %.2f m/batimento em %d corridas",
                averageEfficiency,
                points.size,
            ),
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        McDivider(modifier = Modifier.padding(vertical = 10.dp))
        Text(
            text = "Últimas corridas",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        points.takeLast(8).reversed().forEachIndexed { index, point ->
            if (index > 0) {
                McDivider(modifier = Modifier.padding(vertical = 6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = point.date.format(DAY_FORMAT),
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.width(52.dp),
                )
                Text(
                    text = "${point.pace.format()} /km",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${point.heartRate} bpm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun EfficiencyChart(points: List<PaceHeartRatePoint>) {
    val colors = McTheme.colors
    val values = points.map { it.efficiencyMetersPerBeat }
    val min = values.minOrNull() ?: return
    val max = values.maxOrNull() ?: return
    val range = max - min
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .semantics {
                contentDescription = "Evolução da eficiência pace por FC em ${points.size} corridas"
            },
    ) {
        drawPmcGrid(colors.borderSubtle)
        if (points.size == 1) {
            drawCircle(
                color = colors.accent,
                radius = 4.dp.toPx(),
                center = Offset(size.width / 2f, size.height / 2f),
            )
        } else {
            val path = Path()
            val positions = points.mapIndexed { index, point ->
                val x = index.toFloat() / (points.size - 1) * size.width
                val normalized = if (range < 0.0001) 0.5 else (point.efficiencyMetersPerBeat - min) / range
                val y = size.height - normalized.toFloat() * size.height
                Offset(x, y)
            }
            positions.forEachIndexed { index, position ->
                if (index == 0) path.moveTo(position.x, position.y) else path.lineTo(position.x, position.y)
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
            positions.forEach { position ->
                drawCircle(color = colors.accent, radius = 3.dp.toPx(), center = position)
            }
        }
    }
}

@Composable
private fun IntensityCard(distribution: IntensityDistribution?, lthr: Int?) {
    val colors = McTheme.colors
    McCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tempo em zonas",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lthr?.let { "LTHR $it bpm · últimos ${distribution?.windowDays ?: 28} dias" }
                        ?: "Sem limiar calibrado",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
            distribution?.let { McConfidenceBadge(confidence = it.confidence) }
        }
        Spacer(modifier = Modifier.height(14.dp))
        when {
            distribution != null -> IntensityDetails(distribution)

            lthr == null -> Text(
                text = "A distribuição 80/20 é medida por tempo em zona de FC. " +
                    "Calibre o limiar no perfil para liberá-la.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            else -> Text(
                text = "Nenhuma corrida com FC suficiente nos últimos 28 dias.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun IntensityDetails(distribution: IntensityDistribution) {
    val colors = McTheme.colors
    IntensityBar(
        easySec = distribution.easySec,
        moderateSec = distribution.moderateSec,
        hardSec = distribution.hardSec,
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .semantics {
                contentDescription = String.format(
                    PT_BR,
                    "Distribuição por tempo: %.0f%% fácil, %.0f%% moderado e %.0f%% forte",
                    distribution.easyPercent(),
                    distribution.moderatePercent(),
                    distribution.hardPercent(),
                )
            },
    )
    Spacer(modifier = Modifier.height(12.dp))
    IntensityLegendRow(
        color = colors.zone2,
        label = "Fácil · Z1–Z2",
        seconds = distribution.easySec,
        percent = distribution.easyPercent(),
    )
    IntensityLegendRow(
        color = colors.zone3,
        label = "Moderado · Z3",
        seconds = distribution.moderateSec,
        percent = distribution.moderatePercent(),
    )
    IntensityLegendRow(
        color = colors.zone4,
        label = "Forte · Z4–Z5",
        seconds = distribution.hardSec,
        percent = distribution.hardPercent(),
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = if (distribution.meetsEasyTarget) {
            "80% ou mais do tempo em Z1–Z2."
        } else {
            "Menos de 80% do tempo em Z1–Z2."
        } + " Heurística R-ZONE-02 · EV-SEILER-2010.",
        style = McTextStyles.caption,
        color = colors.textTertiary,
    )
    if (distribution.weeks.size > 1) {
        McDivider(modifier = Modifier.padding(vertical = 10.dp))
        Text(
            text = "Por semana",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        distribution.weeks.forEach { week ->
            WeeklyIntensityRow(week)
        }
    }
}

@Composable
private fun WeeklyIntensityRow(week: WeeklyIntensity) {
    val colors = McTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = week.weekStart.format(DAY_FORMAT),
            style = McTextStyles.caption,
            color = colors.textTertiary,
            modifier = Modifier.width(52.dp),
        )
        IntensityBar(
            easySec = week.easySec,
            moderateSec = week.moderateSec,
            hardSec = week.hardSec,
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
        )
        Text(
            text = String.format(PT_BR, "%.0f%% fácil", week.easyPercent()),
            style = McTextStyles.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(76.dp),
        )
    }
}

@Composable
private fun IntensityLegendRow(color: Color, label: String, seconds: Int, percent: Double) {
    val colors = McTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatDuration(seconds),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(
            text = String.format(PT_BR, "%.0f%%", percent),
            style = McTextStyles.caption,
            color = colors.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun IntensityBar(easySec: Int, moderateSec: Int, hardSec: Int, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    Row(modifier = modifier.clip(RoundedCornerShape(6.dp))) {
        if (easySec > 0) {
            Box(
                modifier = Modifier
                    .weight(easySec.toFloat())
                    .fillMaxHeight()
                    .background(colors.zone2),
            )
        }
        if (moderateSec > 0) {
            Box(
                modifier = Modifier
                    .weight(moderateSec.toFloat())
                    .fillMaxHeight()
                    .background(colors.zone3),
            )
        }
        if (hardSec > 0) {
            Box(
                modifier = Modifier
                    .weight(hardSec.toFloat())
                    .fillMaxHeight()
                    .background(colors.zone4),
            )
        }
    }
}

@Composable
private fun ReadinessHistoryCard(days: List<ReadinessHistoryDay>) {
    val colors = McTheme.colors
    McCard {
        days.forEachIndexed { index, day ->
            if (index > 0) {
                McDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = day.date.format(DAY_FORMAT),
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.width(52.dp),
                )
                Text(
                    text = day.score.toString(),
                    style = McTextStyles.metricSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                McPill(
                    text = when (day.level) {
                        com.meucoach.core.model.ReadinessLevel.GREEN -> "Verde"
                        com.meucoach.core.model.ReadinessLevel.AMBER -> "Âmbar"
                        com.meucoach.core.model.ReadinessLevel.RED -> "Vermelho"
                    },
                    containerColor = when (day.level) {
                        com.meucoach.core.model.ReadinessLevel.GREEN -> colors.success.copy(alpha = 0.12f)
                        com.meucoach.core.model.ReadinessLevel.AMBER -> colors.warning.copy(alpha = 0.12f)
                        com.meucoach.core.model.ReadinessLevel.RED -> colors.danger.copy(alpha = 0.12f)
                    },
                    contentColor = when (day.level) {
                        com.meucoach.core.model.ReadinessLevel.GREEN -> colors.success
                        com.meucoach.core.model.ReadinessLevel.AMBER -> colors.warning
                        com.meucoach.core.model.ReadinessLevel.RED -> colors.danger
                    },
                )
            }
        }
    }
}

@Composable
private fun PmcCard(snapshot: LoadSnapshot) {
    val colors = McTheme.colors
    McCard {
        Text(
            text = "Como a carga acumulou",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Carga acumulada e carga recente na mesma unidade.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(color = colors.accent, label = "Acumulada")
            LegendDot(color = colors.coral, label = "Recente")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .semantics { contentDescription = "Gráfico de carga acumulada e recente dos últimos 42 dias" },
        ) {
            drawPmcGrid(colors.borderSubtle)
            val max = (snapshot.ctlSeries + snapshot.atlSeries).max().coerceAtLeast(1.0)
            drawLoadLine(snapshot.ctlSeries, max, colors.accent)
            drawLoadLine(snapshot.atlSeries, max, colors.coral)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Semana atual à direita.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
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

private fun DrawScope.drawPmcGrid(gridColor: Color) {
    repeat(4) { index ->
        val y = size.height * index / 3
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
    }
}

private fun DrawScope.drawLoadLine(values: List<Double>, max: Double, color: Color) {
    if (values.size < 2) return
    val path = Path()
    values.forEachIndexed { index, value ->
        val x = index.toFloat() / (values.size - 1) * size.width
        val y = size.height - (value / max).toFloat() * size.height
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

private data class AcwrFlag(val title: String, val color: Color)

@Composable
private fun acwrFlag(acwr: Double?): AcwrFlag {
    val colors = McTheme.colors
    return when {
        acwr == null -> AcwrFlag("Sem dados suficientes para a proporção", colors.textTertiary)
        acwr > 1.5 -> AcwrFlag("Sinal alto: não aumente a carga", colors.danger)
        acwr > 1.3 -> AcwrFlag("Carga recente acima da faixa; revise a semana", colors.warning)
        acwr < 0.8 -> AcwrFlag("Carga recente baixa; retome com cautela", colors.warning)
        else -> AcwrFlag("Sem alerta de carga", colors.success)
    }
}

private fun formatOneDecimal(value: Double): String = String.format(PT_BR, "%.1f", value)

private fun formatTwoDecimals(value: Double): String = String.format(PT_BR, "%.2f", value)

private val PT_BR = Locale.forLanguageTag("pt-BR")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM")
