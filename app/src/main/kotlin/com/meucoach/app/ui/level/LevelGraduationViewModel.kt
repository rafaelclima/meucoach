package com.meucoach.app.ui.level

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

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
            try {
                present(evaluateGraduation(), timeProvider.currentDate())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                pending = null
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        suggestion = null,
                        message = "Não foi possível avaliar o nível agora. Tente de novo em instantes.",
                    )
                }
            } finally {
                evaluating = false
                mutableState.update { if (it.isLoading) it.copy(isLoading = false) else it }
            }
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
                        message = "Nível atualizado para $toLabel. " +
                            "O plano seguinte usa o novo nível; hoje nada muda.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
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
                        message = null,
                    )
                }
            }

            is EvaluateLevelGraduationResult.Stay,
            is EvaluateLevelGraduationResult.Unavailable,
            -> {
                pending = null
                mutableState.update {
                    it.copy(isLoading = false, suggestion = null, message = null)
                }
            }
        }
    }
}
