package com.meucoach.app.ui.common

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.race.RacePrediction
import com.meucoach.core.domain.race.RacePredictionResult
import com.meucoach.core.domain.race.RacePredictionUnavailable
import com.meucoach.core.model.DataConfidence

/**
 * VDOT/Riegel race prediction (ADR 0036).
 *
 * It is a reference next to the declared goal, never a prescribed pace or a
 * promise. The block says why a prediction is absent instead of inventing one.
 */
@Composable
internal fun RacePredictionBlock(result: RacePredictionResult, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    Column(modifier = modifier.testTag("race-prediction")) {
        Text(
            text = "PREVISÃO DE REFERÊNCIA",
            style = McTextStyles.overline,
            color = colors.textTertiary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(6.dp))
        when (result) {
            is RacePredictionResult.Available -> PredictionContent(result.prediction)

            is RacePredictionResult.Unavailable -> Text(
                text = unavailableLabel(result.reason),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.testTag("race-prediction-unavailable"),
            )
        }
    }
}

@Composable
private fun PredictionContent(prediction: RacePrediction) {
    val colors = McTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Tempo previsto",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(
            text = raceTargetTimeLabel(prediction.predictedSec).orEmpty(),
            style = McTextStyles.metricSmall,
            color = colors.textPrimary,
            modifier = Modifier.testTag("race-prediction-time"),
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Faixa estimada",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(
            text = "${raceTargetTimeLabel(prediction.rangeSec.first)}–" +
                raceTargetTimeLabel(prediction.rangeSec.last),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = colors.textPrimary,
            modifier = Modifier.testTag("race-prediction-range"),
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = confidenceLabel(prediction.confidence),
        style = McTextStyles.caption,
        color = colors.textTertiary,
    )
    Text(
        text = sourceLabel(prediction.confidence),
        style = McTextStyles.caption,
        color = colors.textTertiary,
    )
    if (prediction.usedRiegel) {
        Text(
            text = "A faixa também considera a checagem de Riegel.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Previsão de referência, não prescrição nem garantia.",
        style = McTextStyles.caption,
        color = colors.textTertiary,
    )
}

private fun confidenceLabel(confidence: DataConfidence): String = when (confidence) {
    DataConfidence.HIGH -> "Confiança alta"
    DataConfidence.MEDIUM -> "Confiança média"
    DataConfidence.LOW -> "Confiança baixa"
}

private fun sourceLabel(confidence: DataConfidence): String = when (confidence) {
    DataConfidence.HIGH -> "Baseada na sua prova ou teste recente."
    DataConfidence.MEDIUM -> "Estimativa pelos seus treinos; confirme com um teste de 30 min."
    DataConfidence.LOW -> "Sem origem de calibração confiável."
}

private fun unavailableLabel(reason: RacePredictionUnavailable): String = when (reason) {
    RacePredictionUnavailable.NO_VDOT ->
        "Sem previsão: calibre com uma prova, um teste de 30 min ou pelos treinos."

    RacePredictionUnavailable.STALE_CALIBRATION ->
        "Sem previsão: calibração vencida; recalibre para atualizar."

    RacePredictionUnavailable.LOW_CONFIDENCE ->
        "Sem previsão: recalibre para registrar a origem da calibração."
}
