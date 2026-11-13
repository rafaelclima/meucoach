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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meucoach.app.ui.common.ProfileAvatar
import com.meucoach.app.ui.common.readinessLabel
import com.meucoach.app.ui.firstrun.FirstRunTipCard
import com.meucoach.app.ui.replan.ReplanChangesSheet
import com.meucoach.app.ui.replan.ReplanMessageCard
import com.meucoach.app.ui.replan.ReplanSuggestionCard
import com.meucoach.app.ui.replan.ReplanUiState
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
import com.meucoach.core.domain.load.LoadDataState
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.SessionSwapper
import com.meucoach.core.domain.prescription.SwapResult
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@Composable
fun TodayScreen(
    onDemoAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: TodayUiState,
    onCheckInClick: () -> Unit = {},
    onTrendsClick: () -> Unit = {},
    onActivityClick: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onHealthClick: () -> Unit = {},
    checklistState: FirstRunChecklistState = FirstRunChecklistState(isLoading = false, dismissed = true),
    onChecklistDismiss: () -> Unit = {},
    onCompleteWithActivity: (LocalDate, String, Int?) -> Unit = { _, _, _ -> },
    onCompleteManually: (LocalDate, Int, Int) -> Unit = { _, _, _ -> },
    onReplaceWithActivity: (LocalDate, String) -> Unit = { _, _ -> },
    onSkipSession: (LocalDate, String) -> Unit = { _, _ -> },
    onSwapSession: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    onCompleteRace: (String?) -> Unit = {},
    onCancelRace: () -> Unit = {},
    onNoShowRace: () -> Unit = {},
    onUndoAutoRaceCompletion: (String) -> Unit = {},
    onConsumeCompletionMessage: () -> Unit = {},
    replan: ReplanUiState = ReplanUiState(),
    onReplanRequest: () -> Unit = {},
    onReplanAccept: () -> Unit = {},
    onReplanDecline: () -> Unit = {},
    onReplanSuppress: () -> Unit = {},
    onReplanConsumeMessage: () -> Unit = {},
) {
    val today = state.today
    var swapOpen by remember { mutableStateOf(false) }
    var startOpen by remember { mutableStateOf(false) }
    var completionOpen by remember { mutableStateOf(false) }
    var correctionOpen by remember { mutableStateOf(false) }
    var changesOpen by remember { mutableStateOf(false) }
    var explanationSource by remember { mutableStateOf<ExplanationSource?>(null) }
    var briefingOpen by remember { mutableStateOf(false) }
    val planExplanation = remember(state.plan) { state.plan?.let(::buildPlanExplanation) }
    val openExplanation: (ExplanationSource) -> Unit = { source ->
        val available = when (source) {
            ExplanationSource.READINESS -> state.readinessExplanation
            ExplanationSource.WORKOUT -> state.explanation
            ExplanationSource.PLAN -> planExplanation
        }
        if (available != null) {
            explanationSource = source
        } else {
            onDemoAction("Configure seu perfil para ver a explicação.")
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
        item { Greeting(state = state, onProfileClick = onProfileClick) }
        if (checklistState.shouldShow) {
            item {
                FirstRunChecklistCard(
                    state = checklistState,
                    onCheckIn = onCheckInClick,
                    onImport = onImportClick,
                    onHealth = onHealthClick,
                    onDismiss = onChecklistDismiss,
                )
            }
        }
        if (checklistState.shouldShow && checklistState.hasCheckedIn) {
            item {
                FirstRunTipCard(
                    tipId = "first-win-checkin",
                    title = "Prontidão atualizada",
                    body = "O treino de hoje já considera este check-in.",
                )
            }
        }
        item {
            ReadinessCard(
                state = state,
                onWhyClick = { openExplanation(ExplanationSource.READINESS) },
                onHealthClick = onHealthClick,
            )
        }
        val todaySession = state.plan?.sessions?.firstOrNull { it.date == today }
        val hasPlannedSessionToday = todaySession?.status == SessionStatus.PLANNED
        val canCorrectManual = todaySession?.status == SessionStatus.DONE &&
            todaySession.completionEvidence == com.meucoach.core.model.CompletionEvidence.MANUAL &&
            state.activitySuggestions.isNotEmpty()
        val raceEvent = state.raceEvent
        if (raceEvent != null) {
            item {
                RaceDayCard(
                    event = raceEvent,
                    projection = state.raceProjection,
                    prediction = state.racePrediction,
                    activityAvailable = state.raceActivityAvailable,
                    suggestions = state.activitySuggestions,
                    completionMessage = state.completionMessage,
                    onComplete = onCompleteRace,
                    onCancel = onCancelRace,
                    onNoShow = onNoShowRace,
                    onUndoAutoCompletion = onUndoAutoRaceCompletion,
                    onConsumeMessage = onConsumeCompletionMessage,
                    onActivityClick = onActivityClick,
                )
            }
        } else {
            item {
                NextWorkoutCard(
                    workout = state.workout,
                    prescription = state.prescription,
                    briefing = state.briefing,
                    session = todaySession,
                    execution = state.todayExecution,
                    canComplete = hasPlannedSessionToday,
                    canCorrectManual = canCorrectManual,
                    completionMessage = state.completionMessage,
                    recovery = state.recovery,
                    onSwapClick = { swapOpen = true },
                    onStartClick = { startOpen = true },
                    onCompleteClick = { completionOpen = true },
                    onCorrectClick = { correctionOpen = true },
                    onConsumeMessage = onConsumeCompletionMessage,
                    onWhyClick = { openExplanation(ExplanationSource.WORKOUT) },
                    onBriefingClick = { briefingOpen = true },
                    onActivityClick = onActivityClick,
                )
            }
        }
        state.plan?.let { plan ->
            item {
                PlanWeekCard(
                    plan = plan,
                    today = today,
                    prescription = state.prescription,
                    replan = replan,
                    raceEvent = raceEvent,
                    eventDates = state.eventDates,
                    recovery = state.recovery,
                    onSkip = onSkipSession,
                    onExplain = { openExplanation(ExplanationSource.PLAN) },
                    onReplan = onReplanRequest,
                    onShowChanges = { changesOpen = true },
                )
            }
            if (replan.suggestion != null) {
                item {
                    ReplanSuggestionCard(
                        state = replan,
                        onAccept = onReplanAccept,
                        onDecline = onReplanDecline,
                        onSuppress = onReplanSuppress,
                    )
                }
            } else {
                replan.message?.let { message ->
                    item {
                        ReplanMessageCard(
                            message = message,
                            onDismiss = onReplanConsumeMessage,
                        )
                    }
                }
            }
        }
        item {
            WeeklyLoadCard(state = state, onTrendsClick = onTrendsClick)
        }
        item { CheckInCard(done = state.hasCheckedIn, onCheckInClick = onCheckInClick) }
        state.recentActivity?.let { activity ->
            item {
                RecentActivityCard(
                    activity = activity,
                    onClick = { onActivityClick(activity.id) },
                )
            }
        }
    }

    if (state.raceEvent == null && swapOpen) {
        SwapSessionDialog(
            plan = state.plan,
            today = today,
            raceDates = state.eventDates,
            onDismiss = { swapOpen = false },
            onSwap = { first, second ->
                onSwapSession(first, second)
                swapOpen = false
            },
        )
    }
    if (state.raceEvent == null && startOpen) {
        val hasPlannedSession = state.plan?.sessions?.any {
            it.date == today && it.status == SessionStatus.PLANNED
        } == true
        WorkoutSessionDialog(
            workout = state.workout,
            hasPlannedSession = hasPlannedSession,
            onDismiss = { startOpen = false },
            onFinish = {
                startOpen = false
                completionOpen = true
            },
        )
    }
    if (state.raceEvent == null && completionOpen) {
        CompleteSessionDialog(
            date = today,
            suggestions = state.activitySuggestions,
            prescribedDurationMin = state.prescription?.prescribedDurationMin,
            onDismiss = { completionOpen = false },
            onLinkActivity = { activityId, rpe ->
                onCompleteWithActivity(today, activityId, rpe)
                completionOpen = false
            },
            onManual = { durationMin, rpe ->
                onCompleteManually(today, durationMin, rpe)
                completionOpen = false
            },
        )
    }
    if (state.raceEvent == null && correctionOpen) {
        ReplaceSessionDialog(
            suggestions = state.activitySuggestions,
            onDismiss = { correctionOpen = false },
            onReplace = { activityId ->
                onReplaceWithActivity(today, activityId)
                correctionOpen = false
            },
        )
    }
    explanationSource?.let { source ->
        val explanation = when (source) {
            ExplanationSource.READINESS -> state.readinessExplanation
            ExplanationSource.WORKOUT -> state.explanation
            ExplanationSource.PLAN -> planExplanation
        }
        explanation?.let {
            ExplanationSheet(
                title = when (source) {
                    ExplanationSource.READINESS -> "Por que esta prontidão?"
                    ExplanationSource.WORKOUT -> "Por que este treino?"
                    ExplanationSource.PLAN -> "Por que este plano?"
                },
                explanation = it,
                onDismiss = { explanationSource = null },
            )
        }
    }
    if (changesOpen) {
        replan.lastChange?.let { change ->
            ReplanChangesSheet(change = change, onDismiss = { changesOpen = false })
        }
    }
    if (state.raceEvent == null && briefingOpen) {
        SessionBriefingSheet(
            state = state.briefing
                ?.let { SessionBriefingSheetState.Ready(it) }
                ?: SessionBriefingSheetState.Empty,
            onDismiss = { briefingOpen = false },
        )
    }
}

private enum class ExplanationSource { READINESS, WORKOUT, PLAN }

private data class PlanDayEntry(
    val date: LocalDate,
    val session: com.meucoach.core.domain.prescription.PlannedSession?,
    val event: com.meucoach.core.model.RaceEvent?,
)

@Composable
private fun Greeting(state: TodayUiState, onProfileClick: () -> Unit) {
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
                .clickable(
                    onClick = onProfileClick,
                    role = Role.Button,
                )
                .semantics { contentDescription = "Abrir perfil" }
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(
                name = state.athleteName,
                avatarPath = state.avatarPath,
                size = 44.dp,
            )
        }
    }
}

