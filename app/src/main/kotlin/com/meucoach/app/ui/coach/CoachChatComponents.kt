package com.meucoach.app.ui.coach

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McTheme

/**
 * Chat chrome da aba Coach (PRD_COACH_CHAT F1).
 *
 * Só apresentação: bolhas, header e quick-replies. Nenhum número novo,
 * nenhuma decisão de treino — o conteúdo continua vindo dos mesmos
 * sources/cards.
 */
@Composable
internal fun CoachChatHeader(status: String, onInfoClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("coach-chat-header")
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "C",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onAccent,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Coach",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier
                .padding(end = 44.dp)
                .testTag("coach-chat-info")
                .semantics { contentDescription = "Como funciona o Coach" },
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun CoachBubble(
    text: String,
    modifier: Modifier = Modifier,
    testTag: String = "coach-bubble",
    showAvatar: Boolean = false,
) {
    val colors = McTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showAvatar) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "C",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onAccent,
                )
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .semantics(mergeDescendants = true) {}
                .testTag(testTag),
            shape = MaterialTheme.shapes.medium,
            color = colors.surfaceElevated,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * Consecutive coach bubbles share one visual group. Direct chat hides per
 * message avatars (the header already identifies the coach), so bubbles stay
 * close to the edge and the conversation reads cleaner.
 */
@Composable
internal fun CoachBubbleSequence(
    texts: List<String>,
    testTag: String,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        texts.forEach { text ->
            CoachBubble(
                text = text,
                testTag = testTag,
                showAvatar = showAvatar,
            )
        }
    }
}

/** Chat date divider: one quiet label, never a report overline. */
@Composable
internal fun CoachDateDivider(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = McTheme.colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("coach-date-divider"),
    )
}

/**
 * A rounded attachment that belongs to the conversation (today's session,
 * guards, numbers). It aligns under the coach avatar and never fills the whole
 * width, so the feed still reads as chat instead of a dashboard.
 */
@Composable
internal fun CoachAttachment(
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .padding(end = 24.dp)
            .testTag(testTag),
        shape = MaterialTheme.shapes.large,
        color = McTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, McTheme.colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

/**
 * One coach prompt for the retrovisor. Review, evolution and history are on
 * demand: the feed stays a conversation instead of a report with every section
 * open at once.
 */
@Composable
internal fun CoachRetroPrompt(
    showReview: Boolean,
    showProgress: Boolean,
    reviewAvailable: Boolean,
    progressAvailable: Boolean,
    historyAvailable: Boolean,
    onReview: () -> Unit,
    onProgress: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("coach-retro-prompt"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoachBubble(
            text = "Quer olhar pra trás?",
            testTag = "coach-retro-bubble",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            if (reviewAvailable) {
                TextButton(
                    onClick = onReview,
                    modifier = Modifier.testTag("coach-retro-review"),
                ) {
                    Text(
                        text = if (showReview) "Ocultar semana" else "Semana passada",
                        color = if (showReview) colors.accentText else colors.textSecondary,
                    )
                }
            }
            if (progressAvailable) {
                TextButton(
                    onClick = onProgress,
                    modifier = Modifier.testTag("coach-retro-progress"),
                ) {
                    Text(
                        text = if (showProgress) "Ocultar evolução" else "Evolução",
                        color = if (showProgress) colors.accentText else colors.textSecondary,
                    )
                }
            }
            if (historyAvailable) {
                TextButton(
                    onClick = onHistory,
                    modifier = Modifier.testTag("coach-retro-history"),
                ) {
                    Text(text = "Histórico", color = colors.textSecondary)
                }
            }
            Spacer(modifier = Modifier.width(24.dp))
        }
    }
}

@Composable
internal fun UserBubble(text: String, modifier: Modifier = Modifier, testTag: String = "coach-user-bubble") {
    val colors = McTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 48.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            modifier = Modifier.testTag(testTag),
            shape = MaterialTheme.shapes.medium,
            color = colors.accent,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onAccent,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * One free-text round trip in flow order (ADR 0057 item 2): the question
 * bubble immediately followed by its answer, never floating over content.
 */
@Composable
internal fun FreeExchangeRow(exchange: FreeExchange, onRetryFreeAnswer: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UserBubble(
            text = exchange.question,
            testTag = "coach-user-message",
        )
        when (val answer = exchange.answer) {
            is FreeAnswerUiState.Loading -> CoachTyping(
                modifier = Modifier.testTag("coach-free-typing"),
            )

            is FreeAnswerUiState.Ready -> CoachBubbleSequence(
                texts = answer.texts,
                testTag = "coach-free-answer",
            )

            is FreeAnswerUiState.Fallback -> {
                CoachBubble(
                    text = answer.copy,
                    testTag = "coach-free-fallback",
                )
                TextButton(
                    onClick = onRetryFreeAnswer,
                    modifier = Modifier.testTag("coach-free-retry"),
                ) {
                    Text(text = "Tentar de novo", color = McTheme.colors.accentText)
                }
            }

            FreeAnswerUiState.Idle -> Unit
        }
    }
}

@Composable
internal fun CoachSystemLine(text: String, modifier: Modifier = Modifier, testTag: String = "coach-system-line") {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = McTheme.colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(testTag),
    )
}

