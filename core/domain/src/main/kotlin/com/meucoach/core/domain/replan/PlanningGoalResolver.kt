package com.meucoach.core.domain.replan

import com.meucoach.core.model.GoalType
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import com.meucoach.core.model.TrainingGoal

/**
 * Single source of the planning objective while `TrainingGoal` migrates to
 * `RaceEvent` (ADR 0030 §3 / ADR 0031).
 *
 * A planned event wins. Once any event exists, the legacy goal stops planning,
 * so there are never two race objectives feeding the week. Before the first
 * event the legacy projection keeps working exactly as before; the migration
 * itself creates no event and therefore no T4.
 */
object PlanningGoalResolver {
    fun resolve(events: List<RaceEvent>, legacyGoal: TrainingGoal?): PlanningGoal? {
        val planned = events.filter { it.status == RaceStatus.PLANNED }.maxByOrNull { it.updatedAt }
        if (planned != null) return planned.toPlanningGoal()
        if (events.isNotEmpty()) return null
        return PlanningGoal.from(legacyGoal)
    }

    private fun RaceEvent.toPlanningGoal(): PlanningGoal = PlanningGoal(
        type = distance.goalType(),
        targetDistanceM = distance.meters,
        targetDate = targetDate,
    )

    private fun RaceDistance.goalType(): GoalType = when (this) {
        RaceDistance.FIVE_K -> GoalType.FIVE_K
        RaceDistance.TEN_K -> GoalType.TEN_K
        RaceDistance.HALF_MARATHON -> GoalType.HALF_MARATHON
        RaceDistance.MARATHON -> GoalType.MARATHON
    }
}
