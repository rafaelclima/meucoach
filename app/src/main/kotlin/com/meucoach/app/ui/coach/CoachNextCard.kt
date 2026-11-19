package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.prescription.QualityBlockReason

/**
 * Next relevant session at the top of the Coach tab (PRD evolução, F1).
 *
 * Display-only: every line derives from the persisted week, the latest
 * persisted prescription of the picked date or a race event on the date.
 * A future day without a decision yet shows the plan without deciding.
 */
@Composable
internal fun CoachNextCard(
    state: CoachNextUiState,
    onRetry: () -> Unit,
    onCheckIn: () -> Unit = {},
    onToday: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    when (state) {
        CoachNextUiState.Hidden -> Unit

        CoachNextUiState.Loading -> {
            McCard(modifier = modifier.testTag("coach-next-loading")) {
                Text(
                    text = "Consultando o próximo treino…",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }

        CoachNextUiState.Empty -> {
            McCard(modifier = modifier.testTag("coach-next-empty")) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Semana concluída.",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Os próximos treinos aparecem aqui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        is CoachNextUiState.Error -> {
            McCard(modifier = modifier.testTag("coach-next-error")) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
        }

        is CoachNextUiState.Ready -> {
            McCard(modifier = modifier.testTag("coach-next-card")) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "PRÓXIMO TREINO",
                        style = McTextStyles.overline,
                        color = colors.textTertiary,
                    )
                    Text(
                        text = CoachNextLabels.headline(state.context),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = CoachNextLabels.statusLine(state.context),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    val needsCheckIn = state.context.prescription?.qualityBlockReason ==
                        QualityBlockReason.SUBJECTIVE_MISSING
                    if (needsCheckIn) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            McPrimaryButton(
                                text = "Fazer check-in",
                                onClick = onCheckIn,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coach-next-checkin"),
                            )
                            McSecondaryButton(
                                text = "Ver no Hoje",
                                onClick = onToday,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coach-next-today"),
                            )
                        }
                    } else {
                        McSecondaryButton(
                            text = "Ver no Hoje",
                            onClick = onToday,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("coach-next-today"),
                        )
                    }
                    Text(
                        text = "O motor decide; a IA só reescreve.",
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}
