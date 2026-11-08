package com.meucoach.app.application

import com.meucoach.app.importer.ImportedRunPromptStore
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.model.Sport
import javax.inject.Inject

/**
 * Decides whether a freshly imported run deserves a prompt.
 *
 * The prompt is only queued for a run that happened today on a day that still
 * has a planned session. A completed session is also queued: the app then shows
 * an informational notice instead of offering a link.
 */
class OfferImportedRun @Inject constructor(
    private val activities: ActivityRepository,
    private val plans: TrainingPlanRepository,
    private val checkIns: CheckInRepository,
    private val executions: SessionExecutionRepository,
    private val store: ImportedRunPromptStore,
    private val timeProvider: TimeProvider,
) {
    suspend fun evaluate(activityId: String) {
        val today = timeProvider.currentDate()
        val zone = timeProvider.zoneId.value
        val activity = activities.getActivitySummary(activityId) ?: return
        if (activity.sport != Sport.RUNNING && activity.sport != Sport.TREADMILL) return
        if (activity.startTime.atZone(zone).toLocalDate() != today) return
        if (checkIns.get(today)?.illnessOrInjury == true) return
        if (executions.getForActivity(activityId) != null) return

        val plan = plans.getWeek(today.weekStart()) ?: return
        val session = plan.sessions.firstOrNull { it.date == today } ?: return
        if (session.status == SessionStatus.CLOSED) return
        if (session.activityId == activityId) return

        store.add(activityId)
    }
}
