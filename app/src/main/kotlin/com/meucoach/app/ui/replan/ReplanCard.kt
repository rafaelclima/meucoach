package com.meucoach.app.ui.replan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meucoach.app.application.ReplanChangeKind
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Invitation card shown after the weekly plan when a revision is available. */
@Composable
fun ReplanSuggestionCard(state: ReplanUiState, onAccept: () -> Unit, onDecline: () -> Unit, onSuppress: () -> Unit) {
    val suggestion = state.suggestion ?: return
    val colors = McTheme.colors
    McCard(modifier = Modifier.testTag("replan-card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "AJUSTE DA SEMANA",
                style = McTextStyles.overline,
                color = colors.textTertiary,
            )
            McPill(
                text = "v${suggestion.versionFrom} → v${suggestion.versionTo}",
                containerColor = colors.surfaceInset,
                contentColor = colors.textSecondary,
                modifier = Modifier.testTag("replan-version"),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = suggestion.headline,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = suggestion.body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "O treino de hoje não muda. Sessões já feitas também ficam intactas.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        if (suggestion.changes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "O QUE MUDA",
                style = McTextStyles.overline,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            suggestion.changes.take(MAX_PREVIEW_CHANGES).forEach { change ->
                ReplanChangeRow(change = change)
            }
            val hidden = suggestion.changes.size - MAX_PREVIEW_CHANGES
            if (hidden > 0) {
                Text(
                    text = "e mais $hidden ${if (hidden == 1) "dia" else "dias"}",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.testTag("replan-more-changes"),
                )
            }
        }
        state.message?.let { message ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = McTextStyles.caption,
                color = colors.warning,
                modifier = Modifier.testTag("replan-message"),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        McPrimaryButton(
            text = if (state.isApplying) "Ajustando…" else "Ajustar a semana",
            onClick = onAccept,
            enabled = !state.isApplying,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("replan-accept"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            McTextButton(
                text = "Agora não",
                onClick = onDecline,
                trailingArrow = false,
                enabled = !state.isApplying,
                modifier = Modifier.testTag("replan-decline"),
            )
            McTextButton(
                text = "Não sugerir hoje",
                onClick = onSuppress,
                trailingArrow = false,
                enabled = !state.isApplying,
                modifier = Modifier.testTag("replan-suppress"),
            )
        }
        Text(
            text = confidenceLabel(suggestion.confidence),
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
}

/** Passive result of an explicit request (no change, stale, applied). */
@Composable
fun ReplanMessageCard(message: String, onDismiss: () -> Unit) {
    val colors = McTheme.colors
    McCard(
        variant = McCardVariant.INSET,
        modifier = Modifier.testTag("replan-message-card"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("replan-message"),
            )
            McTextButton(
                text = "Entendi",
                onClick = onDismiss,
                trailingArrow = false,
                modifier = Modifier.testTag("replan-dismiss-message"),
            )
        }
    }
}

/** Persistent explanation of the latest accepted revision. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplanChangesSheet(change: ReplanVersionChangeUi, onDismiss: () -> Unit) {
    val colors = McTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        modifier = Modifier.testTag("replan-changes-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(
                text = "O QUE MUDOU",
                style = McTextStyles.overline,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Plano v${change.fromVersion} → v${change.toVersion}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = change.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            change.changes.forEachIndexed { index, item ->
                if (index > 0) McDivider(modifier = Modifier.padding(vertical = 8.dp))
                ReplanChangeRow(change = item)
            }
            Spacer(modifier = Modifier.height(16.dp))
            McPrimaryButton(
                text = "Fechar",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("replan-changes-close"),
            )
        }
    }
}

@Composable
private fun ReplanChangeRow(change: ReplanChangeUi) {
    val colors = McTheme.colors
    val kindColor = when (change.kind) {
        ReplanChangeKind.ADDED -> colors.success
        ReplanChangeKind.REMOVED -> colors.textTertiary
        ReplanChangeKind.MODIFIED -> colors.textSecondary
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatDay(change.date),
            style = McTextStyles.caption,
            color = colors.textTertiary,
            modifier = Modifier.width(48.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = change.kindLabel,
                style = McTextStyles.caption,
                color = kindColor,
            )
            Text(
                text = when (change.kind) {
                    ReplanChangeKind.ADDED -> change.nextLabel.orEmpty()

                    ReplanChangeKind.REMOVED -> change.previousLabel.orEmpty()

                    ReplanChangeKind.MODIFIED ->
                        listOfNotNull(change.previousLabel, change.nextLabel).joinToString(" → ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
            )
        }
    }
}

private fun formatDay(date: LocalDate): String {
    val locale = Locale.forLanguageTag("pt-BR")
    val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    return label.replaceFirstChar { it.uppercaseChar() }.trimEnd('.')
}

private const val MAX_PREVIEW_CHANGES = 3
