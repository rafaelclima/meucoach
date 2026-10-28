package com.meucoach.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McConfidenceBadge
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McGauge
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McSignalDot
import com.meucoach.core.designsystem.component.McStat
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.ReadinessLevel
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@Composable
fun TodayScreen(
    onDemoAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: TodayUiState = demoTodayUiState(),
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Greeting(state = state) }
        item { ReadinessCard(state = state, onWhyClick = { onDemoAction("Explicação detalhada chega na Fase 1.") }) }
        item {
            NextWorkoutCard(
                workout = state.workout,
                onSwapClick = { onDemoAction("Troca de treino chega na Fase 1.") },
                onStartClick = { onDemoAction("Execução do treino chega na Fase 1.") },
            )
        }
        item {
            WeeklyLoadCard(
                state = state,
                onTrendsClick = { onDemoAction("Tendências chegam na Fase 1.") },
            )
        }
        item { CheckInCard(onCheckInClick = { onDemoAction("Check-in chega na Fase 1.") }) }
        item {
            RecentActivityCard(
                activity = state.recentActivity,
                onClick = { onDemoAction("Detalhe da atividade chega na Fase 0.") },
            )
        }
        item { DemoFooter() }
    }
}

@Composable
private fun Greeting(state: TodayUiState) {
    val colors = McTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${state.greeting}, ${state.athleteName}",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WbSunny,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colors.warning,
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${state.dateLine} · ${state.weather}",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.athleteName.take(1).uppercase(Locale.forLanguageTag("pt-BR")),
                style = MaterialTheme.typography.titleLarge,
                color = colors.accentText,
            )
        }
    }
}

@Composable
private fun ReadinessCard(state: TodayUiState, onWhyClick: () -> Unit) {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.ELEVATED) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PRONTIDÃO",
                style = McTextStyles.overline,
                color = colors.textTertiary,
            )
            McConfidenceBadge(confidence = state.readinessConfidence)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            McGauge(
                score = state.readinessScore,
                level = state.readinessLevel,
                levelLabel = readinessLabel(state.readinessLevel),
                size = 132.dp,
                strokeWidth = 9.dp,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.readinessSignals.forEach { signal ->
                    ReadinessSignalRow(signal = signal)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        McTextButton(
            text = "Por que esta prontidão?",
            onClick = onWhyClick,
            modifier = Modifier.align(Alignment.Start),
        )
    }
}

