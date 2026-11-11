package com.meucoach.app.application

import com.meucoach.app.importer.RaceAutoCompletionStore
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.RaceEventOutcome
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.domain.race.RaceAutoCompletionDecision
import com.meucoach.core.domain.race.RaceAutoCompletionInput
import com.meucoach.core.domain.race.RaceAutoCompletionRejection
import com.meucoach.core.domain.race.RaceAutoCompletionRule
import com.meucoach.core.model.RaceCompletionSource
import com.meucoach.core.model.RaceStatus
import java.time.LocalDate
import javax.inject.Inject

/** What happened when a finished import was checked against the planned race. */
sealed interface RaceAutoCompletionResult {
    data class Completed(val eventId: String, val activityId: String) : RaceAutoCompletionResult

    data class Skipped(val reason: RaceAutoCompletionRejection) : RaceAutoCompletionResult

    data class NoPlannedEvent(val date: LocalDate) : RaceAutoCompletionResult

    data class Failed(val message: String) : RaceAutoCompletionResult
}

/**
 * Auto-completes the planned race when an imported run is compatible (ADR 0032).
 *
 * The decision lives in [RaceAutoCompletionRule] (pure domain). This use case
 * only gathers the persisted state, calls the existing transactional completion
 * and never creates a `SessionExecution`, so the linked run keeps counting as a
 * normal activity.
 */
class AutoCompleteRaceFromActivity @Inject constructor(
    private val raceEvents: RaceEventRepository,
    private val activities: ActivityRepository,
    private val executions: SessionExecutionRepository,
    private val declines: RaceAutoCompletionStore,
    private val timeProvider: TimeProvider,
) {
    suspend fun evaluate(activityId: String): RaceAutoCompletionResult {
        val zone = timeProvider.zoneId.value
        val activity = activities.getActivitySummary(activityId)
            ?: return RaceAutoCompletionResult.Skipped(RaceAutoCompletionRejection.ACTIVITY_NOT_FOUND)
        val localDate = activity.startTime.atZone(zone).toLocalDate()
        val event = raceEvents
            .getForDate(localDate)
            ?.takeIf { it.status == RaceStatus.PLANNED }
            ?: return RaceAutoCompletionResult.NoPlannedEvent(localDate)

        val dayStart = localDate.atStartOfDay(zone).toInstant()
        val dayEnd = localDate.plusDays(1).atStartOfDay(zone).toInstant()
        val activitiesOnDate = activities.listActivitySummariesBetween(dayStart, dayEnd)
        val sessionLinked = executions.listAll().mapNotNull { it.activityId }.toSet()
        val raceLinked = raceEvents.listAll().mapNotNull { it.activityId }.toSet()

        val decision = RaceAutoCompletionRule.evaluate(
            RaceAutoCompletionInput(
                event = event,
                activityId = activityId,
                activitiesOnDate = activitiesOnDate,
                zoneId = zone,
                sessionLinkedActivityIds = sessionLinked,
                raceLinkedActivityIds = raceLinked,
                declinedActivityIds = declines.declinedActivityIds(),
            ),
        )
        if (decision is RaceAutoCompletionDecision.Rejected) {
            return RaceAutoCompletionResult.Skipped(decision.reason)
        }
        require(decision is RaceAutoCompletionDecision.Eligible) { "Unexpected race auto-completion decision" }

        return when (
            val outcome = raceEvents.complete(
                id = decision.eventId,
                activityId = decision.activityId,
                source = RaceCompletionSource.AUTO_IMPORT,
            )
        ) {
            is RaceEventOutcome.Saved ->
                RaceAutoCompletionResult.Completed(outcome.event.id, decision.activityId)

            is RaceEventOutcome.Rejected -> RaceAutoCompletionResult.Failed(outcome.message)

            is RaceEventOutcome.ActiveEventExists ->
                RaceAutoCompletionResult.Failed("Já existe uma prova planejada ativa.")

            is RaceEventOutcome.ActivityAlreadyLinked -> RaceAutoCompletionResult.Failed(outcome.message)
        }
    }
}
