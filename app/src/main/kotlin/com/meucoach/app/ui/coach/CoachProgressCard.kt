package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme

/**
 * Four-week rollup below the review card (PRD evolução, F4 / F3-bolhas).
 *
 * The chat shows one synthesis bubble (adherence); the aggregates live behind
 * "Ver números". Display-only: no grades, streaks or promises.
 */
@Composable
internal fun CoachProgressCard(
    state: CoachProgressUiState,
    onRetry: () -> Unit,
    onTrends: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.testTag("coach-progress-card"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            CoachProgressUiState.Hidden -> Unit

            CoachProgressUiState.Loading -> {
                Text(
                    text = "Consultando sua evolução…",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.testTag("coach-progress-loading"),
                )
            }

            is CoachProgressUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coach-progress-error"),
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

            is CoachProgressUiState.Ready -> {
                if (state.summary.availableWeeks == 0) {
                    CoachBubble(
                        text = "Pouco histórico ainda. Importe treinos e monte o plano para ver sua evolução.",
                        testTag = "coach-progress-bubble",
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
                    CoachBubble(
                        text = CoachProgressLabels.adherenceLine(state.summary),
                        testTag = "coach-progress-bubble",
                    )
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.testTag("coach-progress-numbers-toggle"),
                    ) {
                        Text(
                            text = if (expanded) "Ocultar números" else "Ver números",
                            color = colors.accentText,
                        )
                    }
                    if (expanded) {
                        Column(
                            modifier = Modifier.testTag("coach-progress-numbers"),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
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
                    }
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

@Composable
private fun ProgressRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = McTheme.colors.textSecondary,
    )
}
