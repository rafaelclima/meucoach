package com.meucoach.app.coach

import com.meucoach.app.application.readinessResult
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.coach.CoachNextResolver
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.readiness.DailyMetricsReadinessBuilder
import com.meucoach.core.domain.readiness.ReadinessResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Next relevant event for the Coach tab (PRD evolução, F1).
 *
 * Read-only composition of the persisted week, the active race event and the
 * latest persisted prescription of the picked training date. Today also
 * carries the readiness computed from the persisted check-in and metrics; a
 * future day carries `readiness == null` instead of an invented semaphore.
 * It never computes or appends a prescription: a future day without a
 * persisted row surfaces with `prescription == null`, and the day authority
 * stays in `DailyPrescription`/`GetTodayDecision` (ADR 0023).
 */
data class CoachNextContext(
    val subject: CoachNextSubject?,
    val today: LocalDate,
    val hasPlan: Boolean,
    val hasProfile: Boolean,
    val prescription: DailyPrescription? = null,
    val readiness: ReadinessResult? = null,
    val hasCheckIn: Boolean = false,
    val sleepMinutes: Int? = null,
) {
    val isToday: Boolean get() = subject?.date == today

    /** True only when a persisted prescription exists for a training day. */
    val training: CoachNextSubject.Training? get() = subject as? CoachNextSubject.Training
}

interface CoachNextSource {
    fun observeNext(): Flow<CoachNextContext>
}

class RoomCoachNextSource @Inject constructor(
    private val plans: TrainingPlanRepository,
    private val prescriptions: DailyPrescriptionRepository,
    private val raceEvents: RaceEventRepository,
    private val profiles: ProfileRepository,
    private val checkIns: CheckInRepository,
    private val metrics: DailyMetricsRepository,
    private val time: TimeProvider,
) : CoachNextSource {
    private data class Draft(
        val today: LocalDate,
        val subject: CoachNextSubject?,
        val hasPlan: Boolean,
        val hasProfile: Boolean,
        val readiness: ReadinessResult,
        val hasCheckIn: Boolean,
        val sleepMinutes: Int?,
        val prescriptionDate: LocalDate?,
    ) {
        fun toContext(prescription: DailyPrescription?) = CoachNextContext(
            subject = subject,
            today = today,
            hasPlan = hasPlan,
            hasProfile = hasProfile,
            prescription = prescription,
            readiness = readiness,
            hasCheckIn = hasCheckIn,
            sleepMinutes = sleepMinutes,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeNext(): Flow<CoachNextContext> = time.today
        .map { it to it.weekStart() }
        .distinctUntilChanged()
        .flatMapLatest { (today, weekStart) ->
            combine(
                plans.observeWeek(weekStart),
                profiles.observeProfile(),
                checkIns.observe(today),
                metrics.observeAll(),
                raceEvents.observeAll(),
            ) { plan, profile, checkIn, dailyMetrics, races ->
                val next = CoachNextResolver.resolve(
                    sessions = plan?.sessions.orEmpty(),
                    today = today,
                    races = races,
                )
                val inputs = DailyMetricsReadinessBuilder.build(dailyMetrics, today)
                Draft(
                    today = today,
                    subject = next?.subject,
                    hasPlan = plan != null,
                    hasProfile = profile != null,
                    readiness = readinessResult(checkIn, dailyMetrics, today),
                    hasCheckIn = checkIn != null,
                    sleepMinutes = inputs.sleep?.durationMin,
                    prescriptionDate = (next?.subject as? CoachNextSubject.Training)?.session?.date,
                )
            }.flatMapLatest { draft ->
                val date = draft.prescriptionDate
                if (date == null) {
                    flowOf(draft.toContext(prescription = null))
                } else {
                    prescriptions.observeLatest(date).map { prescription ->
                        draft.toContext(prescription)
                    }
                }
            }
        }
}
