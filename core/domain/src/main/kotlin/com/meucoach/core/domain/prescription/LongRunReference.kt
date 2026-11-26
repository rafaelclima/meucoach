package com.meucoach.core.domain.prescription

import java.time.DayOfWeek
import java.time.LocalDate

/** How the last normal long run was proved (ADR 0067 §2.1). */
enum class LongRunReferenceSource {
    REAL_STRONG,
    PLANNED,
}

/**
 * Last normal long run of the beginner, used as the ramp base.
 *
 * A real strong completion wins over the planned duration; a special week
 * (deload/taper/race/recovery) is never a reference.
 */
data class LongRunReference(val date: LocalDate, val minutes: Int, val source: LongRunReferenceSource) {
    init {
        require(minutes > 0) { "Long run reference must be positive" }
    }
}

/**
 * Resolves the most recent long run from a normal week in the four-week window
 * of ADR 0050. Weeks marked as deload/taper/race/recovery are skipped, and a
 * long run closed as non-trainable (rest, illness, replacement) is not a dose.
 * Without a real strong completion the planned duration is the reference;
 * without any normal long run there is no reference at all.
 */
object LongRunReferenceResolver {
    fun resolve(plans: List<WeeklyPlan>, targetWeekStart: LocalDate): LongRunReference? {
        require(targetWeekStart.dayOfWeek == DayOfWeek.MONDAY) { "Target week must start on Monday" }
        val windowStart = targetWeekStart.minusWeeks(MAX_REFERENCE_WEEKS.toLong())
        return plans
            .distinctBy { it.weekStart }
            .filter { !it.weekStart.isBefore(windowStart) && it.weekStart.isBefore(targetWeekStart) }
            .filter { it.ruleIds.none { rule -> rule in SPECIAL_WEEK_RULE_IDS } }
            .sortedByDescending { it.weekStart }
            .firstNotNullOfOrNull { plan -> plan.longRunReference() }
    }

    private fun WeeklyPlan.longRunReference(): LongRunReference? {
        val session = sessions
            .filter { it.type == WorkoutType.LONG }
            .filter { it.date in weekStart..weekStart.plusDays(DAYS_IN_WEEK - 1L) }
            .filterNot { it.status == SessionStatus.CLOSED && it.closureReason?.isNonTrainable == true }
            .maxByOrNull { it.date }
            ?: return null
        val minutes = session.durationMin?.takeIf { it > 0 } ?: return null
        val real = session.status == SessionStatus.DONE && session.completionEvidence?.isStrong == true
        return LongRunReference(
            date = session.date,
            minutes = minutes,
            source = if (real) LongRunReferenceSource.REAL_STRONG else LongRunReferenceSource.PLANNED,
        )
    }

    private const val MAX_REFERENCE_WEEKS = 4
    private const val DAYS_IN_WEEK = 7L
}
