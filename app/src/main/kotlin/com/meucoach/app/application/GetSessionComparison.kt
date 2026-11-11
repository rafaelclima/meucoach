package com.meucoach.app.application

import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.feedback.ComparisonPlan
import com.meucoach.core.domain.feedback.SessionComparison
import com.meucoach.core.domain.feedback.SessionComparisonCalculator
import com.meucoach.core.domain.prescription.PlannedSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Reads one activity against the plan it fulfilled (FR-010).
 *
 * Only an activity that is the evidence of a planned session is compared: the
 * link comes from the execution or from the session's own `activityId`. With no
 * link the run is a free run and no target is invented. The persisted daily
 * prescription is preferred because it is what the athlete was told to do that
 * day; a week session is only a fallback for a linked day without prescription.
 */
class GetSessionComparison @Inject constructor(
    private val activities: ActivityRepository,
    private val executions: SessionExecutionRepository,
    private val prescriptions: DailyPrescriptionRepository,
    private val plans: TrainingPlanRepository,
) {
    suspend fun forActivity(activity: StoredActivity): SessionComparison? {
        val summary = activities.getActivitySummary(activity.id) ?: return null
        val execution = executions.getForActivity(activity.id)
        val session = planSession(activity.localDate)
        val linked = execution != null || session?.activityId == activity.id
        val prescription = if (linked) prescriptions.getLatest(activity.localDate) else null
        // A persisted REST day means no training target: it never falls back to
        // the week session, which could still look like a planned workout.
        val plan = when {
            prescription != null -> ComparisonPlan.from(prescription)
            linked -> session?.let { ComparisonPlan.from(it) }
            else -> null
        }
        return SessionComparisonCalculator.compare(
            linkedPlan = plan,
            activity = summary,
            rpe = execution?.rpe,
        )
    }

    private suspend fun planSession(date: LocalDate): PlannedSession? {
        val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return plans.getWeek(weekStart)?.sessions?.firstOrNull { it.date == date }
    }
}