@Composable
private fun ReadinessSignalRow(signal: ReadinessSignalUi) {
    val colors = McTheme.colors
    val toneColor = when (signal.tone) {
        SignalTone.POSITIVE -> colors.success
        SignalTone.NEUTRAL -> colors.textTertiary
        SignalTone.WARNING -> colors.warning
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        McSignalDot(
            color = toneColor,
            modifier = Modifier.padding(top = 5.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = signal.label, style = McTextStyles.caption, color = colors.textTertiary)
                Text(
                    text = signal.value,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
            }
            Text(
                text = signal.hint,
                style = McTextStyles.caption,
                color = colors.textTertiary,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun NextWorkoutCard(workout: WorkoutUi, onSwapClick: () -> Unit, onStartClick: () -> Unit) {
    val colors = McTheme.colors
    McCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "PRÓXIMO TREINO", style = McTextStyles.overline, color = colors.textTertiary)
            McPill(
                text = workout.zone,
                containerColor = colors.zone2.copy(alpha = 0.10f),
                contentColor = colors.zone2,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = workout.title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkoutMetric(
                label = "DURAÇÃO",
                value = workout.duration,
                unit = "min",
                modifier = Modifier.weight(1f),
            )
            WorkoutMetric(
                label = "DISTÂNCIA",
                value = workout.distance,
                unit = "km",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkoutMetric(
                label = "PACE",
                value = workout.pace,
                unit = "/km",
                modifier = Modifier.weight(1f),
            )
            WorkoutMetric(
                label = "FC-ALVO",
                value = workout.heartRate,
                unit = "bpm",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accent),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "POR QUÊ", style = McTextStyles.overline, color = colors.accentText)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = workout.rationale,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            McSecondaryButton(
                text = "Trocar",
                onClick = onSwapClick,
                modifier = Modifier.weight(1f),
            )
            McPrimaryButton(
                text = "Iniciar",
                onClick = onStartClick,
                modifier = Modifier.weight(1f),
                leadingIcon = Icons.Outlined.PlayArrow,
            )
        }
    }
}

@Composable
private fun WorkoutMetric(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    Column(modifier = modifier) {
        Text(text = label, style = McTextStyles.overline, color = colors.textTertiary)
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = McTextStyles.metricSmall, color = colors.textPrimary)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = unit,
                style = McTextStyles.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun WeeklyLoadCard(state: TodayUiState, onTrendsClick: () -> Unit) {
    val colors = McTheme.colors
    McCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(text = "CARGA DA SEMANA", style = McTextStyles.overline, color = colors.textTertiary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatDecimal(state.weeklyLoadKm),
                        style = McTextStyles.metric,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "km",
                        style = McTextStyles.caption,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            McPill(
                text = "ACWR 1,1",
                containerColor = colors.success.copy(alpha = 0.10f),
                contentColor = colors.success,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        WeeklyLoadBars(days = state.days)
        Spacer(modifier = Modifier.height(12.dp))
        McDivider()
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            McStat(
                label = "CTL",
                value = state.ctl.toString(),
                hint = "fitness",
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            )
            McStat(
                label = "ATL",
                value = state.atl.toString(),
                hint = "fadiga",
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            )
            McStat(
                label = "TSB",
                value = state.tsb.toString(),
                hint = "forma",
                modifier = Modifier.weight(1f),
                valueColor = colors.warning,
                horizontalAlignment = Alignment.CenterHorizontally,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            McTextButton(
                text = "Ver tendências",
                onClick = onTrendsClick,
                trailingArrow = true,
            )
        }
    }
}

@Composable
private fun WeeklyLoadBars(days: List<DayLoadUi>) {
    val colors = McTheme.colors
    val maxLoad = days.maxOfOrNull { it.loadKm }?.coerceAtLeast(1.0) ?: 1.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(maxOf(2.0, day.loadKm / maxLoad * 64.0).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    day.isToday -> colors.accent
                                    day.loadKm > 0.0 -> colors.borderStrong
                                    else -> colors.surfaceInset
                                },
                            ),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = day.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.isToday) colors.textPrimary else colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun CheckInCard(onCheckInClick: () -> Unit) {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.ACCENT) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Check-in de hoje",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "15 s para calibrar sono, dor e energia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            McTextButton(
                text = "Fazer",
                onClick = onCheckInClick,
                modifier = Modifier.widthIn(min = 72.dp),
            )
        }
    }
}

@Composable
private fun RecentActivityCard(activity: RecentActivityUi, onClick: () -> Unit) {
    val colors = McTheme.colors
    McCard(onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceInset),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.DirectionsRun,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = colors.accentText,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "ÚLTIMA CORRIDA", style = McTextStyles.overline, color = colors.textTertiary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = activity.subtitle,
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = activity.distance,
                style = McTextStyles.metricSmall,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = activity.duration,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = activity.pace,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            McPill(
                text = activity.zone,
                containerColor = colors.zone5.copy(alpha = 0.10f),
                contentColor = colors.zone5,
            )
        }
    }
}

@Composable
private fun DemoFooter() {
    Text(
        text = "Fase 0 · dados de demonstração",
        style = McTextStyles.caption,
        color = McTheme.colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
    )
}

private fun readinessLabel(level: ReadinessLevel): String = when (level) {
    ReadinessLevel.GREEN -> "Verde"
    ReadinessLevel.AMBER -> "Âmbar"
    ReadinessLevel.RED -> "Vermelho"
}

private fun formatDecimal(value: Double): String = String.format(Locale.forLanguageTag("pt-BR"), "%.1f", value)

@Preview(name = "Hoje — Dark", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun TodayScreenDarkPreview() {
    McTheme(darkTheme = true) {
        TodayScreen(
            onDemoAction = {},
            state = demoTodayUiState(LocalDate.of(2026, 10, 27), LocalTime.of(9, 0)),
        )
    }
}

@Preview(name = "Hoje — Light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun TodayScreenLightPreview() {
    McTheme(darkTheme = false) {
        TodayScreen(
            onDemoAction = {},
            state = demoTodayUiState(LocalDate.of(2026, 10, 27), LocalTime.of(9, 0)),
        )
    }
}
