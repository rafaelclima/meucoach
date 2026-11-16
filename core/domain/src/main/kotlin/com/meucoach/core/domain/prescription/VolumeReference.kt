package com.meucoach.core.domain.prescription

import com.meucoach.core.model.ActivitySource
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One imported run that proves real volume (ADR 0050 §2.1).
 *
 * A strong run comes from an imported activity, never from a click: [source]
 * must not be [ActivitySource.MANUAL]. [plannedSessionId] is set when the
 * activity is linked to a planned session; an unplanned run still counts for
 * volume, just not for adherence.
 */
data class StrongRun(
    val date: LocalDate,
    val durationMin: Int,
    val source: ActivitySource,
    val plannedSessionId: String? = null,
) {
    init {
        require(durationMin > 0) { "Strong run duration must be positive" }
        require(source != ActivitySource.MANUAL) { "Manual entries are not strong evidence" }
        require(plannedSessionId == null || plannedSessionId.isNotBlank()) {
            "Planned session id must not be blank"
        }
    }
}

/**
 * Weekly execution projection used by volume progression, the mesocycle and
 * graduation (ADR 0050, FR-408).
 *
 * [strongMinutes] is the base of the volume progression and comes from imported
 * activities, planned or not. [plannedSessions]/[createdOn]/[specialWeek]
 * describe the plan and keep adherence separate from extra training.
 */
data class VolumeReference(
    val weekStart: LocalDate,
    val plannedMinutes: Int?,
    val executedMinutes: Int,
    val strongMinutes: Int,
    val strongSessions: Int,
    val plannedSessions: Int,
    val createdOn: LocalDate?,
    val specialWeek: Boolean,
) {
    init {
        require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "Week must start on Monday" }
        require(plannedMinutes == null || plannedMinutes >= 0) {
            "Planned minutes must not be negative"
        }
        require(executedMinutes >= 0) { "Executed minutes must not be negative" }
        require(strongMinutes >= 0) { "Strong minutes must not be negative" }
        require(strongMinutes <= executedMinutes) {
            "Strong minutes must not exceed executed minutes"
        }
        require(strongMinutes == 0 || strongSessions > 0) {
            "Strong minutes require at least one strong session"
        }
        require(strongSessions >= 0) { "Strong sessions must not be negative" }
        require(plannedSessions >= 0) { "Planned sessions must not be negative" }
    }

    /** False for a week without a stored plan; such a week is never a reference. */
    val hasPlan: Boolean get() = plannedMinutes != null
}

/**
 * Builds a [VolumeReference] from a stored plan and the strong runs of its week.
 *
 * The builder is pure and order-independent. It only aggregates: whether the
 * week is usable as a volume base (created on Monday, not special, has trainable
 * sessions) remains a [VolumeProgression] decision (ADR 0050 §2.2).
 */
object VolumeReferenceBuilder {
    fun build(weekStart: LocalDate, plan: WeeklyPlan?, runs: List<StrongRun>): VolumeReference {
        require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "Week must start on Monday" }
        val weekEnd = weekStart.plusDays(DAYS_IN_WEEK - 1L)
        val weekRuns = runs.filter { run -> run.date in weekStart..weekEnd }
        val strongMinutes = weekRuns.sumOf { it.durationMin }
        return VolumeReference(
            weekStart = weekStart,
            plannedMinutes = plan?.weeklyMinutes,
            executedMinutes = strongMinutes,
            strongMinutes = strongMinutes,
            strongSessions = weekRuns.size,
            plannedSessions = plan?.trainableSessions()?.size ?: 0,
            createdOn = plan?.createdOn,
            specialWeek = plan?.ruleIds?.any { it in SPECIAL_WEEK_RULE_IDS } ?: false,
        )
    }

    private const val DAYS_IN_WEEK = 7L
}

/**
 * Rules that mark a week as deload, taper, race or recovery (ADR 0038/0045/0050).
 * Such weeks are never comparable volume references.
 */
internal val SPECIAL_WEEK_RULE_IDS = setOf("R-PLAN-04", "R-PLAN-05", "R-PLAN-10", "R-RACE-04")
