package com.meucoach.core.domain.prescription

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Mesocycle phase derived from real history (ADR 0041). */
enum class MesoPhase {
    BASE,
    BUILD,
    PEAK,
    TAPER,
    RECOVERY,
}

/** Resolved mesocycle plus the counters that prove it. */
data class MesoState(
    val phase: MesoPhase,
    val isDeload: Boolean,
    val strongStreak: Int,
    val totalStrong: Int,
    val fallbackUsed: Boolean,
)

/**
 * Derives the mesocycle from persisted weeks, never from a volatile counter.
 *
 * A load week has at least one `DONE` session with strong evidence, was
 * created on its own Monday and carries no taper/deload/recovery rule. A hole
 * (missing plan or no strong completion) resets the streak for deload purposes
 * but preserves the macrocycle phase, which uses the total in 12 weeks.
 */
object MesoCycle {
    const val MAX_HISTORY_WEEKS = 12
    const val STREAK_FOR_DELOAD = 3
    const val BASE_THRESHOLD_DAYS = 56L
    const val PEAK_WINDOW_DAYS = 21L

    private val specialWeekRules = setOf("R-PLAN-04", "R-PLAN-05", "R-PLAN-10", "R-RACE-04")

    fun isLoadWeek(plan: WeeklyPlan): Boolean {
        if (plan.createdOn != plan.weekStart) return false
        if (plan.ruleIds.any { it in specialWeekRules }) return false
        return plan.completedVolumeMinutes() > 0
    }

    fun consecutiveStrongWeeks(references: List<WeeklyPlan>, targetWeekStart: LocalDate): Int {
        val byWeek = references.distinctBy { it.weekStart }.associateBy { it.weekStart }
        var streak = 0
        var expected = targetWeekStart.minusWeeks(1)
        val earliest = targetWeekStart.minusWeeks(MAX_HISTORY_WEEKS.toLong())
        while (!expected.isBefore(earliest)) {
            val plan = byWeek[expected] ?: break
            if (!isLoadWeek(plan)) break
            streak += 1
            expected = expected.minusWeeks(1)
        }
        return streak
    }

    fun totalStrongWeeks(
        references: List<WeeklyPlan>,
        targetWeekStart: LocalDate,
        windowWeeks: Int = MAX_HISTORY_WEEKS,
    ): Int {
        val earliest = targetWeekStart.minusWeeks(windowWeeks.toLong())
        val latest = targetWeekStart.minusWeeks(1)
        return references
            .distinctBy { it.weekStart }
            .filter { !it.weekStart.isBefore(earliest) && !it.weekStart.isAfter(latest) }
            .count(::isLoadWeek)
    }

    @Suppress("LongParameterList")
    fun resolve(
        targetWeekStart: LocalDate,
        race: RacePlan?,
        recoveryActive: Boolean,
        isRaceWeek: Boolean,
        isTaperWeek: Boolean,
        references: List<WeeklyPlan>,
        fallbackWeekNumberInBlock: Int = 1,
    ): MesoState {
        if (references.isEmpty()) {
            val phase = when {
                recoveryActive -> MesoPhase.RECOVERY
                isRaceWeek || isTaperWeek -> MesoPhase.TAPER
                else -> MesoPhase.BASE
            }
            return MesoState(
                phase = phase,
                isDeload = fallbackWeekNumberInBlock % DELOAD_EVERY_WEEKS == 0 &&
                    phase != MesoPhase.TAPER &&
                    phase != MesoPhase.RECOVERY,
                strongStreak = 0,
                totalStrong = 0,
                fallbackUsed = true,
            )
        }
        val streak = consecutiveStrongWeeks(references, targetWeekStart)
        val total = totalStrongWeeks(references, targetWeekStart)
        if (recoveryActive) {
            return MesoState(MesoPhase.RECOVERY, false, streak, total, false)
        }
        if (isRaceWeek || isTaperWeek) {
            return MesoState(MesoPhase.TAPER, false, streak, total, false)
        }
        if (race == null) {
            val blockIndex = total / DELOAD_EVERY_WEEKS
            val phase = if (blockIndex % 2 == 0) MesoPhase.BASE else MesoPhase.BUILD
            return MesoState(phase, streak >= STREAK_FOR_DELOAD, streak, total, false)
        }
        val daysTo = ChronoUnit.DAYS.between(targetWeekStart, race.targetDate)
        if (daysTo < 0) {
            return MesoState(MesoPhase.BASE, streak >= STREAK_FOR_DELOAD, streak, total, false)
        }
        if (daysTo > BASE_THRESHOLD_DAYS) {
            return MesoState(MesoPhase.BASE, streak >= STREAK_FOR_DELOAD, streak, total, false)
        }
        if (daysTo > race.taperWindowDays + PEAK_WINDOW_DAYS) {
            return MesoState(MesoPhase.BUILD, streak >= STREAK_FOR_DELOAD, streak, total, false)
        }
        if (daysTo > race.taperWindowDays) {
            // PEAK mantém volume estável: nunca é descarga.
            return MesoState(MesoPhase.PEAK, false, streak, total, false)
        }
        return MesoState(MesoPhase.TAPER, false, streak, total, false)
    }

    private const val DELOAD_EVERY_WEEKS = 4
}
