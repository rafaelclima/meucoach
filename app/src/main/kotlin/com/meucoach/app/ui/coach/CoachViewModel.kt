package com.meucoach.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.coach.CoachConsentStore
import com.meucoach.app.coach.CoachPlanSource
import com.meucoach.core.data.coach.CoachApi
import com.meucoach.core.data.coach.CoachKind
import com.meucoach.core.domain.coach.CoachPromptBuilder
import com.meucoach.core.domain.coach.CoachResponseValidator
import com.meucoach.core.domain.coach.CoachValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

sealed interface CoachExplanationState {
    data object Unavailable : CoachExplanationState

    data object Loading : CoachExplanationState

    data class Ready(val title: String, val body: String, val bullets: List<String>) : CoachExplanationState

    data class Fallback(val reason: String, val planRationale: String) : CoachExplanationState
}

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val consent: CoachConsentStore,
    private val plans: CoachPlanSource,
    private val api: CoachApi,
) : ViewModel() {
    private val _consented = MutableStateFlow(consent.isConsented())
    val consented: StateFlow<Boolean> = _consented

    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed

    private val _explanation =
        MutableStateFlow<CoachExplanationState>(CoachExplanationState.Unavailable)
    val explanation: StateFlow<CoachExplanationState> = _explanation

    private val retryTick = MutableStateFlow(0)
    private var lastHash: String? = null

    init {
        viewModelScope.launch {
            combine(_consented, plans.observeCurrent(), retryTick) { isConsented, context, tick ->
                Triple(isConsented, context, tick)
            }.collect { (isConsented, context, _) ->
                val plan = context?.plan
                if (!isConsented || plan == null) {
                    lastHash = null
                    _explanation.value = CoachExplanationState.Unavailable
                    return@collect
                }
                if (context.inputsHash == lastHash &&
                    _explanation.value is CoachExplanationState.Ready
                ) {
                    return@collect
                }
                _explanation.value = CoachExplanationState.Loading
                try {
                    val prompt = CoachPromptBuilder.build(plan, context.inputsHash)
                    val raw = api.explain(prompt, CoachKind.EXPLAIN)
                    val allowed = prompt.sessions.mapNotNull { it.durationMin }.toSet() +
                        prompt.weeklyMinutes +
                        listOfNotNull(prompt.executedMinutes)
                    when (val validated = CoachResponseValidator.validate(raw, allowed)) {
                        is CoachValidationResult.Valid -> {
                            lastHash = context.inputsHash
                            android.util.Log.d("Coach", "explain ok hash=${context.inputsHash}")
                            _explanation.value = CoachExplanationState.Ready(
                                title = validated.response.title,
                                body = validated.response.body,
                                bullets = validated.response.bullets,
                            )
                        }

                        is CoachValidationResult.Invalid -> {
                            android.util.Log.d("Coach", "explain rejected: ${validated.reason}")
                            _explanation.value = CoachExplanationState.Fallback(
                                reason = validated.reason,
                                planRationale = plan.rationale,
                            )
                        }
                    }
                } catch (e: IOException) {
                    android.util.Log.d("Coach", "explain io: ${e.message}")
                    _explanation.value = CoachExplanationState.Fallback(
                        reason = "Sem conexão com o Coach. Mostrando o resumo do motor.",
                        planRationale = plan.rationale,
                    )
                } catch (e: Exception) {
                    android.util.Log.d("Coach", "explain failed: ${e.message}")
                    _explanation.value = CoachExplanationState.Fallback(
                        reason = "O Coach não respondeu. Mostrando o resumo do motor.",
                        planRationale = plan.rationale,
                    )
                }
            }
        }
    }

    fun grant() {
        consent.grant()
        _consented.value = true
    }

    fun revoke() {
        consent.revoke()
        _consented.value = false
        _dismissed.value = false
    }

    fun dismiss() {
        _dismissed.value = true
    }

    fun retry() {
        lastHash = null
        retryTick.value += 1
    }
}
