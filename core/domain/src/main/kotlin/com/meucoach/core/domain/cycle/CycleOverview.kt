package com.meucoach.core.domain.cycle

import com.meucoach.core.domain.prescription.MacroPlan
import com.meucoach.core.domain.prescription.MacroPlanResolver
import com.meucoach.core.domain.prescription.MesoCycle
import com.meucoach.core.domain.prescription.MesoPhase
import com.meucoach.core.domain.prescription.RacePlan
import com.meucoach.core.domain.prescription.RecoveryWindow
import com.meucoach.core.domain.prescription.SPECIAL_WEEK_RULE_IDS
import com.meucoach.core.domain.prescription.VolumeReference
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.RaceDistance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Read-only role of a week inside the visible cycle (ADR 0072). */
enum class CycleWeekState {
    CURRENT,
    NEXT,
    FUTURE,
    RACE,
    RECOVERY,
}

/** How the weekly volume is expected to move; never a future minute target. */
enum class CycleVolumeIntent {
    BUILD,
    MAINTAIN,
    REDUCE,
    RACE,
    RECOVER,
    UNKNOWN,
}

/** Upper bound of strong sessions, not a promise of future sessions. */
enum class CycleStrongIntent {
    NONE,
    MICRO_DOSE,
    UP_TO_ONE,
    UP_TO_TWO,
}

/** How the deload status is known: confirmed by a plan or a conditional scenario. */
enum class CycleDeload {
    NONE,
    CONFIRMED,
    CONDITIONAL,
}

enum class CycleMilestoneKind {
    PREPARATION_START,
    TAPER_START,
    RACE,
    DELOAD,
}

data class CycleRace(
    val eventId: String,
    val distance: RaceDistance,
    val targetDate: LocalDate,
    /** Whole weeks between the current Monday and the race week (0 on race week). */
    val weeksToRace: Int,
) {
    init {
        require(eventId.isNotBlank()) { "Cycle race event id must not be blank" }
        require(weeksToRace >= 0) { "Weeks to race must not be negative" }
    }
}

/**
 * One visible week. [plannedMinutes] and [strongDays] only exist for the
 * current week and the persisted next-week preview; future weeks never carry
 * session-level data (ADR 0072 §2.8).
 */
data class CycleWeek(
    val weekStart: LocalDate,
    val phase: MesoPhase?,
    val state: CycleWeekState,
    val isCurrent: Boolean,
    val isNext: Boolean,
    val volume: CycleVolumeIntent,
    val strong: CycleStrongIntent,
    val plannedMinutes: Int? = null,
    val strongDays: List<LocalDate> = emptyList(),
    val isPreview: Boolean = false,
    val partialWeek: Boolean = false,
    val deload: CycleDeload = CycleDeload.NONE,
    val deloadConditionWeeks: Int? = null,
    val confidence: DataConfidence = DataConfidence.MEDIUM,
) {
    init {
        require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "Cycle week must start on Monday" }
        require(isCurrent || isNext || state == CycleWeekState.FUTURE || state == CycleWeekState.RACE) {
            "Current/next flags must match the visible state"
        }
        require(plannedMinutes == null || plannedMinutes >= 0) {
            "Planned minutes must not be negative"
        }
        require(strongDays.all { it in weekStart..weekStart.plusDays(6) }) {
            "Strong days must belong to the week"
        }
        require(state != CycleWeekState.FUTURE || plannedMinutes == null) {
            "A future week cannot carry planned minutes"
        }
        require(state != CycleWeekState.FUTURE || strongDays.isEmpty()) {
            "A future week cannot carry strong days"
        }
        require(!isPreview || isNext) { "Only the next week can be a preview" }
        require(!partialWeek || isCurrent) { "Only the current week can be partial" }
        require(deload != CycleDeload.CONDITIONAL || deloadConditionWeeks != null) {
            "A conditional deload needs the number of load weeks"
        }
        require(deload != CycleDeload.CONFIRMED || deloadConditionWeeks == null) {
            "A confirmed deload cannot carry a condition"
        }
    }

    val weekEnd: LocalDate get() = weekStart.plusDays(6)
}

