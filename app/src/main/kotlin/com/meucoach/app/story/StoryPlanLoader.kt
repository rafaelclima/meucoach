package com.meucoach.app.story

import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.domain.story.StoryRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** Planned session that ran on the activity date, and the next one in the same week. */
data class StorySessions(val current: PlannedSession? = null, val next: PlannedSession? = null)

/**
 * Pure look-up of the planned sessions around an activity date. Shared by the
 * UI preview and by the background export, so the verdict is identical both
 * ways (ADR 0018).
 */
class StorySessionResolver @Inject constructor(private val plans: TrainingPlanRepository) {
    /**
     * Missing plans are not an error: the story then reports effort only.
     */
    suspend fun resolve(activityDate: LocalDate): StorySessions {
        val weekStart = activityDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = runCatching { plans.getWeek(weekStart) }.getOrNull() ?: return StorySessions()
        return StorySessions(
            current = week.sessions.firstOrNull { it.date == activityDate },
            next = week.sessions
                .filter { it.date.isAfter(activityDate) }
                .minByOrNull { it.date },
        )
    }
}

/**
 * Rebuilds the story plan deterministically from the repositories. The worker
 * always re-derives the plan instead of trusting UI state, so an export keeps
 * working after the detail screen (or the whole process) is gone (ADR 0018).
 */
class StoryPlanLoader @Inject constructor(
    private val repository: ActivityRepository,
    private val profiles: ProfileRepository,
    private val sessions: StorySessionResolver,
) {
    suspend fun load(activityId: String, recipe: StoryRecipe = StoryRecipe()): StoryPlan? {
        val stored = repository.openActivity(activityId) ?: return null
        val planned = sessions.resolve(stored.localDate)
        return withContext(Dispatchers.Default) {
            StoryPlanCalculator.calculate(
                activity = stored.imported,
                targetDurationSec = recipe.targetDurationSec(stored.imported.distanceM / 1_000.0),
                lthrBpm = profiles.getProfile()?.lactateThresholdHeartRate,
                session = planned.current,
                nextSession = planned.next,
            )?.copy(localDate = stored.localDate)
        }
    }

    companion object {
        /** Recipe used when no preference or worker payload is available. */
        val DEFAULT_RECIPE = StoryRecipe()
    }
}
