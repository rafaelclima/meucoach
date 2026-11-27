package com.meucoach.app.ui.level

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.application.EvaluateLevelGraduationResult
import com.meucoach.app.application.EvaluateReplan
import com.meucoach.app.application.EvaluateReplanResult
import com.meucoach.app.application.LevelGraduationEvaluator
import com.meucoach.app.level.LevelGraduationSuggestionStore
import com.meucoach.app.replan.ProfilePlanEvaluationStore
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.ui.profile.experienceLabel
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.domain.prescription.LevelGraduationEngine
import com.meucoach.core.domain.prescription.LevelGraduationResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Experience
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "LevelGraduation"

/**
 * Oferece a subida de nível no Perfil com aceite explícito (padrão T4).
 *
 * A sugestão automática respeita a supressão do dia e a recusa por proposta
 * (`profile.id` + versão do motor + `inputsHash`); um novo hash volta a
 * exibir. O aceite persiste o novo `experience` e reavalia o plano por
 * `EvaluateReplan` sem aplicar nada sozinho. Downgrade nunca é proposto nem
 * aplicado em nenhum caminho: o motor só sugere subida e o aceite exige que
 * o destino seja estritamente acima do nível vigente.
 */
@HiltViewModel
class LevelGraduationViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val evaluateGraduation: LevelGraduationEvaluator,
    private val evaluateReplan: EvaluateReplan,
    private val planEvaluations: ProfilePlanEvaluationStore,
    private val store: LevelGraduationSuggestionStore,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LevelGraduationUiState())
    val uiState: StateFlow<LevelGraduationUiState> = mutableState.asStateFlow()

    private var pending: EvaluateLevelGraduationResult.Suggestion? = null
    private var evaluating = false

    /** Chamado quando o Perfil fica visível; nunca aplica nada sozinho. */
    fun refresh() {
        if (mutableState.value.isApplying || evaluating) return
        evaluating = true
        mutableState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val today = timeProvider.currentDate()
            val result = try {
                evaluateGraduation()
            } catch (error: CancellationException) {
                evaluating = false
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao avaliar a graduação de nível", error)
                pending = null
                evaluating = false
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        suggestion = null,
                        nextLevel = null,
                        message = "Não foi possível avaliar o nível agora. Tente de novo em instantes.",
                    )
                }
                return@launch
            }
            // Libera o guarda antes de publicar: `isLoading = false` é o sinal
            // público de "pode pedir de novo" e não pode chegar antes dele,
            // senão o refresh disparado ao ver o card é descartado em silêncio.
            evaluating = false
            present(result, today)
        }
    }

    fun accept() {
        val suggestion = pending ?: return
        if (mutableState.value.isApplying) return
        mutableState.update { it.copy(isApplying = true) }
        viewModelScope.launch {
            val today = timeProvider.currentDate()
            try {
                val profile = profiles.getProfile()
                if (profile == null || profile.id != suggestion.profileId) {
                    pending = null
                    present(evaluateGraduation(), today)
                    mutableState.update {
                        it.copy(
                            isApplying = false,
                            message = "Os dados mudaram enquanto você decidia. Confira a avaliação atual.",
                        )
                    }
                    return@launch
                }
                if (profile.experience != suggestion.suggestion.from ||
                    suggestion.suggestion.to.ordinal <= suggestion.suggestion.from.ordinal
                ) {
                    pending = null
                    mutableState.update {
                        it.copy(isApplying = false, suggestion = null, message = null)
                    }
                    return@launch
                }
                profiles.saveProfile(profile.copy(experience = suggestion.suggestion.to))
                store.clearDecline(profile.id)
                store.clearSuppression(today)
                runCatching { evaluateReplan(explicit = false) }
                    .onSuccess { evaluation ->
                        when (evaluation) {
                            is EvaluateReplanResult.Unavailable -> planEvaluations.clear()
                            else -> planEvaluations.publish(evaluation)
                        }
                    }
                pending = null
                val toLabel = experienceLabel(suggestion.suggestion.to)
                mutableState.update {
                    it.copy(
                        isApplying = false,
                        suggestion = null,
                        nextLevel = null,
                        message = "Nível atualizado para $toLabel. " +
                            "O plano seguinte usa o novo nível; hoje nada muda.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Falha ao aplicar a graduação de nível", error)
                mutableState.update {
                    it.copy(
                        isApplying = false,
                        message = "Não foi possível atualizar o nível agora. Tente de novo em instantes.",
                    )
                }
            }
        }
    }

    /** Recusa esta proposta exata; um novo `inputsHash` pode sugerir de novo. */
    fun decline() {
        val suggestion = pending ?: return
        store.decline(
            suggestion.profileId,
            LevelGraduationEngine.ENGINE_VERSION,
            suggestion.suggestion.inputsHash,
        )
        pending = null
        mutableState.update { it.copy(suggestion = null, message = null) }
    }

    /** Esconde a sugestão automática até o fim do dia; o card reaparece amanhã. */
    fun suppress() {
        val today = timeProvider.currentDate()
        store.suppress(today)
        pending = null
        mutableState.update {
            it.copy(
                suggestion = null,
                message = "Tudo bem. Não vou sugerir de novo hoje.",
            )
        }
    }

    fun consumeMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private suspend fun present(result: EvaluateLevelGraduationResult, today: LocalDate) {
        when (result) {
            is EvaluateLevelGraduationResult.Suggestion -> {
                val key = LevelGraduationSuggestionStore.suggestionKey(
                    LevelGraduationEngine.ENGINE_VERSION,
                    result.suggestion.inputsHash,
                )
                val blocked = store.isSuppressed(today) ||
                    store.declinedInputsHash(result.profileId) == key
                pending = result
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        suggestion = if (blocked) null else result.suggestion.toUi(),
                        nextLevel = null,
                        message = null,
                    )
                }
            }

            is EvaluateLevelGraduationResult.Stay -> {
                pending = null
                val profile = runCatching { profiles.getProfile() }.getOrNull()
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        suggestion = null,
                        nextLevel = result.toNextUi(profile),
                        message = null,
                    )
                }
            }

            is EvaluateLevelGraduationResult.Unavailable -> {
                pending = null
                mutableState.update {
                    it.copy(isLoading = false, suggestion = null, nextLevel = null, message = null)
                }
            }
        }
    }
}

