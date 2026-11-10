package com.meucoach.core.domain.replan

import com.meucoach.core.model.GoalType
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import com.meucoach.core.model.TrainingGoal
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PlanningGoalResolverTest {
    private val date = LocalDate.of(2026, 12, 6)
    private val at = Instant.parse("2026-11-10T12:00:00Z")

    @Test
    fun `a planned event wins over the legacy goal`() {
        val event = event(distance = RaceDistance.HALF_MARATHON)

        val projected = PlanningGoalResolver.resolve(
            events = listOf(event),
            legacyGoal = TrainingGoal(type = GoalType.FIVE_K, targetDate = date, targetDistanceM = 5_000.0),
        )

        assertThat(projected).isEqualTo(
            PlanningGoal(
                type = GoalType.HALF_MARATHON,
                targetDistanceM = 21_097.5,
                targetDate = date,
            ),
        )
    }

    @Test
    fun `the same distance and date produce the same projection from goal or event`() {
        val goal = TrainingGoal(
            type = GoalType.TEN_K,
            targetDate = date,
            targetDistanceM = 10_000.0,
            targetTimeSec = 3_600,
        )

        assertThat(PlanningGoalResolver.resolve(emptyList(), goal))
            .isEqualTo(PlanningGoalResolver.resolve(listOf(event()), legacyGoal = null))
    }

    @Test
    fun `target time never changes the planning projection`() {
        val first = event(targetTimeSec = 3_000)
        val second = event(targetTimeSec = 4_200)

        assertThat(PlanningGoalResolver.resolve(listOf(first), null))
            .isEqualTo(PlanningGoalResolver.resolve(listOf(second), null))
    }

    @Test
    fun `after any event exists the legacy goal no longer plans`() {
        val completed = event(status = RaceStatus.COMPLETED, resolvedAt = at)

        val projected = PlanningGoalResolver.resolve(
            events = listOf(completed),
            legacyGoal = TrainingGoal(type = GoalType.FIVE_K, targetDate = date, targetDistanceM = 5_000.0),
        )

        assertThat(projected).isNull()
    }

    @Test
    fun `cancelled and no show events also end the legacy fallback`() {
        for (status in listOf(RaceStatus.CANCELLED, RaceStatus.NO_SHOW)) {
            val projected = PlanningGoalResolver.resolve(
                events = listOf(event(status = status, resolvedAt = at)),
                legacyGoal = TrainingGoal(type = GoalType.TEN_K, targetDate = date, targetDistanceM = 10_000.0),
            )

            assertThat(projected).isNull()
        }
    }

    @Test
    fun `without events the legacy goal still plans`() {
        val goal = TrainingGoal(
            type = GoalType.MARATHON,
            targetDate = date,
            targetDistanceM = 42_195.0,
        )

        assertThat(PlanningGoalResolver.resolve(emptyList(), goal)?.type).isEqualTo(GoalType.MARATHON)
    }

    @Test
    fun `unsupported legacy goals never project an event`() {
        assertThat(PlanningGoalResolver.resolve(emptyList(), TrainingGoal(type = GoalType.CUSTOM))).isNull()
        assertThat(PlanningGoalResolver.resolve(emptyList(), null)).isNull()
    }

    @Test
    fun `a single planned event is picked even with history present`() {
        val planned = event(distance = RaceDistance.MARATHON, updatedAt = at.plusSeconds(60))
        val cancelled = event(
            distance = RaceDistance.FIVE_K,
            status = RaceStatus.CANCELLED,
            resolvedAt = at,
            updatedAt = at.plusSeconds(120),
        )

        val projected = PlanningGoalResolver.resolve(listOf(cancelled, planned), legacyGoal = null)

        assertThat(projected?.type).isEqualTo(GoalType.MARATHON)
        assertThat(projected?.targetDistanceM).isEqualTo(42_195.0)
    }

    private fun event(
        distance: RaceDistance = RaceDistance.TEN_K,
        status: RaceStatus = RaceStatus.PLANNED,
        resolvedAt: Instant? = null,
        targetTimeSec: Int? = null,
        updatedAt: Instant = at,
    ) = RaceEvent(
        id = "race-${distance.name}-${status.name}",
        distance = distance,
        targetDate = date,
        targetTimeSec = targetTimeSec,
        status = status,
        createdAt = at,
        updatedAt = updatedAt,
        resolvedAt = resolvedAt,
    )
}
