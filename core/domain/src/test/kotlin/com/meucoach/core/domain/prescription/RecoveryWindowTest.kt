package com.meucoach.core.domain.prescription

import com.meucoach.core.model.RaceDistance
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

class RecoveryWindowTest {
    private val raceDay = LocalDate.of(2026, 11, 8) // Sunday

    @Test
    fun `uses the approved window per distance`() {
        assertThat(window(RaceDistance.FIVE_K).totalDays).isEqualTo(2)
        assertThat(window(RaceDistance.TEN_K).totalDays).isEqualTo(3)
        assertThat(window(RaceDistance.HALF_MARATHON).totalDays).isEqualTo(5)
        assertThat(window(RaceDistance.MARATHON).totalDays).isEqualTo(10)
    }

    @Test
    fun `race day is not inside the window`() {
        val recovery = window(RaceDistance.TEN_K)

        assertThat(recovery.isActive(raceDay)).isFalse()
        assertThat(recovery.isActive(raceDay.plusDays(1))).isTrue()
        assertThat(recovery.isActive(raceDay.plusDays(3))).isTrue()
        assertThat(recovery.isActive(raceDay.plusDays(4))).isFalse()
        assertThat(recovery.dayNumber(raceDay)).isZero()
        assertThat(recovery.dayNumber(raceDay.plusDays(2))).isEqualTo(2)
    }

    @Test
    fun `blocks quality and long run only inside the window`() {
        val recovery = window(RaceDistance.HALF_MARATHON)

        assertThat(recovery.allowsQuality(raceDay.plusDays(1))).isFalse()
        assertThat(recovery.allowsLongRun(raceDay.plusDays(5))).isFalse()
        assertThat(recovery.allowsQuality(raceDay.plusDays(6))).isTrue()
        assertThat(recovery.allowsLongRun(raceDay)).isTrue()
    }

    @Test
    fun `caps sessions by phase`() {
        assertThat(window(RaceDistance.FIVE_K).maxSessionMinutes(raceDay)).isNull()
        assertThat(window(RaceDistance.FIVE_K).maxSessionMinutes(raceDay.plusDays(1))).isEqualTo(30)
        assertThat(window(RaceDistance.TEN_K).maxSessionMinutes(raceDay.plusDays(3))).isEqualTo(30)

        val half = window(RaceDistance.HALF_MARATHON)
        assertThat(half.maxSessionMinutes(raceDay.plusDays(3))).isEqualTo(30)
        assertThat(half.maxSessionMinutes(raceDay.plusDays(4))).isEqualTo(45)
        assertThat(half.maxSessionMinutes(raceDay.plusDays(5))).isEqualTo(45)
        assertThat(half.maxSessionMinutes(raceDay.plusDays(6))).isNull()

        val marathon = window(RaceDistance.MARATHON)
        assertThat(marathon.maxSessionMinutes(raceDay.plusDays(5))).isEqualTo(30)
        assertThat(marathon.maxSessionMinutes(raceDay.plusDays(6))).isEqualTo(60)
        assertThat(marathon.maxSessionMinutes(raceDay.plusDays(10))).isEqualTo(60)
        assertThat(marathon.maxSessionMinutes(raceDay.plusDays(11))).isNull()
    }

    @Test
    fun `blocks a session only when it violates the window`() {
        val recovery = window(RaceDistance.TEN_K)
        val day = raceDay.plusDays(1)

        assertThat(recovery.blocks(WorkoutType.QUALITY, day, 30)).isTrue()
        assertThat(recovery.blocks(WorkoutType.LONG, day, 30)).isTrue()
        assertThat(recovery.blocks(WorkoutType.EASY, day, 45)).isTrue()
        assertThat(recovery.blocks(WorkoutType.EASY, day, 30)).isFalse()
        assertThat(recovery.blocks(WorkoutType.EASY, day, null)).isFalse()
        assertThat(recovery.blocks(WorkoutType.QUALITY, raceDay.plusDays(4), 30)).isFalse()
        assertThat(recovery.blocks(WorkoutType.EASY, raceDay, 90)).isFalse()
    }

    @Test
    fun `exposes day of window and remaining days`() {
        val recovery = window(RaceDistance.MARATHON)

        assertThat(recovery.dayOfWindow(raceDay.plusDays(1))).isEqualTo(1)
        assertThat(recovery.dayOfWindow(raceDay.plusDays(10))).isEqualTo(10)
        assertThat(recovery.daysRemaining(raceDay.plusDays(1))).isEqualTo(10)
        assertThat(recovery.daysRemaining(raceDay.plusDays(9))).isEqualTo(2)
        assertThat(recovery.dayOfWindow(raceDay)).isZero()
        assertThat(recovery.daysRemaining(raceDay.plusDays(11))).isZero()
    }

    @Test
    fun `intersects only when the range touches the window`() {
        val recovery = window(RaceDistance.HALF_MARATHON)

        assertThat(recovery.intersects(raceDay, raceDay.plusDays(6))).isTrue()
        assertThat(recovery.intersects(raceDay.plusDays(5), raceDay.plusDays(12))).isTrue()
        assertThat(recovery.intersects(raceDay.plusDays(6), raceDay.plusDays(12))).isFalse()
        assertThat(recovery.intersects(raceDay.minusDays(7), raceDay)).isFalse()
        assertThat(recovery.intersects(raceDay.plusDays(6), raceDay)).isFalse()
    }

    @Test
    fun `active picks the window that ends last`() {
        val marathon = CompletedRace("race-1", RaceDistance.MARATHON, raceDay)
        val fiveK = CompletedRace("race-2", RaceDistance.FIVE_K, raceDay.plusDays(3))

        val active = RecoveryWindow.active(listOf(marathon, fiveK), raceDay.plusDays(4))

        assertThat(active?.race?.eventId).isEqualTo("race-1")
        assertThat(RecoveryWindow.active(listOf(marathon), raceDay.minusDays(1))).isNull()
        assertThat(RecoveryWindow.active(emptyList(), raceDay)).isNull()
    }

    @Test
    fun `covering resolves the window that applies to a plan week`() {
        val older = CompletedRace("race-1", RaceDistance.TEN_K, raceDay.minusDays(30))
        val marathon = CompletedRace("race-2", RaceDistance.MARATHON, raceDay.minusDays(2))
        val fiveK = CompletedRace("race-3", RaceDistance.FIVE_K, raceDay)

        val covering = RecoveryWindow.covering(
            listOf(older, marathon, fiveK),
            raceDay.plusDays(1),
            raceDay.plusDays(7),
        )

        assertThat(covering?.race?.eventId).isEqualTo("race-2")
        assertThat(RecoveryWindow.covering(listOf(older), raceDay, raceDay.plusDays(7))).isNull()
        assertThat(RecoveryWindow.covering(emptyList(), raceDay, raceDay.plusDays(7))).isNull()
    }

    @Test
    fun `completed race requires an event id`() {
        assertThatThrownBy {
            CompletedRace(" ", RaceDistance.FIVE_K, raceDay)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun window(distance: RaceDistance): RecoveryWindow =
        RecoveryWindow(CompletedRace("race-1", distance, raceDay))
}
