package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.coach.ChatTurn
import com.meucoach.core.domain.review.WeeklyReview
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
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
    val exchanges by viewModel.exchanges.collectAsStateWithLifecycle()
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
        exchanges = exchanges,
        onSendFreeText = {
            viewModel.sendFreeText(it)
        },
        onEchoQuestion = { viewModel.echoQuestion(it) },
        onRetryFreeAnswer = { viewModel.retryFreeAnswer() },
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
        onDeleteHistoryTurn = { viewModel.deleteHistoryTurn(it) },
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onSendFreeText: (String) -> Unit = {},
    onEchoQuestion: (String) -> Unit = {},
    onRetryFreeAnswer: () -> Unit = {},
    exchanges: List<FreeExchange> = emptyList(),
    onOnboardingProfile: () -> Unit = {},
    onOnboardingCalibration: () -> Unit = {},
    onOnboardingImport: () -> Unit = {},
    guards: CoachGuardsUiState = CoachGuardsUiState.Hidden,
    onGuardsRetry: () -> Unit = {},
    progress: CoachProgressUiState = CoachProgressUiState.Hidden,
    onProgressRetry: () -> Unit = {},
    onProgressTrends: () -> Unit = {},
    onDeleteHistoryTurn: (ChatTurn) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var replanSheetOpen by rememberSaveable { mutableStateOf(false) }
    var replanChangesOpen by rememberSaveable { mutableStateOf(false) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var showReview by rememberSaveable { mutableStateOf(false) }
    var showProgress by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val feedScroll = rememberScrollState()
    var explainY by remember { mutableStateOf<Int?>(null) }
    var retroY by remember { mutableStateOf<Int?>(null) }
    val reviewBring = remember { BringIntoViewRequester() }
    val progressBring = remember { BringIntoViewRequester() }
    fun scrollTo(y: Int?) {
        if (y == null) return
        scope.launch { feedScroll.animateScrollTo(y.coerceAtLeast(0)) }
    }
    LaunchedEffect(exchanges) {
        if (exchanges.isNotEmpty()) {
            feedScroll.animateScrollTo(feedScroll.maxValue)
        }
    }
    LaunchedEffect(replan.suggestion != null) {
        if (replan.suggestion != null) replanSheetOpen = true
    }
    LaunchedEffect(showReview) {
        if (showReview) reviewBring.bringIntoView()
    }
    LaunchedEffect(showProgress) {
        if (showProgress) progressBring.bringIntoView()
    }
    val onboarding = next is CoachNextUiState.Onboarding
    val hour = remember { LocalTime.now().hour }
    val status = "motor no comando"
    val reviewAvailable = consented && review != null
    val historyAvailable = consented && history != CoachHistoryState.Hidden
    val progressAvailable = progress != CoachProgressUiState.Hidden
    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            CoachDateDivider(text = "Hoje")
            CoachBubbleSequence(
                texts = CoachBubbles.split(openingText(next, hour)),
                testTag = "coach-chat-opening",
            )
            (next as? CoachNextUiState.Ready)?.context?.let { context ->
                CoachCheckInLabels.ack(context)?.let { ack ->
                    CoachSystemLine(
                        text = CoachCheckInLabels.SYSTEM_LINE,
                        testTag = "coach-system-checkin",
                    )
                    CoachBubble(
                        text = ack,
                        testTag = "coach-checkin-ack",
                    )
                }
            }
            (next as? CoachNextUiState.Ready)?.context?.let { context ->
                CoachDoneLabels.cheer(context)?.let { cheer ->
                    CoachSystemLine(
                        text = CoachDoneLabels.SYSTEM_LINE,
                        testTag = "coach-system-done",
                    )
                    CoachBubble(
                        text = cheer,
                        testTag = "coach-done-cheer",
                    )
                }
            }
            (next as? CoachNextUiState.Ready)?.context?.let { context ->
                CoachImportLabels.ack(context)?.let { ack ->
                    CoachSystemLine(
                        text = CoachImportLabels.SYSTEM_LINE,
                        testTag = "coach-system-import",
                    )
                    CoachBubble(
                        text = ack,
                        testTag = "coach-import-ack",
                    )
                }
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
                                CoachBubble(
                                    text = "Deixa eu olhar sua semana…",
                                    testTag = "coach-explanation-loading",
                                )
                            }

                            is CoachExplanationState.Ready -> {
                                CoachExplanationCard(explanation)
                            }

                            is CoachExplanationState.Fallback -> {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CoachBubble(
                                        text = CoachFallbackLabels.reasonCopy(explanation.reason),
                                        testTag = "coach-fallback-reason",
                                    )
                                    CoachBubbleSequence(
                                        texts = CoachBubbles.split(explanation.planRationale),
                                        testTag = "coach-fallback-plan",
                                    )
                                }
                                TextButton(
                                    onClick = onRetry,
                                    modifier = Modifier.testTag("coach-fallback-retry"),
                                ) {
                                    Text(text = "Tentar de novo", color = McTheme.colors.accentText)
                                }
                            }
                        }
                    }
                } else {
                    CoachAiInviteCard(
                        expanded = showConsent,
                        onGrant = onGrant,
                        onDismiss = onDismiss,
                    )
                }
                if (reviewAvailable || progressAvailable || historyAvailable) {
                    Column(
                        modifier = Modifier.onGloballyPositioned {
                            retroY = it.positionInParent().y.roundToInt()
                        },
                    ) {
                        CoachRetroPrompt(
                            showReview = showReview && reviewAvailable,
                            showProgress = showProgress && progressAvailable,
                            reviewAvailable = reviewAvailable,
                            progressAvailable = progressAvailable,
                            historyAvailable = historyAvailable,
                            onReview = {
                                showReview = !showReview
                                if (showReview) {
                                    onEchoQuestion("Como foi minha semana passada?")
                                    showProgress = false
                                }
                            },
                            onProgress = {
                                showProgress = !showProgress
                                if (showProgress) showReview = false
                            },
                            onHistory = { historyOpen = true },
                        )
                    }
                }
                if (reviewAvailable && showReview) {
                    Column(modifier = Modifier.bringIntoViewRequester(reviewBring)) {
                        CoachReviewCard(review = review, state = reviewState)
                    }
                }
                if (progressAvailable && showProgress) {
                    Column(modifier = Modifier.bringIntoViewRequester(progressBring)) {
                        CoachProgressCard(
                            state = progress,
                            onRetry = onProgressRetry,
                            onTrends = onProgressTrends,
                        )
                    }
                }
                exchanges.forEach { exchange ->
                    FreeExchangeRow(
                        exchange = exchange,
                        onRetryFreeAnswer = onRetryFreeAnswer,
                    )
                }
            }
        }
        if (!onboarding) {
            var draft by rememberSaveable { mutableStateOf("") }
            val busy = exchanges.any { it.answer is FreeAnswerUiState.Loading }
            fun sendDraft() {
                val text = draft.trim()
                if (text.isEmpty() || busy) return
                onSendFreeText(text)
                draft = ""
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("coach-input-bar"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val typing = explanation is CoachExplanationState.Loading ||
                    (showReview && reviewState is CoachReviewState.Loading) ||
                    (showProgress && progress is CoachProgressUiState.Loading)
                if (typing) CoachTyping()
                CoachQuickReplies(
                    onExplain = {
                        onEchoQuestion("Pode explicar minha semana?")
                        onRetry()
                        scrollTo(explainY)
                    },
                    onRetro = { scrollTo(retroY) },
                    onMissed = {
                        onEchoQuestion("Perdi o treino de ontem")
                        onReplanRequestMissed()
                    },
                    onLessTime = {
                        onEchoQuestion("Só tenho 30 min na quinta")
                        onReplanRequestLessTime()
                    },
                    onAdjust = {
                        onEchoQuestion("Quero ajustar a semana")
                        onReplanRequest()
                    },
                )
                if (consented) {
                    CoachComposer(
                        value = draft,
                        onValueChange = { draft = it },
                        onSend = ::sendDraft,
                        sendEnabled = !busy,
                    )
                } else {
                    CoachComposerHint()
                }
            }
        }
    }

    if (infoOpen) {
        CoachHowItWorksSheet(
            onDismiss = { infoOpen = false },
            onRevoke = onRevoke,
            aiActive = consented,
        )
    }
    if (historyOpen) {
        ModalBottomSheet(
            onDismissRequest = { historyOpen = false },
            sheetState = historySheetState,
            containerColor = McTheme.colors.surface,
            modifier = Modifier.testTag("coach-history-sheet"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                CoachHistoryCard(
                    state = history,
                    onClear = onClearHistory,
                    onRetry = onRetryHistory,
                    onDeleteTurn = onDeleteHistoryTurn,
                )
            }
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

/**
 * AI is optional; the deterministic Coach never depends on it (P-03).
 *
 * The invite is a chat bubble, not a card: after "Depois" it keeps a persistent
 * "Ativar IA" so the screen never becomes a dead end.
 */
@Composable
private fun CoachAiInviteCard(
    expanded: Boolean,
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("coach-ai-invite"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoachBubble(
            text = if (expanded) {
                "A IA é opcional: seu treino, as travas e a evolução já funcionam no aparelho. " +
                    "Quer que eu escreva a explicação e a revisão? Envio só um resumo, sem nome e sem mapa."
            } else {
                "A explicação em texto está desligada. Quer ligar a IA?"
            },
            testTag = "coach-ai-invite-bubble",
        )
        if (expanded) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            TextButton(
                onClick = onGrant,
                modifier = Modifier.testTag("coach-ai-grant"),
            ) {
                Text(text = "Ativar IA", color = colors.accentText)
            }
        }
    }
}

private fun openingText(next: CoachNextUiState, hour: Int): String = when (next) {
    CoachNextUiState.Empty -> "Fechamos a semana. Descansa que segunda eu trago a próxima."

    is CoachNextUiState.Onboarding ->
        "Opa! Pra eu montar seus treinos, preciso de 3 coisas. " +
            "Me conta aqui embaixo que eu já organizo."

    is CoachNextUiState.Error -> "Não consegui carregar aqui. Tenta de novo?"

    is CoachNextUiState.Ready -> CoachOpeningLabels.opening(next.context, hour)

    else -> "Oi, bora ver o que importa?"
}
