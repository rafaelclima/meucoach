package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme

private const val COLLAPSED_LINES = 5

/**
 * Current week explanation. The full AI text is useful but too long to dominate
 * the tab: it starts collapsed and only expands on request. The engine copy
 * stays the source of every number; the AI only rewrites it.
 */
@Composable
internal fun CoachExplanationCard(state: CoachExplanationState.Ready, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    McCard(modifier = modifier.testTag("coach-explanation-card")) {
        Text(
            text = "EXPLICAÇÃO DA SEMANA",
            style = McTextStyles.overline,
            color = colors.textTertiary,
        )
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(
            text = state.body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (result.hasVisualOverflow) hasOverflow = true
            },
        )
        if (expanded && state.bullets.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("coach-explanation-bullets"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.bullets.forEach { bullet ->
                    Text(
                        text = "• $bullet",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        if (hasOverflow || state.bullets.isNotEmpty()) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.testTag("coach-explanation-toggle"),
            ) {
                Text(
                    text = if (expanded) "Mostrar menos" else "Ler explicação completa",
                    color = colors.accentText,
                )
            }
        }
        Text(
            text = "A IA só reescreve o plano do motor; nenhum número nasce daqui.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
}
