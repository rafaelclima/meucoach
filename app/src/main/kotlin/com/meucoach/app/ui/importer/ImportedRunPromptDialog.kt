package com.meucoach.app.ui.importer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme

@Composable
fun ImportedRunPromptHost(viewModel: ImportedRunPromptViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ImportedRunPromptDialog(
        state = state,
        onLink = viewModel::link,
        onReplaceManualRecord = viewModel::replaceManualRecord,
        onKeepAsFreeRun = viewModel::keepAsFreeRun,
        onConsumeMessage = viewModel::consumeMessage,
    )
}

@Composable
internal fun ImportedRunPromptDialog(
    state: ImportedRunPromptState,
    onLink: () -> Unit,
    onReplaceManualRecord: () -> Unit,
    onKeepAsFreeRun: () -> Unit,
    onConsumeMessage: () -> Unit,
) {
    val prompt = state.prompt
    if (prompt == null) {
        state.message?.let { message ->
            InfoDialog(message = message, onDismiss = onConsumeMessage)
        }
        return
    }

    val colors = McTheme.colors
    val isOffer = prompt is ImportedRunPromptUi.Offer
    val canReplace = prompt is ImportedRunPromptUi.AlreadyDone && prompt.canReplace
    AlertDialog(
        onDismissRequest = { if (!state.isLinking) onKeepAsFreeRun() },
        modifier = Modifier.testTag("imported-run-prompt"),
        title = {
            Text(
                text = when {
                    isOffer -> "Corrida importada"
                    canReplace -> "Corrigir registro do treino"
                    else -> "Treino de hoje já concluído"
                },
            )
        },
        text = {
            Column {
                Text(
                    text = "${prompt.activityTitle} · ${prompt.activityDetail}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (prompt is ImportedRunPromptUi.Offer) {
                    Text(
                        text = "Você importou esta corrida hoje. Ela pode ser o treino do dia:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = listOfNotNull(
                            prompt.sessionTitle,
                            prompt.sessionDurationMin?.let { "$it min" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Quer usar esta corrida como o treino de hoje?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                } else if (prompt is ImportedRunPromptUi.AlreadyDone) {
                    if (prompt.canReplace) {
                        Text(
                            text = "O treino de hoje foi marcado como feito sem registro, " +
                                "mas esta corrida é do mesmo dia.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Quer substituir o registro manual pelos dados reais da corrida? " +
                                "A duração e o RPE informados à mão serão descartados.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    } else {
                        Text(
                            text = "O treino de hoje já foi concluído ${prompt.evidenceLabel}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Esta corrida vai ficar registrada no app como treino livre. " +
                                "O coach não vai usá-la como o treino do dia.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                state.message?.let { message ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message,
                        style = McTextStyles.caption,
                        color = colors.warning,
                        modifier = Modifier.testTag("imported-run-message"),
                    )
                }
            }
        },
        confirmButton = {
            when {
                prompt is ImportedRunPromptUi.Offer -> McPrimaryButton(
                    text = if (state.isLinking) "Vinculando…" else "Usar como treino de hoje",
                    onClick = onLink,
                    enabled = !state.isLinking,
                    modifier = Modifier.testTag("imported-run-link"),
                )

                canReplace -> McPrimaryButton(
                    text = if (state.isLinking) "Substituindo…" else "Usar esta corrida",
                    onClick = onReplaceManualRecord,
                    enabled = !state.isLinking,
                    modifier = Modifier.testTag("imported-run-replace"),
                )

                else -> McTextButton(
                    text = "Entendi",
                    onClick = onKeepAsFreeRun,
                    trailingArrow = false,
                    modifier = Modifier.testTag("imported-run-keep"),
                )
            }
        },
        dismissButton = {
            when {
                prompt is ImportedRunPromptUi.Offer -> McTextButton(
                    text = "Só salvar no app",
                    onClick = onKeepAsFreeRun,
                    trailingArrow = false,
                    enabled = !state.isLinking,
                    modifier = Modifier.testTag("imported-run-keep"),
                )

                canReplace -> McTextButton(
                    text = "Manter sem registro",
                    onClick = onKeepAsFreeRun,
                    trailingArrow = false,
                    enabled = !state.isLinking,
                    modifier = Modifier.testTag("imported-run-keep"),
                )
            }
        },
    )
}

@Composable
private fun InfoDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("imported-run-result"),
        title = { Text(text = "Corrida importada") },
        text = { Text(text = message) },
        confirmButton = {
            McTextButton(
                text = "Entendi",
                onClick = onDismiss,
                trailingArrow = false,
            )
        },
    )
}
