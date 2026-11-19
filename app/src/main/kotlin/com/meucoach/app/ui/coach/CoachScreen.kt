package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.app.ui.replan.LessTimeChange
import com.meucoach.app.ui.replan.ReplanChangesSheet
import com.meucoach.app.ui.replan.ReplanMessageCard
import com.meucoach.app.ui.replan.ReplanSuggestionSheet
import com.meucoach.app.ui.replan.ReplanUiState
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.review.WeeklyReview
import java.time.LocalDate

@Composable
fun CoachRoute(
    viewModel: CoachViewModel = hiltViewModel(),
    onCheckInClick: () -> Unit = {},
    onTodayClick: () -> Unit = {},
    onTrendsClick: () -> Unit = {},
) {
    val consented by viewModel.consented.collectAsStateWithLifecycle()
    val dismissed by viewModel.dismissed.collectAsStateWithLifecycle()
    val explanation by viewModel.explanation.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    val reviewState by viewModel.reviewState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val next by viewModel.next.collectAsStateWithLifecycle()
    val guards by viewModel.guards.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val replan by viewModel.replan.collectAsStateWithLifecycle()
    CoachScreen(
        consented = consented,
        showConsent = !consented && !dismissed,
        explanation = explanation,
        review = review,
        reviewState = reviewState,
        history = history,
        next = next,
        guards = guards,
        progress = progress,
        replan = replan,
        onGrant = { viewModel.grant() },
        onDismiss = { viewModel.dismiss() },
        onRevoke = { viewModel.revoke() },
        onRetry = { viewModel.retry() },
        onNextRetry = { viewModel.retry() },
        onNextCheckIn = onCheckInClick,
        onNextToday = onTodayClick,
        onGuardsRetry = { viewModel.retry() },
        onProgressRetry = { viewModel.retry() },
        onProgressTrends = onTrendsClick,
        onClearHistory = { viewModel.clearHistory() },
        onRetryHistory = { viewModel.retryHistory() },
        onDeleteHistoryMessage = { viewModel.deleteHistoryMessage(it) },
        onDeleteHistorySummary = { viewModel.deleteHistorySummary(it) },
        onReplanRequest = { viewModel.requestReplan() },
        onReplanRequestMissed = { viewModel.requestMissedSession() },
        onReplanRequestLessTime = { viewModel.requestLessTime() },
        onReplanMissedSelected = { viewModel.replanMissed(it) },
        onReplanLessTimeSelected = { changes -> viewModel.replanLessTime(changes) },
        onReplanDismissIntent = { viewModel.dismissReplanIntent() },
        onReplanAccept = { viewModel.acceptReplan() },
        onReplanDecline = { viewModel.declineReplan() },
        onReplanSuppress = { viewModel.suppressReplan() },
        onReplanUndo = { viewModel.undoReplan() },
        onReplanConsumeMessage = { viewModel.consumeReplanMessage() },
        onReplanDismiss = { viewModel.dismissReplanSuggestion() },
    )
}