data class CycleMilestone(val kind: CycleMilestoneKind, val weekStart: LocalDate, val conditional: Boolean = false) {
    init {
        require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "Milestone week must start on Monday" }
    }
}

data class CycleOverview(
    val today: LocalDate,
    val race: CycleRace?,
    val weeks: List<CycleWeek>,
    val milestones: List<CycleMilestone>,
    val preparationStart: LocalDate?,
    val staleRace: Boolean,
    val hasLoadHistory: Boolean,
    val confidence: DataConfidence,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val recoveryUntil: LocalDate? = null,
) {
    init {
        require(weeks.zipWithNext().all { (first, second) -> first.weekStart.isBefore(second.weekStart) }) {
            "Weeks must be strictly ordered"
        }
        require(weeks.size <= MAX_VISIBLE_WEEKS) { "Cycle overview shows at most $MAX_VISIBLE_WEEKS weeks" }
        require(milestones.zipWithNext().all { (first, second) -> !second.weekStart.isBefore(first.weekStart) }) {
            "Milestones must be ordered"
        }
    }

    companion object {
        /** UI horizon: current week plus five (ADR 0072 §2.2). */
        const val MAX_VISIBLE_WEEKS = 6
    }
}

/** Inputs already resolved at the edge; the calculator never reads repositories. */
data class CycleOverviewInput(
    val today: LocalDate,
    val experience: Experience,
    val race: RacePlan? = null,
    /** Local date of the last race edit; used to warn about a stale current plan. */
    val raceUpdatedOn: LocalDate? = null,
    val currentPlan: WeeklyPlan? = null,
    val nextWeekPlan: WeeklyPlan? = null,
    val references: List<VolumeReference> = emptyList(),
    val recovery: RecoveryWindow? = null,
    val maxWeeks: Int = CycleOverview.MAX_VISIBLE_WEEKS,
) {
    init {
        require(maxWeeks in 1..CycleOverview.MAX_VISIBLE_WEEKS) {
            "Cycle horizon must be between 1 and ${CycleOverview.MAX_VISIBLE_WEEKS} weeks"
        }
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        require(currentPlan == null || currentPlan.weekStart == currentWeekStart) {
            "The current plan must belong to the current week"
        }
        require(nextWeekPlan == null || nextWeekPlan.weekStart == currentWeekStart.plusWeeks(1)) {
            "The next plan must belong to the next week"
        }
    }
}

/**
 * Read-only projection of the race preparation (ADR 0072).
 *
 * Reuses [MacroPlanResolver] per visible week (so the general → specific
 * transition is explicit), and [MesoCycle] only to answer a conditional deload.
 * No weekly plan, session, prescription or persistence is created here.
 */
object CycleOverviewCalculator {
    fun calculate(input: CycleOverviewInput): CycleOverview {
        val window = window(input) ?: return empty(input)
        val weeks = window.visibleWeeks.map { week -> week(window, week) }
        return overview(window, weeks)
    }

    private fun window(input: CycleOverviewInput): Window? {
        val today = input.today
        val currentWeekStart = weekStart(today)
        val race = input.race?.takeIf { !it.targetDate.isBefore(today) } ?: return null
        val raceWeekStart = weekStart(race.targetDate)
        val weeksToRace = ChronoUnit.WEEKS.between(currentWeekStart, raceWeekStart).toInt()
        val visibleCount = minOf(input.maxWeeks, weeksToRace + 1)
        val visibleWeeks = (0 until visibleCount).map { currentWeekStart.plusWeeks(it.toLong()) }
        val currentPlan = input.currentPlan
        val staleRace = currentPlan != null &&
            input.raceUpdatedOn != null &&
            input.raceUpdatedOn.isAfter(currentPlan.createdOn)
        return Window(
            input = input,
            currentWeekStart = currentWeekStart,
            race = race,
            raceWeekStart = raceWeekStart,
            weeksToRace = weeksToRace,
            visibleWeeks = visibleWeeks,
            macro = MacroPlanResolver.resolve(race, today),
            staleRace = staleRace,
            conditional = conditionalDeload(input, race, currentWeekStart, visibleWeeks),
            hasLoadHistory = input.references.any { reference ->
                reference.weekStart.isBefore(currentWeekStart) && reference.strongSessions > 0
            },
            recoveryUntil = input.recovery
                ?.takeIf {
                    it.isActive(today) ||
                        it.intersects(currentWeekStart, currentWeekStart.plusDays(6))
                }
                ?.endDate(),
        )
    }

