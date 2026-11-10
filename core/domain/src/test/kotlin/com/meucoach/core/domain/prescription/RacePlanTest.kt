package com.meucoach.core.domain.prescription

import com.meucoach.core.model.GoalType
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.TrainingGoal
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class RacePlanTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `uses the approved taper window by distance`() {
        assertThat(race(RaceDistance.FIVE_K).taperWindowDays).isEqualTo(14)
        assertThat(race(RaceDistance.TEN_K).taperWindowDays).isEqualTo(14)
        assertThat(race(RaceDistance.HALF_MARATHON).taperWindowDays).isEqualTo(14)
        assertThat(race(RaceDistance.MARATHON).taperWindowDays).isEqualTo(21)
    }

    @Test
    fun `uses the conservative quality cutoff by distance`() {
        assertThat(race(RaceDistance.FIVE_K).qualityCutoffDays).isEqualTo(10)
        assertThat(race(RaceDistance.TEN_K).qualityCutoffDays).isEqualTo(10)
        assertThat(race(RaceDistance.HALF_MARATHON).qualityCutoffDays).isEqualTo(14)
        assertThat(race(RaceDistance.MARATHON).qualityCutoffDays).isEqualTo(14)
    }

    @Test
    fun `no quality in the civil race week`() {
        val plan = race(RaceDistance.TEN_K, target = monday.plusDays(5))

        assertThat(plan.allowsQuality(monday)).isFalse()
        assertThat(plan.allowsQuality(monday.plusDays(4))).isFalse()
        assertThat(plan.allowsQuality(monday.minusWeeks(2))).isTrue()
    }

    @Test
    fun `cutoff is inclusive and the day before it allows quality`() {
        val fiveK = race(RaceDistance.FIVE_K, target = monday.plusDays(20))

        assertThat(fiveK.allowsQuality(monday.plusDays(10))).isFalse()
        assertThat(fiveK.allowsQuality(monday.plusDays(9))).isTrue()

        val half = race(RaceDistance.HALF_MARATHON, target = monday.plusDays(30))
        assertThat(half.allowsQuality(monday.plusDays(16))).isFalse()
        assertThat(half.allowsQuality(monday.plusDays(15))).isTrue()
    }

    @Test
    fun `a race in the past no longer blocks quality or taper`() {
        val plan = race(RaceDistance.TEN_K, target = monday.minusDays(5))

        assertThat(plan.allowsQuality(monday)).isTrue()
        assertThat(plan.isInTaperWindow(monday)).isFalse()
        assertThat(plan.isRaceDayOrLater(monday)).isTrue()
    }

    @Test
    fun `deep cut applies inside the last seven days`() {
        val plan = race(RaceDistance.TEN_K, target = monday.plusDays(20))

        assertThat(plan.taperFactor(monday.plusDays(8))).isEqualTo(RacePlan.TAPER_FACTOR)
        assertThat(plan.taperFactor(monday.plusDays(13))).isEqualTo(RacePlan.DEEP_CUT_FACTOR)
    }

    @Test
    fun `projects a supported legacy goal and ignores unsupported ones`() {
        val plan = RacePlan.from(
            TrainingGoal(
                type = GoalType.HALF_MARATHON,
                targetDate = monday.plusDays(30),
                targetDistanceM = 21_097.5,
            ),
        )

        assertThat(plan?.distance).isEqualTo(RaceDistance.HALF_MARATHON)
        assertThat(plan?.targetDate).isEqualTo(monday.plusDays(30))
        assertThat(RacePlan.from(TrainingGoal(type = GoalType.CUSTOM, targetDate = monday))).isNull()
        assertThat(RacePlan.from(null)).isNull()
    }

    private fun race(distance: RaceDistance, target: LocalDate = monday.plusDays(30)) =
        RacePlan(eventId = "race-1", distance = distance, targetDate = target)
}
