package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meucoach.app.coach.CoachNextContext
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McSignalDot
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.prescription.QualityBlockReason
import com.meucoach.core.model.ReadinessLevel

/**
 * Next relevant event as a chat attachment (PRD evolução F1 + WhatsApp redesign).
 *
 * Display-only: the headline follows the persisted prescription when one
 * exists, a race needs no artificial session and readiness is shown only for
 * today. The attachment stays compact: the conversation carries the narrative,
 * the attachment carries the proven numbers and one action.
 */
@Composable
internal fun CoachNextCard(
    state: CoachNextUiState,
    onRetry: () -> Unit,
    onCheckIn: () -> Unit = {},
    onToday: () -> Unit = {},
    onProfile: () -> Unit = {},
    onCalibration: () -> Unit = {},
    onImport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    when (state) {
        CoachNextUiState.Hidden -> Unit

        CoachNextUiState.Loading -> {
            CoachAttachment(testTag = "coach-next-loading", modifier = modifier) {
                Text(
                    text = "Consultando o motor…",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }

        CoachNextUiState.Onboarding -> {
            CoachAttachment(testTag = "coach-next-onboarding", modifier = modifier) {
                Text(
                    text = "Monte seu plano para começar",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Complete perfil e disponibilidade, importe um treino ou faça a calibração. " +
                        "O próximo treino aparece aqui.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                McPrimaryButton(
                    text = "Abrir perfil",
                    onClick = onProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coach-onboarding-profile"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    McSecondaryButton(
                        text = "Calibrar",
                        onClick = onCalibration,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("coach-onboarding-calibration"),
                    )
                    McSecondaryButton(
                        text = "Importar",
                        onClick = onImport,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("coach-onboarding-import"),
                    )
                }
            }
        }

        CoachNextUiState.Empty -> {
            CoachAttachment(testTag = "coach-next-empty", modifier = modifier) {
                Text(
                    text = "Semana concluída.",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Os próximos treinos aparecem aqui.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }

        is CoachNextUiState.Error -> {
            CoachAttachment(testTag = "coach-next-error", modifier = modifier) {
                Text(
                    text = state.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                McSecondaryButton(
                    text = "Tentar de novo",
                    onClick = onRetry,
                    modifier = Modifier.testTag("coach-next-retry"),
                )
            }
        }

        is CoachNextUiState.Ready -> {
            ReadyCard(
                state = state,
                onCheckIn = onCheckIn,
                onToday = onToday,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ReadyCard(state: CoachNextUiState.Ready, onCheckIn: () -> Unit, onToday: () -> Unit, modifier: Modifier) {
    val colors = McTheme.colors
    val context = state.context
    CoachAttachment(testTag = "coach-next-card", modifier = modifier) {
        Text(
            text = CoachNextLabels.headline(context),
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        CoachNextLabels.plannedLine(context)?.let { planned ->
            Text(
                text = planned,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.testTag("coach-next-planned"),
            )
        }
        Text(
            text = CoachNextLabels.statusLine(context),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        val readiness = CoachNextLabels.readinessLine(context)
        if (readiness != null) {
            Row(
                modifier = Modifier.testTag("coach-next-readiness"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                McSignalDot(color = readinessColor(context))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = readiness,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        val needsCheckIn = context.isToday && (
            !context.hasCheckIn ||
                context.prescription?.qualityBlockReason == QualityBlockReason.SUBJECTIVE_MISSING
            )
        val actionLabel = when {
            context.subject is CoachNextSubject.Race -> "Ver no Hoje"
            context.isToday -> "Ver como correr hoje"
            else -> "Ver o plano"
        }
        if (needsCheckIn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                McPrimaryButton(
                    text = "Fazer check-in",
                    onClick = onCheckIn,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("coach-next-checkin"),
                )
                TextButton(
                    onClick = onToday,
                    modifier = Modifier.testTag("coach-next-today"),
                ) {
                    Text(text = actionLabel, color = colors.accentText)
                }
            }
        } else {
            TextButton(
                onClick = onToday,
                modifier = Modifier.testTag("coach-next-today"),
            ) {
                Text(text = actionLabel, color = colors.accentText)
            }
        }
    }
}

@Composable
private fun readinessColor(context: CoachNextContext) = when (
    CoachNextLabels.readinessLevel(context)
) {
    ReadinessLevel.GREEN -> McTheme.colors.success
    ReadinessLevel.AMBER -> McTheme.colors.warning
    ReadinessLevel.RED -> McTheme.colors.danger
    null -> McTheme.colors.textTertiary
}
