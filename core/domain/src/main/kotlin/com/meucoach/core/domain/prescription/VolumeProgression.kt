package com.meucoach.core.domain.prescription

import java.time.LocalDate
import kotlin.math.roundToInt

/** Why a week was not allowed to grow (ADR 0038, R-LOAD-03 v2 proposto). `NONE` means it grew. */
enum class VolumeGrowthBlock {
    NONE,
    NO_HISTORY,
    NO_PREVIOUS_WEEK,
    PREVIOUS_WEEK_BELOW_80,
    PREVIOUS_WEEK_SPECIAL,
    DELOAD,
    PEAK,
    TAPER,
    RACE_WEEK,
    RECOVERY,
    READINESS,
    LOAD_UNKNOWN,
    ACWR_UNRELIABLE,
    ACWR_OUT_OF_RANGE,
    MONOTONY_HIGH,
    STRAIN_SPIKE,
}

/**
 * Conditions that allow a weekly volume increase. The caller only translates
 * already-computed signals; the decision itself stays pure.
 *
 * R-LOAD-03 v3 (ADR 0046): a reliable ACWR inside 0.8–1.3 plus monotony < 2.0
 * and no reliable strain spike are required to grow. Without a full 28-day
 * baseline, volume is maintained.
 */
data class VolumeGates(
    /** The week being planned is a deload/taper/race/recovery week and never grows. */
    val targetWeekBlock: VolumeGrowthBlock = VolumeGrowthBlock.NONE,
    val readinessGreen: Boolean = true,
    val loadKnown: Boolean = true,
    /** The 28-day window is fully covered and the ACWR can decide. */
    val acwrReliable: Boolean = true,
    val acwrInRange: Boolean = true,
    val monotonyOk: Boolean = true,
    val strainOk: Boolean = true,
)

data class VolumeDecision(
    val minutes: Int,
    /** Highest completed volume in the window; `null` when no week qualifies. */
    val baseMinutes: Int?,
    val baseWeekStart: LocalDate?,
    /** 0 when the volume was maintained (or the ceiling stopped the increase). */
    val growthPercent: Int,
    /** True when the availability ceiling stopped the target (or the base itself). */
    val cappedByAvailability: Boolean,
    val block: VolumeGrowthBlock,
) {
    init {
        require(minutes >= 0) { "Volume target must not be negative" }
        require(growthPercent >= 0) { "Growth percent must not be negative" }
        require(baseMinutes == null || baseMinutes > 0) { "Volume base must be positive" }
        require(baseMinutes == null || baseWeekStart != null) { "A base requires its week start" }
    }

    val hasHistory: Boolean get() = baseMinutes != null
}

/**
 * Volume progression from what the athlete actually ran (ADR 0038/0050, FR-004/FR-408).
 *
 * The availability stays a hard ceiling. Without history the ceiling is used as
 * today. With history the base is the highest real strong volume in the last
 * four usable weeks — a dose the athlete already demonstrated, planned or not.
 * It only grows 6% (midpoint of the 5–8% rule, `R-PLAN-01`) when the base is the
 * immediately previous week, that week was at least 80% completed with strong
 * **planned** evidence and every guardrail is green; otherwise it is maintained.
 * Extra training counts for volume but never inflates adherence, so adherence is
 * read from [previousWeek], not from the reference. A weak week never lowers the
 * prescription on its own.
 */
