package com.meucoach.app.application

import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.StoredWeeklyPlan
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.load.ActivitySourceStatus
import com.meucoach.core.domain.replan.PlanReplanner
import com.meucoach.core.domain.replan.ReplanInput
import com.meucoach.core.domain.replan.ReplanTrigger
import java.time.LocalDate
import javax.inject.Inject

/** The persisted week plus the pure input the replanner expects. */
data class ReplanContext(val today: LocalDate, val record: StoredWeeklyPlan, val input: ReplanInput)

/**
 * Reads every signal the replanner needs and rebuilds the same deterministic
 * input on evaluation and on acceptance. Nothing here decides training: the
 * availability baseline is captured, the persisted plan is passed as-is and the
 * pure `PlanReplanner` does the rest.
 */
class ReplanContextBuilder @Inject constructor(
    private val plans: TrainingPlanRepository,
    private val profiles: ProfileRepository,
    private val checkIns: CheckInRepository,
    private val metrics: DailyMetricsRepository,
    private val activities: ActivityRepository,
    private val timeProvider: TimeProvider,
) {
    suspend fun build(explicit: Boolean): ReplanContext? {
        val today = timeProvider.currentDate()
        val weekStart = today.weekStart()
        var record = plans.getWeekRecord(weekStart) ?: return null
        val profile = profiles.getProfile() ?: return null

        val availabilityHash = PlanReplanner.availabilityHash(profile.weeklyAvailability)
        if (record.availabilityHash == null) {
            // A legacy/initial plan has no history. Capture the current baseline
            // explicitly instead of inferring a change that never happened.
            plans.captureAvailabilityBaseline(weekStart, availabilityHash)
            record = plans.getWeekRecord(weekStart) ?: record
        }

        val checkIn = checkIns.get(today)
        val readiness = readinessResult(checkIn, metrics.getAll(), today)
        val summaries = activities.listActivitySummaries()
        val load = buildLoadSnapshot(
            activities = summaries,
            profile = profile,
            today = today,
            sourceStatus = ActivitySourceStatus(
                lastImportedAt = activities.lastImportedAt(),
                checkedAt = timeProvider.now(),
            ),
        )
        val previousWeek = plans.getWeek(weekStart.minusWeeks(1))
        val weekNumberInBlock = plans.consecutiveWeeksBefore(weekStart) + 1
        val nonTrainablePastDates = nonTrainablePastDates(
            record = record,
            weekStart = weekStart,
            today = today,
        )

        val input = ReplanInput(
            currentPlan = record.plan,
            currentVersion = record.version,
            currentInputsHash = record.inputsHash,
            currentAvailabilityHash = record.availabilityHash,
            // T1/T2 are always evaluated; the trigger only forces evaluation for T3.
            trigger = if (explicit) ReplanTrigger.USER_REQUEST else ReplanTrigger.MISSED_SESSIONS,
            profile = profile,
            readiness = readiness,
            load = load,
            previousWeek = previousWeek,
            weekNumberInBlock = weekNumberInBlock,
            nonTrainablePastDates = nonTrainablePastDates,
            today = today,
        )
        return ReplanContext(today = today, record = record, input = input)
    }

    /** A day with illness/injury in the check-in never counts as a missed workout. */
    private suspend fun nonTrainablePastDates(
        record: StoredWeeklyPlan,
        weekStart: LocalDate,
        today: LocalDate,
    ): Set<LocalDate> {
        val start = maxOf(record.plan.createdOn, weekStart)
        if (!start.isBefore(today)) return emptySet()
        val dates = mutableSetOf<LocalDate>()
        var date = start
        while (date.isBefore(today)) {
            if (checkIns.get(date)?.illnessOrInjury == true) dates += date
            date = date.plusDays(1)
        }
        return dates
    }
}
