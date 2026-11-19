package com.meucoach.app.coach

import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.coach.CoachNextResolver
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.model.RaceEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Next relevant session for the Coach tab (PRD evolução, F1).
 *
 * Read-only composition of the persisted week, the latest persisted
 * prescription of the picked date and a race event on that date. It never
 * computes or appends a prescription: a future day without a persisted row
 * surfaces with `prescription == null`, and the day authority stays in
 * `DailyPrescription`/`GetTodayDecision` (ADR 0023).
 */
data class CoachNextContext(
    val session: PlannedSession,
    val isToday: Boolean,
    val prescription: DailyPrescription?,
    val raceEvent: RaceEvent?,
)

interface CoachNextSource {
    fun observeNext(): Flow<CoachNextContext?>
}

class RoomCoachNextSource @Inject constructor(
    private val plans: TrainingPlanRepository,
    private val prescriptions: DailyPrescriptionRepository,
    private val raceEvents: RaceEventRepository,
    private val time: TimeProvider,
) : CoachNextSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeNext(): Flow<CoachNextContext?> = time.today
        .map { it to it.weekStart() }
        .distinctUntilChanged()
        .flatMapLatest { (today, weekStart) ->
            plans.observeWeek(weekStart).flatMapLatest { plan ->
                val next = plan?.let { CoachNextResolver.resolve(it.sessions, today) }
                if (next == null) {
                    flow { emit(null) }
                } else {
                    combine(
                        prescriptions.observeLatest(next.session.date),
                        flow { emit(raceEvents.getForDate(next.session.date)) },
                    ) { prescription, event ->
                        CoachNextContext(
                            session = next.session,
                            isToday = next.isToday,
                            prescription = prescription,
                            raceEvent = event,
                        )
                    }
                }
            }
        }
}
