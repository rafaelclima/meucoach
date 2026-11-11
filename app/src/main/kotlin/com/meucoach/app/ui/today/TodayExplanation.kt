package com.meucoach.app.ui.today

import com.meucoach.app.ui.common.readinessLabelLowercase
import com.meucoach.core.domain.load.LoadDataState
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.ExplanationCatalog
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
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
    /** "Planejado → Hoje → por quê" in a single sentence when the plan had a session. */
    val planComparison: String? = null,
    /** Explains where the confidence comes from and why it does not change during the week. */
    val confidenceNote: String? = null,
)

internal fun buildExplanation(
    checkIn: DailyCheckIn?,
    inputs: ReadinessInputs,
    result: ReadinessResult,
    prescription: DailyPrescription,
    load: LoadSnapshot?,
): TodayExplanationUi {
    val used = buildList {
        if (checkIn != null) add("Check-in de hoje")
        if (inputs.sleep != null) add("Sono recente")
        if (inputs.hrv != null) add("Variabilidade cardíaca")
        if (inputs.restingHeartRate != null) add("FC de repouso")
        if (load?.dataState == LoadDataState.KNOWN) add("Carga das últimas semanas")
        if (prescription.targets.pace != null) add("Ritmo no limite calibrado")
        if (prescription.targets.heartRate != null) add("FC no limite calibrada")
        if ("R-PLAN-08" in prescription.ruleIds) add("Acesso a esteira")
        if (checkIn?.heatLevel == HeatLevel.HOT) add("Calor informado")
        if (prescription.plannedType != null) add("Plano da semana")
    }.distinct()
    val missing = buildList {
        result.missing.forEach { add(it.label()) }
        if (!prescription.isRest && prescription.targets.pace == null) {
            add("Ritmo no limite (sem calibração)")
        }
        if (!prescription.isRest && prescription.targets.heartRate == null) {
            add("FC no limite (sem calibração)")
        }
        when (load?.dataState) {
            LoadDataState.STALE -> add("Atividades recentes (fonte desatualizada)")
            LoadDataState.INSUFFICIENT, null -> add("Histórico de carga das últimas 4 semanas")
            LoadDataState.KNOWN -> Unit
        }
    }.distinct()

    return TodayExplanationUi(
        rationale = prescription.rationale,
        confidence = prescription.confidence,
        usedData = used,
        missingData = missing,
        rules = prescription.ruleIds.distinct().map { id ->
            val rule = ExplanationCatalog.rule(id)
            ExplanationRuleUi(id = rule.id, title = rule.title)
        },
        evidence = prescription.evidenceIds.distinct().map { id ->
            val item = ExplanationCatalog.evidence(id)
            ExplanationEvidenceUi(id = item.id, title = item.title, source = item.source)
        },
        planComparison = planComparison(prescription),
    )
}

private fun planComparison(prescription: DailyPrescription): String? {
    val plannedType = prescription.plannedType ?: return null
    val planned = "${prescription.plannedTitle ?: plannedType.label()} · " +
        "${prescription.plannedDurationMin?.let { "$it min" } ?: "—"}"
    val today = "${prescription.title} · " +
        "${prescription.prescribedDurationMin?.let { "$it min" } ?: "—"}"
    val verb = when (prescription.adjustment) {
        PrescriptionAdjustment.KEEP -> "mantido"
        PrescriptionAdjustment.REDUCE -> "reduzido"
        PrescriptionAdjustment.DOWNGRADE -> "rebaixado"
        PrescriptionAdjustment.REST -> "trocado por descanso"
        PrescriptionAdjustment.NO_PLAN -> "sem plano"
    }
    return "Planejado: $planned → Hoje: $today (plano $verb)."
}

private fun WorkoutType.label(): String = when (this) {
    WorkoutType.REST -> "descanso"
    WorkoutType.EASY -> "fácil"
    WorkoutType.LONG -> "longão"
    WorkoutType.QUALITY -> "treino forte"
}

/**
 * Explains a persisted weekly plan. The confidence is the readiness confidence captured when the
 * week was built, capped at medium; it is not recomputed as the week goes by.
 */
internal fun buildPlanExplanation(plan: WeeklyPlan): TodayExplanationUi {
    val used = buildList {
        add("Sua disponibilidade semanal")
        add("Sua prontidão quando a semana foi montada")
        if (plan.qualityCount >= 2) add("Histórico de conclusão da semana anterior")
        if ("R-PLAN-04" in plan.ruleIds || "R-PLAN-05" in plan.ruleIds) {
            add("Semanas anteriores do bloco")
        }
        if ("R-PLAN-08" in plan.ruleIds) add("Acesso a esteira")
    }.distinct()
    val missing = buildList {
        if (plan.confidence == DataConfidence.LOW) {
            add("Dados objetivos de recuperação (sono, FC de repouso ou HRV)")
        }
        if ("R-DATA-01" in plan.ruleIds && plan.sessions.isNotEmpty()) {
            add("Carga recente para liberar treino forte")
        }
    }.distinct()

    return TodayExplanationUi(
        rationale = plan.rationale,
        confidence = plan.confidence,
        usedData = used,
        missingData = missing,
        rules = plan.ruleIds.distinct().map { id ->
            val rule = ExplanationCatalog.rule(id)
            ExplanationRuleUi(id = rule.id, title = rule.title)
        },
        evidence = plan.evidenceIds.distinct().map { id ->
            val item = ExplanationCatalog.evidence(id)
            ExplanationEvidenceUi(id = item.id, title = item.title, source = item.source)
        },
        confidenceNote = planConfidenceNote(plan.confidence),
    )
}

private fun planConfidenceNote(confidence: DataConfidence): String = when (confidence) {
    DataConfidence.LOW ->
        "A confiança é calculada quando a semana é montada. Com dados parciais, o plano segue " +
            "conservador e não libera progressão. Ela não muda durante esta semana; novos dados " +
            "entram no plano da próxima."

    DataConfidence.MEDIUM ->
        "A confiança é calculada quando a semana é montada e fica no máximo média: a semana é uma " +
            "estimativa conservadora. Ela não muda durante esta semana; novos dados entram no plano " +
            "da próxima."

    DataConfidence.HIGH ->
        "A confiança é calculada quando a semana é montada. Ela não muda durante esta semana; " +
            "novos dados entram no plano da próxima."
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
    val z = hrv.zScore
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
