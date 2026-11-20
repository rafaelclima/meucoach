package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.meucoach.core.designsystem.theme.McTheme

/**
 * Current week explanation as a chat sequence (PRD_COACH_CHAT F3-bolhas).
 *
 * The validated AI text is split into short bubbles; bullets (plan details)
 * stay behind an explicit "Ver detalhes" toggle. No number is created here.
 */
@Composable
internal fun CoachExplanationCard(state: CoachExplanationState.Ready, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val bubbles = CoachBubbles.split(state.title) + CoachBubbles.split(state.body)

    Column(
        modifier = modifier.testTag("coach-explanation-card"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoachBubbleSequence(texts = bubbles, testTag = "coach-explanation-bubble")
        if (state.bullets.isNotEmpty()) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.testTag("coach-explanation-toggle"),
            ) {
                Text(
                    text = if (expanded) "Ocultar detalhes" else "Ver detalhes",
                    color = colors.accentText,
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.testTag("coach-explanation-bullets"),
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
        }
    }
}
