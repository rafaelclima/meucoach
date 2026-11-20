package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.theme.McTheme

private const val VISIBLE_GUARDS = 2

/**
 * Structural guards as short coach bubbles (PRD evolução F2 + WhatsApp redesign).
 *
 * A green week says nothing: no empty card competing with the conversation.
 * When there is a guard, the coach names it in one bubble and expands the rest
 * only if the athlete asks.
 */
@Composable
internal fun CoachGuardsCard(state: CoachGuardsUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    when (state) {
        CoachGuardsUiState.Hidden -> Unit

        CoachGuardsUiState.Loading -> {
            CoachBubble(
                text = "Consultando as travas da semana…",
                modifier = modifier,
                testTag = "coach-guards-loading",
            )
        }

        is CoachGuardsUiState.Error -> {
            Column(
                modifier = modifier.testTag("coach-guards-error"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoachBubble(text = state.reason, testTag = "coach-guards-error-bubble")
                McSecondaryButton(
                    text = "Tentar de novo",
                    onClick = onRetry,
                    modifier = Modifier.testTag("coach-guards-retry"),
                )
            }
        }

        is CoachGuardsUiState.Ready -> {
            if (state.context.guards.isEmpty()) return
            var expanded by rememberSaveable { mutableStateOf(false) }
            val visible = if (expanded) state.context.guards else state.context.guards.take(VISIBLE_GUARDS)
            Column(
                modifier = modifier.testTag("coach-guards-card"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                visible.forEach { guard ->
                    CoachBubble(
                        text = "${guard.title} ${guard.detail}",
                        testTag = "coach-guard-bubble",
                    )
                }
                if (!expanded && state.context.guards.size > VISIBLE_GUARDS) {
                    TextButton(
                        onClick = { expanded = true },
                        modifier = Modifier.testTag("coach-guards-more"),
                    ) {
                        Text(
                            text = "Ver mais (${state.context.guards.size - VISIBLE_GUARDS})",
                            color = colors.accentText,
                        )
                    }
                }
            }
        }
    }
}
