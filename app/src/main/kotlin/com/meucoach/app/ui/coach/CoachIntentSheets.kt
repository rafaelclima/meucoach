package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meucoach.app.ui.replan.LessTimeChange
import com.meucoach.app.ui.replan.LessTimeDayUi
import com.meucoach.app.ui.replan.MissedSessionUi
import com.meucoach.app.ui.replan.formatDay
import com.meucoach.app.ui.replan.formatDayDate
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import java.time.LocalDate

/**
 * Lost-session picker (IA Coach 3.3-B). The list comes from the replanner's T1
 * eligibility; selecting a day evaluates that exact session and never reposts
 * its load.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MissedSessionPickerSheet(
    sessions: List<MissedSessionUi>,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = McTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        modifier = Modifier.testTag("coach-missed-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "PERDI UM TREINO",
                style = McTextStyles.overline,
                color = colors.textTertiary,
            )
            Text(
                text = "Escolha o treino que não aconteceu. O resto da semana é reorganizado " +
                    "sem repor a carga que ficou para trás.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            if (sessions.isEmpty()) {
                Text(
                    text = "Não encontrei treinos perdidos nesta semana.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.testTag("coach-missed-empty"),
                )
            } else {
                sessions.forEach { session ->
                    val duration = session.durationMin?.let { " · $it min" }.orEmpty()
                    McSecondaryButton(
                        text = "${formatDayDate(session.date)} · ${session.title}$duration",
                        onClick = { onSelect(session.date) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("coach-missed-${session.date}"),
                    )
                }
            }
            McTextButton(
                text = "Fechar",
                onClick = onDismiss,
                trailingArrow = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("coach-missed-close"),
            )
        }
    }
}

/**
 * Temporary availability picker (IA Coach 3.3-B). It offers future days of the
 * current week, accepts one or more days and only allows fewer minutes than the
 * persisted value; `0` removes the day. The override is never saved in the profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessTimeSheet(
    days: List<LessTimeDayUi>,
    onReview: (List<LessTimeChange>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = McTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedEpochDays by rememberSaveable {
        mutableStateOf(days.firstOrNull()?.date?.toEpochDay()?.toString().orEmpty())
    }
    var minutesText by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedEpochSet = selectedEpochDays
        .split(',')
        .mapNotNull(String::toLongOrNull)
        .toSet()
    val selected = days.filter { it.date.toEpochDay() in selectedEpochSet }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        modifier = Modifier.testTag("coach-less-time-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "TENHO MENOS TEMPO",
                style = McTextStyles.overline,
                color = colors.textTertiary,
            )
            Text(
                text = "Selecione um ou mais dias e quanto tempo você tem. Use 0 para não " +
                    "treinar no dia. A mudança vale só nesta revisão — sua disponibilidade " +
                    "continua a mesma.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            if (days.isEmpty()) {
                Text(
                    text = "Não há dias futuros nesta semana para reduzir o tempo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.testTag("coach-less-time-empty"),
                )
            } else {
                days.forEach { day ->
                    val isSelected = day.date.toEpochDay() in selectedEpochSet
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val epochDay = day.date.toEpochDay()
                            val next = if (isSelected) {
                                selectedEpochSet - epochDay
                            } else {
                                selectedEpochSet + epochDay
                            }
                            selectedEpochDays = next.sorted().joinToString(",")
                            error = null
                        },
                        label = { Text(text = "${formatDay(day.date)} · ${day.currentMinutes} min") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("coach-less-time-day-${day.date}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent,
                            selectedLabelColor = colors.onAccent,
                            containerColor = colors.surfaceInset,
                            labelColor = colors.textSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = colors.borderSubtle,
                            selectedBorderColor = colors.accent,
                        ),
                    )
                }
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { input ->
                        minutesText = input.filter(Char::isDigit).take(3)
                        error = null
                    },
                    label = { Text(text = "Minutos disponíveis em cada dia") },
                    supportingText = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (selected.isEmpty()) {
                                    "Nenhum dia selecionado."
                                } else {
                                    "Selecionados: " + selected.joinToString(" · ") {
                                        "${formatDay(it.date)} (${it.currentMinutes} min)"
                                    }
                                },
                            )
                            Text(text = "Use 0 para não treinar no dia selecionado.")
                        }
                    },
                    isError = error != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coach-less-time-minutes"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                )
                error?.let { message ->
                    Text(
                        text = message,
                        style = McTextStyles.caption,
                        color = colors.danger,
                        modifier = Modifier.testTag("coach-less-time-error"),
                    )
                }
                McPrimaryButton(
                    text = "Revisar semana",
                    onClick = {
                        val maxMinutes = selected.minOfOrNull { it.currentMinutes }?.minus(1)
                        val minutes = minutesText.toIntOrNull()
                        when {
                            selected.isEmpty() -> error = "Escolha pelo menos um dia."

                            maxMinutes == null || minutes == null || minutes !in 0..maxMinutes ->
                                error = "Informe um tempo entre 0 e ${maxMinutes ?: 0} min."

                            else -> onReview(
                                selected.map { LessTimeChange(date = it.date, minutes = minutes) },
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coach-less-time-review"),
                )
            }
            McTextButton(
                text = "Fechar",
                onClick = onDismiss,
                trailingArrow = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("coach-less-time-close"),
            )
        }
    }
}