    private data class Window(
        val input: CycleOverviewInput,
        val currentWeekStart: LocalDate,
        val race: RacePlan,
        val raceWeekStart: LocalDate,
        val weeksToRace: Int,
        val visibleWeeks: List<LocalDate>,
        val macro: MacroPlan,
        val staleRace: Boolean,
        val conditional: ConditionalDeload?,
        val hasLoadHistory: Boolean,
        val recoveryUntil: LocalDate?,
    )

    private fun week(window: Window, weekStart: LocalDate): CycleWeek {
        val isCurrent = weekStart == window.currentWeekStart
        val isNext = weekStart == window.currentWeekStart.plusWeeks(1)
        val isRaceWeek = weekStart == window.raceWeekStart
        val phase = phaseIntent(window.race, weekStart)?.phase
        val plan = when {
            isCurrent -> window.input.currentPlan
            isNext -> window.input.nextWeekPlan
            else -> null
        }
        // A race edit after the current plan was created makes the new phase
        // disagree with the persisted week. The plan remains the authority
        // until the user re-evaluates it, so the current row shows no phase.
        val effectivePhase = phase.takeUnless { window.staleRace && isCurrent }
        val state = state(isCurrent, isNext, isRaceWeek)
        val deload = deload(window, weekStart, isCurrent, isNext, plan)
        return CycleWeek(
            weekStart = weekStart,
            phase = effectivePhase,
            state = state,
            isCurrent = isCurrent,
            isNext = isNext,
            volume = volumeIntent(effectivePhase, state, deload),
            strong = strongIntent(effectivePhase, window.input.experience, isRaceWeek),
            plannedMinutes = plan?.weeklyMinutes?.takeIf { plan.sessions.isNotEmpty() },
            strongDays = strongDays(plan, isCurrent || isNext),
            isPreview = isNext && window.input.nextWeekPlan != null,
            partialWeek = isCurrent &&
                window.input.currentPlan != null &&
                window.input.currentPlan.createdOn.isAfter(weekStart),
            deload = deload,
            deloadConditionWeeks = window.conditional
                ?.takeIf { it.weekStart == weekStart }
                ?.loadWeeks,
            confidence = confidence(deload, plan, phase),
        )
    }

    private fun state(isCurrent: Boolean, isNext: Boolean, isRaceWeek: Boolean): CycleWeekState = when {
        isRaceWeek -> CycleWeekState.RACE
        isCurrent -> CycleWeekState.CURRENT
        isNext -> CycleWeekState.NEXT
        else -> CycleWeekState.FUTURE
    }

    private fun deload(
        window: Window,
        weekStart: LocalDate,
        isCurrent: Boolean,
        isNext: Boolean,
        plan: WeeklyPlan?,
    ): CycleDeload {
        val confirmed = (isCurrent || isNext) && plan?.ruleIds?.contains(DELOAD_RULE_ID) == true
        return when {
            confirmed -> CycleDeload.CONFIRMED
            window.conditional?.weekStart == weekStart && plan == null -> CycleDeload.CONDITIONAL
            else -> CycleDeload.NONE
        }
    }

    private fun strongDays(plan: WeeklyPlan?, visible: Boolean): List<LocalDate> {
        if (!visible) return emptyList()
        return plan
            ?.sessions
            .orEmpty()
            .filter { it.type == WorkoutType.QUALITY }
            .map { it.date }
            .sorted()
    }

    private fun confidence(deload: CycleDeload, plan: WeeklyPlan?, phase: MesoPhase?): DataConfidence = when {
        deload == CycleDeload.CONDITIONAL -> DataConfidence.LOW
        plan != null -> plan.confidence
        phase == null -> DataConfidence.LOW
        else -> DataConfidence.MEDIUM
    }

