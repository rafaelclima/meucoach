package com.meucoach.app.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meucoach.app.ui.common.RacePredictionBlock
import com.meucoach.app.ui.common.RaceTargetProjectionBlock
import com.meucoach.app.ui.common.raceDateLabel
import com.meucoach.app.ui.common.raceDistanceLabel
import com.meucoach.app.ui.common.raceStatusLabel
import com.meucoach.app.ui.common.raceTargetTimeLabel
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.race.RacePredictionResult
import com.meucoach.core.domain.race.RaceTargetProjection
import com.meucoach.core.model.RaceCompletionSource
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus

/**
 * The competition branch of the Today screen (ADR 0030).
 *
 * It replaces the training card instead of sitting next to it: a race day never
 * gets a second training instruction or the "how to run" briefing.
 */
@Composable
internal fun RaceDayCard(
    event: RaceEvent,
    projection: RaceTargetProjection?,
    prediction: RacePredictionResult?,
    activityAvailable: Boolean,
    suggestions: List<ActivitySuggestionUi>,
    completionMessage: String?,
    onComplete: (String?) -> Unit,
    onCancel: () -> Unit,
    onNoShow: () -> Unit,
    onUndoAutoCompletion: (String) -> Unit,
    onConsumeMessage: () -> Unit,
    onActivityClick: (String) -> Unit,
) {
    val colors = McTheme.colors
    var completeOpen by remember { mutableStateOf(false) }
    McCard(modifier = Modifier.testTag("race-day-card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "PROVA DE HOJE", style = McTextStyles.overline, color = colors.textTertiary)
            val statusColor = when (event.status) {
                RaceStatus.PLANNED -> colors.accentText
                RaceStatus.COMPLETED -> colors.success
                RaceStatus.CANCELLED, RaceStatus.NO_SHOW -> colors.warning
            }
            McPill(
                text = raceStatusLabel(event.status),
                containerColor = statusColor.copy(alpha = 0.12f),
                contentColor = statusColor,
                modifier = Modifier.testTag("race-status"),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = raceDistanceLabel(event.distance),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = raceDateLabel(event.targetDate),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        raceTargetTimeLabel(event.targetTimeSec)?.let { target ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "META INFORMADA", style = McTextStyles.overline, color = colors.textTertiary)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "$target (meta sua, não é pace prescrito)",
                style = McTextStyles.caption,
                color = colors.textSecondary,
                modifier = Modifier.testTag("race-target-time"),
            )
        }
        if (projection != null) {
            Spacer(modifier = Modifier.height(14.dp))
            RaceTargetProjectionBlock(projection = projection)
        }
        prediction?.let {
            Spacer(modifier = Modifier.height(14.dp))
            RacePredictionBlock(result = it)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = when (event.status) {
                RaceStatus.PLANNED ->
                    "Hoje é dia de prova. Não há treino prescrito para hoje; o app não cria uma segunda instrução."

                RaceStatus.COMPLETED ->
                    if (event.completionSource == RaceCompletionSource.AUTO_IMPORT) {
                        "Prova concluída com a corrida importada. Se não foi a prova, você pode desfazer."
                    } else {
                        "Prova concluída. A corrida vinculada segue contando como atividade normal."
                    }

                RaceStatus.CANCELLED ->
                    "Prova cancelada. O app não inicia recuperação nem libera treino automático para hoje."

                RaceStatus.NO_SHOW ->
                    "Registrado que você não compareceu. O app não inicia recuperação nem libera treino automático."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (event.status == RaceStatus.PLANNED) {
            Spacer(modifier = Modifier.height(16.dp))
            McPrimaryButton(
                text = "Concluir prova",
                onClick = { completeOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("race-complete"),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                McSecondaryButton(
                    text = "Não compareceu",
                    onClick = onNoShow,
                    horizontalPadding = 10.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("race-no-show"),
                )
                McSecondaryButton(
                    text = "Cancelar prova",
                    onClick = onCancel,
                    horizontalPadding = 10.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("race-cancel"),
                )
            }
        }
        if (event.status == RaceStatus.COMPLETED) {
            if (event.completionSource == RaceCompletionSource.AUTO_IMPORT) {
                McTextButton(
                    text = "Não era a prova",
                    onClick = { onUndoAutoCompletion(event.id) },
                    trailingArrow = false,
                    modifier = Modifier.testTag("race-undo-auto"),
                )
            }
            event.activityId?.let { activityId ->
                if (activityAvailable) {
                    Spacer(modifier = Modifier.height(4.dp))
                    McTextButton(
                        text = "Ver corrida",
                        onClick = { onActivityClick(activityId) },
                        trailingArrow = false,
                        modifier = Modifier.testTag("race-open-activity"),
                    )
                }
            }
        }
        completionMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = McTextStyles.caption,
                color = colors.textSecondary,
                modifier = Modifier.testTag("completion-message"),
            )
            McTextButton(
                text = "Entendi",
                onClick = onConsumeMessage,
                trailingArrow = false,
            )
        }
    }

    if (completeOpen) {
        CompleteRaceDialog(
            suggestions = suggestions,
            onDismiss = { completeOpen = false },
            onConfirm = { activityId ->
                onComplete(activityId)
                completeOpen = false
            },
        )
    }
}

@Composable
private fun CompleteRaceDialog(
    suggestions: List<ActivitySuggestionUi>,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    val colors = McTheme.colors
    var selectedActivityId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("race-complete-dialog"),
        title = { Text(text = "Concluir prova") },
        text = {
            Column {
                Text(
                    text = "A corrida é opcional. Se vincular, ela também entra em volume, carga e zonas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "CORRIDAS DE HOJE", style = McTextStyles.overline, color = colors.textTertiary)
                    suggestions.forEach { suggestion ->
                        val selected = suggestion.id == selectedActivityId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedActivityId = if (selected) null else suggestion.id
                                }
                                .testTag("race-activity-${suggestion.id}")
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${suggestion.title} · ${suggestion.distance}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "${suggestion.subtitle} · ${suggestion.duration}",
                                    style = McTextStyles.caption,
                                    color = colors.textSecondary,
                                )
                            }
                            Text(
                                text = if (selected) "Selecionada" else "Vincular",
                                style = McTextStyles.caption,
                                color = if (selected) colors.accentText else colors.textTertiary,
                            )
                        }
                        McDivider()
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (selectedActivityId == null) {
                        "Nenhuma corrida será vinculada."
                    } else {
                        "A corrida selecionada será vinculada à prova."
                    },
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        },
        confirmButton = {
            McTextButton(
                text = if (selectedActivityId == null) "Concluir sem corrida" else "Concluir com corrida",
                onClick = { onConfirm(selectedActivityId) },
                trailingArrow = false,
                modifier = Modifier.testTag("race-confirm"),
            )
        },
        dismissButton = {
            McTextButton(
                text = "Voltar",
                onClick = onDismiss,
                trailingArrow = false,
            )
        },
    )
}
