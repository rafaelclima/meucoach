package com.meucoach.app.coach

import com.meucoach.app.application.TrainingSignalSource
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.domain.coach.CoachGuard
import com.meucoach.core.domain.coach.CoachGuardInputs
import com.meucoach.core.domain.coach.CoachGuards
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

/**
 * Structural guards for the Coach tab (PRD evolução, F2).
 *
 * Read-only composition of the shared training signals, the persisted
 * profile and all race events. Day-level blocks (check-in, sleep,
 * readiness) belong to the next-session card (F1) and are not repeated.
 */
data class CoachGuardsContext(val guards: List<CoachGuard>)

interface CoachGuardsSource {
    fun observeGuards(): Flow<CoachGuardsContext>
}

class RoomCoachGuardsSource @Inject constructor(
    private val profiles: ProfileRepository,
    private val raceEvents: RaceEventRepository,
    private val signals: TrainingSignalSource,
    private val time: TimeProvider,
    private val clock: Clock,
) : CoachGuardsSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeGuards(): Flow<CoachGuardsContext> = combine(
        time.today,
        profiles.observeProfile(),
        raceEvents.observeAll(),
    ) { today, profile, races -> Triple(today, profile, races) }
        .flatMapLatest { (today, profile, races) ->
            if (profile == null) {
                flowOf(CoachGuardsContext(emptyList()))
            } else {
                flow { emit(signals.snapshot(profile, today, clock.instant())) }
                    .map { snapshot ->
                        CoachGuardsContext(
                            CoachGuards.resolve(
                                CoachGuardInputs(
                                    load = snapshot.load,
                                    races = races,
                                    hasAvailability = profile.weeklyAvailability.minutesByDay.isNotEmpty(),
                                    today = today,
                                ),
                            ),
                        )
                    }
            }
        }
}
