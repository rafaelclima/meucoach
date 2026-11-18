package com.meucoach.app.replan

import com.meucoach.app.application.ApplyReplan
import com.meucoach.app.application.EvaluateReplan
import com.meucoach.app.application.UndoReplan
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** The three use cases behind one acceptance machine; grouped to keep wiring small. */
class ReplanOperations @Inject constructor(val evaluate: EvaluateReplan, val apply: ApplyReplan, val undo: UndoReplan)

/**
 * Binds one [ReplanController] to a screen scope. ViewModels own the scope
 * (`viewModelScope`); the controller itself is not a singleton, so Today and
 * Coach never overwrite each other's proposal.
 */
class ReplanControllerFactory @Inject constructor(
    private val operations: ReplanOperations,
    private val plans: TrainingPlanRepository,
    private val profiles: ProfileRepository,
    private val store: ReplanSuggestionStore,
    private val profileResults: ProfilePlanEvaluationStore,
    private val timeProvider: TimeProvider,
) {
    fun create(scope: CoroutineScope): ReplanController = ReplanController(
        operations = operations,
        plans = plans,
        profiles = profiles,
        store = store,
        profileResults = profileResults,
        timeProvider = timeProvider,
        scope = scope,
    )
}