@Composable
private fun ReadinessCard(state: TodayUiState, onWhyClick: () -> Unit, onHealthClick: () -> Unit) {
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
        McTextButton(
            text = "Ver recuperação",
            onClick = onHealthClick,
            contentColor = colors.textSecondary,
            modifier = Modifier
                .align(Alignment.Start)
                .testTag("readiness-health-link"),
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
            Text(
                text = signal.label,
                style = McTextStyles.caption,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = signal.value,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
                signal.hint?.takeIf { it.isNotBlank() }?.let { hint ->
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "· $hint",
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

@Composable
private fun NextWorkoutCard(
    workout: WorkoutUi,
    prescription: com.meucoach.core.domain.prescription.DailyPrescription?,
    briefing: com.meucoach.core.domain.briefing.SessionBriefing?,
    session: com.meucoach.core.domain.prescription.PlannedSession?,
    execution: TodayExecutionUi?,
    canComplete: Boolean,
    canCorrectManual: Boolean,
    completionMessage: String?,
    recovery: RecoveryUi? = null,
    onSwapClick: () -> Unit,
    onStartClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onCorrectClick: () -> Unit,
    onConsumeMessage: () -> Unit,
    onWhyClick: () -> Unit,
    onBriefingClick: () -> Unit,
    onActivityClick: (String) -> Unit,
) {
    val colors = McTheme.colors
    val done = session?.status == SessionStatus.DONE
    val closed = session?.status == SessionStatus.CLOSED
    val adjusted = prescription?.differsFromPlan == true && !done && !closed
    McCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "TREINO DE HOJE", style = McTextStyles.overline, color = colors.textTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    done -> McPill(
                        text = "Concluído",
                        containerColor = colors.success.copy(alpha = 0.12f),
                        contentColor = colors.success,
                        modifier = Modifier.testTag("today-done-badge"),
                    )

                    closed -> McPill(
                        text = session?.let(::sessionStatusLabel) ?: "Encerrado",
                        containerColor = colors.warning.copy(alpha = 0.12f),
                        contentColor = colors.warning,
                        modifier = Modifier.testTag("today-closed-badge"),
                    )

                    else -> {
                        if (adjusted) {
                            McPill(
                                text = "Ajustado hoje",
                                containerColor = colors.warning.copy(alpha = 0.12f),
                                contentColor = colors.warning,
                                modifier = Modifier.testTag("prescription-adjusted"),
                            )
                        }
                        McPill(
                            text = workout.zone,
                            containerColor = colors.zone2.copy(alpha = 0.10f),
                            contentColor = colors.zone2,
                        )
                    }
                }
            }
        }
        recovery?.let { info ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Recuperação após a prova · dia ${info.dayOfWindow} de ${info.totalDays} " +
                    "· até ${RECOVERY_DATE.format(info.until)}",
                style = McTextStyles.caption,
                color = colors.textSecondary,
                modifier = Modifier.testTag("today-recovery"),
            )
        }
        when {
            done -> {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = todayDoneNote(session),
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("today-done-note"),
                )
            }

            closed -> {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Este dia foi encerrado. O card fica como registro do que estava planejado.",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("today-closed-note"),
                )
            }
        }
        if (adjusted) {
            prescription?.plannedTitle?.let { planned ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Planejado: $planned · ${prescription.plannedDurationMin?.let { "$it min" } ?: "—"}",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.testTag("planned-intent"),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = workout.title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (done) {
            Text(text = "FEITO", style = McTextStyles.overline, color = colors.success)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = execution?.let(::executionSummary) ?: "Sessão concluída.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.testTag("today-execution-summary"),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "PLANEJADO", style = McTextStyles.overline, color = colors.textTertiary)
            Spacer(modifier = Modifier.height(8.dp))
        }
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
            if (workout.rpe != null) {
                WorkoutMetric(
                    label = "ESFORÇO",
                    value = workout.rpe.toString(),
                    unit = "/10",
                    modifier = Modifier.weight(1f),
                )
                WorkoutMetric(
                    label = "ZONA",
                    value = workout.zone.substringBefore(" ·"),
                    unit = "esforço",
                    modifier = Modifier.weight(1f),
                )
            } else {
                WorkoutMetric(
                    label = "RITMO",
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
        if (!done && !closed && briefing != null) {
            Spacer(modifier = Modifier.height(8.dp))
            McSecondaryButton(
                text = "Como correr hoje",
                onClick = onBriefingClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today-briefing-cta"),
            )
        }
        if (!done && !closed) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                McSecondaryButton(
                    text = "Trocar",
                    onClick = onSwapClick,
                    enabled = workout.canTrain,
                    modifier = Modifier.weight(1f),
                )
                McPrimaryButton(
                    text = "Iniciar",
                    onClick = onStartClick,
                    enabled = workout.canTrain && canComplete,
                    modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Outlined.PlayArrow,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                McTextButton(
                    text = "Concluir treino",
                    onClick = onCompleteClick,
                    trailingArrow = false,
                    enabled = workout.canTrain && canComplete,
                    modifier = Modifier.testTag("complete-today"),
                )
            }
        }
        if (done) {
            execution?.activityId?.let { activityId ->
                Spacer(modifier = Modifier.height(4.dp))
                McTextButton(
                    text = "Ver corrida",
                    onClick = { onActivityClick(activityId) },
                    trailingArrow = false,
                    modifier = Modifier.testTag("today-open-activity"),
                )
            }
            if (canCorrectManual) {
                Spacer(modifier = Modifier.height(4.dp))
                McTextButton(
                    text = "Corrigir registro",
                    onClick = onCorrectClick,
                    trailingArrow = false,
                    modifier = Modifier.testTag("correct-manual"),
                )
            }
        }
        completionMessage?.let { message ->
            Text(
                text = message,
                style = McTextStyles.caption,
                color = colors.textSecondary,
                modifier = Modifier.testTag("completion-message"),
            )
            McTextButton(
                text = "Entendi",
                onClick = onConsumeMessage,
                trailingArrow = false,
                enabled = true,
            )
        }
    }
}

