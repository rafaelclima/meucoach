package com.meucoach.app.ui.level

import com.meucoach.core.domain.prescription.LevelGraduationResult
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience

/** Tudo que o Perfil precisa para oferecer a subida de nível com aceite explícito. */
data class LevelGraduationUiState(
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val suggestion: LevelGraduationSuggestionUi? = null,
    val message: String? = null,
    /** Compact "next level" state when the engine is on `Stay` (D7). */
    val nextLevel: LevelGraduationNextUi? = null,
)

/** Criteria still missing for the next level, in pt-BR and without internal ids. */
data class LevelGraduationNextUi(val reason: String, val missing: List<String>, val availabilityHint: String? = null)

data class LevelGraduationSuggestionUi(
    val from: Experience,
    val to: Experience,
    val reasons: List<String>,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
    val inputsHash: String,
)

internal fun LevelGraduationResult.SuggestUpgrade.toUi(): LevelGraduationSuggestionUi = LevelGraduationSuggestionUi(
    from = from,
    to = to,
    reasons = reasons,
    ruleIds = ruleIds,
    evidenceIds = evidenceIds,
    confidence = confidence,
    inputsHash = inputsHash,
)

internal fun levelConfidenceLabel(confidence: DataConfidence): String = when (confidence) {
    DataConfidence.HIGH -> "Confiança alta na sugestão"
    DataConfidence.MEDIUM -> "Confiança média na sugestão"
    DataConfidence.LOW -> "Confiança baixa na sugestão"
}
