package com.meucoach.core.domain.prescription

import com.meucoach.core.model.GoalType
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.TrainingGoal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The race the week is being planned around (ADR 0030).
 *
 * Taper and quality cutoff are calculated per session date, never by the week
 * start: the same week can contain days outside the window and a race on any
 * weekday. The cutoff is a conservative product convention (`R-PLAN-10`,
 * LIMITED), not a claim derived from the taper meta-analyses.
 */
data class RacePlan(val eventId: String, val distance: RaceDistance, val targetDate: LocalDate) {
    init {
        require(eventId.isNotBlank()) { "Race plan event id must not be blank" }
    }

    val taperWindowDays: Int
        get() = if (distance == RaceDistance.MARATHON) MARATHON_TAPER_DAYS else DEFAULT_TAPER_DAYS

    val qualityCutoffDays: Int
        get() = when (distance) {
            RaceDistance.FIVE_K, RaceDistance.TEN_K -> SHORT_RACE_QUALITY_CUTOFF_DAYS
            RaceDistance.HALF_MARATHON, RaceDistance.MARATHON -> LONG_RACE_QUALITY_CUTOFF_DAYS
        }

    fun daysTo(date: LocalDate): Long = ChronoUnit.DAYS.between(date, targetDate)

    fun isInTaperWindow(date: LocalDate): Boolean = daysTo(date) in 0..taperWindowDays.toLong()

    /** A race day or a day after it is not ordinary training in the same week. */
    fun isRaceDayOrLater(date: LocalDate): Boolean = !date.isBefore(targetDate)

    /**
     * No strong session in the civil week of the race and none inside the
     * conservative cutoff. Past race days no longer constrain quality.
     */
    fun allowsQuality(date: LocalDate): Boolean {
        val days = daysTo(date)
        if (days < 0) return true
        if (weekStart(date) == weekStart(targetDate)) return false
        return days > qualityCutoffDays
    }

    fun taperFactor(date: LocalDate): Double = if (daysTo(date) <= DEEP_CUT_DAYS) DEEP_CUT_FACTOR else TAPER_FACTOR

    companion object {
        const val DEFAULT_TAPER_DAYS = 14
        const val MARATHON_TAPER_DAYS = 21
        const val SHORT_RACE_QUALITY_CUTOFF_DAYS = 10
        const val LONG_RACE_QUALITY_CUTOFF_DAYS = 14
        const val DEEP_CUT_DAYS = 7L
        const val TAPER_FACTOR = 0.55
        const val DEEP_CUT_FACTOR = 0.45

        /** Canonical projection of a persisted event (ADR 0030 §3.2). */
        fun from(event: RaceEvent): RacePlan =
            RacePlan(eventId = event.id, distance = event.distance, targetDate = event.targetDate)

        /** Legacy compatibility while the goal is still the planning source. */
        fun from(goal: TrainingGoal?): RacePlan? {
            val type = goal?.type ?: return null
            val date = goal.targetDate ?: return null
            val distance = type.toRaceDistance() ?: return null
            return RacePlan(eventId = "goal:$type", distance = distance, targetDate = date)
        }

        private fun GoalType.toRaceDistance(): RaceDistance? = when (this) {
            GoalType.FIVE_K -> RaceDistance.FIVE_K
            GoalType.TEN_K -> RaceDistance.TEN_K
            GoalType.HALF_MARATHON -> RaceDistance.HALF_MARATHON
            GoalType.MARATHON -> RaceDistance.MARATHON
            GoalType.GENERAL_FITNESS, GoalType.CUSTOM -> null
        }

        private fun weekStart(date: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
