package com.meucoach.app.application

import com.meucoach.core.domain.prescription.LevelGraduationEngine
import com.meucoach.core.domain.prescription.LevelGraduationResult
import javax.inject.Inject

sealed interface EvaluateLevelGraduationResult {
    /** Uma subida de nível que o usuário pode aceitar; nunca é aplicada sozinha. */
    data class Suggestion(val suggestion: LevelGraduationResult.SuggestUpgrade, val profileId: String) :
        EvaluateLevelGraduationResult

    /** O nível vigente segue valendo; o card não aparece. */
    data class Stay(val reason: String, val missing: List<String>) : EvaluateLevelGraduationResult

    /** Ainda não há perfil persistido para avaliar. */
    data class Unavailable(val reason: String) : EvaluateLevelGraduationResult
}

/** Ponto de avaliação injetável; a implementação real só coleta estado e delega ao motor puro. */
interface LevelGraduationEvaluator {
    suspend operator fun invoke(): EvaluateLevelGraduationResult
}

/**
 * Avaliação da graduação no app. Só coleta o estado persistido e delega a
 * decisão ao [LevelGraduationEngine] puro; nenhuma regra de treino é repetida
 * aqui e nenhum rebaixamento é proposto em nenhum caminho.
 */
class EvaluateLevelGraduation @Inject constructor(private val contexts: LevelGraduationContextBuilder) :
    LevelGraduationEvaluator {
    override suspend operator fun invoke(): EvaluateLevelGraduationResult {
        val input = contexts.build()
            ?: return EvaluateLevelGraduationResult.Unavailable(
                "Ainda não há um perfil para avaliar o nível.",
            )
        return when (val result = LevelGraduationEngine.evaluate(input)) {
            is LevelGraduationResult.SuggestUpgrade -> EvaluateLevelGraduationResult.Suggestion(
                suggestion = result,
                profileId = input.profile.id,
            )

            is LevelGraduationResult.Stay -> EvaluateLevelGraduationResult.Stay(
                reason = result.reason,
                missing = result.missing,
            )
        }
    }
}