/** Evidence is part of the note; a completed session never pretends to have run data. */
private fun todayDoneNote(session: com.meucoach.core.domain.prescription.PlannedSession?): String =
    when (session?.completionEvidence) {
        com.meucoach.core.model.CompletionEvidence.ACTIVITY ->
            "Você já concluiu este treino com uma corrida vinculada."

        com.meucoach.core.model.CompletionEvidence.GUIDED ->
            "Você já concluiu este treino com uma sessão guiada registrada."

        com.meucoach.core.model.CompletionEvidence.MANUAL ->
            "Você já concluiu este treino sem vincular uma corrida. Conta adesão, mas não entra " +
                "em volume, carga ou zonas."

        com.meucoach.core.model.CompletionEvidence.LEGACY_UNVERIFIED, null ->
            "Você já concluiu este treino, mas a evidência não foi verificada."
    }

private fun executionSummary(execution: TodayExecutionUi): String = listOfNotNull(
    execution.duration,
    execution.distance,
    execution.pace,
    execution.rpe?.let { "RPE $it" },
).joinToString(" · ").ifBlank { "Sessão concluída." }

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
    val hasLoad = (state.ctl ?: 0) > 0
    val hasDistance = (state.weeklyLoadKm ?: 0.0) > 0.0
    McCard {
        Text(text = "CARGA DA SEMANA", style = McTextStyles.overline, color = colors.textTertiary)
        Spacer(modifier = Modifier.height(4.dp))
        if (!hasLoad && !hasDistance) {
            val stale = state.loadDataState == LoadDataState.STALE
            Text(
                text = when (state.loadDataState) {
                    LoadDataState.STALE -> "Dados de carga desatualizados"
                    LoadDataState.INSUFFICIENT -> "Dados de carga insuficientes"
                    else -> "Nenhuma corrida nesta semana"
                },
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (stale) {
                    "Importe atividades recentes para atualizar a carga. " +
                        "Sem isso, o app não libera treino forte."
                } else {
                    "Importe uma corrida com FC ou ritmo para ver carga e recuperação. " +
                        "São necessários pelo menos 8 dias com carga em 28."
                },
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
                        text = "Aumento ${formatTwoDecimals(acwr)}× vs. 4 sem",
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
                    label = "Base",
                    value = state.ctl?.toString() ?: NO_VALUE,
                    hint = "média/dia · 42d",
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
                McStat(
                    label = "Fadiga",
                    value = state.atl?.toString() ?: NO_VALUE,
                    hint = "média/dia · 7d",
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
                McStat(
                    label = "Balanço",
                    value = state.tsb?.toString() ?: NO_VALUE,
                    hint = "base − fadiga",
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
    prescription: com.meucoach.core.domain.prescription.DailyPrescription?,
    replan: ReplanUiState,
    raceEvent: com.meucoach.core.model.RaceEvent?,
    eventDates: Set<LocalDate>,
    recovery: RecoveryUi? = null,
    onSkip: (LocalDate, String) -> Unit,
    onExplain: () -> Unit,
    onReplan: () -> Unit,
    onShowChanges: () -> Unit,
) {
    val colors = McTheme.colors
    var skipTarget by remember { mutableStateOf<LocalDate?>(null) }
    var skipReason by remember { mutableStateOf("") }
    var rationaleExpanded by remember { mutableStateOf(false) }
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (recovery != null) {
                    McPill(
                        text = "Recuperação",
                        containerColor = colors.warning.copy(alpha = 0.12f),
                        contentColor = colors.warning,
                        modifier = Modifier.testTag("plan-recovery"),
                    )
                }
                McPill(
                    text = "v${replan.planVersion}",
                    containerColor = colors.surfaceInset,
                    contentColor = colors.textSecondary,
                    modifier = Modifier.testTag("plan-version"),
                )
                if (prescription?.differsFromPlan == true) {
                    McPill(
                        text = "Ajustado hoje",
                        containerColor = colors.warning.copy(alpha = 0.12f),
                        contentColor = colors.warning,
                        modifier = Modifier.testTag("plan-adjusted"),
                    )
                }
            }
        }
        if (plan.confidence == DataConfidence.LOW) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Dados de recuperação parciais: a semana segue conservadora.",
                style = McTextStyles.caption,
                color = colors.textTertiary,
                modifier = Modifier.testTag("plan-low-confidence-note"),
            )
        }
        if ("R-PLAN-11" in plan.ruleIds) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Sua meta é uma prova sem data: a semana não tem redução antes da prova.",
                style = McTextStyles.caption,
                color = colors.textTertiary,
                modifier = Modifier.testTag("plan-no-race-date-note"),
            )
        }
        if (plan.qualityCount >= 2) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Dois treinos fortes é o máximo desta semana.",
                style = McTextStyles.caption,
                color = colors.textTertiary,
                modifier = Modifier.testTag("plan-quality-cap-note"),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        val weekEnd = plan.weekStart.plusDays(6)
        val weekEvents = eventDates.filter { !it.isBefore(plan.weekStart) && !it.isAfter(weekEnd) }
        val visibleSessions = plan.sessions.filter { it.date !in eventDates }
        if (visibleSessions.isEmpty() && weekEvents.isEmpty()) {
            Text(
                text = plan.rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            McTextButton(
                text = "Por que este plano?",
                onClick = onExplain,
                trailingArrow = false,
                modifier = Modifier.testTag("plan-explanation-link"),
            )
            return@McCard
        }
        val raceByDate = raceEvent
            ?.takeIf { it.targetDate in plan.weekStart..weekEnd }
            ?.let { mapOf(it.targetDate to it) }
            .orEmpty()
        val entries = buildList {
            visibleSessions.forEach { session -> add(PlanDayEntry(session.date, session, null)) }
            weekEvents.forEach { date -> add(PlanDayEntry(date, null, raceByDate[date])) }
        }.sortedBy { it.date }
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                McDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            val session = entry.session
            if (session == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plan-race-${entry.date}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatPlanDay(entry.date),
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                        modifier = Modifier.width(48.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                            entry.event?.let {
                                "Prova · ${com.meucoach.app.ui.common.raceDistanceLabel(it.distance)}"
                            }
                                ?: "Prova",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Sem treino neste dia",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                        )
                    }
                    McPill(
                        text = entry.event?.let {
                            com.meucoach.app.ui.common.raceStatusLabel(it.status)
                        } ?: "Evento",
                        containerColor = colors.accentSoft,
                        contentColor = colors.accentText,
                    )
                }
                return@forEachIndexed
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    session.skipReason?.let { reason ->
                        Text(
                            text = "Motivo: $reason",
                            style = McTextStyles.caption,
                            color = colors.warning,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = session.durationMin?.let { "$it min" } ?: "—",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
                val statusLabel = sessionStatusLabel(session)
                if (statusLabel != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val statusColor = if (session.status == SessionStatus.DONE) colors.success else colors.warning
                    McPill(
                        text = statusLabel,
                        containerColor = statusColor.copy(alpha = 0.12f),
                        contentColor = statusColor,
                    )
                }
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
                        enabled = prescription?.isRest != true,
                    )
                }
            }
        }
        val manualToday = visibleSessions.any { session ->
            session.date == today &&
                session.status == SessionStatus.DONE &&
                session.completionEvidence == com.meucoach.core.model.CompletionEvidence.MANUAL
        }
        if (manualToday) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "“Feito · sem registro” significa que o treino foi marcado como feito sem " +
                    "vincular uma corrida. Ele conta adesão, mas não entra em volume, carga ou zonas.",
                style = McTextStyles.caption,
                color = colors.textTertiary,
                modifier = Modifier.testTag("plan-manual-note"),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = plan.rationale,
            style = McTextStyles.caption,
            color = colors.textTertiary,
            maxLines = if (rationaleExpanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (plan.rationale.length > PLAN_RATIONALE_PREVIEW) {
            McTextButton(
                text = if (rationaleExpanded) "Ver menos" else "Ver mais",
                onClick = { rationaleExpanded = !rationaleExpanded },
                trailingArrow = false,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        McTextButton(
            text = "Por que este plano?",
            onClick = onExplain,
            trailingArrow = false,
            modifier = Modifier.testTag("plan-explanation-link"),
        )
        if (replan.lastChange != null) {
            McTextButton(
                text = "Ver o que mudou",
                onClick = onShowChanges,
                trailingArrow = false,
                modifier = Modifier.testTag("plan-changes"),
            )
        }
        if (plan.sessions.any { it.date.isAfter(today) }) {
            McTextButton(
                text = "Replanejar minha semana",
                onClick = onReplan,
                trailingArrow = false,
                enabled = !replan.isLoading,
                modifier = Modifier.testTag("plan-replan"),
            )
        }
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
private fun ExplanationSheet(title: String, explanation: TodayExplanationUi, onDismiss: () -> Unit) {
    val colors = McTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showTechnicalDetails by remember { mutableStateOf(false) }
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
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("explanation-title"),
                )
                McConfidenceBadge(confidence = explanation.confidence)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = explanation.rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            explanation.planComparison?.let { comparison ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = comparison,
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.testTag("explanation-plan-comparison"),
                )
            }
            explanation.confidenceNote?.let { note ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note,
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            ExplanationList(
                title = "O que usei",
                items = explanation.usedData,
                emptyText = "Nenhum dado de prontidão disponível.",
            )
            ExplanationList(
                title = "O que faltou",
                items = explanation.missingData,
                emptyText = "Nenhum dado essencial ausente.",
            )
            ExplanationList(
                title = "Como decidi",
                items = explanation.rules.map { it.title },
                emptyText = "Nenhuma regra específica para hoje.",
            )
            McTextButton(
                text = if (showTechnicalDetails) {
                    "Ocultar fontes e detalhes técnicos"
                } else {
                    "Ver fontes e detalhes técnicos"
                },
                onClick = { showTechnicalDetails = !showTechnicalDetails },
                trailingArrow = false,
                modifier = Modifier.testTag("explanation-technical-toggle"),
            )
            if (showTechnicalDetails) {
                Spacer(modifier = Modifier.height(8.dp))
                ExplanationList(
                    title = "Fontes",
                    items = explanation.evidence.map { item ->
                        if (item.source.isBlank()) item.title else "${item.title} — ${item.source}"
                    },
                    emptyText = "Nenhuma fonte catalogada.",
                )
                val technicalIds = buildList {
                    if (explanation.rules.isNotEmpty()) {
                        add("regras: ${explanation.rules.joinToString { it.id }}")
                    }
                    if (explanation.evidence.isNotEmpty()) {
                        add("evidências: ${explanation.evidence.joinToString { it.id }}")
                    }
                }.joinToString(" · ")
                if (technicalIds.isNotEmpty()) {
                    Text(
                        text = "Para auditoria — $technicalIds.",
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            Text(
                text = "O app decide por regras fixas, não por IA. Seus dados ficam neste aparelho.",
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
    raceDates: Set<LocalDate> = emptySet(),
    onDismiss: () -> Unit,
    onSwap: (LocalDate, LocalDate) -> Unit,
) {
    val colors = McTheme.colors
    val week = plan
    val todaySession = week?.sessions?.firstOrNull {
        it.date == today && it.status == SessionStatus.PLANNED
    }
    val candidates = week?.sessions.orEmpty().filter {
        it.date.isAfter(today) &&
            it.status == SessionStatus.PLANNED &&
            it.type != WorkoutType.REST &&
            it.date !in raceDates
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
                        val result = remember(week, today, candidate.date, raceDates) {
                            SessionSwapper.swap(week, today, candidate.date, today, raceDates)
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
private fun ReplaceSessionDialog(
    suggestions: List<ActivitySuggestionUi>,
    onDismiss: () -> Unit,
    onReplace: (String) -> Unit,
) {
    val colors = McTheme.colors
    var selectedActivityId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("replace-dialog"),
        title = { Text(text = "Corrigir registro") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Escolha a corrida que deve substituir o registro manual. A duração e o " +
                        "RPE informados à mão serão descartados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                suggestions.forEach { suggestion ->
                    val selected = selectedActivityId == suggestion.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("replace-suggestion-${suggestion.id}")
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (selected) 1.dp else 0.dp,
                                color = if (selected) colors.accent else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { selectedActivityId = suggestion.id }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = suggestion.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "${suggestion.subtitle} · ${suggestion.duration} · " +
                                    suggestion.distance,
                                style = McTextStyles.caption,
                                color = colors.textSecondary,
                            )
                        }
                        Text(
                            text = if (selected) "Selecionada" else "Selecionar",
                            style = McTextStyles.caption,
                            color = if (selected) colors.accentText else colors.textTertiary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                McTextButton(
                    text = "Usar esta corrida",
                    onClick = { selectedActivityId?.let(onReplace) },
                    trailingArrow = false,
                    enabled = selectedActivityId != null,
                    modifier = Modifier.testTag("replace-activity"),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            McTextButton(
                text = "Cancelar",
                onClick = onDismiss,
                trailingArrow = false,
            )
        },
    )
}

@Composable
private fun CompleteSessionDialog(
    date: LocalDate,
    suggestions: List<ActivitySuggestionUi>,
    prescribedDurationMin: Int?,
    onDismiss: () -> Unit,
    onLinkActivity: (String, Int?) -> Unit,
    onManual: (Int, Int) -> Unit,
) {
    val colors = McTheme.colors
    var selectedActivityId by remember { mutableStateOf<String?>(null) }
    var durationInput by remember {
        mutableStateOf(prescribedDurationMin?.toString().orEmpty())
    }
    var rpe by remember { mutableStateOf<Int?>(null) }
    var linkedRpe by remember { mutableStateOf<Int?>(null) }
    val duration = durationInput.toIntOrNull()
    val manualValid = duration != null && duration > 0 && rpe != null

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("completion-dialog"),
        title = { Text(text = "Concluir treino") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Concluir exige evidência. Vincule a corrida importada ou registre " +
                        "sem registro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "CORRIDA IMPORTADA · ${formatPlanDay(date)}",
                    style = McTextStyles.overline,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (suggestions.isEmpty()) {
                    Text(
                        text = "Nenhuma corrida compatível com este treino foi encontrada hoje. " +
                            "Importe a corrida ou use o registro manual abaixo.",
                        style = McTextStyles.caption,
                        color = colors.textSecondary,
                    )
                } else {
                    suggestions.forEach { suggestion ->
                        val selected = selectedActivityId == suggestion.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("suggestion-${suggestion.id}")
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (selected) 1.dp else 0.dp,
                                    color = if (selected) colors.accent else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { selectedActivityId = suggestion.id }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = suggestion.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "${suggestion.subtitle} · ${suggestion.duration} · " +
                                        suggestion.distance,
                                    style = McTextStyles.caption,
                                    color = colors.textSecondary,
                                )
                            }
                            Text(
                                text = if (selected) "Selecionada" else "Selecionar",
                                style = McTextStyles.caption,
                                color = if (selected) colors.accentText else colors.textTertiary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (selectedActivityId != null) {
                        Text(
                            text = "Como foi o esforço? (opcional)",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = "Se a corrida não tiver FC ou ritmo, seu relato vira a carga do dia. " +
                                "Se não souber, deixe em branco.",
                            style = McTextStyles.caption,
                            color = colors.textTertiary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        RpeChips(
                            selected = linkedRpe,
                            onSelect = { value -> linkedRpe = if (value == linkedRpe) null else value },
                            tagPrefix = "linked-rpe",
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    McTextButton(
                        text = "Vincular corrida",
                        onClick = { selectedActivityId?.let { onLinkActivity(it, linkedRpe) } },
                        trailingArrow = false,
                        enabled = selectedActivityId != null,
                        modifier = Modifier.testTag("link-activity"),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                McDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "FIZ SEM REGISTRO",
                    style = McTextStyles.overline,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = durationInput,
                    onValueChange = { value -> durationInput = value.filter(Char::isDigit) },
                    label = { Text(text = "Duração (min)") },
                    singleLine = true,
                    modifier = Modifier.testTag("manual-duration"),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Esforço percebido (RPE)",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                RpeChips(
                    selected = rpe,
                    onSelect = { value -> rpe = if (value == rpe) null else value },
                    tagPrefix = "rpe",
                )
                Text(
                    text = "Sem registro não entra em volume, carga ou zonas — só conta a adesão.",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                McTextButton(
                    text = "Salvar sem registro",
                    onClick = { if (manualValid) onManual(requireNotNull(duration), requireNotNull(rpe)) },
                    trailingArrow = false,
                    enabled = manualValid,
                    modifier = Modifier.testTag("manual-save"),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            McTextButton(
                text = "Cancelar",
                onClick = onDismiss,
                trailingArrow = false,
            )
        },
    )
}

@Composable
private fun RpeChips(selected: Int?, onSelect: (Int) -> Unit, tagPrefix: String) {
    val colors = McTheme.colors
    Column {
        (1..10).chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { value ->
                    val isSelected = selected == value
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("$tagPrefix-$value")
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accent else colors.surfaceInset)
                            .clickable { onSelect(value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) colors.onAccent else colors.textSecondary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
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
                    text = if (hasPlannedSession) "Prévia da sessão" else "Sem sessão planejada hoje",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = workout.rationale,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                if (hasPlannedSession) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "O cronômetro é uma prévia e não fica salvo. " +
                            "Para concluir, vincule a corrida ou registre sem registro.",
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
                }
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
                    text = "Encerrar treino",
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

private val RECOVERY_DATE: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("dd/MM")

/** Evidence is part of the label; a click from the past is never shown as verified. */
private fun sessionStatusLabel(session: com.meucoach.core.domain.prescription.PlannedSession): String? =
    when (session.status) {
        SessionStatus.DONE -> when (session.completionEvidence) {
            com.meucoach.core.model.CompletionEvidence.ACTIVITY,
            com.meucoach.core.model.CompletionEvidence.GUIDED,
            -> "Feito · verificado"

            com.meucoach.core.model.CompletionEvidence.MANUAL -> "Feito · sem registro"

            com.meucoach.core.model.CompletionEvidence.LEGACY_UNVERIFIED -> "Feito · não verificado"

            null -> "Feito · não verificado"
        }

        SessionStatus.CLOSED -> when (session.closureReason) {
            com.meucoach.core.domain.prescription.SessionClosureReason.REST_PRESCRIBED -> "Descanso prescrito"
            com.meucoach.core.domain.prescription.SessionClosureReason.ILLNESS_OR_INJURY -> "Doença/lesão"
            com.meucoach.core.domain.prescription.SessionClosureReason.USER_SKIPPED -> "Pulado"
            com.meucoach.core.domain.prescription.SessionClosureReason.MISSED -> "Não feito"
            com.meucoach.core.domain.prescription.SessionClosureReason.PLAN_REPLACED -> "Substituído"
            null -> "Encerrado"
        }

        SessionStatus.PLANNED -> null
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

/** Roughly two caption lines at 360 dp before the plan rationale needs a "Ver mais". */
private const val PLAN_RATIONALE_PREVIEW = 90
private const val NO_VALUE = "—"