    private fun overview(window: Window, weeks: List<CycleWeek>): CycleOverview {
        val hasDeload = weeks.any { it.deload != CycleDeload.NONE }
        return CycleOverview(
            today = window.input.today,
            race = CycleRace(
                eventId = window.race.eventId,
                distance = window.race.distance,
                targetDate = window.race.targetDate,
                weeksToRace = window.weeksToRace,
            ),
            weeks = weeks,
            milestones = milestones(
                currentWeekStart = window.currentWeekStart,
                race = window.race,
                raceWeekStart = window.raceWeekStart,
                macroPreparationStart = window.macro.preparationStart,
                weeks = weeks,
                conditional = window.conditional,
            ),
            preparationStart = window.macro.preparationStart
                ?.takeIf { it.isAfter(window.currentWeekStart) },
            staleRace = window.staleRace,
            hasLoadHistory = window.hasLoadHistory,
            confidence = if (weeks.any { it.phase != null }) {
                DataConfidence.MEDIUM
            } else {
                DataConfidence.LOW
            },
            ruleIds = buildList {
                add(PERIODIZATION_RULE_ID)
                if (hasDeload) add(DELOAD_RULE_ID)
                add(TAPER_RULE_ID)
            },
            evidenceIds = EVIDENCE_IDS,
            recoveryUntil = window.recoveryUntil,
        )
    }

    private fun empty(input: CycleOverviewInput): CycleOverview {
        val currentWeekStart = weekStart(input.today)
        return CycleOverview(
            today = input.today,
            race = null,
            weeks = emptyList(),
            milestones = emptyList(),
            preparationStart = null,
            staleRace = false,
            hasLoadHistory = input.references.any { reference ->
                reference.weekStart.isBefore(currentWeekStart) && reference.strongSessions > 0
            },
            confidence = DataConfidence.LOW,
            ruleIds = emptyList(),
            evidenceIds = emptyList(),
            recoveryUntil = input.recovery
                ?.takeIf {
                    it.isActive(input.today) ||
                        it.intersects(currentWeekStart, currentWeekStart.plusDays(6))
                }
                ?.endDate(),
        )
    }

    private fun phaseIntent(race: RacePlan, weekStart: LocalDate) = MacroPlanResolver.resolve(race, weekStart).weeks
        .singleOrNull { it.weekStart == weekStart }

    private fun volumeIntent(phase: MesoPhase?, state: CycleWeekState, deload: CycleDeload): CycleVolumeIntent = when {
        state == CycleWeekState.RACE -> CycleVolumeIntent.RACE
        deload != CycleDeload.NONE -> CycleVolumeIntent.REDUCE
        phase == MesoPhase.BASE || phase == MesoPhase.BUILD -> CycleVolumeIntent.BUILD
        phase == MesoPhase.PEAK -> CycleVolumeIntent.MAINTAIN
        phase == MesoPhase.TAPER -> CycleVolumeIntent.REDUCE
        phase == MesoPhase.RECOVERY -> CycleVolumeIntent.RECOVER
        else -> CycleVolumeIntent.UNKNOWN
    }

    private fun strongIntent(phase: MesoPhase?, experience: Experience, isRaceWeek: Boolean): CycleStrongIntent = when {
        isRaceWeek || phase == MesoPhase.TAPER -> CycleStrongIntent.MICRO_DOSE
        phase == MesoPhase.RECOVERY -> CycleStrongIntent.NONE
        experience == Experience.BEGINNER -> CycleStrongIntent.NONE
        phase == MesoPhase.BASE -> CycleStrongIntent.UP_TO_ONE
        phase == MesoPhase.BUILD || phase == MesoPhase.PEAK -> CycleStrongIntent.UP_TO_TWO
        else -> CycleStrongIntent.UP_TO_ONE
    }