private fun EvaluateLevelGraduationResult.Stay.toNextUi(profile: AthleteProfile?): LevelGraduationNextUi? {
    if (profile?.experience == Experience.ADVANCED) return null
    val maxAvailability = profile?.weeklyAvailability?.minutesByDay?.values?.maxOrNull() ?: 0
    val hint = when {
        LevelGraduationEngine.MISSING_LONG_RUN in missing && maxAvailability in 1 until 60 ->
            "Sua agenda máxima é $maxAvailability min. Libere um dia de 60 min ou registre um " +
                "longão real de 60 min por conta própria para cumprir este critério."

        LevelGraduationEngine.MISSING_LONG_RUN_90 in missing && maxAvailability in 1 until 90 ->
            "Sua agenda máxima é $maxAvailability min. Libere um dia de 90 min ou registre um " +
                "longão real de 90 min por conta própria para cumprir este critério."

        else -> null
    }
    return LevelGraduationNextUi(
        reason = reason,
        missing = missing.map(::missingCriterionLabel),
        availabilityHint = hint,
    )
}

private fun missingCriterionLabel(key: String): String = when (key) {
    LevelGraduationEngine.MISSING_STRONG_WEEKS ->
        "6 semanas com pelo menos 80% dos treinos confirmados por uma corrida"

    LevelGraduationEngine.MISSING_EFFORTS -> "3 esforços de 20–35 min com FC e ritmo"

    LevelGraduationEngine.MISSING_CALIBRATION -> "Calibração recente válida"

    LevelGraduationEngine.MISSING_ILLNESS -> "14 dias sem doença ou lesão"

    LevelGraduationEngine.MISSING_READINESS -> "Prontidão atual não está baixa"

    LevelGraduationEngine.MISSING_LONG_RUN -> "Longão real de pelo menos 60 min"

    LevelGraduationEngine.MISSING_CTL -> "Carga das últimas 4 semanas estável ou subindo"

    LevelGraduationEngine.MISSING_LONG_RUN_90 -> "Longão real de pelo menos 90 min"

    LevelGraduationEngine.MISSING_RACE -> "Prova recente confirmada por uma corrida"

    LevelGraduationEngine.MISSING_MONOTONY -> "Variedade acima de 2,0 nas últimas 4 semanas"

    else -> key
}
