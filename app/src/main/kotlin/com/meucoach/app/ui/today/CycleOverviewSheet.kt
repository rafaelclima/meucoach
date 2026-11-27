package com.meucoach.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McConfidenceBadge
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McEmptyState
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.cycle.CycleDeload
import com.meucoach.core.domain.cycle.CycleMilestone
import com.meucoach.core.domain.cycle.CycleOverview
import com.meucoach.core.domain.cycle.CycleWeek
import com.meucoach.core.domain.cycle.CycleWeekState
import java.time.LocalDate

/** States the read-only cycle sheet can present (ADR 0072). */
sealed interface CycleOverviewSheetState {
    data object Loading : CycleOverviewSheetState

    /** No planned race, or a recovery window without a future race. */
    data class Empty(val recoveryUntil: LocalDate? = null) : CycleOverviewSheetState

    data class Failed(val message: String) : CycleOverviewSheetState

    data class Ready(val overview: CycleOverview) : CycleOverviewSheetState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleOverviewSheet(state: CycleOverviewSheetState, onDismiss: () -> Unit) {
    val colors = McTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        modifier = Modifier.testTag("cycle-sheet"),
    ) {
        CycleOverviewContent(
            state = state,
            onClose = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}

@Composable
fun CycleOverviewContent(state: CycleOverviewSheetState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            .testTag("cycle-content"),
    ) {
        when (state) {
            CycleOverviewSheetState.Loading -> LoadingContent()
            is CycleOverviewSheetState.Empty -> EmptyContent(state.recoveryUntil, onClose)
            is CycleOverviewSheetState.Failed -> FailedContent(state.message, onClose)
            is CycleOverviewSheetState.Ready -> ReadyContent(state.overview, onClose)
        }
    }
}

@Composable
private fun LoadingContent() {
    val colors = McTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
            .testTag("cycle-loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = colors.accent,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Montando o caminho até a prova…",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun EmptyContent(recoveryUntil: LocalDate?, onClose: () -> Unit) {
    val description = if (recoveryUntil != null) {
        "Recuperação até ${CycleOverviewFormat.fullDate(recoveryUntil)}. " +
            "Quando a próxima prova for marcada, o caminho aparece aqui."
    } else {
        "Marque uma prova no Perfil para ver o caminho até ela. Sem prova, o app não inventa etapas."
    }
    McEmptyState(
        icon = Icons.AutoMirrored.Outlined.DirectionsRun,
        title = "Sem prova planejada",
        description = description,
        modifier = Modifier.testTag("cycle-empty"),
        actionLabel = "Fechar",
        onAction = onClose,
    )
}

@Composable
private fun FailedContent(message: String, onClose: () -> Unit) {
    McEmptyState(
        icon = Icons.Outlined.WarningAmber,
        title = "Não foi possível montar o caminho agora",
        description = "$message O plano da semana continua valendo.",
        modifier = Modifier.testTag("cycle-error"),
        actionLabel = "Fechar",
        onAction = onClose,
    )
}

@Composable
private fun ReadyContent(overview: CycleOverview, onClose: () -> Unit) {
    val colors = McTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "CAMINHO ATÉ A PROVA",
            style = McTextStyles.overline,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = CycleOverviewFormat.raceHeadline(overview),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            modifier = Modifier.testTag("cycle-race-title"),
        )
        Spacer(modifier = Modifier.height(6.dp))
        McConfidenceBadge(confidence = overview.confidence)
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = CycleOverviewFormat.raceCountdown(overview),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
        modifier = Modifier.testTag("cycle-race-countdown"),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Planejado, sujeito a ajuste. O check-in do dia confirma ou reduz.",
        style = McTextStyles.caption,
        color = colors.textTertiary,
    )
    if (overview.staleRace) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "A prova mudou depois deste plano. Ajuste a semana para alinhar.",
            style = McTextStyles.caption,
            color = colors.warning,
            modifier = Modifier.testTag("cycle-stale-race"),
        )
    }
    if (overview.milestones.isNotEmpty()) {
        McDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text(
            text = "MARCOS",
            style = McTextStyles.overline,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        overview.milestones.forEach { milestone ->
            MilestoneRow(milestone)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    McDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text(
        text = "SEMANAS",
        style = McTextStyles.overline,
        color = colors.textTertiary,
    )
    Spacer(modifier = Modifier.height(4.dp))
    overview.weeks.forEachIndexed { index, week ->
        if (index > 0) {
            McDivider(modifier = Modifier.padding(vertical = 10.dp))
        }
        WeekRow(week)
    }
    McDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text(
        text = "Semanas futuras são intenção. O volume exato é definido quando cada semana chega; " +
            "o check-in do dia confirma ou reduz.",
        style = McTextStyles.caption,
        color = colors.textTertiary,
        modifier = Modifier.testTag("cycle-footer"),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Semanas futuras não contam como histórico.",
        style = McTextStyles.caption,
        color = colors.textTertiary,
    )
    Spacer(modifier = Modifier.height(12.dp))
    McTextButton(
        text = "Fechar",
        onClick = onClose,
        trailingArrow = false,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("cycle-close"),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MilestoneRow(milestone: CycleMilestone) {
    val colors = McTheme.colors
    val color = if (milestone.conditional) colors.warning else colors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cycle-milestone-${milestone.kind.name.lowercase()}"),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = CycleOverviewFormat.milestone(milestone),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                if (milestone.conditional) {
                    McPill(
                        text = "A confirmar",
                        containerColor = colors.warning.copy(alpha = 0.12f),
                        contentColor = colors.warning,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = CycleOverviewFormat.milestoneDescription(milestone),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekRow(week: CycleWeek) {
    val colors = McTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = CycleOverviewFormat.weekAccessibility(week)
            }
            .testTag("cycle-week-${week.weekStart}"),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = CycleOverviewFormat.weekRange(week.weekStart),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            val (container, content) = when {
                week.state == CycleWeekState.RACE || week.isCurrent -> colors.accentSoft to colors.accentText
                else -> colors.surfaceInset to colors.textTertiary
            }
            McPill(
                text = CycleOverviewFormat.weekState(week),
                containerColor = container,
                contentColor = content,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = CycleOverviewFormat.phase(week.phase),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = CycleOverviewFormat.volume(week.volume),
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = CycleOverviewFormat.strong(week),
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
            CycleOverviewFormat.minutes(week)?.let { minutes ->
                Text(
                    text = minutes,
                    style = McTextStyles.caption,
                    color = colors.textPrimary,
                )
            }
        }
        CycleOverviewFormat.deload(week.deload)?.let { label ->
            Spacer(modifier = Modifier.height(4.dp))
            McPill(
                text = label,
                containerColor = colors.warning.copy(alpha = 0.12f),
                contentColor = colors.warning,
            )
            val conditionWeeks = week.deloadConditionWeeks
            if (week.deload == CycleDeload.CONDITIONAL && conditionWeeks != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = CycleOverviewFormat.deloadCondition(conditionWeeks),
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }
        if (week.partialWeek) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "A semana começou hoje; o plano vale de hoje em diante.",
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
        }
    }
}
