package com.meucoach.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
        TextButton(
            onClick = onInfoClick,
            modifier = Modifier
                .padding(end = 44.dp)
                .testTag("coach-chat-info")
                .semantics { contentDescription = "Como funciona o Coach" },
        ) {
            Text(
                text = "Como funciona",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun CoachBubble(
    text: String,
    modifier: Modifier = Modifier,
    testTag: String = "coach-bubble",
    showAvatar: Boolean = true,
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
        } else {
            Spacer(modifier = Modifier.size(28.dp))
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
 * Consecutive coach bubbles share one avatar, like a chat thread. Each bubble
 * keeps its own text semantics so TalkBack reads them one by one.
 */
@Composable
internal fun CoachBubbleSequence(texts: List<String>, testTag: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        texts.forEachIndexed { index, text ->
            CoachBubble(
                text = text,
                testTag = testTag,
                showAvatar = index == 0,
            )
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
    onReview: () -> Unit,
    onMissed: () -> Unit,
    onLessTime: () -> Unit,
    onAdjust: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("coach-quick-replies"),
    ) {
        QuickChip(text = "Explica minha semana", testTag = "coach-quick-explain", onClick = onExplain)
        QuickChip(text = "Semana passada?", testTag = "coach-quick-review", onClick = onReview)
        QuickChip(text = "Perdi um treino", testTag = "coach-quick-missed", onClick = onMissed)
        QuickChip(text = "Só tenho 30 min", testTag = "coach-quick-less-time", onClick = onLessTime)
        QuickChip(text = "Ajustar a semana", testTag = "coach-quick-adjust", onClick = onAdjust)
        Spacer(modifier = Modifier.width(24.dp))
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
