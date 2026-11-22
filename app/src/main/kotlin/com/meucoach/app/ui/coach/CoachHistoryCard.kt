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
import androidx.compose.runtime.remember
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
import com.meucoach.core.domain.coach.ChatTurn
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
    onDeleteTurn: (ChatTurn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteTurn by remember { mutableStateOf<ChatTurn?>(null) }

    McCard(modifier = modifier.testTag("coach-history-card")) {
        Text(
            text = "Nossa conversa",
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
                var expanded by rememberSaveable { mutableStateOf(false) }
                val totalItems = state.turns.size
                val showTurns = if (expanded) state.turns else state.turns.take(3)
                var firstRow = true
                showTurns.forEach { turn ->
                    if (!firstRow) McDivider(modifier = Modifier.padding(vertical = 8.dp))
                    firstRow = false
                    when (turn) {
                        is ChatTurn.Coach -> TurnRow(
                            turn = turn,
                            onDelete = { pendingDeleteTurn = turn },
                        )

                        is ChatTurn.You -> YouRow(
                            turn = turn,
                            onDelete = { pendingDeleteTurn = turn },
                        )
                    }
                }
                if (!expanded && totalItems > 3) {
                    McSecondaryButton(
                        text = "Ver conversa completa",
                        onClick = { expanded = true },
                        modifier = Modifier.testTag("coach-history-more"),
                    )
                }
                if (expanded && totalItems > 3) {
                    McSecondaryButton(
                        text = "Mostrar menos",
                        onClick = { expanded = false },
                        modifier = Modifier.testTag("coach-history-less"),
                    )
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

    val deleteTurn = pendingDeleteTurn
    if (deleteTurn != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteTurn = null },
            modifier = Modifier.testTag("coach-history-delete-dialog"),
            title = { Text(text = "Apagar este item do histórico?") },
            text = {
                Text(
                    text = "O item será removido deste aparelho. " +
                        "Treinos, perfil e check-ins não serão alterados.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteTurn = null
                        onDeleteTurn(deleteTurn)
                    },
                    modifier = Modifier.testTag("coach-history-delete-confirm"),
                ) {
                    Text(text = "Apagar", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTurn = null }) {
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
private fun TurnRow(turn: ChatTurn.Coach, onDelete: () -> Unit) {
    val meta = when (turn.origin) {
        ChatTurn.Origin.SUMMARY -> {
            val week = turn.weekStart ?: turn.dateInSystemZone()
            "Resumo da semana de ${SUMMARY_DATE.format(week)}"
        }

        ChatTurn.Origin.EXPLAIN -> "Explicação da semana · ${HISTORY_DATE.format(turn.dateInSystemZone())}"

        ChatTurn.Origin.REVIEW -> "Revisão da semana · ${HISTORY_DATE.format(turn.dateInSystemZone())}"

        ChatTurn.Origin.ANSWER -> "Resposta do Coach · ${HISTORY_DATE.format(turn.dateInSystemZone())}"
    }
    HistoryRow(
        tag = "coach-history-turn-${turn.id}",
        meta = meta,
        title = turn.title,
        body = turn.body,
        bullets = turn.bullets,
        deleteTag = "coach-history-delete-turn-${turn.id}",
        deleteDescription = "Apagar item: ${turn.title}",
        onDelete = onDelete,
    )
}

@Composable
private fun YouRow(turn: ChatTurn.You, onDelete: () -> Unit) {
    val colors = McTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("coach-history-you-${turn.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                text = "Você · ${HISTORY_DATE.format(turn.dateInSystemZone())}",
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
            UserBubble(
                text = turn.body,
                testTag = "coach-history-you-bubble-${turn.id}",
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .semantics { contentDescription = "Apagar sua mensagem" }
                .testTag("coach-history-delete-you-${turn.id}"),
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

private fun Instant.dateInSystemZone(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private fun ChatTurn.dateInSystemZone(): LocalDate = createdAt.dateInSystemZone()
