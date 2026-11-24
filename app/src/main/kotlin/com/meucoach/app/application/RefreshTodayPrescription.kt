package com.meucoach.app.application

import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.CompletedRace
import com.meucoach.core.domain.prescription.RecoveryWindow
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.today.TodayDecision
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.HeatLevel
import com.meucoach.core.model.RaceEvent
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** One day authority read: the persisted week and the resolved decision. */
data class TodayAuthority(val today: LocalDate, val plan: WeeklyPlan?, val decision: TodayDecision?)

/**
 * Re-resolves today's prescription from the persisted signals (ADR 0023).
 *
 * Every surface that can change the day (check-in, load sync, import,
 * completion, replan) funnels here, so the notification, the Coach and Hoje
 * read the same authority instead of the weekly plan intent. It writes only
 * through [GetTodayDecision] (append-only, deduped by inputs hash) and never
 * invents a decision without a profile and a stored week.
 */
class RefreshTodayPrescription @Inject constructor(
    private val profiles: ProfileRepository,
    private val plans: TrainingPlanRepository,
    private val raceEvents: RaceEventRepository,
    private val signals: TrainingSignalSource,
    private val getTodayDecision: GetTodayDecision,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(): TodayAuthority? {
        val profile = profiles.getProfile() ?: return null
        val today = time.currentDate()
        val plan = plans.getWeek(today.weekStart())
        val races = raceEvents.listAll()
        if (plan == null) {
            val event = races.firstOrNull { it.targetDate == today }
            return TodayAuthority(
                today = today,
                plan = null,
                decision = event?.let(TodayDecision::Competition),
            )
        }
        return resolve(
            today = today,
            profile = profile,
            plan = plan,
            races = races,
            snapshot = signals.snapshot(profile, today, time.now()),
        )
    }

    /** Reuses one already-observed emission so a watcher does not read again. */
    suspend fun resolve(
        today: LocalDate,
        profile: AthleteProfile,
        plan: WeeklyPlan?,
        races: List<RaceEvent>,
        persisted: PersistedTrainingSignals,
        checkedAt: Instant,
    ): TodayAuthority = resolve(
        today = today,
        profile = profile,
        plan = plan,
        races = races,
        snapshot = signals.snapshot(persisted, profile, today, checkedAt),
    )

    private suspend fun resolve(
        today: LocalDate,
        profile: AthleteProfile,
        plan: WeeklyPlan?,
        races: List<RaceEvent>,
        snapshot: TrainingSignals,
    ): TodayAuthority {
        val weekStart = today.weekStart()
        val recovery = RecoveryWindow.covering(
            races = races.mapNotNull(CompletedRace::from),
            start = weekStart,
            end = weekStart.plusDays(6),
        )
        val decision = getTodayDecision(
            TodayDecisionRequest(
                date = today,
                profile = profile,
                readiness = snapshot.readiness,
                load = snapshot.load,
                heatLevel = snapshot.checkIn?.heatLevel ?: HeatLevel.NONE,
                plan = plan,
                recovery = recovery,
            ),
        )
        return TodayAuthority(today, plan, decision)
    }
}
