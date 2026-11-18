package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meucoach.app.ui.replan.ReplanUiState
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme

/**
 * Quick actions that open the shared replanning acceptance flow inside Coach
 * (PRD 3.3-A). The engine decides; this card only asks for an explicit review.
 */
@Composable
internal fun CoachReplanCard(
    state: ReplanUiState,
    onRequest: () -> Unit,
    onMissed: () -> Unit,
    onLessTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    val busy = state.isLoading || state.isApplying || state.isUndoing
    McCard(modifier = modifier.testTag("coach-replan-card")) {
        Text(
            text = "AJUSTAR A SEMANA",
            style = McTextStyles.overline,
            color = colors.textTertiary,
        )
        Text(
            text = "Se algo mudou, o Coach reorganiza só os próximos dias. " +
                "Nada muda sem você confirmar.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        McPrimaryButton(
            text = if (state.isLoading) "Avaliando…" else "Ajustar a semana",
            onClick = onRequest,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("coach-replan-request"),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            McSecondaryButton(
                text = "Perdi um treino",
                onClick = onMissed,
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .testTag("coach-replan-missed"),
            )
            McSecondaryButton(
                text = "Tenho menos tempo",
                onClick = onLessTime,
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .testTag("coach-replan-less-time"),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "A revisão cobre só os próximos dias; hoje e o que já passou ficam como estão.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
}
