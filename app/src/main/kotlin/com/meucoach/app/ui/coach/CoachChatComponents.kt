package com.meucoach.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
internal fun CoachBubble(text: String, modifier: Modifier = Modifier, testTag: String = "coach-bubble") {
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
            modifier = Modifier
                .weight(1f, fill = false)
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

@Composable
internal fun UserBubble(text: String, modifier: Modifier = Modifier, testTag: String = "coach-user-bubble") {
    val colors = McTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            QuickChip(
                text = "Explica minha semana",
                testTag = "coach-quick-explain",
                onClick = onExplain,
                modifier = Modifier.weight(1f),
            )
            QuickChip(
                text = "Semana passada?",
                testTag = "coach-quick-review",
                onClick = onReview,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            QuickChip(
                text = "Perdi um treino",
                testTag = "coach-quick-missed",
                onClick = onMissed,
                modifier = Modifier.weight(1f),
            )
            QuickChip(
                text = "Só tenho 30 min",
                testTag = "coach-quick-less-time",
                onClick = onLessTime,
                modifier = Modifier.weight(1f),
            )
        }
        QuickChip(
            text = "Ajustar a semana",
            testTag = "coach-quick-adjust",
            onClick = onAdjust,
            modifier = Modifier.fillMaxWidth(),
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
internal fun CoachHowItWorksSheet(onDismiss: () -> Unit) {
    val colors = McTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("coach-how-it-works-sheet"),
        title = { Text(text = "Como funciona o Coach") },
        text = {
            Text(
                text = "O motor decide o treino; a IA só reescreve em linguagem de treinador. " +
                    "Sem IA, o plano continua igual. O histórico fica só neste aparelho e " +
                    "apaga ao desativar a IA.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("coach-how-it-works-close"),
            ) {
                Text(text = "Entendi", color = colors.textPrimary)
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("coach-composer"),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceInset,
    ) {
        Text(
            text = "Em breve você poderá digitar — use as sugestões acima.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
