package com.meucoach.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.meucoach.app.ui.common.RaceDateVisualTransformation
import com.meucoach.app.ui.common.TargetTimeUnit
import com.meucoach.app.ui.common.convert
import com.meucoach.app.ui.common.formatRaceDateForInput
import com.meucoach.app.ui.common.maskLabel
import com.meucoach.app.ui.common.parseRaceDate
import com.meucoach.app.ui.common.raceDateLabel
import com.meucoach.app.ui.common.raceDistanceLabel
import com.meucoach.app.ui.common.raceStatusLabel
import com.meucoach.app.ui.common.raceTargetTimeLabel
import com.meucoach.app.ui.common.targetTimeParts
import com.meucoach.app.ui.common.totalSeconds
import com.meucoach.app.ui.common.unitLabel
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import java.time.LocalDate

/**
 * The explicit race surface of the profile (ADR 0030).
 *
 * `targetTimeSec` is shown as a declared goal only. The card never derives a
 * pace, range or prediction from it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RaceEventCard(
    events: List<RaceEvent>,
    activities: List<RaceActivityOption>,
    message: String?,
    onSave: (RaceDistance, LocalDate, Int?) -> Unit,
    onComplete: (String?) -> Unit,
    onCancel: () -> Unit,
    onNoShow: () -> Unit,
    onConsumeMessage: () -> Unit,
) {
    val colors = McTheme.colors
    val planned = events.firstOrNull { it.status == RaceStatus.PLANNED }
    val history = events.filter { it.status != RaceStatus.PLANNED }.sortedByDescending { it.targetDate }
    var formOpen by remember { mutableStateOf(false) }
    var completeOpen by remember { mutableStateOf(false) }

    McCard(variant = McCardVariant.INSET, modifier = Modifier.testTag("race-card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "PROVA", style = McTextStyles.overline, color = colors.textTertiary)
            planned?.let {
                McPill(
                    text = raceStatusLabel(it.status),
                    containerColor = colors.accentSoft,
                    contentColor = colors.accentText,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (planned == null) {
            Text(
                text = "Nenhuma prova planejada. Ao adicionar, o plano respeita o taper e não " +
                    "cria treino no dia da prova.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            McSecondaryButton(
                text = "Adicionar prova",
                onClick = { formOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("race-add"),
            )
        } else {
            Text(
                text = raceDistanceLabel(planned.distance),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            SummaryRow("Data", raceDateLabel(planned.targetDate))
            raceTargetTimeLabel(planned.targetTimeSec)?.let { target ->
                SummaryRow("Meta informada", target)
            }
            Spacer(modifier = Modifier.height(12.dp))
            McPrimaryButton(
                text = "Concluir prova",
                onClick = { completeOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("race-profile-complete"),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                McSecondaryButton(
                    text = "Editar",
                    onClick = { formOpen = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("race-edit"),
                )
                McSecondaryButton(
                    text = "Não compareceu",
                    onClick = onNoShow,
                    horizontalPadding = 10.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("race-profile-no-show"),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                McTextButton(
                    text = "Cancelar prova",
                    onClick = onCancel,
                    trailingArrow = false,
                    modifier = Modifier.testTag("race-profile-cancel"),
                )
            }
        }
        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            McDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "HISTÓRICO", style = McTextStyles.overline, color = colors.textTertiary)
            history.forEach { event ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${raceDistanceLabel(event.distance)} · ${raceDateLabel(event.targetDate)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        raceTargetTimeLabel(event.targetTimeSec)?.let { target ->
                            Text(
                                text = "Meta: $target",
                                style = McTextStyles.caption,
                                color = colors.textTertiary,
                            )
                        }
                    }
                    McPill(
                        text = raceStatusLabel(event.status),
                        containerColor = colors.surface,
                        contentColor = colors.textSecondary,
                    )
                }
            }
        }
        message?.let { text ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                style = McTextStyles.caption,
                color = colors.textSecondary,
                modifier = Modifier.testTag("race-message"),
            )
            McTextButton(
                text = "Entendi",
                onClick = onConsumeMessage,
                trailingArrow = false,
            )
        }
    }

    if (formOpen) {
        RaceEventFormDialog(
            initial = planned,
            onDismiss = { formOpen = false },
            onSave = { distance, date, time ->
                onSave(distance, date, time)
                formOpen = false
            },
        )
    }
    if (completeOpen) {
        CompleteRaceProfileDialog(
            activities = activities,
            onDismiss = { completeOpen = false },
            onConfirm = { activityId ->
                onComplete(activityId)
                completeOpen = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaceEventFormDialog(
    initial: RaceEvent?,
    onDismiss: () -> Unit,
    onSave: (RaceDistance, LocalDate, Int?) -> Unit,
) {
    val colors = McTheme.colors
    var distance by remember { mutableStateOf(initial?.distance ?: RaceDistance.TEN_K) }
    var dateValue by remember {
        mutableStateOf(TextFieldValue(initial?.targetDate?.let(::formatRaceDateForInput).orEmpty()))
    }
    var targetUnit by remember {
        mutableStateOf(
            if ((initial?.targetTimeSec ?: 0) >= 3_600) TargetTimeUnit.HOURS else TargetTimeUnit.MINUTES,
        )
    }
    var targetParts by remember { mutableStateOf(targetTimeParts(initial?.targetTimeSec, targetUnit)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("race-form"),
        title = { Text(text = if (initial == null) "Adicionar prova" else "Editar prova") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Distância",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RaceDistance.entries.forEach { option ->
                            FilterChip(
                                selected = option == distance,
                                onClick = { distance = option },
                                label = { Text(text = raceDistanceLabel(option)) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.surfaceInset,
                                    labelColor = colors.textSecondary,
                                    selectedContainerColor = colors.accentSoft,
                                    selectedLabelColor = colors.accentText,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = option == distance,
                                    borderColor = colors.borderSubtle,
                                    selectedBorderColor = colors.accent.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = dateValue,
                    onValueChange = { input ->
                        val digits = input.text.filter(Char::isDigit).take(8)
                        dateValue = input.copy(
                            text = digits,
                            selection = TextRange(
                                start = input.selection.start.coerceIn(0, digits.length),
                                end = input.selection.end.coerceIn(0, digits.length),
                            ),
                        )
                        error = null
                    },
                    visualTransformation = RaceDateVisualTransformation,
                    label = { Text(text = "Data (dd/mm/aaaa)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("race-date"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tempo alvo (opcional)",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TargetTimeUnit.entries.forEach { option ->
                            FilterChip(
                                selected = option == targetUnit,
                                onClick = {
                                    targetParts = targetParts.convert(targetUnit, option)
                                    targetUnit = option
                                    error = null
                                },
                                label = { Text(text = option.unitLabel()) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("race-time-unit-${option.name.lowercase()}"),
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.surfaceInset,
                                    labelColor = colors.textSecondary,
                                    selectedContainerColor = colors.accentSoft,
                                    selectedLabelColor = colors.accentText,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = option == targetUnit,
                                    borderColor = colors.borderSubtle,
                                    selectedBorderColor = colors.accent.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }
                    Text(
                        text = "Máscara: ${targetUnit.maskLabel()}",
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                        modifier = Modifier.testTag("race-time-mask"),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("race-time"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (targetUnit == TargetTimeUnit.HOURS) {
                            TimePartField(
                                label = "Horas",
                                value = targetParts.hours,
                                maxDigits = 2,
                                onValueChange = { value ->
                                    targetParts = targetParts.copy(hours = value)
                                    error = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("race-time-hours"),
                            )
                            Text(text = ":", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
                        }
                        TimePartField(
                            label = "Min",
                            value = targetParts.minutes,
                            maxDigits = if (targetUnit == TargetTimeUnit.MINUTES) 3 else 2,
                            onValueChange = { value ->
                                targetParts = targetParts.copy(minutes = value)
                                error = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("race-time-minutes"),
                        )
                        Text(text = ":", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
                        TimePartField(
                            label = "Seg",
                            value = targetParts.seconds,
                            maxDigits = 2,
                            onValueChange = { value ->
                                targetParts = targetParts.copy(seconds = value)
                                error = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("race-time-seconds"),
                        )
                    }
                }
                Text(
                    text = "A meta é só sua. O app não transforma isso em pace, faixa ou previsão.",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
                error?.let { text ->
                    Text(
                        text = text,
                        style = McTextStyles.caption,
                        color = colors.warning,
                        modifier = Modifier.testTag("race-form-error"),
                    )
                }
            }
        },
        confirmButton = {
            McTextButton(
                text = "Salvar",
                onClick = {
                    val date = parseRaceDate(dateValue.text)
                    if (date == null) {
                        error = "Informe uma data válida no formato dd/mm/aaaa."
                        return@McTextButton
                    }
                    val time = targetParts.totalSeconds(targetUnit)
                    if (!targetParts.isBlank && (time == null || time <= 0)) {
                        error = if (targetUnit == TargetTimeUnit.HOURS) {
                            "Use horas, minutos (0–59) e segundos (0–59), ou deixe vazio."
                        } else {
                            "Use minutos e segundos (0–59), ou deixe vazio."
                        }
                        return@McTextButton
                    }
                    onSave(distance, date, time)
                },
                trailingArrow = false,
                modifier = Modifier.testTag("race-save"),
            )
        },
        dismissButton = {
            McTextButton(
                text = "Cancelar",
                onClick = onDismiss,
                trailingArrow = false,
            )
        },
    )
}

@Composable
private fun TimePartField(
    label: String,
    value: String,
    maxDigits: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(maxDigits)) },
        label = { Text(text = label) },
        placeholder = { Text(text = "00") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
        ),
    )
}

@Composable
private fun CompleteRaceProfileDialog(
    activities: List<RaceActivityOption>,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    val colors = McTheme.colors
    var selected by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("race-profile-complete-dialog"),
        title = { Text(text = "Concluir prova") },
        text = {
            Column {
                Text(
                    text = "A corrida é opcional. Se vincular, ela também conta como atividade normal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                if (activities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "CORRIDAS DO DIA", style = McTextStyles.overline, color = colors.textTertiary)
                    activities.forEach { option ->
                        val isSelected = option.id == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = if (isSelected) null else option.id }
                                .testTag("race-profile-activity-${option.id}")
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = option.subtitle,
                                    style = McTextStyles.caption,
                                    color = colors.textSecondary,
                                )
                            }
                            Text(
                                text = if (isSelected) "Selecionada" else "Vincular",
                                style = McTextStyles.caption,
                                color = if (isSelected) colors.accentText else colors.textTertiary,
                            )
                        }
                        McDivider()
                    }
                }
            }
        },
        confirmButton = {
            McTextButton(
                text = if (selected == null) "Concluir sem corrida" else "Concluir com corrida",
                onClick = { onConfirm(selected) },
                trailingArrow = false,
                modifier = Modifier.testTag("race-profile-confirm"),
            )
        },
        dismissButton = {
            McTextButton(
                text = "Voltar",
                onClick = onDismiss,
                trailingArrow = false,
            )
        },
    )
}
