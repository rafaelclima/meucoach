package com.meucoach.app.ui.today

import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.ExplanationCatalog
import com.meucoach.core.domain.prescription.WorkoutRecommendation
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessInputs
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.HeatLevel

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
        if (inputs.hrv != null) add("Variabilidade cardíaca (HRV)")
        if (inputs.restingHeartRate != null) add("FC de repouso")
        if (load?.acwr != null) add("Carga recente (ACWR)")
        if (profile.thresholdPace != null) add("Pace de limiar calibrado")
        if (profile.lactateThresholdHeartRate != null) add("LTHR calibrado")
        if ("R-PLAN-08" in recommendation.ruleIds) add("Acesso a esteira")
        if (checkIn?.heatLevel == HeatLevel.HOT) add("Calor informado")
    }.distinct()
    val missing = buildList {
        result.missing.forEach { add(it.label()) }
        if (recommendation.pace == null) add("Pace de limiar (sem calibração)")
        if (recommendation.heartRate == null) add("LTHR (sem calibração)")
        if (load?.acwr == null) add("Histórico de carga para ACWR")
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
    ReadinessComponent.HRV -> "HRV"
    ReadinessComponent.RESTING_HEART_RATE -> "FC de repouso"
}
