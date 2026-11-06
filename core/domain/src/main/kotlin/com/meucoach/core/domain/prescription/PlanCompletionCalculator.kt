package com.meucoach.core.domain.prescription

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class PlanCompletionWeek(val weekStart: LocalDate, val planned: Int, val done: Int, val skipped: Int) {
    /** Null when no session was planned for that week. */
    val completionRatio: Double?
        get() = if (planned == 0) null else done.toDouble() / planned
}

data class PlanCompletion(val weeks: List<PlanCompletionWeek>, val windowWeeks: Int) {
    val planned: Int get() = weeks.sumOf { it.planned }
    val done: Int get() = weeks.sumOf { it.done }
    val skipped: Int get() = weeks.sumOf { it.skipped }
    val completionRatio: Double? get() = if (planned == 0) null else done.toDouble() / planned
}

/**
 * Summarizes adherence to persisted plans. Only sessions up to [today] count:
 * future sessions must not lower the current week's completion.
 */
class PlanCompletionCalculator {
    fun calculate(plans: List<WeeklyPlan>, today: LocalDate, weeks: Int = 8): PlanCompletion {
        require(weeks in 1..26) { "Week window must be between 1 and 26" }
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStarts = (weeks - 1 downTo 0).map { currentWeekStart.minusWeeks(it.toLong()) }
        val byWeek = plans.associateBy { it.weekStart }
        val rows = weekStarts.map { weekStart ->
            val plan = byWeek[weekStart]
            val sessions = plan
                ?.sessions
                .orEmpty()
                // N-02: a plan created midweek must not count days before its creation.
                .filter { plan == null || !it.date.isBefore(plan.createdOn) }
                .filter { it.type != WorkoutType.REST && !it.date.isAfter(today) }
            PlanCompletionWeek(
                weekStart = weekStart,
                planned = sessions.size,
                done = sessions.count { it.status == SessionStatus.DONE },
                skipped = sessions.count { it.status == SessionStatus.SKIPPED },
            )
        }
        return PlanCompletion(weeks = rows, windowWeeks = weeks)
    }
}
