package com.meucoach.core.domain.prescription

import java.time.LocalDate
import kotlin.math.roundToInt

/** Why a week was not allowed to grow (ADR 0038). `NONE` means it grew. */
enum class VolumeGrowthBlock {
    NONE,
    NO_HISTORY,
    NO_PREVIOUS_WEEK,
    PREVIOUS_WEEK_BELOW_80,
    PREVIOUS_WEEK_SPECIAL,
    DELOAD,
    TAPER,
    RACE_WEEK,
    RECOVERY,
    READINESS,
    LOAD_UNKNOWN,
    ACWR_OUT_OF_RANGE,
}

/**
 * Conditions that allow a weekly volume increase. The caller only translates
 * already-computed signals; the decision itself stays pure.
 */
data class VolumeGates(
    /** The week being planned is a deload/taper/race/recovery week and never grows. */
    val targetWeekBlock: VolumeGrowthBlock = VolumeGrowthBlock.NONE,
    val readinessGreen: Boolean = true,
    val loadKnown: Boolean = true,
    val acwrInRange: Boolean = true,
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
 * Volume progression from what the athlete actually completed (ADR 0038, FR-004).
 *
 * The availability stays a hard ceiling. Without history the ceiling is used as
 * today. With history the base is the highest completed volume in the last four
 * usable weeks — a dose the athlete already demonstrated. It only grows 6%
 * (midpoint of the 5–8% rule, `R-PLAN-01`) when the base is the immediately
 * previous week, that week was at least 80% completed with strong evidence and
 * every guardrail is green; otherwise it is maintained. A weak week never lowers
 * the prescription on its own.
 */
class VolumeProgression {
    fun decide(
        targetWeekStart: LocalDate,
        availabilityCeilingMinutes: Int,
        references: List<WeeklyPlan>,
        gates: VolumeGates = VolumeGates(),
    ): VolumeDecision {
        require(availabilityCeilingMinutes >= 0) { "Availability ceiling must not be negative" }
        val previousWeekStart = targetWeekStart.minusWeeks(1)
        val usable = references
            .filter { it.isUsableVolumeReference(targetWeekStart) }
            .distinctBy { it.weekStart }
        val base = usable.maxWithOrNull(
            compareBy<WeeklyPlan>({ it.completedVolumeMinutes() }, { it.weekStart }),
        ) ?: return VolumeDecision(
            minutes = availabilityCeilingMinutes,
            baseMinutes = null,
            baseWeekStart = null,
            growthPercent = 0,
            cappedByAvailability = false,
            block = VolumeGrowthBlock.NO_HISTORY,
        )

        val baseMinutes = base.completedVolumeMinutes()
        val previous = references.firstOrNull { it.weekStart == previousWeekStart }
        val block = when {
            gates.targetWeekBlock != VolumeGrowthBlock.NONE -> gates.targetWeekBlock

            previous == null -> VolumeGrowthBlock.NO_PREVIOUS_WEEK

            base.weekStart != previousWeekStart && !previous.isUsableVolumeReference(targetWeekStart) ->
                VolumeGrowthBlock.PREVIOUS_WEEK_SPECIAL

            base.weekStart != previousWeekStart -> VolumeGrowthBlock.PREVIOUS_WEEK_BELOW_80

            (previous.strongCompletionRatio() ?: 0.0) < MIN_COMPLETION -> VolumeGrowthBlock.PREVIOUS_WEEK_BELOW_80

            !gates.readinessGreen -> VolumeGrowthBlock.READINESS

            !gates.loadKnown -> VolumeGrowthBlock.LOAD_UNKNOWN

            !gates.acwrInRange -> VolumeGrowthBlock.ACWR_OUT_OF_RANGE

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
     * Monday, is at most four weeks old, was not a deload/taper/recovery week and
     * has at least one session completed with strong evidence.
     */
    private fun WeeklyPlan.isUsableVolumeReference(targetWeekStart: LocalDate): Boolean {
        if (!weekStart.isBefore(targetWeekStart)) return false
        if (weekStart.isBefore(targetWeekStart.minusWeeks(MAX_REFERENCE_WEEKS.toLong()))) return false
        if (createdOn.isAfter(weekStart)) return false
        if (ruleIds.any { it in SPECIAL_WEEK_RULES }) return false
        if (trainableSessions().isEmpty()) return false
        return completedVolumeMinutes() > 0
    }

    companion object {
        /** Point in the documented 5–8% range; a single deterministic value. */
        const val GROWTH_PERCENT = 6
        const val GROWTH_FACTOR = 1.06
        const val MAX_REFERENCE_WEEKS = 4
        const val MIN_COMPLETION = 0.80

        private val SPECIAL_WEEK_RULES = setOf("R-PLAN-04", "R-PLAN-05", "R-PLAN-10", "R-RACE-04")
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
 */
internal fun WeeklyPlan.completedVolumeMinutes(): Int = trainableSessions()
    .filter { it.status == SessionStatus.DONE && it.completionEvidence?.isStrong == true }
    .sumOf { it.durationMin ?: 0 }

/** Share of planned sessions completed with strong evidence; `null` when nothing was planned. */
internal fun WeeklyPlan.strongCompletionRatio(): Double? {
    val planned = trainableSessions()
    if (planned.isEmpty()) return null
    return planned.count {
        it.status == SessionStatus.DONE && it.completionEvidence?.isStrong == true
    }.toDouble() / planned.size
}
