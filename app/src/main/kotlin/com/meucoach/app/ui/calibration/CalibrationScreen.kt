package com.meucoach.app.ui.calibration

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.app.ui.common.DateMask
import com.meucoach.app.ui.common.DateVisualTransformation
import com.meucoach.app.ui.common.TargetTimeField
import com.meucoach.app.ui.common.convert
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McStat
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.calibration.CalibrationResult
import com.meucoach.core.domain.calibration.CalibrationTier
import com.meucoach.core.domain.calibration.CalibrationWarning
import com.meucoach.core.model.DataConfidence
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")

@Composable
fun CalibrationScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalibrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    CalibrationContent(
        state = state,
        onUpdate = viewModel::update,
        onCalculate = viewModel::calculate,
        onSave = viewModel::save,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun CalibrationContent(
    state: CalibrationUiState,
    onUpdate: ((CalibrationForm) -> CalibrationForm) -> Unit,
    onCalculate: () -> Unit,
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
                Text(text = "CALIBRAÇÃO", style = McTextStyles.overline, color = colors.textTertiary)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("calibration-list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Seu limite de esforço",
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Use uma prova ou um teste de 30 min recente. " +
                                "Sem dados válidos, o app não inventa um resultado.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                        if (state.recentEfforts > 0) {
                            Text(
                                text = "${state.recentEfforts} treinos recentes com FC e ritmo disponíveis.",
                                style = McTextStyles.caption,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
                state.prefilledFrom?.let { candidate ->
                    item {
                        McCard(
                            variant = McCardVariant.INSET,
                            modifier = Modifier.testTag("calibration-prefill"),
                        ) {
                            Text(
                                text = "Dados da corrida",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Preenchido com a corrida de " +
                                    "${candidate.date.format(DATE)} · " +
                                    "${CalibrationFormValidator.formatTime(candidate.durationSec)} · " +
                                    "${candidate.averageHeartRate} bpm de FC média.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Confirme se foi um esforço máximo e contínuo de 25 a 35 min. " +
                                    "Se foi um treino leve ou com pausas, não salve: o valor sairia subestimado.",
                                style = McTextStyles.caption,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
                item {
                    McCard {
                        CalibrationField(
                            label = "Distância",
                            value = state.form.distanceKm,
                            onValueChange = { value -> onUpdate { it.copy(distanceKm = value) } },
                            suffix = "km",
                            keyboardType = KeyboardType.Decimal,
                            error = state.errors.distance,
                        )
                        TargetTimeField(
                            label = "Tempo",
                            parts = state.form.timeParts,
                            unit = state.form.timeUnit,
                            onPartsChange = { parts -> onUpdate { it.copy(timeParts = parts) } },
                            onUnitChange = { unit ->
                                onUpdate {
                                    it.copy(
                                        timeParts = it.timeParts.convert(it.timeUnit, unit),
                                        timeUnit = unit,
                                    )
                                }
                            },
                            tagPrefix = "calibration-time",
                            error = state.errors.time,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        CalibrationField(
                            label = "Data",
                            value = state.form.date,
                            onValueChange = { value -> onUpdate { it.copy(date = DateMask.digits(value)) } },
                            placeholder = "dd/mm/aaaa",
                            keyboardType = KeyboardType.Number,
                            visualTransformation = DateVisualTransformation,
                            error = state.errors.date,
                            testTag = "calibration-date",
                        )
                        CalibrationField(
                            label = "FC média do esforço (opcional)",
                            value = state.form.averageHeartRate,
                            onValueChange = { value -> onUpdate { it.copy(averageHeartRate = value) } },
                            suffix = "bpm",
                            keyboardType = KeyboardType.Number,
                            error = state.errors.heartRate,
                        )
                    }
                }
                item {
                    McPrimaryButton(
                        text = "Calcular",
                        onClick = onCalculate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.result?.let { result ->
                    item { CalibrationResultCard(result) }
                    item {
                        val canSave = result.vdot != null || result.lthr != null || result.thresholdPace != null
                        if (canSave) {
                            McPrimaryButton(
                                text = if (state.isSaving) "Salvando..." else "Salvar no perfil",
                                onClick = onSave,
                                enabled = !state.isSaving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calibration-save"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationResultCard(result: CalibrationResult) {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.ELEVATED) {
        Text(
            text = tierLabel(result.tier),
            style = McTextStyles.overline,
            color = colors.accentText,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Resultado",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = confidenceLabel(result.confidence),
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            McStat(
                label = "Aptidão",
                value = result.vdot?.let { String.format(PT_BR, "%.1f", it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "FC no limite",
                value = result.lthr?.toString() ?: "—",
                unit = if (result.lthr != null) "bpm" else null,
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Ritmo",
                value = result.thresholdPace?.format() ?: "—",
                unit = if (result.thresholdPace != null) "/km" else null,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = result.recommendation,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        result.warnings.forEach { warning ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• ${warningLabel(warning)}",
                style = McTextStyles.caption,
                color = if (warning == CalibrationWarning.MAX_HEART_RATE_INCONSISTENT) {
                    colors.warning
                } else {
                    colors.textTertiary
                },
            )
        }
    }
}

@Composable
private fun CalibrationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Ascii,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    error: String? = null,
    testTag: String? = null,
) {
    val colors = McTheme.colors
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            label = { Text(text = label) },
            placeholder = if (placeholder != null) {
                { Text(text = placeholder) }
            } else {
                null
            },
            suffix = if (suffix != null) {
                { Text(text = suffix) }
            } else {
                null
            },
            singleLine = true,
            isError = error != null,
            visualTransformation = visualTransformation,
            supportingText = if (error != null) {
                { Text(text = error) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.borderStrong,
                focusedLabelColor = colors.accentText,
                unfocusedLabelColor = colors.textTertiary,
                cursorColor = colors.accent,
                errorBorderColor = colors.danger,
                errorLabelColor = colors.danger,
                errorSupportingTextColor = colors.danger,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
            ),
        )
    }
}

private fun tierLabel(tier: CalibrationTier): String = when (tier) {
    CalibrationTier.A -> "CALIBRAÇÃO POR PROVA RECENTE"
    CalibrationTier.B -> "CALIBRAÇÃO POR TREINOS RECENTES"
    CalibrationTier.C -> "BASE INICIAL"
}

private fun confidenceLabel(confidence: DataConfidence): String = when (confidence) {
    DataConfidence.HIGH -> "Confiança alta"
    DataConfidence.MEDIUM -> "Confiança média"
    DataConfidence.LOW -> "Confiança baixa"
}

private fun warningLabel(warning: CalibrationWarning): String = when (warning) {
    CalibrationWarning.MAX_HEART_RATE_INCONSISTENT ->
        "A FC máxima informada parece baixa para este esforço; priorize a FC no limite."

    CalibrationWarning.NO_RECENT_PERFORMANCE ->
        "Sem performance recente. Faça um teste de 30 min para calibrar."

    CalibrationWarning.NO_EFFORT_HEART_RATE ->
        "Sem FC média do esforço, a FC no limite não foi estimada."

    CalibrationWarning.PERFORMANCE_DURATION_OUT_OF_RANGE ->
        "A performance está fora da faixa válida (1–100 km, 8–180 min)."
}

private val PT_BR = Locale.forLanguageTag("pt-BR")
