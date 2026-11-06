package com.meucoach.core.domain.prescription

import com.meucoach.core.model.CompletionEvidence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class PlanCompletionWeek(
    val weekStart: LocalDate,
    val planned: Int,
    val done: Int,
    val verified: Int,
    val manual: Int,
    val legacy: Int,
    val skipped: Int,
) {
    /** Null when no session was planned for that week. */
    val completionRatio: Double?
        get() = if (planned == 0) null else done.toDouble() / planned

    /** Share of planned sessions with strong evidence (activity/guided). */
    val verifiedRatio: Double?
        get() = if (planned == 0) null else verified.toDouble() / planned
}

data class PlanCompletion(val weeks: List<PlanCompletionWeek>, val windowWeeks: Int) {
    val planned: Int get() = weeks.sumOf { it.planned }
    val done: Int get() = weeks.sumOf { it.done }
    val verified: Int get() = weeks.sumOf { it.verified }
    val manual: Int get() = weeks.sumOf { it.manual }
    val legacy: Int get() = weeks.sumOf { it.legacy }
    val skipped: Int get() = weeks.sumOf { it.skipped }
    val completionRatio: Double? get() = if (planned == 0) null else done.toDouble() / planned
    val verifiedRatio: Double? get() = if (planned == 0) null else verified.toDouble() / planned
}

/**
 * Summarizes adherence to persisted plans (ADR 0024).
 *
 * Adherence is not the same as proven execution: [done] counts every documented
 * completion, while [verified] counts only strong evidence. Rest prescribed,
 * illness and a replaced plan leave the denominator instead of becoming skipped
 * days. Only sessions up to [today] count, so future sessions never lower the
 * current week's completion.
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
                // ADR 0024: a non-trainable closure is not a failure of adherence.
                .filterNot {
                    it.status == SessionStatus.CLOSED && it.closureReason?.isNonTrainable == true
                }
            PlanCompletionWeek(
                weekStart = weekStart,
                planned = sessions.size,
                done = sessions.count { it.status == SessionStatus.DONE },
                verified = sessions.count {
                    it.status == SessionStatus.DONE && it.completionEvidence?.isStrong == true
                },
                manual = sessions.count {
                    it.status == SessionStatus.DONE && it.completionEvidence == CompletionEvidence.MANUAL
                },
                legacy = sessions.count {
                    it.status == SessionStatus.DONE &&
                        it.completionEvidence == CompletionEvidence.LEGACY_UNVERIFIED
                },
                skipped = sessions.count {
                    it.status == SessionStatus.CLOSED &&
                        (
                            it.closureReason == SessionClosureReason.USER_SKIPPED ||
                                it.closureReason == SessionClosureReason.MISSED
                            )
                },
            )
        }
        return PlanCompletion(weeks = rows, windowWeeks = weeks)
    }
}
