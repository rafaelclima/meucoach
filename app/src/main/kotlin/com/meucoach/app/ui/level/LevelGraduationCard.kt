package com.meucoach.app.ui.level

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.app.ui.profile.experienceLabel
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.prescription.ExplanationCatalog

/**
 * Convite de subida de nível no Perfil (ADR 0042 §3.3).
 *
 * A superfície mostra motivos em pt-BR, sem IDs; os `ruleIds`/`evidenceIds`
 * aparecem só no detalhe "Por quê?". Segue o padrão do card T4 de
 * replanejamento: aceitar, recusar ("Agora não") e suprimir até o fim do dia.
 */
@Composable
fun LevelGraduationSection(
    state: LevelGraduationUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onSuppress: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading && state.suggestion == null -> LevelGraduationLoading(modifier)

        state.suggestion != null -> LevelGraduationCard(
            state = state,
            onAccept = onAccept,
            onDecline = onDecline,
            onSuppress = onSuppress,
            modifier = modifier,
        )

        state.message != null -> LevelGraduationMessageCard(
            message = state.message,
            onDismiss = onDismissMessage,
            modifier = modifier,
        )

        state.nextLevel != null -> LevelGraduationNextCard(
            next = state.nextLevel,
            modifier = modifier,
        )
    }
}

@Composable
private fun LevelGraduationNextCard(next: LevelGraduationNextUi, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    McCard(
        variant = McCardVariant.INSET,
        modifier = modifier.testTag("level-next-card"),
    ) {
        Text(
            text = "PRÓXIMO NÍVEL",
            style = McTextStyles.overline,
            color = colors.textTertiary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = next.reason,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (next.missing.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Critérios que faltam",
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
            next.missing.forEach { criterion ->
                Text(
                    text = "• $criterion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
        next.availabilityHint?.let { hint ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                style = McTextStyles.caption,
                color = colors.textSecondary,
                modifier = Modifier.testTag("level-next-availability"),
            )
        }
    }
}

@Composable
private fun LevelGraduationLoading(modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    McCard(modifier = modifier.testTag("level-graduation-loading")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
fun LevelGraduationCard(
    state: LevelGraduationUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onSuppress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestion = state.suggestion ?: return
    val colors = McTheme.colors
    var showWhy by rememberSaveable { mutableStateOf(false) }
    McCard(modifier = modifier.testTag("level-graduation-card")) {
        Text(
            text = "PRONTO PARA SUBIR DE NÍVEL?",
            style = McTextStyles.overline,
            color = colors.textTertiary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "De ${experienceLabel(suggestion.from)} para ${experienceLabel(suggestion.to)}",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.testTag("level-graduation-transition"),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Você cumpriu os critérios das últimas 6 semanas. Confira os motivos:",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        suggestion.reasons.forEach { reason ->
            Text(
                text = "• $reason",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = "O treino de hoje não muda. Sessões já feitas também ficam intactas.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        state.message?.let { message ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = McTextStyles.caption,
                color = colors.warning,
                modifier = Modifier.testTag("level-graduation-message"),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        McTextButton(
            text = if (showWhy) "Ocultar o porquê" else "Por quê?",
            onClick = { showWhy = !showWhy },
            trailingArrow = false,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("level-graduation-why-toggle"),
        )
        if (showWhy) {
            Column(modifier = Modifier.testTag("level-graduation-why")) {
                suggestion.ruleIds.forEach { ruleId ->
                    val rule = ExplanationCatalog.rule(ruleId)
                    Text(
                        text = "${rule.id} · ${rule.title}",
                        style = McTextStyles.caption,
                        color = colors.textSecondary,
                    )
                }
                suggestion.evidenceIds.forEach { evidenceId ->
                    val evidence = ExplanationCatalog.evidence(evidenceId)
                    val label = evidence.source.ifBlank { evidence.title }.ifBlank { evidence.id }
                    Text(
                        text = "${evidence.id} · $label",
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        McPrimaryButton(
            text = if (state.isApplying) {
                "Atualizando…"
            } else {
                "Subir para ${experienceLabel(suggestion.to)}"
            },
            onClick = onAccept,
            enabled = !state.isApplying,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("level-graduation-accept"),
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
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("level-graduation-decline"),
            )
            McTextButton(
                text = "Não sugerir hoje",
                onClick = onSuppress,
                trailingArrow = false,
                enabled = !state.isApplying,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("level-graduation-suppress"),
            )
        }
        Text(
            text = levelConfidenceLabel(suggestion.confidence),
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
}

/** Resultado passivo de uma ação (aceite, supressão ou erro). */
@Composable
fun LevelGraduationMessageCard(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    McCard(
        variant = McCardVariant.INSET,
        modifier = modifier.testTag("level-graduation-message-card"),
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
                    .testTag("level-graduation-message"),
            )
            McTextButton(
                text = "Entendi",
                onClick = onDismiss,
                trailingArrow = false,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("level-graduation-dismiss-message"),
            )
        }
    }
}
