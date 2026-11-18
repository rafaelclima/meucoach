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
 * Temporary availability picker (IA Coach 3.3-B). It only offers future days of
 * the current week and only accepts fewer minutes than the persisted value; the
 * override is never saved in the profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessTimeSheet(days: List<LessTimeDayUi>, onReview: (LocalDate, Int) -> Unit, onDismiss: () -> Unit) {
    val colors = McTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedEpochDay by rememberSaveable { mutableStateOf(days.firstOrNull()?.date?.toEpochDay()) }
    var minutesText by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = days.firstOrNull { it.date.toEpochDay() == selectedEpochDay } ?: days.firstOrNull()
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
                text = "Escolha o dia e quanto tempo você tem. A mudança vale só nesta revisão — " +
                    "sua disponibilidade continua a mesma.",
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
                    FilterChip(
                        selected = selected?.date == day.date,
                        onClick = {
                            selectedEpochDay = day.date.toEpochDay()
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
                            selected = selected?.date == day.date,
                            borderColor = colors.borderSubtle,
                            selectedBorderColor = colors.accent,
                        ),
                    )
                }
                selected?.let { day ->
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { input ->
                            minutesText = input.filter(Char::isDigit).take(3)
                            error = null
                        },
                        label = { Text(text = "Minutos disponíveis") },
                        supportingText = {
                            Text(text = "Hoje você tem ${day.currentMinutes} min em ${formatDay(day.date)}.")
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
                            val minutes = minutesText.toIntOrNull()
                            if (minutes == null || minutes !in 1 until day.currentMinutes) {
                                error = "Informe um tempo entre 1 e ${day.currentMinutes - 1} min."
                            } else {
                                onReview(day.date, minutes)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("coach-less-time-review"),
                    )
                }
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
