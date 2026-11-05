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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.app.ui.common.readinessLabel
import com.meucoach.app.ui.firstrun.FirstRunTipCard
import com.meucoach.core.designsystem.component.McBarChart
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McChartSeries
import com.meucoach.core.designsystem.component.McConfidenceBadge
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McEmptyState
import com.meucoach.core.designsystem.component.McLineChart
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McSectionHeader
import com.meucoach.core.designsystem.component.McStat
import com.meucoach.core.designsystem.component.McTextButton
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
    onCheckInClick: () -> Unit = {},
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
        onCheckInClick = onCheckInClick,
    )
}

@Composable
internal fun TrendsContent(
    state: TrendsUiState,
    modifier: Modifier = Modifier,
    readinessHistory: List<ReadinessHistoryDay> = emptyList(),
    onPeriodSelected: (TrendsPeriod) -> Unit = {},
    onCheckInClick: () -> Unit = {},
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

            state.snapshot == null || !state.snapshot.hasData -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                McEmptyState(
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    title = "Sem carga para mostrar",
                    description = "Importe corridas com FC ou ritmo e calibre seu limite. " +
                        "Assim você vê carga e recuperação.",
                )
                FirstRunTipCard(
                    tipId = "trends-empty",
                    title = "A evolução aparece com o tempo",
                    body = "Depois de 2 semanas de corridas, carga e tendências ficam mais claras. " +
                        "Enquanto isso, o check-in de hoje já ajusta seu treino.",
                    actionLabel = "Fazer check-in",
                    onAction = onCheckInClick,
                )
            }

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
    var loadHelpOpen by remember { mutableStateOf(false) }
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
                    text = "Como sua carga evoluiu nos últimos 42 dias. " +
                        "É tendência, não prescrição.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
        item {
            LoadSummaryCard(snapshot = snapshot, onHelpClick = { loadHelpOpen = true })
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
                    text = "O aumento compara a carga dos últimos 7 dias com a média das últimas " +
                        "4 semanas. Entre 0,8 e 1,3 é uma faixa de segurança; acima de 1,5 é sinal " +
                        "de subida rápida. É um alerta, nunca uma meta. " +
                        "${snapshot.missingDays} dias sem carga no período.",
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
                item { McSectionHeader(title = "Economia · Ritmo vs FC") }
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
    if (loadHelpOpen) {
        LoadHelpSheet(onDismiss = { loadHelpOpen = false })
    }
}

