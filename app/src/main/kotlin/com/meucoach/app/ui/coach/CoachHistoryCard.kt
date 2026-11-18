package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.coach.CoachMemoryMessage
import com.meucoach.core.domain.coach.CoachMemorySummary
import com.meucoach.core.domain.coach.CoachMessageKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HISTORY_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.forLanguageTag("pt-BR"))
private val SUMMARY_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"))

/**
 * Read-only history of the Coach tab (PRD decisions 17/25).
 *
 * It only exposes copy that already passed `CoachResponseValidator` and was
 * stored with opt-in. There is no edit path and no rule/number here: the engine
 * remains the authority for load, pace and planning.
 */
@Composable
internal fun CoachHistoryCard(
    state: CoachHistoryState,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onDeleteMessage: (String) -> Unit,
    onDeleteSummary: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteKind by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteKey by rememberSaveable { mutableStateOf<String?>(null) }

    McCard(modifier = modifier.testTag("coach-history-card")) {
        Text(
            text = "HISTÓRICO DO COACH",
            style = McTextStyles.overline,
            color = colors.textTertiary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Só você vê este histórico. Ele lembra a conversa, mas as decisões " +
                "de treino continuam vindo do motor.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(
            text = "Somente leitura, sem edição.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        McDivider(modifier = Modifier.padding(vertical = 10.dp))

        when (state) {
            CoachHistoryState.Hidden -> Unit

            CoachHistoryState.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coach-history-loading"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )
                    Text(
                        text = "Carregando histórico…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }

            CoachHistoryState.Empty -> {
                Text(
                    text = "Nada guardado ainda. As explicações e revisões antigas aparecem aqui.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("coach-history-empty"),
                )
            }

            is CoachHistoryState.Error -> {
                Text(
                    text = state.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("coach-history-error"),
                )
                McSecondaryButton(
                    text = "Tentar de novo",
                    onClick = onRetry,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag("coach-history-retry"),
                )
            }

            is CoachHistoryState.Ready -> {
                if (state.summaries.isNotEmpty()) {
                    HistorySectionTitle(text = "Resumos por semana")
                    state.summaries.forEachIndexed { index, summary ->
                        if (index > 0) McDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SummaryRow(
                            summary = summary,
                            onDelete = {
                                pendingDeleteKind = "summary"
                                pendingDeleteKey = summary.weekStart.toString()
                            },
                        )
                    }
                }
                if (state.messages.isNotEmpty()) {
                    if (state.summaries.isNotEmpty()) {
                        McDivider(modifier = Modifier.padding(vertical = 10.dp))
                    }
                    HistorySectionTitle(text = "Conversa")
                    state.messages.forEachIndexed { index, message ->
                        if (index > 0) McDivider(modifier = Modifier.padding(vertical = 8.dp))
                        MessageRow(
                            message = message,
                            onDelete = {
                                pendingDeleteKind = "message"
                                pendingDeleteKey = message.id
                            },
                        )
                    }
                }
                TextButton(
                    onClick = { confirmingClear = true },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                        .semantics { contentDescription = "Apagar todo o histórico do Coach" }
                        .testTag("coach-history-clear"),
                ) {
                    Text(text = "Apagar histórico", color = colors.danger)
                }
            }
        }
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            modifier = Modifier.testTag("coach-history-clear-dialog"),
            title = { Text(text = "Apagar o histórico do Coach?") },
            text = {
                Text(
                    text = "Mensagens e resumos guardados serão removidos deste aparelho. " +
                        "Treinos, perfil e check-ins não serão alterados.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClear = false
                        onClear()
                    },
                    modifier = Modifier.testTag("coach-history-clear-confirm"),
                ) {
                    Text(text = "Apagar histórico", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(text = "Cancelar", color = colors.textPrimary)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
        )
    }

    val deleteKind = pendingDeleteKind
    val deleteKey = pendingDeleteKey
    if (deleteKind != null && deleteKey != null) {
        AlertDialog(
            onDismissRequest = {
                pendingDeleteKind = null
                pendingDeleteKey = null
            },
            modifier = Modifier.testTag("coach-history-delete-dialog"),
            title = {
                Text(
                    text = if (deleteKind == "message") {
                        "Apagar esta mensagem do histórico?"
                    } else {
                        "Apagar este resumo da semana?"
                    },
                )
            },
            text = {
                Text(
                    text = "O item será removido deste aparelho. " +
                        "Treinos, perfil e check-ins não serão alterados.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteKind = null
                        pendingDeleteKey = null
                        if (deleteKind == "message") {
                            onDeleteMessage(deleteKey)
                        } else {
                            runCatching { LocalDate.parse(deleteKey) }
                                .getOrNull()
                                ?.let(onDeleteSummary)
                        }
                    },
                    modifier = Modifier.testTag("coach-history-delete-confirm"),
                ) {
                    Text(text = "Apagar", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDeleteKind = null
                        pendingDeleteKey = null
                    },
                ) {
                    Text(text = "Cancelar", color = colors.textPrimary)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
        )
    }
}

@Composable
private fun HistorySectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = McTheme.colors.textPrimary,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SummaryRow(summary: CoachMemorySummary, onDelete: () -> Unit) {
    HistoryRow(
        tag = "coach-history-summary-${summary.weekStart}",
        meta = "Resumo da semana de ${SUMMARY_DATE.format(summary.weekStart)}",
        title = summary.title,
        body = summary.body,
        bullets = summary.bullets,
        deleteTag = "coach-history-delete-summary-${summary.weekStart}",
        deleteDescription = "Apagar resumo da semana de ${SUMMARY_DATE.format(summary.weekStart)}",
        onDelete = onDelete,
    )
}

@Composable
private fun MessageRow(message: CoachMemoryMessage, onDelete: () -> Unit) {
    HistoryRow(
        tag = "coach-history-message-${message.id}",
        meta = "${kindLabel(message.kind)} · ${HISTORY_DATE.format(message.createdAt.dateInSystemZone())}",
        title = message.title,
        body = message.body,
        bullets = message.bullets,
        deleteTag = "coach-history-delete-message-${message.id}",
        deleteDescription = "Apagar mensagem: ${message.title}",
        onDelete = onDelete,
    )
}

@Composable
private fun HistoryRow(
    tag: String,
    meta: String,
    title: String,
    body: String,
    bullets: List<String>,
    deleteTag: String,
    deleteDescription: String,
    onDelete: () -> Unit,
) {
    val colors = McTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = meta,
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            bullets.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .semantics { contentDescription = deleteDescription }
                .testTag(deleteTag),
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.textTertiary,
            )
        }
    }
}

private fun kindLabel(kind: CoachMessageKind): String = when (kind) {
    CoachMessageKind.EXPLAIN -> "Explicação da semana"
    CoachMessageKind.REVIEW -> "Revisão da semana"
}

private fun Instant.dateInSystemZone(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()