    /**
     * Projects the first deload inside the visible horizon assuming the athlete
     * completes every normal week. The simulated references never leave this
     * calculation and never feed the real mesocycle.
     */
    private fun conditionalDeload(
        input: CycleOverviewInput,
        race: RacePlan,
        currentWeekStart: LocalDate,
        visibleWeeks: List<LocalDate>,
    ): ConditionalDeload? {
        val history = input.references
            .filter { it.weekStart.isBefore(currentWeekStart) && it.strongSessions > 0 }
            .distinctBy { it.weekStart }
        if (history.isEmpty()) return null
        val simulated = history.toMutableList()
        var loadWeeks = 0
        visibleWeeks.forEach { week ->
            if (week != currentWeekStart) {
                val macro = MacroPlanResolver.resolve(race, week)
                val intent = macro.weeks.singleOrNull { it.weekStart == week }
                val meso = MesoCycle.resolve(
                    targetWeekStart = week,
                    race = race.takeUnless { macro.isGeneral },
                    recoveryActive = input.recovery?.intersects(week, week.plusDays(6)) == true,
                    isRaceWeek = race.targetDate in week..week.plusDays(6),
                    isTaperWeek = (0L..6L).any { race.isInTaperWindow(week.plusDays(it)) },
                    references = simulated,
                    fallbackWeekNumberInBlock = 1,
                    phaseOverride = intent?.phase,
                    overrideWeekInPhase = intent?.weekInPhase ?: 0,
                    overridePhaseWeeks = intent?.phaseWeeks,
                )
                if (meso.isDeload) return ConditionalDeload(week, loadWeeks.coerceAtLeast(1))
            }
            val plan = when (week) {
                currentWeekStart -> input.currentPlan
                currentWeekStart.plusWeeks(1) -> input.nextWeekPlan
                else -> null
            }
            val isDeloadWeek = plan?.ruleIds?.contains(DELOAD_RULE_ID) == true
            val isSpecial = isDeloadWeek || plan?.ruleIds.orEmpty().any { it in SPECIAL_WEEK_RULE_IDS }
            simulated += simulatedReference(week, isSpecial)
            if (!isSpecial) loadWeeks += 1
        }
        return null
    }

    private fun simulatedReference(weekStart: LocalDate, special: Boolean) = VolumeReference(
        weekStart = weekStart,
        plannedMinutes = 1,
        executedMinutes = 1,
        strongMinutes = 1,
        strongSessions = 1,
        plannedSessions = 1,
        createdOn = weekStart,
        specialWeek = special,
    )

    private fun milestones(
        currentWeekStart: LocalDate,
        race: RacePlan,
        raceWeekStart: LocalDate,
        macroPreparationStart: LocalDate?,
        weeks: List<CycleWeek>,
        conditional: ConditionalDeload?,
    ): List<CycleMilestone> {
        val result = mutableListOf<CycleMilestone>()
        macroPreparationStart
            ?.takeIf { it.isAfter(currentWeekStart) }
            ?.let { result += CycleMilestone(CycleMilestoneKind.PREPARATION_START, it) }
        val firstTaperWeek = raceWeekStart.minusWeeks((race.taperWindowDays / DAYS_PER_WEEK - 1).toLong())
        if (!firstTaperWeek.isBefore(currentWeekStart)) {
            result += CycleMilestone(CycleMilestoneKind.TAPER_START, firstTaperWeek)
        }
        weeks
            .firstOrNull { it.deload == CycleDeload.CONFIRMED }
            ?.let { result += CycleMilestone(CycleMilestoneKind.DELOAD, it.weekStart) }
        conditional
            ?.takeIf { weeks.firstOrNull { week -> week.deload == CycleDeload.CONFIRMED } == null }
            ?.let { result += CycleMilestone(CycleMilestoneKind.DELOAD, it.weekStart, conditional = true) }
        result += CycleMilestone(CycleMilestoneKind.RACE, raceWeekStart)
        return result.sortedWith(compareBy({ it.weekStart }, { it.kind.ordinal }))
    }

    private fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private data class ConditionalDeload(val weekStart: LocalDate, val loadWeeks: Int)

    private const val DAYS_PER_WEEK = 7
    private const val PERIODIZATION_RULE_ID = "R-PER-01"
    private const val DELOAD_RULE_ID = "R-PLAN-04"
    private const val TAPER_RULE_ID = "R-PLAN-05"
    private val EVIDENCE_IDS = listOf("EV-ISSURIN-2010", "EV-BOSQUET-2007", "EV-MUJIKA-2003")
}