@Composable
internal fun CoachTyping(modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "C",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onAccent,
            )
        }
        Surface(
            modifier = Modifier.testTag("coach-typing"),
            shape = MaterialTheme.shapes.medium,
            color = colors.surfaceElevated,
        ) {
            Text(
                text = "digitando…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
internal fun CoachQuickReplies(
    onExplain: () -> Unit,
    onMissed: () -> Unit,
    onLessTime: () -> Unit,
    onAdjust: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("coach-quick-replies"),
        ) {
            QuickChip(text = "Explica a semana", testTag = "coach-quick-explain", onClick = onExplain)
            QuickChip(text = "Perdi treino", testTag = "coach-quick-missed", onClick = onMissed)
            QuickChip(text = "Tenho menos tempo", testTag = "coach-quick-less-time", onClick = onLessTime)
            QuickChip(text = "Ajustar semana", testTag = "coach-quick-adjust", onClick = onAdjust)
            Spacer(modifier = Modifier.width(40.dp))
        }
        // A fade says "there are more chips" instead of a chip cut off at the edge.
        // It starts at 0.94 so the last chip text stays readable (v3).
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.94f to Color.Transparent,
                        1f to McTheme.colors.background,
                    ),
                ),
        )
    }
}

@Composable
private fun QuickChip(text: String, testTag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(text = text) },
        modifier = modifier.testTag(testTag),
    )
}

@Composable
internal fun CoachHowItWorksSheet(onDismiss: () -> Unit, onRevoke: () -> Unit = {}, aiActive: Boolean = false) {
    val colors = McTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("coach-how-it-works-sheet"),
        title = { Text(text = "Como funciona o Coach") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "O motor decide o treino; a IA só reescreve em linguagem de treinador. " +
                        "Sem IA, o plano continua igual. O histórico fica só neste aparelho e " +
                        "apaga ao desativar a IA.",
                )
                Text(
                    text = if (aiActive) {
                        "IA ativa neste aparelho."
                    } else {
                        "IA desligada neste aparelho — o plano segue igual."
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("coach-how-it-works-close"),
            ) {
                Text(text = "Entendi", color = colors.textPrimary)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onRevoke()
                    onDismiss()
                },
                modifier = Modifier.testTag("coach-disable"),
            ) {
                Text(text = "Desativar IA", color = colors.danger)
            }
        },
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
    )
}

@Composable
internal fun CoachComposerHint(modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    Text(
        text = "Em breve você poderá digitar — use as sugestões acima.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textTertiary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag("coach-composer"),
    )
}

/**
 * Typed input of the Coach tab (ADR 0056).
 *
 * Enabled only with opt-in and a plan context; otherwise the caller keeps
 * the disabled hint. Send target is 48 dp and both nodes expose TalkBack
 * descriptions; the message itself is parsed on-device and only `intent +
 * slots` ever leave it.
 */
@Composable
internal fun CoachComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    sendEnabled: Boolean = true,
) {
    val colors = McTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Digite sua mensagem para o Coach" }
                .testTag("coach-composer-input"),
            placeholder = { Text(text = "Digite sua mensagem…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (sendEnabled) onSend() }),
            shape = MaterialTheme.shapes.medium,
        )
        IconButton(
            onClick = onSend,
            enabled = sendEnabled,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Enviar mensagem" }
                .testTag("coach-composer-send"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = colors.accent,
            )
        }
    }
}
