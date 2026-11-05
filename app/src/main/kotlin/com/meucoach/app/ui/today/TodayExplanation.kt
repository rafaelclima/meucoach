package com.meucoach.app.ui.today

import com.meucoach.app.ui.common.readinessLabelLowercase
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.ExplanationCatalog
import com.meucoach.core.domain.prescription.WorkoutRecommendation
import com.meucoach.core.domain.readiness.HrvMetrics
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessInputs
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.domain.readiness.RestingHeartRateMetrics
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.HeatLevel
import com.meucoach.core.model.ReadinessLevel
import kotlin.math.ln
import kotlin.math.roundToInt

data class ExplanationRuleUi(val id: String, val title: String)

data class ExplanationEvidenceUi(val id: String, val title: String, val source: String)

data class TodayExplanationUi(
    val rationale: String,
    val confidence: DataConfidence,
    val usedData: List<String>,
    val missingData: List<String>,
    val rules: List<ExplanationRuleUi>,
    val evidence: List<ExplanationEvidenceUi>,
)

internal fun buildExplanation(
    profile: AthleteProfile,
    checkIn: DailyCheckIn?,
    inputs: ReadinessInputs,
    result: ReadinessResult,
    recommendation: WorkoutRecommendation,
    load: LoadSnapshot?,
): TodayExplanationUi {
    val used = buildList {
        if (checkIn != null) add("Check-in de hoje")
        if (inputs.sleep != null) add("Sono recente")
        if (inputs.hrv != null) add("Variabilidade cardíaca")
        if (inputs.restingHeartRate != null) add("FC de repouso")
        if (load?.acwr != null) add("Carga das últimas semanas")
        if (profile.thresholdPace != null) add("Ritmo no limite calibrado")
        if (profile.lactateThresholdHeartRate != null) add("FC no limite calibrada")
        if ("R-PLAN-08" in recommendation.ruleIds) add("Acesso a esteira")
        if (checkIn?.heatLevel == HeatLevel.HOT) add("Calor informado")
    }.distinct()
    val missing = buildList {
        result.missing.forEach { add(it.label()) }
        if (recommendation.pace == null) add("Ritmo no limite (sem calibração)")
        if (recommendation.heartRate == null) add("FC no limite (sem calibração)")
        if (load?.acwr == null) add("Histórico de carga das últimas 4 semanas")
    }.distinct()

    return TodayExplanationUi(
        rationale = recommendation.rationale,
        confidence = recommendation.confidence,
        usedData = used,
        missingData = missing,
        rules = recommendation.ruleIds.distinct().map { id ->
            val rule = ExplanationCatalog.rule(id)
            ExplanationRuleUi(id = rule.id, title = rule.title)
        },
        evidence = recommendation.evidenceIds.distinct().map { id ->
            val item = ExplanationCatalog.evidence(id)
            ExplanationEvidenceUi(id = item.id, title = item.title, source = item.source)
        },
    )
}

private fun ReadinessComponent.label(): String = when (this) {
    ReadinessComponent.SUBJECTIVE -> "Check-in de hoje"
    ReadinessComponent.SLEEP -> "Sono"
    ReadinessComponent.HRV -> "Variabilidade cardíaca"
    ReadinessComponent.RESTING_HEART_RATE -> "FC de repouso"
}

