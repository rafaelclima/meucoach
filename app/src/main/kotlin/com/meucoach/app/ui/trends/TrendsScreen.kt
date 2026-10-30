package com.meucoach.app.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McEmptyState
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McSectionHeader
import com.meucoach.core.designsystem.component.McStat
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessHistoryDay
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
    )
}

@Composable
internal fun TrendsContent(
    state: TrendsUiState,
    modifier: Modifier = Modifier,
    readinessHistory: List<ReadinessHistoryDay> = emptyList(),
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
                description = "Importe corridas com FC ou pace e calibre o limiar para ver CTL, ATL e ACWR.",
                modifier = Modifier.align(Alignment.Center),
            )

            else -> LoadDetails(snapshot = state.snapshot, readinessHistory = readinessHistory)
        }
    }
}

@Composable
private fun LoadDetails(snapshot: LoadSnapshot, readinessHistory: List<ReadinessHistoryDay>) {
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
                    text = "CTL, ATL e TSB dos últimos 42 dias. Tendência, não prescrição.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
        item {
            McCard {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    McStat(
                        label = "CTL",
                        value = formatOneDecimal(snapshot.ctl),
                        modifier = Modifier.weight(1f),
                    )
                    McStat(
                        label = "ATL",
                        value = formatOneDecimal(snapshot.atl),
                        modifier = Modifier.weight(1f),
                    )
                    McStat(
                        label = "TSB",
                        value = formatOneDecimal(snapshot.tsb),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    McStat(
                        label = "Carga 7d",
                        value = formatOneDecimal(snapshot.daily.takeLast(7).sumOf { it.load }),
                        modifier = Modifier.weight(1f),
                    )
                    McStat(
                        label = "ACWR",
                        value = snapshot.acwr?.let { formatTwoDecimals(it) } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    McStat(
                        label = "Monotonia",
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
                    text = "O ACWR é um sinal de atenção, nunca uma meta. " +
                        "${snapshot.missingDays} dias sem carga na janela.",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
        }
        if (readinessHistory.isNotEmpty()) {
            item { McSectionHeader(title = "Prontidão · 14 dias") }
            item { ReadinessHistoryCard(readinessHistory) }
        }
        item { McSectionHeader(title = "PMC · 42 dias") }
        item { PmcCard(snapshot) }
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
            text = "Linhas de CTL e ATL na mesma unidade de carga.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(color = colors.accent, label = "CTL")
            LegendDot(color = colors.coral, label = "ATL")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .semantics { contentDescription = "Gráfico de CTL e ATL dos últimos 42 dias" },
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
        acwr == null -> AcwrFlag("Sem dados suficientes para ACWR", colors.textTertiary)
        acwr > 1.5 -> AcwrFlag("Sinal alto: não aumente a carga", colors.danger)
        acwr > 1.3 -> AcwrFlag("Carga aguda acima da faixa; revise a semana", colors.warning)
        acwr < 0.8 -> AcwrFlag("Carga aguda baixa; retome com cautela", colors.warning)
        else -> AcwrFlag("Sem alerta de ACWR", colors.success)
    }
}

private fun formatOneDecimal(value: Double): String = String.format(PT_BR, "%.1f", value)

private fun formatTwoDecimals(value: Double): String = String.format(PT_BR, "%.2f", value)

private val PT_BR = Locale.forLanguageTag("pt-BR")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM")
