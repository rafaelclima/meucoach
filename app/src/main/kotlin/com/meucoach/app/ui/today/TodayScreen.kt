package com.meucoach.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.SessionSwapper
import com.meucoach.core.domain.prescription.SwapResult
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ReadinessLevel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@Composable
fun TodayScreen(
    onDemoAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: TodayUiState = demoTodayUiState(),
    today: LocalDate = LocalDate.now(),
    onCheckInClick: () -> Unit = {},
    onCompleteSession: (LocalDate) -> Unit = {},
    onSkipSession: (LocalDate, String) -> Unit = { _, _ -> },
    onSwapSession: (LocalDate, LocalDate) -> Unit = { _, _ -> },
) {
    var swapOpen by remember { mutableStateOf(false) }
    var startOpen by remember { mutableStateOf(false) }
    var explanationOpen by remember { mutableStateOf(false) }
    val openExplanation = {
        if (state.explanation != null) {
            explanationOpen = true
        } else {
            onDemoAction("Explicação disponível quando houver dados reais.")
        }
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(McTheme.colors.background)
            .statusBarsPadding()
            .testTag("today-list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Greeting(state = state) }
        item { ReadinessCard(state = state, onWhyClick = openExplanation) }
        item {
            NextWorkoutCard(
                workout = state.workout,
                onSwapClick = { swapOpen = true },
                onStartClick = { startOpen = true },
                onWhyClick = openExplanation,
            )
        }
        state.plan?.let { plan ->
            item {
                PlanWeekCard(
                    plan = plan,
                    today = today,
                    onComplete = onCompleteSession,
                    onSkip = onSkipSession,
                )
            }
        }
        item {
            WeeklyLoadCard(
                state = state,
                onTrendsClick = { onDemoAction("Tendências chegam na Fase 1.") },
            )
        }
        item { CheckInCard(done = state.hasCheckedIn, onCheckInClick = onCheckInClick) }
        item {
            RecentActivityCard(
                activity = state.recentActivity,
                onClick = { onDemoAction("Detalhe da atividade chega na Fase 0.") },
            )
        }
        item { DemoFooter() }
    }

    if (swapOpen) {
        SwapSessionDialog(
            plan = state.plan,
            today = today,
            onDismiss = { swapOpen = false },
            onSwap = { first, second ->
                onSwapSession(first, second)
                swapOpen = false
            },
        )
    }
    if (startOpen) {
        val hasPlannedSession = state.plan?.sessions?.any {
            it.date == today && it.status == SessionStatus.PLANNED
        } == true
        WorkoutSessionDialog(
            workout = state.workout,
            hasPlannedSession = hasPlannedSession,
            onDismiss = { startOpen = false },
            onFinish = {
                onCompleteSession(today)
                startOpen = false
            },
        )
    }
    if (explanationOpen) {
        state.explanation?.let { explanation ->
            ExplanationSheet(
                explanation = explanation,
                onDismiss = { explanationOpen = false },
            )
        }
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
                text = if (state.athleteName.isBlank()) {
                    state.greeting
                } else {
                    "${state.greeting}, ${state.athleteName}"
                },
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.weather.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Outlined.WbSunny,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.warning,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = if (state.weather.isBlank()) {
                        state.dateLine
                    } else {
                        "${state.dateLine} · ${state.weather}"
                    },
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
                text = state.athleteName.take(1).ifBlank { "M" }.uppercase(Locale.forLanguageTag("pt-BR")),
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
private fun NextWorkoutCard(
    workout: WorkoutUi,
    onSwapClick: () -> Unit,
    onStartClick: () -> Unit,
    onWhyClick: () -> Unit,
) {
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
        Spacer(modifier = Modifier.height(4.dp))
        McTextButton(
            text = "Ver explicação",
            onClick = onWhyClick,
            trailingArrow = false,
        )
        Spacer(modifier = Modifier.height(14.dp))
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
    val hasLoad = state.ctl != null
    val hasDistance = state.weeklyLoadKm != null
    McCard {
        Text(text = "CARGA DA SEMANA", style = McTextStyles.overline, color = colors.textTertiary)
        Spacer(modifier = Modifier.height(4.dp))
        if (!hasLoad && !hasDistance) {
            Text(
                text = "Sem carga ainda",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Importe uma corrida com FC ou ritmo para ver carga e recuperação.",
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                McTextButton(
                    text = "Ver tendências",
                    onClick = onTrendsClick,
                    trailingArrow = true,
                )
            }
            return@McCard
        }
        if (hasDistance || state.acwr != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    state.weeklyLoadKm?.let { weeklyKm ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = formatDecimal(weeklyKm),
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
                }
                state.acwr?.let { acwr ->
                    McPill(
                        text = "Proporção ${formatTwoDecimals(acwr)}",
                        containerColor = acwrColor(acwr).copy(alpha = 0.10f),
                        contentColor = acwrColor(acwr),
                    )
                }
            }
        }
        if (state.days.any { it.loadKm > 0.0 }) {
            Spacer(modifier = Modifier.height(16.dp))
            WeeklyLoadBars(days = state.days)
        }
        if (hasLoad) {
            Spacer(modifier = Modifier.height(12.dp))
            McDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                McStat(
                    label = "Acumulada",
                    value = state.ctl?.toString() ?: NO_VALUE,
                    hint = "42 dias",
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
                McStat(
                    label = "Recente",
                    value = state.atl?.toString() ?: NO_VALUE,
                    hint = "7 dias",
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
                McStat(
                    label = "Balanço",
                    value = state.tsb?.toString() ?: NO_VALUE,
                    hint = "recuperação",
                    modifier = Modifier.weight(1f),
                    valueColor = colors.warning,
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
            }
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
private fun CheckInCard(done: Boolean, onCheckInClick: () -> Unit) {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.ACCENT) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (done) "Check-in feito hoje" else "Check-in de hoje",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (done) {
                        "Sono, dor e energia registrados. Você pode revisar."
                    } else {
                        "15 s para calibrar sono, dor e energia."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            McTextButton(
                text = if (done) "Revisar" else "Fazer",
                onClick = onCheckInClick,
                modifier = Modifier.widthIn(min = 72.dp),
            )
        }
    }
}

@Composable
private fun PlanWeekCard(
    plan: com.meucoach.core.domain.prescription.WeeklyPlan,
    today: LocalDate,
    onComplete: (LocalDate) -> Unit,
    onSkip: (LocalDate, String) -> Unit,
) {
    val colors = McTheme.colors
    var skipTarget by remember { mutableStateOf<LocalDate?>(null) }
    var skipReason by remember { mutableStateOf("") }
    McCard(variant = McCardVariant.INSET) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PLANO DA SEMANA",
                style = McTextStyles.overline,
                color = colors.textTertiary,
            )
            McConfidenceBadge(confidence = plan.confidence)
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (plan.sessions.isEmpty()) {
            Text(
                text = plan.rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@McCard
        }
        plan.sessions.forEachIndexed { index, session ->
            if (index > 0) {
                McDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPlanDay(session.date),
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.width(48.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = session.durationMin?.let { "$it min" } ?: "—",
                        style = McTextStyles.caption,
                        color = colors.textSecondary,
                    )
                    session.skipReason?.let { reason ->
                        Text(
                            text = "Motivo: $reason",
                            style = McTextStyles.caption,
                            color = colors.warning,
                        )
                    }
                }
                val statusLabel = when (session.status) {
                    com.meucoach.core.domain.prescription.SessionStatus.DONE -> "Feito"
                    com.meucoach.core.domain.prescription.SessionStatus.SKIPPED -> "Pulado"
                    com.meucoach.core.domain.prescription.SessionStatus.PLANNED -> planTypeLabel(session.type)
                }
                val statusColor = when (session.status) {
                    com.meucoach.core.domain.prescription.SessionStatus.DONE -> colors.success
                    com.meucoach.core.domain.prescription.SessionStatus.SKIPPED -> colors.warning
                    com.meucoach.core.domain.prescription.SessionStatus.PLANNED -> colors.textSecondary
                }
                McPill(
                    text = statusLabel,
                    containerColor = statusColor.copy(alpha = 0.12f),
                    contentColor = statusColor,
                )
            }
            if (session.date == today &&
                session.status == com.meucoach.core.domain.prescription.SessionStatus.PLANNED
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    McTextButton(
                        text = "Pular",
                        onClick = {
                            skipTarget = session.date
                            skipReason = ""
                        },
                        trailingArrow = false,
                    )
                    McTextButton(
                        text = "Concluir",
                        onClick = { onComplete(session.date) },
                        trailingArrow = false,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = plan.rationale,
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }

    skipTarget?.let { date ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { skipTarget = null },
            title = { Text(text = "Pular treino") },
            text = {
                Column {
                    Text(text = "Conte rapidamente o motivo. Isso ajuda o coach a ajustar a semana.")
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = skipReason,
                        onValueChange = { skipReason = it },
                        label = { Text(text = "Motivo (opcional)") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                McTextButton(
                    text = "Pular",
                    onClick = {
                        onSkip(date, skipReason)
                        skipTarget = null
                    },
                    trailingArrow = false,
                )
            },
            dismissButton = {
                McTextButton(
                    text = "Cancelar",
                    onClick = { skipTarget = null },
                    trailingArrow = false,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplanationSheet(explanation: TodayExplanationUi, onDismiss: () -> Unit) {
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
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
                .testTag("explanation-content"),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "Por que este treino?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                McConfidenceBadge(confidence = explanation.confidence)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = explanation.rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(18.dp))
            ExplanationList(
                title = "Dados usados",
                items = explanation.usedData,
                emptyText = "Nenhum dado de prontidão disponível.",
            )
            ExplanationList(
                title = "Dados ausentes",
                items = explanation.missingData,
                emptyText = "Nenhum dado essencial ausente.",
            )
            ExplanationList(
                title = "Regras",
                items = explanation.rules.map { "${it.id} · ${it.title}" },
                emptyText = "Nenhuma regra catalogada.",
            )
            ExplanationList(
                title = "Evidências",
                items = explanation.evidence.map { item ->
                    if (item.source.isBlank()) {
                        "${item.id} · ${item.title}"
                    } else {
                        "${item.id} · ${item.title} — ${item.source}"
                    }
                },
                emptyText = "Nenhuma evidência catalogada.",
            )
            Text(
                text = "Motor determinístico, sem LLM. Dados ficam no aparelho.",
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun ExplanationList(title: String, items: List<String>, emptyText: String) {
    val colors = McTheme.colors
    Column {
        Text(
            text = title.uppercase(Locale.ROOT),
            style = McTextStyles.overline,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (items.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        } else {
            items.forEach { item ->
                Text(
                    text = "• $item",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun SwapSessionDialog(
    plan: WeeklyPlan?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSwap: (LocalDate, LocalDate) -> Unit,
) {
    val colors = McTheme.colors
    val week = plan
    val todaySession = week?.sessions?.firstOrNull {
        it.date == today && it.status == SessionStatus.PLANNED
    }
    val candidates = week?.sessions.orEmpty().filter {
        it.date.isAfter(today) && it.status == SessionStatus.PLANNED && it.type != WorkoutType.REST
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Trocar treino") },
        text = {
            Column {
                if (week == null || todaySession == null) {
                    Text(text = "Hoje não há uma sessão planejada para trocar.")
                } else if (candidates.isEmpty()) {
                    Text(text = "Não há outra sessão planejada nesta semana.")
                } else {
                    Text(
                        text = "Escolha o dia que receberá a sessão de hoje. " +
                            "A sessão do outro dia vem para hoje.",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    candidates.forEach { candidate ->
                        val result = remember(week, today, candidate.date) {
                            SessionSwapper.swap(week, today, candidate.date, today)
                        }
                        val enabled = result is SwapResult.Applied
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) { onSwap(today, candidate.date) }
                                .testTag("swap-candidate-${candidate.date}")
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatPlanDay(candidate.date),
                                    style = McTextStyles.caption,
                                    color = colors.textTertiary,
                                )
                                Text(
                                    text = candidate.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = candidate.durationMin?.let { "$it min" } ?: "—",
                                    style = McTextStyles.caption,
                                    color = colors.textSecondary,
                                )
                            }
                            Text(
                                text = if (enabled) "Trocar" else "Indisponível",
                                style = McTextStyles.caption,
                                color = if (enabled) colors.accentText else colors.textTertiary,
                            )
                        }
                        if (result is SwapResult.Rejected) {
                            Text(
                                text = result.reason,
                                style = McTextStyles.caption,
                                color = colors.warning,
                            )
                        }
                        McDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            McTextButton(
                text = "Fechar",
                onClick = onDismiss,
                trailingArrow = false,
            )
        },
    )
}

@Composable
private fun WorkoutSessionDialog(
    workout: WorkoutUi,
    hasPlannedSession: Boolean,
    onDismiss: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = McTheme.colors
    var running by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        while (running) {
            delay(1_000)
            elapsedSec += 1
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = workout.title) },
        text = {
            Column {
                Text(
                    text = if (hasPlannedSession) "Sessão guiada" else "Sem sessão planejada hoje",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = workout.rationale,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${workout.duration} min · ${workout.pace} /km · ${workout.heartRate} bpm",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                if (hasPlannedSession) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = formatElapsed(elapsedSec),
                        style = McTextStyles.metric,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Tempo decorrido",
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    McSecondaryButton(
                        text = if (running) "Pausar" else "Começar",
                        onClick = { running = !running },
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ajuste a disponibilidade no perfil ou faça o treino livre.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            if (hasPlannedSession) {
                McTextButton(
                    text = "Concluir treino",
                    onClick = onFinish,
                    trailingArrow = false,
                )
            } else {
                McTextButton(
                    text = "Fechar",
                    onClick = onDismiss,
                    trailingArrow = false,
                )
            }
        },
        dismissButton = if (hasPlannedSession) {
            {
                McTextButton(
                    text = "Fechar",
                    onClick = onDismiss,
                    trailingArrow = false,
                )
            }
        } else {
            null
        },
    )
}

private fun formatElapsed(seconds: Int): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remaining = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remaining)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, remaining)
    }
}

private fun formatPlanDay(date: LocalDate): String {
    val label = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, PLAN_LOCALE)
    return label.replaceFirstChar { it.uppercaseChar() }.trimEnd('.')
}

private fun planTypeLabel(type: com.meucoach.core.domain.prescription.WorkoutType): String = when (type) {
    com.meucoach.core.domain.prescription.WorkoutType.REST -> "Descanso"
    com.meucoach.core.domain.prescription.WorkoutType.EASY -> "Fácil"
    com.meucoach.core.domain.prescription.WorkoutType.LONG -> "Longão"
    com.meucoach.core.domain.prescription.WorkoutType.QUALITY -> "Qualidade"
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

private fun formatTwoDecimals(value: Double): String = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", value)

/** Same thresholds the trends screen explains; the pill never turns into a goal. */
@Composable
private fun acwrColor(acwr: Double): Color {
    val colors = McTheme.colors
    return when {
        acwr > 1.5 -> colors.danger
        acwr > 1.3 || acwr < 0.8 -> colors.warning
        else -> colors.success
    }
}

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

private val PLAN_LOCALE = Locale.forLanguageTag("pt-BR")
private const val NO_VALUE = "—"
