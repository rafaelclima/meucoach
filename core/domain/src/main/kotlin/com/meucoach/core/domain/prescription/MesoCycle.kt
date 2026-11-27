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
    /** 0-based position inside the phase; feeds the quality rotation (ADR 0047 §2.7). */
    val weekInPhase: Int = 0,
    /** Duration of the phase; the light `BASE` fartlek only appears in its second half. */
    val phaseWeeks: Int? = null,
)

/**
 * Derives the mesocycle from persisted weeks, never from a volatile counter.
 *
 * A load week has at least one real strong run (planned or not), was created on
 * or before its own Monday and carries no taper/deload/recovery rule (ADR 0050
 * §2.3). A preview generated on Sunday counts as on time.
 * A hole (missing plan or a week without strong runs) resets the streak for deload
 * purposes but preserves the macrocycle phase, which uses the total in 12 weeks.
 */
object MesoCycle {
    const val MAX_HISTORY_WEEKS = 12
    const val STREAK_FOR_DELOAD = 3
    const val BASE_THRESHOLD_DAYS = 56L
    const val PEAK_WINDOW_DAYS = 21L

    fun isLoadWeek(reference: VolumeReference): Boolean {
        val createdOn = reference.createdOn ?: return false
        if (createdOn.isAfter(reference.weekStart)) return false
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
        /** 0-based position inside the macro phase, when [phaseOverride] is set. */
        overrideWeekInPhase: Int = 0,
        /** Full macro phase length, when [phaseOverride] is set. */
        overridePhaseWeeks: Int? = null,
    ): MesoState {
        if (references.isEmpty()) {
            return fallbackState(
                recoveryActive = recoveryActive,
                isRaceWeek = isRaceWeek,
                isTaperWeek = isTaperWeek,
                phaseOverride = phaseOverride,
                overrideWeekInPhase = overrideWeekInPhase,
                overridePhaseWeeks = overridePhaseWeeks,
                fallbackWeekNumberInBlock = fallbackWeekNumberInBlock,
            )
        }
        val streak = consecutiveStrongWeeks(references, targetWeekStart)
        val total = totalStrongWeeks(references, targetWeekStart)
        return when {
            recoveryActive -> fixedState(MesoPhase.RECOVERY, streak, total)

            phaseOverride != null -> MesoState(
                phaseOverride,
                allowsDeload(phaseOverride) && streak >= STREAK_FOR_DELOAD,
                streak,
                total,
                false,
                weekInPhase = overrideWeekInPhase,
                phaseWeeks = overridePhaseWeeks ?: rotationWeeks(phaseOverride),
            )

            isRaceWeek || isTaperWeek -> fixedState(MesoPhase.TAPER, streak, total)

            race == null -> rotationState(generalPhase(total), streak, total)

            else -> raceState(race, targetWeekStart, streak, total)
        }
    }

    private fun fallbackState(
        recoveryActive: Boolean,
        isRaceWeek: Boolean,
        isTaperWeek: Boolean,
        phaseOverride: MesoPhase?,
        overrideWeekInPhase: Int,
        overridePhaseWeeks: Int?,
        fallbackWeekNumberInBlock: Int,
    ): MesoState {
        val phase = phaseOverride ?: when {
            recoveryActive -> MesoPhase.RECOVERY
            isRaceWeek || isTaperWeek -> MesoPhase.TAPER
            else -> MesoPhase.BASE
        }
        val window = if (phaseOverride != null) {
            overrideWeekInPhase to (overridePhaseWeeks ?: rotationWeeks(phase))
        } else {
            fallbackWindow(phase, fallbackWeekNumberInBlock)
        }
        return MesoState(
            phase = phase,
            isDeload = fallbackWeekNumberInBlock % DELOAD_EVERY_WEEKS == 0 && allowsDeload(phase),
            strongStreak = 0,
            totalStrong = 0,
            fallbackUsed = phaseOverride == null,
            weekInPhase = window.first,
            phaseWeeks = window.second,
        )
    }

    private fun generalPhase(total: Int): MesoPhase =
        if (total / DELOAD_EVERY_WEEKS % 2 == 0) MesoPhase.BASE else MesoPhase.BUILD

    private fun raceState(race: RacePlan, targetWeekStart: LocalDate, streak: Int, total: Int): MesoState {
        val daysTo = ChronoUnit.DAYS.between(targetWeekStart, race.targetDate)
        return when {
            daysTo < 0 || daysTo > BASE_THRESHOLD_DAYS -> rotationState(MesoPhase.BASE, streak, total)

            daysTo > race.taperWindowDays + PEAK_WINDOW_DAYS -> rotationState(MesoPhase.BUILD, streak, total)

            // PEAK mantém volume estável: nunca é descarga.
            daysTo > race.taperWindowDays -> fixedState(MesoPhase.PEAK, streak, total)

            else -> fixedState(MesoPhase.TAPER, streak, total)
        }
    }

    private fun fixedState(phase: MesoPhase, streak: Int, total: Int): MesoState = MesoState(
        phase = phase,
        isDeload = false,
        strongStreak = streak,
        totalStrong = total,
        fallbackUsed = false,
        weekInPhase = 0,
        phaseWeeks = 1,
    )

    /** General BASE/BUILD alternates every four load weeks (ADR 0041). */
    private fun rotationState(phase: MesoPhase, streak: Int, total: Int): MesoState {
        val weeks = rotationWeeks(phase)
        return MesoState(
            phase = phase,
            isDeload = streak >= STREAK_FOR_DELOAD,
            strongStreak = streak,
            totalStrong = total,
            fallbackUsed = false,
            weekInPhase = if (weeks > 1) total % weeks else 0,
            phaseWeeks = weeks,
        )
    }

    private fun fallbackWindow(phase: MesoPhase, weekNumberInBlock: Int): Pair<Int, Int> {
        val weeks = rotationWeeks(phase)
        val position = if (weeks > 1) (weekNumberInBlock - 1) % weeks else 0
        return position to weeks
    }

    private fun rotationWeeks(phase: MesoPhase): Int =
        if (phase == MesoPhase.BASE || phase == MesoPhase.BUILD) DELOAD_EVERY_WEEKS else 1

    /** Descarga só existe em `BASE`/`BUILD`; `PEAK`/`TAPER`/`RECOVERY` nunca descarregam. */
    private fun allowsDeload(phase: MesoPhase): Boolean = phase == MesoPhase.BASE || phase == MesoPhase.BUILD

    private const val DELOAD_EVERY_WEEKS = 4
}