internal fun buildReadinessExplanation(
    checkIn: DailyCheckIn?,
    inputs: ReadinessInputs,
    result: ReadinessResult,
): TodayExplanationUi {
    val used = buildList {
        if (checkIn != null) add("Check-in de hoje")
        if (inputs.sleep != null) add("Sono recente")
        if (inputs.hrv != null) add("Variabilidade cardíaca")
        if (inputs.restingHeartRate != null) add("FC de repouso")
    }.distinct()
    val missing = result.missing.map { it.label() }.distinct()
    val rules = buildList {
        add("R-READY-01")
        if (result.missing.isNotEmpty()) add("R-DATA-01")
        if (result.level != ReadinessLevel.GREEN) add("R-READY-03")
        if (ReadinessFlag.SHORT_SLEEP in result.flags) add("R-READY-04")
        if (ReadinessFlag.ILLNESS_OR_INJURY in result.flags) add("R-SAFE-01")
    }.distinct()
    val evidence = buildList {
        if (checkIn != null) add("EV-SAW-2016")
        if (inputs.sleep != null) add("EV-WALSH-2021")
        if (inputs.hrv != null) add("EV-PLEWS-2013")
        if (inputs.restingHeartRate != null) add("EV-BUCHHEIT-2014")
    }.distinct()

    return TodayExplanationUi(
        rationale = readinessRationale(result, inputs, checkIn),
        confidence = result.confidence,
        usedData = used,
        missingData = missing,
        rules = rules.map { id ->
            val rule = ExplanationCatalog.rule(id)
            ExplanationRuleUi(id = rule.id, title = rule.title)
        },
        evidence = evidence.map { id ->
            val item = ExplanationCatalog.evidence(id)
            ExplanationEvidenceUi(id = item.id, title = item.title, source = item.source)
        },
    )
}

private fun readinessRationale(result: ReadinessResult, inputs: ReadinessInputs, checkIn: DailyCheckIn?): String {
    val level = result.level.label()
    val confidence = result.confidence.label()
    val drivers = buildList {
        inputs.sleep?.let { add("sono de ${formatSleepDuration(it.durationMin)}") }
        inputs.hrv?.let { add(hrvLabel(it)) }
        inputs.restingHeartRate?.let { add(restingHeartRateLabel(it)) }
        checkIn?.let { add("check-in ${it.hooperSum}/20") }
    }
    if (drivers.isEmpty()) {
        return "Ainda não há dados de recuperação. A prontidão fica em ${result.score} " +
            "($level) com confiança $confidence até você registrar sono, variabilidade, " +
            "FC de repouso ou um check-in."
    }

    val base = "Sua prontidão está $level (${result.score}/100), com confiança $confidence: " +
        drivers.joinToString(", ") + "."
    val alerts = buildList {
        if (ReadinessFlag.SHORT_SLEEP in result.flags) {
            add("Sono abaixo de 6 h bloqueia treino forte.")
        }
        if (ReadinessFlag.ILLNESS_OR_INJURY in result.flags) {
            add("Sinal de doença ou lesão suspende o treino forte.")
        }
        if (ReadinessFlag.RESTING_HEART_RATE_ELEVATED in result.flags) {
            add("FC de repouso elevada por 2 dias pede atenção.")
        }
    }
    return (listOf(base) + alerts).joinToString(" ")
}

private fun hrvLabel(hrv: HrvMetrics): String {
    val z = ln(hrv.rmssd7dMs / hrv.baseline60dMs) / hrv.sdLnMs
    val position = when {
        z < -1.0 -> "abaixo da média"
        z > 0.0 -> "acima da média"
        else -> "na média"
    }
    return "variabilidade $position em ${hrv.validNights} noites"
}

private fun restingHeartRateLabel(metrics: RestingHeartRateMetrics): String {
    val delta = (metrics.average7dBpm - metrics.baseline30dBpm).roundToInt()
    return when {
        delta >= 7 -> "FC de repouso +$delta bpm acima da média"
        delta <= -3 -> "FC de repouso $delta bpm abaixo da média"
        else -> "FC de repouso estável"
    }
}

private fun ReadinessLevel.label(): String = readinessLabelLowercase(this)

private fun DataConfidence.label(): String = when (this) {
    DataConfidence.HIGH -> "alta"
    DataConfidence.MEDIUM -> "média"
    DataConfidence.LOW -> "baixa"
}

internal fun formatSleepDuration(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours}h${rest.toString().padStart(2, '0')}" else "${rest}min"
}