@Composable
fun CoachScreen(
    consented: Boolean,
    showConsent: Boolean,
    explanation: CoachExplanationState,
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
    onRevoke: () -> Unit,
    onRetry: () -> Unit,
    review: WeeklyReview? = null,
    reviewState: CoachReviewState = CoachReviewState.Unavailable,
    history: CoachHistoryState = CoachHistoryState.Hidden,
    next: CoachNextUiState = CoachNextUiState.Hidden,
    replan: ReplanUiState = ReplanUiState(),
    onReplanRequest: () -> Unit = {},
    onReplanRequestMissed: () -> Unit = {},
    onReplanRequestLessTime: () -> Unit = {},
    onReplanMissedSelected: (LocalDate) -> Unit = {},
    onReplanLessTimeSelected: (List<LessTimeChange>) -> Unit = {},
    onReplanDismissIntent: () -> Unit = {},
    onReplanAccept: () -> Unit = {},
    onReplanDecline: () -> Unit = {},
    onReplanSuppress: () -> Unit = {},
    onReplanUndo: () -> Unit = {},
    onReplanConsumeMessage: () -> Unit = {},
    onReplanDismiss: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    onRetryHistory: () -> Unit = {},
    onNextRetry: () -> Unit = {},
    onNextCheckIn: () -> Unit = {},
    onNextToday: () -> Unit = {},
    guards: CoachGuardsUiState = CoachGuardsUiState.Hidden,
    onGuardsRetry: () -> Unit = {},
    progress: CoachProgressUiState = CoachProgressUiState.Hidden,
    onProgressRetry: () -> Unit = {},
    onProgressTrends: () -> Unit = {},
    onDeleteHistoryMessage: (String) -> Unit = {},
    onDeleteHistorySummary: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var replanSheetOpen by rememberSaveable { mutableStateOf(false) }
    var replanChangesOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(replan.suggestion != null) {
        if (replan.suggestion != null) replanSheetOpen = true
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Coach",
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            color = McTheme.colors.textPrimary,
        )
        Text(
            text = "Sua semana, a revisão anterior e os ajustes — com o motor no comando.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = McTheme.colors.textSecondary,
        )
        if (showConsent) {
            McCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Ativar o Coach?",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = McTheme.colors.textPrimary,
                    )
                    Text(
                        text = "Para explicar sua semana, o app envia um resumo dos treinos " +
                            "(sem nome e sem mapa) para o serviço de IA. " +
                            "Você pode desligar quando quiser em Dados.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = McTheme.colors.textSecondary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        McPrimaryButton(text = "Ativar", onClick = onGrant, modifier = Modifier.weight(1f))
                        McSecondaryButton(text = "Depois", onClick = onDismiss, modifier = Modifier.weight(1f))
                    }
                }
            }
        } else if (consented) {
            CoachNextCard(
                state = next,
                onRetry = onNextRetry,
                onCheckIn = onNextCheckIn,
                onToday = onNextToday,
            )
            CoachGuardsCard(state = guards, onRetry = onGuardsRetry)
            CoachReplanCard(
                state = replan,
                onRequest = onReplanRequest,
                onMissed = onReplanRequestMissed,
                onLessTime = onReplanRequestLessTime,
            )
            if (replan.suggestion == null) {
                replan.message?.let { message ->
                    ReplanMessageCard(
                        message = message,
                        onDismiss = onReplanConsumeMessage,
                        canUndo = replan.canUndo,
                        isUndoing = replan.isUndoing,
                        onUndo = onReplanUndo,
                        onShowChanges = replan.lastChange?.let { { replanChangesOpen = true } },
                    )
                }
            }
            when (explanation) {
                CoachExplanationState.Unavailable -> {
                    McCard(modifier = Modifier.testTag("coach-empty-path")) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Monte seu plano para começar",
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                color = McTheme.colors.textPrimary,
                            )
                            Text(
                                text = "Complete perfil e disponibilidade, importe um treino ou faça " +
                                    "a calibração. O próximo treino aparece aqui.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = McTheme.colors.textSecondary,
                            )
                        }
                    }
                }

                CoachExplanationState.Loading -> {
                    McCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(color = McTheme.colors.accent)
                            Text(
                                text = "Consultando o Coach…",
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                color = McTheme.colors.textPrimary,
                            )
                            Text(
                                text = "Analisando sua semana e seus treinos.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = McTheme.colors.textTertiary,
                            )
                        }
                    }
                }

                is CoachExplanationState.Ready -> {
                    CoachExplanationCard(explanation)
                }

                is CoachExplanationState.Fallback -> {
                    McCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Resumo da semana",
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                color = McTheme.colors.textPrimary,
                            )
                            Text(
                                text = explanation.planRationale,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = McTheme.colors.textSecondary,
                            )
                            Text(
                                text = explanation.reason,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = McTheme.colors.textTertiary,
                                modifier = Modifier.testTag("coach-fallback-reason"),
                            )
                            Text(
                                text = "O Coach não conseguiu explicar desta vez. Tente de novo em instantes.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = McTheme.colors.textTertiary,
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        McSecondaryButton(text = "Tentar de novo", onClick = onRetry)
                        TextButton(onClick = onRevoke) {
                            Text(
                                text = "Desativar",
                                color = McTheme.colors.textTertiary,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            review?.let { CoachReviewCard(review = it, state = reviewState) }
            CoachProgressCard(state = progress, onRetry = onProgressRetry, onTrends = onProgressTrends)
            if (history != CoachHistoryState.Hidden) {
                CoachHistoryCard(
                    state = history,
                    onClear = onClearHistory,
                    onRetry = onRetryHistory,
                    onDeleteMessage = onDeleteHistoryMessage,
                    onDeleteSummary = onDeleteHistorySummary,
                )
            }
            TextButton(
                onClick = onRevoke,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag("coach-disable"),
            ) {
                Text(
                    text = "Desativar Coach",
                    color = McTheme.colors.textTertiary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Text(
                text = "Na primeira vez, pedimos sua permissão para usar um resumo dos treinos. " +
                    "Nada sai do aparelho sem você permitir.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = McTheme.colors.textTertiary,
            )
        }
    }

    if (replanSheetOpen && replan.suggestion != null) {
        ReplanSuggestionSheet(
            state = replan,
            onAccept = onReplanAccept,
            onDecline = {
                onReplanDecline()
                replanSheetOpen = false
            },
            onSuppress = {
                onReplanSuppress()
                replanSheetOpen = false
            },
            onDismiss = {
                onReplanDismiss()
                replanSheetOpen = false
            },
        )
    }
    if (replanChangesOpen) {
        replan.lastChange?.let { change ->
            ReplanChangesSheet(
                change = change,
                onDismiss = { replanChangesOpen = false },
                canUndo = replan.canUndo,
                isUndoing = replan.isUndoing,
                onUndo = onReplanUndo,
            )
        }
    }
    replan.missedSessions?.let { sessions ->
        MissedSessionPickerSheet(
            sessions = sessions,
            onSelect = onReplanMissedSelected,
            onDismiss = onReplanDismissIntent,
        )
    }
    replan.lessTimeDays?.let { days ->
        LessTimeSheet(
            days = days,
            onReview = onReplanLessTimeSelected,
            onDismiss = onReplanDismissIntent,
        )
    }
}
