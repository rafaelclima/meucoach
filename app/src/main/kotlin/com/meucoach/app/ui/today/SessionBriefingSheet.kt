package com.meucoach.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McConfidenceBadge
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McEmptyState
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.briefing.SessionBriefing
import com.meucoach.core.domain.prescription.HeartRateTarget
import com.meucoach.core.domain.prescription.PaceTarget
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WorkoutType
import java.util.Locale

/** States the briefing sheet can present without calculating any training rule. */
sealed interface SessionBriefingSheetState {
    data object Loading : SessionBriefingSheetState

    /** No persisted prescription for today yet. */
    data object Empty : SessionBriefingSheetState

    data class Failed(val message: String) : SessionBriefingSheetState

    data class Ready(val briefing: SessionBriefing) : SessionBriefingSheetState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionBriefingSheet(state: SessionBriefingSheetState, onDismiss: () -> Unit) {
    val colors = McTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        modifier = Modifier.testTag("briefing-sheet"),
    ) {
        SessionBriefingContent(
            state = state,
            onClose = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}

@Composable
fun SessionBriefingContent(state: SessionBriefingSheetState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            .testTag("briefing-content"),
    ) {
        when (state) {
            SessionBriefingSheetState.Loading -> LoadingContent()
            SessionBriefingSheetState.Empty -> EmptyContent(onClose)
            is SessionBriefingSheetState.Failed -> FailedContent(state.message, onClose)
            is SessionBriefingSheetState.Ready -> ReadyContent(state.briefing, onClose)
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
            .testTag("briefing-loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = colors.accent,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Preparando o briefing…",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun EmptyContent(onClose: () -> Unit) {
    McEmptyState(
        icon = Icons.AutoMirrored.Outlined.DirectionsRun,
        title = "Ainda não há prescrição para hoje",
        description = "Quando o treino do dia existir, o briefing mostra só o que foi prescrito.",
        modifier = Modifier.testTag("briefing-empty"),
        actionLabel = "Fechar",
        onAction = onClose,
    )
}

@Composable
private fun FailedContent(message: String, onClose: () -> Unit) {
    McEmptyState(
        icon = Icons.Outlined.WarningAmber,
        title = "Não foi possível mostrar o briefing",
        description = message,
        modifier = Modifier.testTag("briefing-error"),
        actionLabel = "Fechar",
        onAction = onClose,
    )
}

@Composable
private fun ReadyContent(briefing: SessionBriefing, onClose: () -> Unit) {
    val colors = McTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "COMO CORRER HOJE",
                style = McTextStyles.overline,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = briefing.title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                modifier = Modifier.testTag("briefing-title"),
            )
        }
        McConfidenceBadge(confidence = briefing.confidence)
    }
    Spacer(modifier = Modifier.height(18.dp))

    BriefingSection(title = "FOCO", tag = "briefing-focus") {
        Text(
            text = briefing.focusLine(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }

    if (!briefing.targets.isEmpty) {
        BriefingSection(title = "ALVO", tag = "briefing-target") {
            BriefingTargets(briefing.targets)
        }
    }

    if (briefing.steps.isNotEmpty()) {
        BriefingSection(title = "PASSO A PASSO", tag = "briefing-steps") {
            BriefingSteps(briefing.steps)
        }
    }

    BriefingSection(title = "POR QUE HOJE", tag = "briefing-why") {
        Text(
            text = briefing.rationale,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }

    BriefingSection(title = "COMO CORRER", tag = "briefing-how") {
        Text(
            text = briefing.howToRun,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }

    BriefingSection(title = "SE ESTIVER DIFÍCIL", tag = "briefing-if-hard") {
        Text(
            text = briefing.ifHard,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }

    FuelSections()

    McSecondaryButton(
        text = "Fechar",
        onClick = onClose,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("briefing-close"),
    )
}

@Composable
private fun BriefingTargets(targets: PrescriptionTargets) {
    val colors = McTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        orderedTargetRows(targets).forEach { row ->
            BriefingTargetRow(label = row.label, value = row.value, tag = row.tag)
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = primaryNote(targets),
        style = McTextStyles.caption,
        color = colors.textTertiary,
        modifier = Modifier.testTag("briefing-target-primary-note"),
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = "Números copiados da prescrição de hoje.",
        style = McTextStyles.caption,
        color = colors.textTertiary,
    )
}

private data class TargetRow(val label: String, val value: String, val tag: String)

/**
 * FR-410: the leading target renders first; the rest follows as reference.
 * Tags are unchanged so existing tests keep passing.
 */
private fun orderedTargetRows(targets: PrescriptionTargets): List<TargetRow> {
    val pace = targets.pace?.let { TargetRow("Ritmo", it.value(), "briefing-target-pace") }
    val heartRate =
        targets.heartRate?.let { TargetRow("FC-alvo", it.value(), "briefing-target-heart-rate") }
    val distance =
        targets.distanceKm?.let { TargetRow("Distância", distanceValue(it), "briefing-target-distance") }
    val zone = targets.zone?.let { TargetRow("Intensidade", it, "briefing-target-zone") }
    val rpe = targets.rpe?.let { TargetRow("Esforço", "$it/10", "briefing-target-rpe") }
    val leading = when (targets.primary) {
        TargetKind.HEART_RATE -> listOfNotNull(heartRate, rpe, pace)
        TargetKind.PACE -> listOfNotNull(pace, heartRate, rpe)
        TargetKind.MIXED -> listOfNotNull(heartRate, rpe, pace)
        TargetKind.RPE -> listOfNotNull(rpe, pace, heartRate)
    }
    return leading + listOfNotNull(distance, zone)
}

private fun primaryNote(targets: PrescriptionTargets): String = when (targets.primary) {
    TargetKind.HEART_RATE -> "Principal: FC e esforço; o ritmo é referência."

    TargetKind.PACE -> "Principal: ritmo do bloco; a FC confirma."

    TargetKind.MIXED -> "Principal: FC como teto; o esforço é o guia."

    TargetKind.RPE -> if (targets.pace != null) {
        "Principal: esforço percebido; o ritmo é referência."
    } else {
        "Principal: esforço percebido."
    }
}

@Composable
private fun BriefingSteps(steps: List<String>) {
    val colors = McTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.testTag("briefing-step-$index"),
            )
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = "Passos copiados da prescrição de hoje, sem tempo extra.",
        style = McTextStyles.caption,
        color = colors.textTertiary,
    )
}

@Composable
private fun BriefingTargetRow(label: String, value: String, tag: String) {
    val colors = McTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Text(
            text = value,
            style = McTextStyles.metricSmall,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun BriefingSection(title: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = McTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Text(
            text = title,
            style = McTextStyles.overline,
            color = colors.textTertiary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(5.dp))
        content()
        Spacer(modifier = Modifier.height(10.dp))
        McDivider()
        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * F4 educational content (ADR 0029): static approved copy, shown after the
 * prescription-derived sections. Not rendered for `Loading`/`Empty`/`Failed`
 * states and never part of the race-day branch (FR-404).
 */
@Composable
private fun FuelSections() {
    BriefingSection(title = BriefingFuelContent.HYDRATION_TITLE, tag = "briefing-hydration") {
        FuelLines()
    }
    BriefingSection(title = BriefingFuelContent.NUTRITION_TITLE, tag = "briefing-nutrition") {
        FuelLine(label = null, text = BriefingFuelContent.NUTRITION_REFERRAL)
    }
}

@Composable
private fun FuelLines() {
    val colors = McTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FuelLine(BriefingFuelContent.BEFORE_RUN_LABEL, BriefingFuelContent.BEFORE_RUN)
        FuelLine(BriefingFuelContent.DURING_LABEL, BriefingFuelContent.DURING)
        FuelLine(BriefingFuelContent.AFTER_LABEL, BriefingFuelContent.AFTER)
        Text(
            text = BriefingFuelContent.INDIVIDUAL_NOTE,
            style = McTextStyles.caption,
            color = colors.textTertiary,
            modifier = Modifier.testTag("briefing-hydration-note"),
        )
    }
}

@Composable
private fun FuelLine(label: String?, text: String) {
    val colors = McTheme.colors
    Column {
        label?.let {
            Text(
                text = it,
                style = McTextStyles.caption,
                color = colors.textPrimary,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

private fun SessionBriefing.focusLine(): String {
    if (isRest) return "Descanso"
    val type = workoutType.label()
    val duration = durationMin ?: return type
    return "$type · $duration min"
}

private fun WorkoutType.label(): String = when (this) {
    WorkoutType.REST -> "Descanso"
    WorkoutType.EASY -> "Fácil"
    WorkoutType.LONG -> "Longão"
    WorkoutType.QUALITY -> "Treino forte"
}

private fun PaceTarget.value(): String = "${fast.format()}–${slow.format()} /km"

private fun HeartRateTarget.value(): String = maxBpm?.let { "$minBpm–$it bpm" } ?: "$minBpm+ bpm"

private fun distanceValue(distanceKm: Double): String =
    "${String.format(Locale.forLanguageTag("pt-BR"), "%.1f", distanceKm)} km"
