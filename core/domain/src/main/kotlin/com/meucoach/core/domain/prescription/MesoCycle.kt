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
 * A load week has at least one real strong run (planned or not), was created on
 * its own Monday and carries no taper/deload/recovery rule (ADR 0050 §2.3). A
 * hole (missing plan or a week without strong runs) resets the streak for deload
 * purposes but preserves the macrocycle phase, which uses the total in 12 weeks.
 */
object MesoCycle {
    const val MAX_HISTORY_WEEKS = 12
    const val STREAK_FOR_DELOAD = 3
    const val BASE_THRESHOLD_DAYS = 56L
    const val PEAK_WINDOW_DAYS = 21L

    fun isLoadWeek(reference: VolumeReference): Boolean {
        if (reference.createdOn != reference.weekStart) return false
        if (reference.specialWeek) return false
        return reference.strongSessions >= 1
    }

    fun consecutiveStrongWeeks(references: List<VolumeReference>, targetWeekStart: LocalDate): Int {
        val byWeek = references.distinctBy { it.weekStart }.associateBy { it.weekStart }
        var streak = 0
        var expected = targetWeekStart.minusWeeks(1)
        val earliest = targetWeekStart.minusWeeks(MAX_HISTORY_WEEKS.toLong())
        while (!expected.isBefore(earliest)) {
            val reference = byWeek[expected] ?: break
            if (!isLoadWeek(reference)) break
            streak += 1
            expected = expected.minusWeeks(1)
        }
        return streak
    }

    fun totalStrongWeeks(
        references: List<VolumeReference>,
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
        references: List<VolumeReference>,
        fallbackWeekNumberInBlock: Int = 1,
        /**
         * Fase da preparação macro (ADR 0043). Com prova dentro do horizonte,
         * prevalece sobre o ramo de prova abaixo; a recuperação continua
         * suprema e a descarga segue pela contagem de semanas fortes.
         */
        phaseOverride: MesoPhase? = null,
    ): MesoState {
        if (references.isEmpty()) {
            val phase = phaseOverride ?: when {
                recoveryActive -> MesoPhase.RECOVERY
                isRaceWeek || isTaperWeek -> MesoPhase.TAPER
                else -> MesoPhase.BASE
            }
            return MesoState(
                phase = phase,
                isDeload = fallbackWeekNumberInBlock % DELOAD_EVERY_WEEKS == 0 &&
                    allowsDeload(phase),
                strongStreak = 0,
                totalStrong = 0,
                fallbackUsed = phaseOverride == null,
            )
        }
        val streak = consecutiveStrongWeeks(references, targetWeekStart)
        val total = totalStrongWeeks(references, targetWeekStart)
        if (recoveryActive) {
            return MesoState(MesoPhase.RECOVERY, false, streak, total, false)
        }
        if (phaseOverride != null) {
            return MesoState(
                phaseOverride,
                allowsDeload(phaseOverride) && streak >= STREAK_FOR_DELOAD,
                streak,
                total,
                false,
            )
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

    /** Descarga só existe em `BASE`/`BUILD`; `PEAK`/`TAPER`/`RECOVERY` nunca descarregam. */
    private fun allowsDeload(phase: MesoPhase): Boolean = phase == MesoPhase.BASE || phase == MesoPhase.BUILD

    private const val DELOAD_EVERY_WEEKS = 4
}