class VolumeProgression {
    fun decide(
        targetWeekStart: LocalDate,
        availabilityCeilingMinutes: Int,
        references: List<VolumeReference>,
        previousWeek: WeeklyPlan? = null,
        gates: VolumeGates = VolumeGates(),
    ): VolumeDecision {
        require(availabilityCeilingMinutes >= 0) { "Availability ceiling must not be negative" }
        val previousWeekStart = targetWeekStart.minusWeeks(1)
        val usable = references
            .filter { it.isUsableVolumeReference(targetWeekStart) }
            .distinctBy { it.weekStart }
        val base = usable.maxWithOrNull(
            compareBy<VolumeReference>({ it.strongMinutes }, { it.weekStart }),
        ) ?: return VolumeDecision(
            minutes = availabilityCeilingMinutes,
            baseMinutes = null,
            baseWeekStart = null,
            growthPercent = 0,
            cappedByAvailability = false,
            block = VolumeGrowthBlock.NO_HISTORY,
        )

        val baseMinutes = base.strongMinutes
        val previous = references.firstOrNull { it.weekStart == previousWeekStart }
        val block = when {
            gates.targetWeekBlock != VolumeGrowthBlock.NONE -> gates.targetWeekBlock

            previous == null -> VolumeGrowthBlock.NO_PREVIOUS_WEEK

            base.weekStart != previousWeekStart && previous.specialWeek ->
                VolumeGrowthBlock.PREVIOUS_WEEK_SPECIAL

            base.weekStart != previousWeekStart -> VolumeGrowthBlock.PREVIOUS_WEEK_BELOW_80

            (previousWeek?.strongCompletionRatio() ?: 0.0) < MIN_COMPLETION ->
                VolumeGrowthBlock.PREVIOUS_WEEK_BELOW_80

            !gates.readinessGreen -> VolumeGrowthBlock.READINESS

            !gates.loadKnown -> VolumeGrowthBlock.LOAD_UNKNOWN

            !gates.acwrReliable -> VolumeGrowthBlock.ACWR_UNRELIABLE

            !gates.acwrInRange -> VolumeGrowthBlock.ACWR_OUT_OF_RANGE

            !gates.monotonyOk -> VolumeGrowthBlock.MONOTONY_HIGH

            !gates.strainOk -> VolumeGrowthBlock.STRAIN_SPIKE

            else -> VolumeGrowthBlock.NONE
        }

        if (block != VolumeGrowthBlock.NONE) {
            val maintained = minOf(baseMinutes, availabilityCeilingMinutes)
            return VolumeDecision(
                minutes = maintained,
                baseMinutes = baseMinutes,
                baseWeekStart = base.weekStart,
                growthPercent = 0,
                cappedByAvailability = baseMinutes > availabilityCeilingMinutes,
                block = block,
            )
        }

        val grown = (baseMinutes * GROWTH_FACTOR).roundToInt()
        val capped = grown > availabilityCeilingMinutes
        return VolumeDecision(
            minutes = minOf(grown, availabilityCeilingMinutes),
            baseMinutes = baseMinutes,
            baseWeekStart = base.weekStart,
            growthPercent = if (capped) 0 else GROWTH_PERCENT,
            cappedByAvailability = capped,
            block = VolumeGrowthBlock.NONE,
        )
    }

    /**
     * A week is a comparable reference only when it was planned from its own
     * Monday or earlier (a Sunday preview counts), is at most four weeks old,
     * was not a deload/taper/recovery week, has trainable sessions and proved
     * real strong volume (ADR 0050 §2.2).
     * A week without a plan is never a reference, even if it has strong runs.
     */
    private fun VolumeReference.isUsableVolumeReference(targetWeekStart: LocalDate): Boolean {
        if (!weekStart.isBefore(targetWeekStart)) return false
        if (weekStart.isBefore(targetWeekStart.minusWeeks(MAX_REFERENCE_WEEKS.toLong()))) return false
        val createdOn = createdOn ?: return false
        if (createdOn.isAfter(weekStart)) return false
        if (specialWeek) return false
        if (!hasPlan) return false
        if (plannedSessions == 0) return false
        if (strongMinutes <= 0) return false
        return true
    }

    companion object {
        /** Point in the documented 5–8% range; a single deterministic value. */
        const val GROWTH_PERCENT = 6
        const val GROWTH_FACTOR = 1.06
        const val MAX_REFERENCE_WEEKS = 4
        const val MIN_COMPLETION = 0.80
    }
}

/** Sessions that were part of the plan from its creation day; rest is never a dose. */
internal fun WeeklyPlan.trainableSessions(): List<PlannedSession> = sessions
    .filter { it.type != WorkoutType.REST }
    .filter { !it.date.isBefore(createdOn) }
    .filterNot { it.status == SessionStatus.CLOSED && it.closureReason?.isNonTrainable == true }

/**
 * Volume proven by strong evidence. Only `ACTIVITY`/`GUIDED` count, matching the
 * quality progression gate (ADR 0024): a click is not a completed session.
 * Adherence stays plan-based; real volume comes from [VolumeReference].
 */
internal fun WeeklyPlan.strongCompletionRatio(): Double? {
    val planned = trainableSessions()
    if (planned.isEmpty()) return null
    return planned.count {
        it.status == SessionStatus.DONE && it.completionEvidence?.isStrong == true
    }.toDouble() / planned.size
}
