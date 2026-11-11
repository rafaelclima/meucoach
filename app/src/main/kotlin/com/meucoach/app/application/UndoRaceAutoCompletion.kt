package com.meucoach.app.application

import com.meucoach.app.importer.RaceAutoCompletionStore
import com.meucoach.core.data.repository.RaceEventOutcome
import com.meucoach.core.data.repository.RaceEventRepository
import javax.inject.Inject

/**
 * Reverts an inferred race completion and remembers the refusal (ADR 0032/D3).
 *
 * Explicit completions are not touched here; [RaceEventRepository.reopenAutoCompleted]
 * rejects anything that is not `AUTO_IMPORT`. The declined activity id is stored
 * only after a successful reopen, so a blocked attempt can be retried.
 */
class UndoRaceAutoCompletion @Inject constructor(
    private val raceEvents: RaceEventRepository,
    private val declines: RaceAutoCompletionStore,
) {
    suspend operator fun invoke(eventId: String): RaceEventOutcome {
        val event = raceEvents.getById(eventId)
            ?: return RaceEventOutcome.Rejected("Prova não encontrada.")
        val outcome = raceEvents.reopenAutoCompleted(eventId)
        if (outcome is RaceEventOutcome.Saved) {
            event.activityId?.let(declines::decline)
        }
        return outcome
    }
}
