package com.meucoach.app.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.HeatLevel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CheckInScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    CheckInContent(
        state = state,
        onUpdate = viewModel::update,
        onSave = viewModel::save,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun CheckInContent(
    state: CheckInUiState,
    onUpdate: ((CheckInForm) -> CheckInForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 20.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Voltar",
                        tint = colors.textPrimary,
                    )
                }
                Text(text = "CHECK-IN", style = McTextStyles.overline, color = colors.textTertiary)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("checkin-list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Como você está hoje?",
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Quatro respostas rápidas. Sem culpa e sem julgamento: " +
                                "o app usa isso para ajustar o dia.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                item {
                    McCard {
                        ScoreQuestion(
                            title = "Sono",
                            lowLabel = "Ruim",
                            highLabel = "Ótimo",
                            value = state.form.sleepQuality,
                            onSelect = { value -> onUpdate { it.copy(sleepQuality = value) } },
                        )
                        QuestionDivider()
                        ScoreQuestion(
                            title = "Fadiga",
                            lowLabel = "Nenhuma",
                            highLabel = "Muita",
                            value = state.form.fatigue,
                            onSelect = { value -> onUpdate { it.copy(fatigue = value) } },
                        )
                        QuestionDivider()
                        ScoreQuestion(
                            title = "Dor muscular",
                            lowLabel = "Nenhuma",
                            highLabel = "Muita",
                            value = state.form.soreness,
                            onSelect = { value -> onUpdate { it.copy(soreness = value) } },
                        )
                        QuestionDivider()
                        ScoreQuestion(
                            title = "Estresse",
                            lowLabel = "Baixo",
                            highLabel = "Alto",
                            value = state.form.stress,
                            onSelect = { value -> onUpdate { it.copy(stress = value) } },
                        )
                    }
                }
                item {
                    McCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Doente ou lesionado?",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Suspende treino forte.",
                                    style = McTextStyles.caption,
                                    color = colors.textSecondary,
                                )
                            }
                            Switch(
                                checked = state.form.illnessOrInjury,
                                onCheckedChange = { value ->
                                    onUpdate { it.copy(illnessOrInjury = value) }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onAccent,
                                    checkedTrackColor = colors.danger,
                                    uncheckedThumbColor = colors.textTertiary,
                                    uncheckedTrackColor = colors.surfaceInset,
                                ),
                            )
                        }
                    }
                }
                item {
                    McCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Está muito calor?",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Ajusta o ritmo alvo, sem mudar a FC.",
                                    style = McTextStyles.caption,
                                    color = colors.textSecondary,
                                )
                            }
                            Switch(
                                checked = state.form.heatLevel == HeatLevel.HOT,
                                onCheckedChange = { value ->
                                    onUpdate {
                                        it.copy(heatLevel = if (value) HeatLevel.HOT else HeatLevel.NONE)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.onAccent,
                                    checkedTrackColor = colors.warning,
                                    uncheckedThumbColor = colors.textTertiary,
                                    uncheckedTrackColor = colors.surfaceInset,
                                ),
                            )
                        }
                    }
                }
                if (state.history.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "ÚLTIMOS CHECK-INS",
                                style = McTextStyles.overline,
                                color = colors.textTertiary,
                            )
                            Text(
                                text = "Quanto menor, melhor. Esse índice alimenta a prontidão de cada dia.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    item { CheckInHistoryCard(state.history, state.date) }
                }
            }

            McPrimaryButton(
                text = when {
                    state.isSaving -> "Salvando..."
                    state.existing -> "Atualizar check-in"
                    else -> "Salvar check-in"
                },
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                    .testTag("checkin-save"),
            )
        }
    }
}

@Composable
private fun CheckInHistoryCard(history: List<DailyCheckIn>, today: LocalDate) {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.INSET) {
        history.forEachIndexed { index, entry ->
            if (index > 0) {
                McDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatCheckInDate(entry.date, today),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Sono ${entry.sleepQuality} · Fadiga ${entry.fatigue} · " +
                            "Dor ${entry.soreness} · Estresse ${entry.stress}",
                        style = McTextStyles.caption,
                        color = colors.textSecondary,
                    )
                    if (entry.illnessOrInjury || entry.heatLevel == HeatLevel.HOT) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (entry.illnessOrInjury) {
                                McPill(
                                    text = "Doente/lesão",
                                    containerColor = colors.danger.copy(alpha = 0.12f),
                                    contentColor = colors.danger,
                                )
                            }
                            if (entry.heatLevel == HeatLevel.HOT) {
                                McPill(
                                    text = "Calor",
                                    containerColor = colors.warning.copy(alpha = 0.12f),
                                    contentColor = colors.warning,
                                )
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${entry.hooperSum}/20",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Recuperação",
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreQuestion(title: String, lowLabel: String, highLabel: String, value: Int, onSelect: (Int) -> Unit) {
    val colors = McTheme.colors
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { score ->
                val selected = score == value
                Surface(
                    onClick = { onSelect(score) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) colors.accentSoft else colors.surfaceInset,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) colors.accent.copy(alpha = 0.5f) else colors.borderSubtle,
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$score",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) colors.accentText else colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = lowLabel, style = McTextStyles.caption, color = colors.textTertiary)
            Text(text = highLabel, style = McTextStyles.caption, color = colors.textTertiary)
        }
    }
}

@Composable
private fun QuestionDivider() {
    Spacer(modifier = Modifier.height(16.dp))
    McDivider()
    Spacer(modifier = Modifier.height(16.dp))
}

private fun formatCheckInDate(date: LocalDate, today: LocalDate): String =
    if (date == today) "Hoje" else date.format(HISTORY_DATE)

private val HISTORY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
