package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.coach.CoachGuard

private const val VISIBLE_GUARDS = 4

/**
 * Structural guards below the next-session card (PRD evolução, F2).
 *
 * Display-only: every line derives from the shared signals the engine
 * already holds. A green week shows one quiet line; longer lists expand on
 * demand instead of pushing the actions down.
 */
@Composable
internal fun CoachGuardsCard(state: CoachGuardsUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    when (state) {
        CoachGuardsUiState.Hidden -> Unit

        CoachGuardsUiState.Loading -> {
            McCard(modifier = modifier.testTag("coach-guards-loading")) {
                Text(
                    text = "Consultando as travas da semana…",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }

        is CoachGuardsUiState.Error -> {
            McCard(modifier = modifier.testTag("coach-guards-error")) {
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
                        modifier = Modifier.testTag("coach-guards-retry"),
                    )
                }
            }
        }

        is CoachGuardsUiState.Ready -> {
            var expanded by rememberSaveable { mutableStateOf(false) }
            McCard(modifier = modifier.testTag("coach-guards-card")) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "TRAVAS DA SEMANA",
                        style = McTextStyles.overline,
                        color = colors.textTertiary,
                    )
                    if (state.context.guards.isEmpty()) {
                        Text(
                            text = "Nenhum bloqueio ativo — siga o plano.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    } else {
                        val visible = if (expanded) {
                            state.context.guards
                        } else {
                            state.context.guards.take(VISIBLE_GUARDS)
                        }
                        visible.forEachIndexed { index, guard ->
                            if (index > 0) McDivider()
                            GuardRow(guard = guard)
                        }
                        if (!expanded && state.context.guards.size > VISIBLE_GUARDS) {
                            McSecondaryButton(
                                text = "Ver mais (${state.context.guards.size - VISIBLE_GUARDS})",
                                onClick = { expanded = true },
                                modifier = Modifier.testTag("coach-guards-more"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardRow(guard: CoachGuard) {
    val colors = McTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = guard.title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        Text(
            text = guard.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}