@Composable
private fun LoadSummaryCard(snapshot: LoadSnapshot, onHelpClick: () -> Unit) {
    val colors = McTheme.colors
    McCard {
        Text(text = "CARGA DE TREINO", style = McTextStyles.overline, color = colors.textTertiary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Em pontos de esforço",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Calculada pela FC ou pelo ritmo. Compare com você mesmo ao longo das semanas — " +
                "não é km, minutos nem calorias.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            McStat(
                label = "Base",
                hint = "média/dia · 42d",
                value = formatOneDecimal(snapshot.ctl),
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Fadiga",
                hint = "média/dia · 7d",
                value = formatOneDecimal(snapshot.atl),
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Balanço",
                hint = "base − fadiga",
                value = formatOneDecimal(snapshot.tsb),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        McDivider()
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            McStat(
                label = "Carga",
                hint = "soma · 7 dias",
                value = formatOneDecimal(snapshot.daily.takeLast(7).sumOf { it.load }),
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Aumento",
                hint = "7d ÷ 28d",
                value = snapshot.acwr?.let { formatTwoDecimals(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Variedade",
                hint = "menor é melhor",
                value = snapshot.monotony?.let { formatTwoDecimals(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        McTextButton(
            text = "Entenda cada número",
            onClick = onHelpClick,
            modifier = Modifier.testTag("load-help-button"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadHelpSheet(onDismiss: () -> Unit) {
    val colors = McTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
                .testTag("load-help-content"),
        ) {
            Text(
                text = "Como ler a carga",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Os números são pontos de esforço. Não existe um valor universal de “bom”: " +
                    "o que importa é a direção ao longo das semanas.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(20.dp))
            LoadHelpItem(
                title = "Base · 42 dias",
                description = "Média diária de esforço das últimas 6 semanas. É o seu condicionamento; " +
                    "sobe devagar com treino consistente e cai com pausas longas.",
            )
            LoadHelpItem(
                title = "Fadiga · 7 dias",
                description = "Média diária da última semana. Sobe depois de treinos fortes e cai com descanso.",
            )
            LoadHelpItem(
                title = "Balanço",
                description = "Base menos fadiga. Negativo significa que a fadiga recente está acima da base — " +
                    "normal em semana de treino, não é erro. Positivo significa que você está mais descansado " +
                    "que o normal.",
            )
            LoadHelpItem(
                title = "Carga · 7 dias",
                description = "Soma de todos os pontos dos últimos 7 dias. É o volume de esforço da semana.",
            )
            LoadHelpItem(
                title = "Aumento",
                description = "Carga dos últimos 7 dias dividida pela média das últimas 4 semanas. " +
                    "Entre 0,8 e 1,3 é a faixa de segurança; acima de 1,5 indica subida rápida.",
            )
            LoadHelpItem(
                title = "Variedade",
                description = "Mostra se todos os dias têm esforço parecido. Números altos indicam semana " +
                    "monótona, com pouco contraste entre dias leves e fortes.",
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "A carga vem da FC (quando confiável) ou do ritmo. Por isso o número é relativo: " +
                    "compare você com você, nunca com outra pessoa.",
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun LoadHelpItem(title: String, description: String) {
    val colors = McTheme.colors
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = description,
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
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
            text = "Muda o período de volume, hábito e economia. " +
                "Carga e equilíbrio têm janelas próprias.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun WeeklyVolumeCard(weeks: List<com.meucoach.core.domain.trends.WeeklyVolume>) {
    val colors = McTheme.colors
    val total = weeks.sumOf { it.kilometers }
    val average = total / weeks.size
    val best = weeks.maxByOrNull { it.kilometers }
    val maxKilometers = weeks.maxOfOrNull { it.kilometers } ?: 0.0
    val yStep = when {
        maxKilometers <= 6.0 -> 1.0
        maxKilometers <= 12.0 -> 2.0
        maxKilometers <= 25.0 -> 5.0
        else -> 10.0
    }
    McCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Distância por semana",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cada barra é uma semana; a última é a semana atual.",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(PT_BR, "%.1f", total),
                    style = McTextStyles.metricSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = "km no total",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        McBarChart(
            values = weeks.map { it.kilometers },
            labels = weeks.map { it.weekStart.format(DAY_FORMAT) },
            highlightIndex = weeks.lastIndex,
            ySuffix = " km",
            yDecimals = 0,
            yStep = yStep,
            labelSpacing = when {
                weeks.size > 12 -> 4
                weeks.size > 6 -> 2
                else -> 1
            },
            height = 156.dp,
            contentDescription = "Volume semanal em quilômetros",
        )
        Spacer(modifier = Modifier.height(12.dp))
        McDivider()
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            McStat(
                label = "Média",
                hint = "por semana",
                value = String.format(PT_BR, "%.1f km", average),
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Melhor",
                hint = best?.weekStart?.format(DAY_FORMAT) ?: "—",
                value = best?.let { String.format(PT_BR, "%.1f km", it.kilometers) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Corridas",
                hint = "no período",
                value = weeks.sumOf { it.runs }.toString(),
                modifier = Modifier.weight(1f),
            )
        }
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
        McBarChart(
            values = completion.weeks.map { week ->
                week.completionRatio?.takeIf { it > 0.0 }?.times(100)
            },
            labels = completion.weeks.map { it.weekStart.format(DAY_FORMAT) },
            highlightIndex = completion.weeks.lastIndex,
            ySuffix = "%",
            yDecimals = 0,
            yStep = 25.0,
            minY = 0.0,
            maxY = 100.0,
            labelSpacing = if (completion.weeks.size > 6) 2 else 1,
            height = 132.dp,
            contentDescription = "Conclusão do plano nas últimas " +
                "${completion.windowWeeks} semanas: ${completion.done} de " +
                "${completion.planned} sessões concluídas e ${completion.skipped} puladas",
        )
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
                "%.1f km no período · ritmo médio %s",
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
                text = "Dias que você correu",
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
                    colors.borderSubtle.copy(alpha = 0.42f)
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
            text = "Seus melhores trechos",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Suas melhores marcas até hoje, para acompanhar a evolução — não é uma meta.",
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
                label = "Melhor ritmo",
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
            text = "O melhor ritmo considera corridas com pelo menos 3 km.",
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
    val latest = points.last().efficiencyMetersPerBeat
    val change = if (points.size > 1) latest - points.first().efficiencyMetersPerBeat else null
    val minEfficiency = points.minOf { it.efficiencyMetersPerBeat }
    val maxEfficiency = points.maxOf { it.efficiencyMetersPerBeat }
    val axisMin = (minEfficiency * 12).toInt() / 12.0 - 0.05
    val axisMax = (maxEfficiency * 12).toInt() / 12.0 + 0.10
    McCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Metros por batimento",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Quanto você corre por batimento cardíaco. Se sobe com o mesmo esforço, " +
                        "você está mais econômico. Compare sob condições semelhantes.",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(PT_BR, "%.2f", latest),
                    style = McTextStyles.metricSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = "m/batimento",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        McLineChart(
            series = listOf(
                McChartSeries(
                    label = "Metros por batimento",
                    values = points.map { it.efficiencyMetersPerBeat },
                    color = colors.accent,
                    area = true,
                    showPoints = true,
                ),
            ),
            xLabels = points.map { it.date.format(DAY_FORMAT) },
            ySuffix = " m/b",
            yDecimals = 2,
            yStep = 0.05,
            minY = axisMin,
            maxY = axisMax,
            labelSpacing = if (points.size > 8) 2 else 1,
            height = 168.dp,
            showLegend = false,
            contentDescription = String.format(
                PT_BR,
                "Evolução da economia de corrida em %d corridas. " +
                    "Escala ampliada de %.2f a %.2f metros por batimento.",
                points.size,
                axisMin,
                axisMax,
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = String.format(
                PT_BR,
                "Escala ampliada (%.2f a %.2f m/batimento) para destacar diferenças pequenas entre corridas.",
                axisMin,
                axisMax,
            ),
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            McStat(
                label = "Média",
                hint = "m/batimento",
                value = String.format(PT_BR, "%.2f", averageEfficiency),
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Última",
                hint = "m/batimento",
                value = String.format(PT_BR, "%.2f", latest),
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Variação",
                hint = "1ª → última",
                value = change?.let { String.format(PT_BR, "%+.2f", it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
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
                    text = lthr?.let { "FC no limite $it bpm · últimos ${distribution?.windowDays ?: 28} dias" }
                        ?: "Sem limite de esforço calibrado",
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
                text = "Como você distribuiu o tempo entre fácil, moderado e forte, pelas zonas de FC. " +
                    "Calibre seu limite de esforço no perfil para liberá-la.",
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
        } + " O ideal é passar cerca de 80% do tempo em ritmo fácil.",
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
        McBarChart(
            values = days.map { it.score.toDouble() },
            labels = days.map { it.date.format(DAY_FORMAT) },
            pointColors = days.map { day ->
                when (day.level) {
                    com.meucoach.core.model.ReadinessLevel.GREEN -> colors.success.copy(alpha = 0.88f)
                    com.meucoach.core.model.ReadinessLevel.AMBER -> colors.warning.copy(alpha = 0.88f)
                    com.meucoach.core.model.ReadinessLevel.RED -> colors.danger.copy(alpha = 0.88f)
                }
            },
            highlightIndex = days.lastIndex,
            ySuffix = "",
            yDecimals = 0,
            yStep = 25.0,
            minY = 0.0,
            maxY = 100.0,
            labelSpacing = if (days.size > 7) 3 else 2,
            height = 128.dp,
            contentDescription = "Prontidão dos últimos ${days.size} dias",
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Quanto mais alto, mais pronto para treinar. A cor reforça o nível; o texto explica.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        McDivider(modifier = Modifier.padding(vertical = 10.dp))
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
                    text = readinessLabel(day.level),
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
    val maxLoad = (snapshot.ctlSeries + snapshot.atlSeries).maxOrNull() ?: 0.0
    val loadStep = when {
        maxLoad <= 5.0 -> 1.0
        maxLoad <= 12.0 -> 2.0
        maxLoad <= 30.0 -> 5.0
        else -> 10.0
    }
    McCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Como a carga acumulou",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Base (42 dias), fadiga recente (7 dias) e balanço na mesma unidade.",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatOneDecimal(snapshot.tsb),
                    style = McTextStyles.metricSmall,
                    color = if (snapshot.tsb < 0) colors.coral else colors.success,
                )
                Text(
                    text = "balanço hoje",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        McLineChart(
            series = listOf(
                McChartSeries(
                    label = "Base",
                    values = snapshot.ctlSeries,
                    color = colors.accent,
                    area = true,
                ),
                McChartSeries(
                    label = "Fadiga",
                    values = snapshot.atlSeries,
                    color = colors.coral,
                ),
                McChartSeries(
                    label = "Balanço",
                    values = snapshot.tsbSeries,
                    color = colors.lavender,
                    dashed = true,
                ),
            ),
            xLabels = snapshot.daily.map { it.date.format(DAY_FORMAT) },
            ySuffix = " pts",
            yDecimals = 0,
            yStep = loadStep,
            labelSpacing = 7,
            height = 196.dp,
            contentDescription = "Gráfico de carga de base e fadiga recente dos últimos 42 dias",
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Semana atual à direita. Balanço negativo significa mais fadiga que base — " +
                "normal em semana de treino, não é erro.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
}

private data class AcwrFlag(val title: String, val color: Color)

@Composable
private fun acwrFlag(acwr: Double?): AcwrFlag {
    val colors = McTheme.colors
    return when {
        acwr == null -> AcwrFlag("Sem dados suficientes para comparar a carga", colors.textTertiary)
        acwr > 1.5 -> AcwrFlag("Carga subindo rápido: não aumente", colors.danger)
        acwr > 1.3 -> AcwrFlag("Carga recente acima da faixa; revise a semana", colors.warning)
        acwr < 0.8 -> AcwrFlag("Carga recente baixa; retome com cautela", colors.warning)
        else -> AcwrFlag("Sem alerta de carga", colors.success)
    }
}

private fun formatOneDecimal(value: Double): String = String.format(PT_BR, "%.1f", value)

private fun formatTwoDecimals(value: Double): String = String.format(PT_BR, "%.2f", value)

private val PT_BR = Locale.forLanguageTag("pt-BR")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM")
