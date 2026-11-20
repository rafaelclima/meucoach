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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun CoachRoute(
    viewModel: CoachViewModel = hiltViewModel(),
    onCheckInClick: () -> Unit = {},
    onTodayClick: () -> Unit = {},
    onTrendsClick: () -> Unit = {},
    onOnboardingProfile: () -> Unit = {},
    onOnboardingCalibration: () -> Unit = {},
    onOnboardingImport: () -> Unit = {},
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
        onOnboardingProfile = onOnboardingProfile,
        onOnboardingCalibration = onOnboardingCalibration,
        onOnboardingImport = onOnboardingImport,
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
    onOnboardingProfile: () -> Unit = {},
    onOnboardingCalibration: () -> Unit = {},
    onOnboardingImport: () -> Unit = {},
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
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    val userEchoes = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val feedScroll = rememberScrollState()
    var explainY by remember { mutableStateOf<Int?>(null) }
    var reviewY by remember { mutableStateOf<Int?>(null) }
    fun scrollTo(y: Int?) {
        if (y == null) return
        scope.launch { feedScroll.animateScrollTo(y.coerceAtLeast(0)) }
    }
    LaunchedEffect(replan.suggestion != null) {
        if (replan.suggestion != null) replanSheetOpen = true
    }
    val onboarding = next is CoachNextUiState.Onboarding
    val status = if (consented) {
        "motor no comando · IA ativa"
    } else {
        "motor no comando · IA opcional"
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("coach-timeline"),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(feedScroll)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("coach-feed"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoachChatHeader(status = status, onInfoClick = { infoOpen = true })
            if (onboarding) {
                CoachBubble(
                    text = "Opa! Pra eu montar seus treinos, preciso de 3 coisas. " +
                        "Me conta aqui embaixo que eu já organizo.",
                    testTag = "coach-chat-opening",
                )
            } else {
                CoachBubble(
                    text = "Opa, bora ver o que importa hoje? Separei tudo aqui embaixo.",
                    testTag = "coach-chat-opening",
                )
            }
            CoachNextCard(
                state = next,
                onRetry = onNextRetry,
                onCheckIn = onNextCheckIn,
                onToday = onNextToday,
                onProfile = onOnboardingProfile,
                onCalibration = onOnboardingCalibration,
                onImport = onOnboardingImport,
            )
            if (!onboarding) {
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
                if (consented) {
                    Column(
                        modifier = Modifier.onGloballyPositioned {
                            explainY = it.positionInParent().y.roundToInt()
                        },
                    ) {
                        when (explanation) {
                            // The local card covers "no plan"; a consented user without a
                            // plan must not see a second onboarding message here.
                            CoachExplanationState.Unavailable -> Unit

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
                                            text = CoachFallbackLabels.reasonCopy(explanation.reason),
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                            color = McTheme.colors.textTertiary,
                                            modifier = Modifier.testTag("coach-fallback-reason"),
                                        )
                                        Text(
                                            text = "Me enrolei aqui — tenta de novo em instantes?",
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
                                            text = "Desativar IA",
                                            color = McTheme.colors.textTertiary,
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    review?.let { current ->
                        Column(
                            modifier = Modifier.onGloballyPositioned {
                                reviewY = it.positionInParent().y.roundToInt()
                            },
                        ) {
                            CoachReviewCard(review = current, state = reviewState)
                        }
                    }
                } else {
                    CoachAiInviteCard(
                        expanded = showConsent,
                        onGrant = onGrant,
                        onDismiss = onDismiss,
                    )
                }
                CoachProgressCard(state = progress, onRetry = onProgressRetry, onTrends = onProgressTrends)
                if (consented && history != CoachHistoryState.Hidden) {
                    CoachHistoryCard(
                        state = history,
                        onClear = onClearHistory,
                        onRetry = onRetryHistory,
                        onDeleteMessage = onDeleteHistoryMessage,
                        onDeleteSummary = onDeleteHistorySummary,
                    )
                }
            }
        }
        if (!onboarding) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (explanation is CoachExplanationState.Loading || reviewState is CoachReviewState.Loading) {
                    CoachTyping()
                }
                userEchoes.forEach { echo ->
                    UserBubble(
                        text = echo,
                        testTag = "coach-user-echo",
                    )
                }
                CoachQuickReplies(
                    onExplain = {
                        userEchoes.add("Pode explicar minha semana?")
                        onRetry()
                        scrollTo(explainY)
                    },
                    onReview = {
                        userEchoes.add("Como foi minha semana passada?")
                        scrollTo(reviewY)
                    },
                    onMissed = {
                        userEchoes.add("Perdi o treino de ontem")
                        onReplanRequestMissed()
                    },
                    onLessTime = {
                        userEchoes.add("Só tenho 30 min na quinta")
                        onReplanRequestLessTime()
                    },
                    onAdjust = {
                        userEchoes.add("Quero ajustar a semana")
                        onReplanRequest()
                    },
                )
                CoachComposerHint()
            }
        }
    }

    if (infoOpen) {
        CoachHowItWorksSheet(onDismiss = { infoOpen = false }, onRevoke = onRevoke)
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

/**
 * AI is optional; the deterministic Coach never depends on it (P-03).
 *
 * After "Depois" the card collapses but keeps a persistent "Ativar IA", so the
 * screen never becomes a dead end.
 */
@Composable
private fun CoachAiInviteCard(
    expanded: Boolean,
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    McCard(modifier = modifier.testTag("coach-ai-invite")) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "IA do Coach (opcional)",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = McTheme.colors.textPrimary,
            )
            Text(
                text = "Seu próximo treino, as travas e a evolução já funcionam no aparelho, sem IA.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = McTheme.colors.textSecondary,
            )
            if (expanded) {
                Text(
                    text = "Para reescrever a explicação e a revisão, o app envia um resumo dos " +
                        "treinos (sem nome e sem mapa) ao serviço de IA. O motor continua decidindo.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = McTheme.colors.textSecondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    McPrimaryButton(
                        text = "Ativar IA",
                        onClick = onGrant,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("coach-ai-grant"),
                    )
                    McSecondaryButton(
                        text = "Depois",
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("coach-ai-later"),
                    )
                }
            } else {
                Text(
                    text = "A explicação em texto continua desligada.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = McTheme.colors.textTertiary,
                )
                McPrimaryButton(
                    text = "Ativar IA",
                    onClick = onGrant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coach-ai-grant"),
                )
            }
        }
    }
}
