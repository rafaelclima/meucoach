package com.meucoach.app.replan

import com.meucoach.app.application.EvaluateReplanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the result of a save-triggered plan evaluation to the Profile screen.
 *
 * It is intentionally in-memory: a suggestion can always be rebuilt from the
 * persisted week/checkpoint, while a one-shot `NoChange` notice is not repeated
 * forever. The store carries presentation state, never a training decision.
 */
@Singleton
class ProfilePlanEvaluationStore @Inject constructor() {
    private val mutableResult = MutableStateFlow<EvaluateReplanResult?>(null)
    val result: StateFlow<EvaluateReplanResult?> = mutableResult.asStateFlow()

    fun publish(result: EvaluateReplanResult) {
        mutableResult.value = result
    }

    /** Returns the pending result once and clears it. */
    fun consume(): EvaluateReplanResult? {
        val current = mutableResult.value
        mutableResult.value = null
        return current
    }

    fun clear() {
        mutableResult.value = null
    }
}
