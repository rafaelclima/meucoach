package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme

/**
 * Four-week rollup below the review card (PRD evolução, F4).
 *
 * Display-only: every line derives from closed weeks the shared assembler
 * already consolidated. No grades, streaks or promises — the quiet archive
 * of what the athlete actually did.
 */
@Composable
internal fun CoachProgressCard(
    state: CoachProgressUiState,
    onRetry: () -> Unit,
    onTrends: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    when (state) {
        CoachProgressUiState.Hidden -> Unit

        CoachProgressUiState.Loading -> {
            McCard(modifier = modifier.testTag("coach-progress-loading")) {
                Text(
                    text = "Consultando sua evolução…",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }

        is CoachProgressUiState.Error -> {
            McCard(modifier = modifier.testTag("coach-progress-error")) {
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
                        modifier = Modifier.testTag("coach-progress-retry"),
                    )
                }
            }
        }

        is CoachProgressUiState.Ready -> {
            McCard(modifier = modifier.testTag("coach-progress-card")) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Sua evolução",
                        style = McTextStyles.overline,
                        color = colors.textTertiary,
                    )
                    if (state.summary.availableWeeks == 0) {
                        Text(
                            text = "Pouco histórico ainda.",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Importe treinos e monte o plano para ver sua evolução.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    } else {
                        CoachProgressLabels.coverageNote(
                            state.summary.availableWeeks,
                            state.totalWeeks,
                        )?.let { note ->
                            Text(
                                text = note,
                                style = McTextStyles.caption,
                                color = colors.textTertiary,
                            )
                        }
                        ProgressRow(text = CoachProgressLabels.adherenceLine(state.summary))
                        ProgressRow(text = CoachProgressLabels.volumeLine(state.summary))
                        CoachProgressLabels.extraLine(state.summary)?.let { extra ->
                            ProgressRow(text = extra)
                        }
                        McDivider()
                        ProgressRow(text = CoachProgressLabels.intensityLine(state.summary))
                        ProgressRow(text = CoachProgressLabels.readinessLine(state.summary))
                        ProgressRow(text = CoachProgressLabels.sleepLine(state.summary))
                        ProgressRow(
                            text = CoachProgressLabels.vdotLine(state.profile, state.today),
                        )
                    }
                    McSecondaryButton(
                        text = "Ver Tendências",
                        onClick = onTrends,
                        modifier = Modifier.testTag("coach-progress-trends"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = McTheme.colors.textSecondary,
    )
}
