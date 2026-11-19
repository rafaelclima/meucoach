package com.meucoach.app.coach

import com.meucoach.app.application.TrainingSignalSource
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.coach.CoachGuard
import com.meucoach.core.domain.coach.CoachGuardInputs
import com.meucoach.core.domain.coach.CoachGuards
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

/**
 * Structural guards for the Coach tab (PRD evolução, F2).
 *
 * Read-only composition of the shared training signals, the persisted
 * profile, the current week and all race events. Observing the week means a
 * completion, edit or accepted replan re-emits the guards instead of leaving
 * the card frozen until the next day. Day-level blocks (check-in, sleep,
 * readiness) belong to the next-session card (F1) and are not repeated.
 */
data class CoachGuardsContext(val guards: List<CoachGuard>)

interface CoachGuardsSource {
    fun observeGuards(): Flow<CoachGuardsContext>
}

class RoomCoachGuardsSource @Inject constructor(
    private val profiles: ProfileRepository,
    private val plans: TrainingPlanRepository,
    private val raceEvents: RaceEventRepository,
    private val signals: TrainingSignalSource,
    private val coachSignals: CoachSignalsSource,
    private val time: TimeProvider,
    private val clock: Clock,
) : CoachGuardsSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeGuards(): Flow<CoachGuardsContext> = time.today
        .map { today -> today to today.weekStart() }
        .distinctUntilChanged()
        .flatMapLatest { (today, weekStart) ->
            combine(
                profiles.observeProfile(),
                plans.observeWeek(weekStart),
                raceEvents.observeAll(),
                coachSignals.observeSignals(),
            ) { profile, _, races, persisted ->
                if (profile == null) {
                    CoachGuardsContext(emptyList())
                } else {
                    val snapshot = signals.snapshot(persisted, profile, today, clock.instant())
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
