package com.meucoach.app.reminder

import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps an already-posted workout reminder aligned with the day authority.
 *
 * The 07:00 worker resolves the day once; a later check-in, load update, rest,
 * completion or race can change the day. This observer only rewrites or
 * cancels a notification that is still in the shade — it never creates one,
 * never alerts again and never resurrects a dismissed one.
 */
@Singleton
class WorkoutReminderSync @Inject constructor(
    private val prescriptions: DailyPrescriptionRepository,
    private val plans: TrainingPlanRepository,
    private val races: RaceEventRepository,
    private val time: TimeProvider,
    private val notifier: ReminderNotifier,
) {
    private data class DayState(
        val prescription: DailyPrescription?,
        val session: PlannedSession?,
        val raceToday: Boolean,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            time.today
                .flatMapLatest { date ->
                    val weekStart = date.weekStart()
                    combine(
                        prescriptions.observeLatest(date),
                        plans.observeWeek(weekStart),
                        races.observeAll(),
                    ) { prescription, plan, raceEvents ->
                        DayState(
                            prescription = prescription?.takeIf { it.date == date },
                            session = plan?.sessions?.firstOrNull { it.date == date },
                            raceToday = raceEvents.any { it.targetDate == date },
                        )
                    }
                }
                .distinctUntilChanged()
                .collect { state ->
                    if (state.raceToday) {
                        notifier.cancelWorkout()
                        return@collect
                    }
                    if (state.session?.status == SessionStatus.DONE || state.session?.status == SessionStatus.CLOSED) {
                        notifier.cancelWorkout()
                        return@collect
                    }
                    val prescription = state.prescription ?: return@collect
                    val message = ReminderContent.workoutForPrescription(prescription)
                    if (message == null) {
                        notifier.cancelWorkout()
                    } else {
                        notifier.updateIfActive(message)
                    }
                }
        }
    }
}
