package com.meucoach.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.cycle.CycleOverview

/**
 * Compact entry point of the cycle sheet. It never calculates a training rule;
 * it only repeats the projection already produced by the domain (ADR 0072).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CycleSummaryRow(overview: CycleOverview, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val race = overview.race ?: return
    val colors = McTheme.colors
    val current = overview.weeks.firstOrNull { it.isCurrent }
    val phase = current?.phase
    val phaseLabel = if (overview.staleRace) "Prova atualizada" else CycleOverviewFormat.phase(phase)
    val countdown = when (race.weeksToRace) {
        0 -> "Prova esta semana"
        1 -> "Prova em 1 semana"
        else -> "Prova em ${race.weeksToRace} semanas"
    }
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$phaseLabel. $countdown. Ver caminho até a prova."
            }
            .testTag("cycle-summary"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        McPill(
            text = phaseLabel,
            containerColor = if (overview.staleRace) {
                colors.warning.copy(alpha = 0.12f)
            } else {
                colors.accentSoft
            },
            contentColor = if (overview.staleRace) colors.warning else colors.accentText,
        )
        Text(
            text = countdown,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        McTextButton(
            text = "Ver caminho",
            onClick = onClick,
            trailingArrow = false,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("cycle-summary-open"),
        )
    }
}
