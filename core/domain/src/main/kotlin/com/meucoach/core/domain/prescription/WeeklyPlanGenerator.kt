package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadDataState
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.domain.zones.PaceZoneCalculator
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.PaceZone
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.isRace
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

enum class SessionStatus {
    PLANNED,
    DONE,
    CLOSED,
}

data class PlannedSession(
    val date: LocalDate,
    val type: WorkoutType,
    val title: String,
    val durationMin: Int?,
    val rationale: String,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    /** Objective/subjective targets of the session; empty when not applicable. */
    val targets: PrescriptionTargets = PrescriptionTargets(primary = TargetKind.RPE),
    val status: SessionStatus = SessionStatus.PLANNED,
    val skipReason: String? = null,
    /** Present only on [SessionStatus.DONE]; see ADR 0024. */
    val completionEvidence: CompletionEvidence? = null,
    /** Persisted execution that proves the completion. */
    val executionId: String? = null,
    /** Imported activity linked to this session, when the evidence is ACTIVITY. */
    val activityId: String? = null,
    /** Present only on [SessionStatus.CLOSED]. */
    val closureReason: SessionClosureReason? = null,
    val closedAt: Instant? = null,
    /**
     * Nature of the strong stimulus (ADR 0044 §2.2). Legacy rows have `null`
     * until the catalog fills it (C4-15); only a `QUALITY` session may carry
     * one.
     */
    val qualityKind: QualityKind? = null,
    /** Why the plan created or withheld the opportunity (ADR 0044 §3.1). */
    val planDecision: PlanDecision? = null,
    /**
     * Executable structure when the catalog provides one (ADR 0047 §2.1).
     * Legacy rows and sessions without a prescription keep `null`; persistence
     * arrives with schema v18 (C4-45).
     */
    val structure: WorkoutStructure? = null,
) {
    init {
        require(title.isNotBlank()) { "Session title must not be blank" }
        require(ruleIds.isNotEmpty()) { "Session must cite at least one rule" }
        require(evidenceIds.isNotEmpty()) { "Session must cite at least one evidence" }
        require(qualityKind == null || type == WorkoutType.QUALITY) {
            "Only a quality session carries a quality kind"
        }
        require(status != SessionStatus.DONE || completionEvidence != null) {
            "A done session requires completion evidence"
        }
        require(status != SessionStatus.CLOSED || closureReason != null) {
            "A closed session requires a closure reason"
        }
        require(completionEvidence == null || status == SessionStatus.DONE) {
            "Only a done session carries completion evidence"
        }
        require(closureReason == null || status == SessionStatus.CLOSED) {
            "Only a closed session carries a closure reason"
        }
        require(
            activityId == null ||
                completionEvidence == CompletionEvidence.ACTIVITY ||
                completionEvidence == CompletionEvidence.LEGACY_UNVERIFIED,
        ) {
            "An activity link requires ACTIVITY or suspended LEGACY_UNVERIFIED evidence"
        }
        require(completionEvidence != CompletionEvidence.ACTIVITY || activityId != null) {
            "ACTIVITY evidence requires a linked activity"
        }
    }
}

data class WeeklyPlan(
    val weekStart: LocalDate,
    val sessions: List<PlannedSession>,
    val weeklyMinutes: Int,
    val longRunMinutes: Int?,
    /**
     * Number of planned quality **opportunities**, not confirmed sessions
     * (ADR 0044 §2.2). The day may keep or downgrade each one; the UI and the
     * weekly review distinguish planned/kept/downgraded/not done.
     */
    val qualityCount: Int,
    val rationale: String,
    val confidence: DataConfidence,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    /**
     * Local date when this version was created. Adherence never counts days
     * before it, so a plan created midweek does not punish the past (N-02).
     */
    val createdOn: LocalDate = weekStart,
    /**
     * Engine version that generated this plan (ADR 0048 §2.3). Legacy callers
     * read the last v6 default; the upgrade path (C4-47) compares the stored
     * version against the current engine to decide a regeneration.
     */
    val engineVersion: String = WeeklyPlanGenerator.ENGINE_VERSION,
) {
    init {
        require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "Week must start on Monday" }
        require(rationale.isNotBlank()) { "Plan must have a rationale" }
    }
}

data class WeeklyPlanInput(
    val profile: AthleteProfile,
    val readiness: ReadinessResult,
    val load: LoadSnapshot? = null,
    val weekStart: LocalDate,
    /** Local date of generation; only days from here to Sunday enter the plan. */
    val today: LocalDate,
    val previousWeek: WeeklyPlan? = null,
    /**
     * Legacy 1-based position in the block. Deprecated: the deload now derives
     * from [recentWeeks] (ADR 0041); kept for one release as a fallback only
     * when the history is empty.
     */
    @Deprecated("Derivado de recentWeeks (ADR 0041); fallback só sem histórico")
    val weekNumberInBlock: Int = 1,
    /** Active race for taper/cutoff; falls back to the legacy profile goal when absent. */
    val race: RacePlan? = null,
    /**
     * Declared goal time for the active race (FR-411). It is a reference used
     * only to explain the gap against the current prediction; the prescribed
     * pace always comes from the prediction, never from this value.
     */
    val raceTargetTimeSec: Int? = null,
    /** Every event date that must not receive an ordinary training session. */
    val eventDates: Set<LocalDate> = emptySet(),
    /** Post-race recovery window (ADR 0037); caps sessions and blocks quality/long run. */
    val recovery: RecoveryWindow? = null,
    /**
     * Previous persisted weeks, most recent first or any order (ADR 0038/0041,
     * up to 12 weeks). The volume base uses the last 4 usable weeks; the
     * mesocycle (phase + deload) uses up to 12 weeks of real strong runs.
     */
    val recentWeeks: List<WeeklyPlan> = emptyList(),
    /**
     * Real weekly execution projection (ADR 0050). The volume base and the
     * mesocycle read [VolumeReference.strongMinutes]/[VolumeReference.strongSessions];
     * adherence keeps coming from [previousWeek]'s plan. `null` means a legacy
     * caller without activities: the generator derives a conservative reference
     * from planned strong completions only. Production always passes the
     * projection (an empty list means "no real reference" and never falls back).
     */
    val volumeReferences: List<VolumeReference>? = null,
    /**
     * Health sync had not settled when the week was generated (ADR 0048 §2.1).
     * The plan is generated with the available data and says so explicitly;
     * the upgrade path re-evaluates it when the data arrives.
     */
    val dataIncomplete: Boolean = false,
    /**
     * A long run was already completed this week (ADR 0028 addendum 19/11/2026).
     * The week keeps a single longão: no new long day is scheduled and the
     * remaining days stay easy (or keep their quality opportunity).
     */
    val longAlreadyDone: Boolean = false,
) {
    init {
        require(weekNumberInBlock >= 1) { "Week number in block must be >= 1" }
        require(raceTargetTimeSec == null || raceTargetTimeSec > 0) {
            "Race target time must be positive"
        }
    }
}

/**
 * Builds a conservative week from the profile availability.
 *
 * The availability is a hard ceiling. Volume grows at most 6% over the highest
 * recently completed week and only with a green, evidenced, known-load week
 * (ADR 0038); otherwise it is maintained. The plan allows at most two quality
 * **opportunities**: the first is structural (level, days, phase and a safe day),
 * the second additionally requires proven progression, and the day gate decides
 * whether each one is kept or downgraded (ADR 0044). They are kept away from the
 * long run. The deload derives from 3 consecutive load weeks in `recentWeeks`
 * (R-PLAN-04 v2, ADR 0041). A taper or recovery always takes precedence over the
 * deload, and a race week is never a deload.
 */
class WeeklyPlanGenerator {
    fun generate(input: WeeklyPlanInput): WeeklyPlan {
        val weekStart = input.weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val availability = input.profile.weeklyAvailability.minutesByDay
            .filterKeys { it in DayOfWeek.entries }
            .toSortedMap()
        val recovery = input.recovery
        val base = WeeklyPlan(
            weekStart = weekStart,
            sessions = emptyList(),
            weeklyMinutes = 0,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Sem dados suficientes para montar a semana.",
            confidence = DataConfidence.LOW,
            ruleIds = listOf("R-DATA-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = input.today,
        )
        if (availability.isEmpty()) {
            return base.copy(
                rationale = "Configure sua disponibilidade semanal para gerar um plano. " +
                    "Sem isso, o app não inventa dias de treino.",
            )
        }

        val allDays = availability.keys.toList()
        // N-02: a plan created midweek only contains today and the remaining days.
        val daysAfterToday = allDays.filter { day ->
            !weekStart.plusDays((day.value - 1).toLong()).isBefore(input.today)
        }
        // A persisted event (even a terminal one) means the legacy goal no longer
        // plans; only the event projection can define a race from now on.
        val race = input.race ?: RacePlan.from(input.profile.goal)
            .takeIf { input.eventDates.isEmpty() }
        // FR-411: the PEAK pace is always the current prediction. The goal time
        // is a reference only and may explain the gap in the copy.
        val racePacePlan = race?.let { activeRace ->
            RacePaceResolver.resolve(input.profile, activeRace, input.raceTargetTimeSec, input.today)
        }
        val eventDates = input.eventDates + listOfNotNull(race?.targetDate)
        // BUG-08: a race goal without a date cannot schedule the volume reduction.
        // The plan says it out loud instead of silently skipping the taper.
        val raceGoalWithoutDate = race == null &&
            input.eventDates.isEmpty() &&
            input.profile.goal?.targetDate == null &&
            input.profile.goal?.type?.isRace == true
        // A race day never receives an ordinary session. The rest of the race
        // week stays free too: recovery is out of scope, so the app does not
        // invent post-race training (ADR 0030).
        // A prova nunca recebe sessão comum; dias após a prova na mesma semana
        // recebem só EASY curto regenerativo (decisão do usuário 0, ADR 0043
        // §3.3) — sem qualidade/longão, que continuam vetados abaixo.
        val days = daysAfterToday.filter { day ->
            val date = weekStart.plusDays((day.value - 1).toLong())
            date !in eventDates && (race == null || !race.isRaceDayOrLater(date))
        }
        val postRaceDays = daysAfterToday.filter { day ->
            val date = weekStart.plusDays((day.value - 1).toLong())
            date !in eventDates && race != null && race.isRaceDayOrLater(date)
        }
        if (days.isEmpty() && postRaceDays.isEmpty()) {
            val message = if (daysAfterToday.isNotEmpty() && race != null) {
                "Não há dias de treino antes da prova nesta semana. O próximo plano começa depois do evento."
            } else {
                "Não há dias de treino restantes nesta semana. O próximo plano começa na segunda-feira."
            }
            return base.copy(rationale = message)
        }
        val dayTrimmed = daysAfterToday.size < allDays.size
        val eventDaysSkipped = days.size < daysAfterToday.size
        // A recovery day is active but never receives a long run (ADR 0037).
        // ADR 0045 §2.1: highest availability, then weekend, then later day.
        // A completed long run suppresses a second one: the week keeps a
        // single longão (ADR 0028 addendum 19/11/2026).
        val longDay = if (input.longAlreadyDone) {
            null
        } else {
            selectLongDay(days, availability, recovery, weekStart)
        }
        val recoveryDays = days.map { weekStart.plusDays((it.value - 1).toLong()) }
            .filter { recovery?.isActive(it) == true }
        val recoveryActive = recoveryDays.isNotEmpty()
        val fullyRecovering = days.isNotEmpty() && recoveryDays.size == days.size
        val loadState = input.load?.dataState ?: LoadDataState.INSUFFICIENT
        // D5 (ADR 0039) + R-LOAD-03 v3 (ADR 0046): quality needs objective load,
        // low monotony, no reliable strain spike and no reliable ACWR > 1.5.
        // Volume progression additionally needs a reliable ACWR in 0.8–1.3.
        val loadTrusted = input.load?.objectiveDataState == LoadDataState.KNOWN
        val permitsQuality = input.load?.permitsQuality ?: false
        val permitsProgression = input.load?.permitsProgression ?: false
        val monotonyOk = input.load?.monotonyOk ?: false
        val strainOk = input.load?.let { !it.strainSpike || !it.strainReliable } ?: false
        val acwr = input.load?.acwr
        val acwrReliable = input.load?.acwrReliable == true
        val acwrHigh = input.load?.acwrHigh ?: false
        val acwrInRange = acwr != null &&
            acwr in LoadSnapshot.ACWR_PROGRESSION_MIN..LoadSnapshot.ACWR_PROGRESSION_MAX
        // ADR 0041: mesociclo e descarga derivam de `recentWeeks` reais (até 12
        // semanas). O contador `weekNumberInBlock` só vale sem histórico.
        val combinedReferences =
            (input.recentWeeks + listOfNotNull(input.previousWeek)).distinctBy { it.weekStart }
        val volumeReferences = input.volumeReferences
            ?: VolumeReferenceBuilder.fromPlans(combinedReferences)
        val isRaceWeek = race != null && race.targetDate in weekStart..weekStart.plusDays(6)
        val isTaperWeek = race != null && (0L..6L).any { race.isInTaperWindow(weekStart.plusDays(it)) }
        val mesoRecoveryActive = recovery != null &&
            recovery.intersects(weekStart, weekStart.plusDays(6))
        // ADR 0043: com prova dentro do horizonte, a fase da semana vem da
        // preparação macro (tabela por distância); sem prova, além do horizonte
        // ou passada, o mesociclo segue como antes. A recuperação continua suprema.
        val macroPlan = MacroPlanResolver.resolve(race, input.today)
        val macroIntent = macroPlan.weeks.singleOrNull { it.weekStart == weekStart }
        // FR-411: a race beyond the macro horizon must not freeze the general
        // phase in BASE (MesoCycle.raceState). Passing `null` lets the ADR 0041
        // BASE/BUILD rotation keep the structural quality opportunity alive
        // until the longer 5K table starts; recovery/taper/race still win above.
        val mesoRace = race.takeUnless { macroPlan.isGeneral }
        val meso = MesoCycle.resolve(
            targetWeekStart = weekStart,
            race = mesoRace,
            recoveryActive = mesoRecoveryActive,
            isRaceWeek = isRaceWeek,
            isTaperWeek = isTaperWeek,
            references = volumeReferences,
            fallbackWeekNumberInBlock = input.weekNumberInBlock,
            phaseOverride = macroIntent?.phase,
            overrideWeekInPhase = macroIntent?.weekInPhase ?: 0,
            overridePhaseWeeks = macroIntent?.phaseWeeks,
        )
        val postRecoveryForDeload = isPostRecoveryWeek(combinedReferences, weekStart)
        // Taper, recovery, pós-recovery e semana de prova nunca são descarga;
        // PEAK já sai sem descarga do resolvedor para manter volume estável.
        val deload = meso.isDeload && !postRecoveryForDeload
        // R-PLAN-05/10 v2 (proposto): taper mantém intensidade em micro-dose.
        val taperLimitsQuality = race != null && days.any { day ->
            race.isInTaperWindow(weekStart.plusDays((day.value - 1).toLong()))
        }
        val qualityCandidateDays = days.filter { day ->
            val date = weekStart.plusDays((day.value - 1).toLong())
            (race?.allowsQuality(date) ?: true) && (recovery?.allowsQuality(date) ?: true)
        }
        val microDoseCandidateDays = days.filter { day ->
            val date = weekStart.plusDays((day.value - 1).toLong())
            (recovery?.allowsQuality(date) ?: true) &&
                race != null &&
                race.isInTaperWindow(date) &&
                !race.allowsQuality(date) &&
                !race.isRaceDayOrLater(date)
        }
        // ADR 0044 §2.1–2.3: the first opportunity is structural. Level, days,
        // phase, race week and a safe day decide it; readiness and load of the
        // week's first day belong to the day gate (C4-04), never to generation.
        val structuralQualityEligible = input.profile.experience != Experience.BEGINNER &&
            days.size >= 3 &&
            meso.phase != MesoPhase.TAPER &&
            meso.phase != MesoPhase.RECOVERY &&
            !isRaceWeek &&
            qualityCandidateDays.isNotEmpty()
        val completionRatio = input.previousWeek?.strongCompletionRatio() ?: 0.0
        // ADR 0047 §2.2 (C4-18): the intro fartlek needs four consistent weeks
        // (≥80% strong completion). Illness has no weekly signal in the plan
        // history; it surfaces as missed sessions, and the day gate still
        // applies to every session when the athlete is ill.
        val consistentWeeks = combinedReferences.count { (it.strongCompletionRatio() ?: 0.0) >= PROGRESSION_COMPLETION }
        val introFartlekEligible = input.profile.experience == Experience.BEGINNER &&
            consistentWeeks >= INTRO_FARTLEK_CONSISTENT_WEEKS
        // The second opportunity still requires proven progression: completed
        // week, reliable ACWR in 0.8–1.3 and no taper/race/recovery week.
        val secondQualityAllowed = structuralQualityEligible &&
            permitsProgression &&
            completionRatio >= PROGRESSION_COMPLETION &&
            days.size >= 4 &&
            !taperLimitsQuality
        // A taper/race week keeps its structural micro-dose even without a
        // check-in or load: the plan never starts with zero stimulus (ADR 0044
        // §2.3). Recovery weeks stay empty.
        val structuralMicroDoseEligible = input.profile.experience != Experience.BEGINNER &&
            meso.phase != MesoPhase.RECOVERY &&
            microDoseCandidateDays.isNotEmpty()
        // ADR 0043 §3.2/ADR 0047: papéis da semana pela biblioteca (fase, nível).
        // Títulos das sessões fáceis vêm de `easyTemplates`; a qualidade é
        // escolhida pela rotação determinística do catálogo, que também decide
        // se a semana tem estímulo (BASE só na segunda metade, taper em
        // micro-dose, recovery sem qualidade).
        val easyTemplates = SessionLibrary.easyTemplates(
            meso.phase,
            input.profile.experience,
            includeIntroFartlek = introFartlekEligible,
        )
        fun easyTemplate(role: SessionRole): SessionTemplate? = easyTemplates.singleOrNull { it.role == role }
        val qualitySelection = if (structuralQualityEligible) {
            SessionLibrary.qualityRotation(
                phase = meso.phase,
                level = input.profile.experience,
                distance = race?.distance,
                weekInPhase = meso.weekInPhase,
                maxQualities = if (secondQualityAllowed) 2 else 1,
                phaseWeeks = meso.phaseWeeks,
            ).map { template -> template.withRacePace(racePacePlan) }
        } else {
            emptyList()
        }
        val qualityDays = if (qualitySelection.isEmpty()) {
            emptySet()
        } else {
            selectQualityDays(
                days = qualityCandidateDays,
                longDay = longDay,
                maxQualities = qualitySelection.size,
            )
        }
        val qualityByDay = qualityDays.sortedBy { it.value }.zip(qualitySelection).toMap()
        val microDoseTemplate = if (structuralMicroDoseEligible && qualityDays.isEmpty()) {
            SessionLibrary.qualityRotation(
                phase = MesoPhase.TAPER,
                level = input.profile.experience,
                distance = race?.distance,
                weekInPhase = meso.weekInPhase,
                maxQualities = 1,
                phaseWeeks = meso.phaseWeeks,
            ).singleOrNull()
        } else {
            null
        }
        val microDoseDays = if (microDoseTemplate != null) {
            selectQualityDays(
                days = microDoseCandidateDays,
                longDay = longDay,
                maxQualities = 1,
            )
        } else {
            emptySet()
        }
        // C4-18 (ADR 0047 §2.2): strides are an EASY stimulus for every level
        // in BASE/BUILD/PEAK, on the shortest non-long day with room to spare.
        // Quality and micro-dose days are excluded: the stimulus must not pile
        // onto a hard day, and the branch order below keeps them winning any
        // leftover collision. Recovery days stay out; without 2+ easy days
        // beyond the long run there are no strides. The beginner swaps strides
        // for the intro fartlek after four consistent weeks (still EASY, never
        // a quality opportunity).
        val stridesDay = if (
            meso.phase == MesoPhase.BASE ||
            meso.phase == MesoPhase.BUILD ||
            meso.phase == MesoPhase.PEAK
        ) {
            days.filter { day ->
                val date = weekStart.plusDays((day.value - 1).toLong())
                day != longDay &&
                    day !in qualityDays &&
                    day !in microDoseDays &&
                    recovery?.isActive(date) != true
            }
                .sortedWith(compareBy({ availability.getValue(it) }, { it.value }))
                .takeIf { it.size >= 2 }
                ?.first()
        } else {
            null
        }

        // ADR 0045 §2.2: the long run is sized before the sessions exist. The
        // other sessions' durations define the share target and the "longest
        // other" preference; the availability of the long day is a hard ceiling.
        val reducedByPeriodization = deload || taperLimitsQuality
        val longFloor = if (reducedByPeriodization) MIN_LONG_MIN else longRunFloor(input.profile.experience)
        val otherDurations = (days + postRaceDays)
            .filter { it != longDay }
            .associateWith { day ->
                val date = weekStart.plusDays((day.value - 1).toLong())
                plannedDuration(
                    day = day,
                    date = date,
                    available = availability.getValue(day),
                    plan = DurationPlan(
                        qualityDays = qualityDays,
                        microDoseDays = microDoseDays,
                        stridesDay = stridesDay,
                        postRaceDays = postRaceDays,
                        recovery = recovery,
                    ),
                )
            }
        val longTarget = longDay?.let { day ->
            longRunTarget(
                maxAvailable = availability.getValue(day),
                experience = input.profile.experience,
                otherDurations = otherDurations.values,
                reducedByPeriodization = reducedByPeriodization,
            )
        }
        val durationByDay = otherDurations.toMutableMap().apply {
            if (longDay != null && longTarget != null) put(longDay, longTarget)
        }

        val sessions = (days + postRaceDays).sorted().map { day ->
            val date = weekStart.plusDays((day.value - 1).toLong())
            when {
                day in postRaceDays -> {
                    val duration = durationByDay.getValue(day)
                    PlannedSession(
                        date = date,
                        type = WorkoutType.EASY,
                        title = "Fácil e conversável",
                        durationMin = duration,
                        rationale = "Após a prova: sessão curta e leve, sem esforço.",
                        ruleIds = listOf("R-ZONE-01", "R-PLAN-06"),
                        evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
                        targets = PrescriptionPolicy.targets(
                            type = WorkoutType.EASY,
                            profile = input.profile,
                            durationMin = duration,
                        ),
                    )
                }

                day in qualityDays -> {
                    val duration = durationByDay.getValue(day)
                    val template = qualityByDay.getValue(day)
                    val structure = requireNotNull(template.structure) {
                        "Quality template ${template.title} must carry a structure"
                    }.fittedTo(duration)
                    val rationale = if (template.durationPolicy == DurationPolicy.MICRO_DOSE_25) {
                        "Semana de prova: micro-dose para manter o estímulo, sem fadiga. " +
                            describeStructure(structure)
                    } else {
                        qualityRationale(
                            profile = input.profile,
                            durationMin = duration,
                            structure = structure,
                            reduction = null,
                            extraNote = racePacePlan?.note.takeIf {
                                template.qualityKind == QualityKind.RACE_PACE
                            },
                        )
                    }
                    PlannedSession(
                        date = date,
                        type = WorkoutType.QUALITY,
                        title = template.title,
                        durationMin = duration,
                        rationale = rationale,
                        ruleIds = buildList {
                            addAll(template.ruleIds)
                            add("R-PLAN-06")
                            if (input.profile.treadmillAccess) add("R-PLAN-08")
                        },
                        evidenceIds = buildList {
                            addAll(template.evidenceIds)
                            if (input.profile.treadmillAccess) add("EV-ELY-2007")
                        },
                        targets = PrescriptionPolicy.targets(
                            type = WorkoutType.QUALITY,
                            profile = input.profile,
                            durationMin = duration,
                            racePace = racePacePlan.takeIf {
                                template.qualityKind == QualityKind.RACE_PACE
                            },
                        ),
                        qualityKind = requireNotNull(template.qualityKind) {
                            "Quality session ${template.title} must carry a kind"
                        },
                        planDecision = qualityPlanDecision(template),
                        structure = structure,
                    )
                }

                day in microDoseDays -> {
                    val duration = durationByDay.getValue(day)
                    val template = requireNotNull(microDoseTemplate) {
                        "A micro-dose day requires a taper template"
                    }
                    PlannedSession(
                        date = date,
                        type = WorkoutType.QUALITY,
                        title = template.title,
                        durationMin = duration,
                        rationale = "Semana de prova: micro-dose para manter o estímulo, sem fadiga. " +
                            describeStructure(requireNotNull(template.structure).fittedTo(duration)),
                        ruleIds = buildList {
                            addAll(template.ruleIds)
                            add("R-PLAN-06")
                        },
                        evidenceIds = template.evidenceIds,
                        targets = PrescriptionPolicy.targets(
                            type = WorkoutType.QUALITY,
                            profile = input.profile,
                            durationMin = duration,
                        ),
                        qualityKind = requireNotNull(template.qualityKind) {
                            "Micro-dose ${template.title} must carry a kind"
                        },
                        planDecision = qualityPlanDecision(template),
                        structure = requireNotNull(template.structure).fittedTo(duration),
                    )
                }

                day == longDay -> {
                    val duration = durationByDay.getValue(day)
                    PlannedSession(
                        date = date,
                        type = WorkoutType.LONG,
                        title = LONG_RUN_TITLE,
                        durationMin = duration,
                        rationale = "Longão em ritmo fácil; a duração vem do piso e da disponibilidade.",
                        ruleIds = listOf("R-PLAN-03"),
                        evidenceIds = listOf("EV-PFITZINGER-2008", "EV-DANIELS-2014"),
                        targets = PrescriptionPolicy.targets(
                            type = WorkoutType.LONG,
                            profile = input.profile,
                            durationMin = duration,
                        ),
                    )
                }

                else -> {
                    val recoveryCap = recovery?.maxSessionMinutes(date)
                    val isRecoveryDay = recoveryCap != null
                    if (day == stridesDay) {
                        val duration = durationByDay.getValue(day)
                        val template = if (introFartlekEligible) {
                            easyTemplate(SessionRole.INTRO_FARTLEK)
                        } else {
                            null
                        } ?: easyTemplate(SessionRole.STRIDES)
                        PlannedSession(
                            date = date,
                            type = WorkoutType.EASY,
                            title = template?.title ?: "Strides curtos",
                            durationMin = duration,
                            rationale = template?.blurb
                                ?: "Estímulo neuromuscular curto, sem fadiga.",
                            ruleIds = listOf("R-ZONE-01", "R-PLAN-06", "R-PER-02"),
                            evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
                            targets = PrescriptionPolicy.targets(
                                type = WorkoutType.EASY,
                                profile = input.profile,
                                durationMin = duration,
                            ),
                            structure = template?.structure?.let { structure ->
                                runCatching { structure.fittedTo(duration) }.getOrNull()
                            },
                        )
                    } else {
                        val duration = durationByDay.getValue(day)
                        PlannedSession(
                            date = date,
                            type = WorkoutType.EASY,
                            title = if (isRecoveryDay) {
                                RECOVERY_TITLE
                            } else {
                                easyTemplate(SessionRole.EASY_RUN)?.title ?: "Fácil e conversável"
                            },
                            durationMin = duration,
                            rationale = if (isRecoveryDay) {
                                "Recuperação após a prova: sessão curta e leve, sem treino forte."
                            } else {
                                "Base aeróbica conversável; sem treino forte neste dia."
                            },
                            ruleIds = buildList {
                                add("R-ZONE-01")
                                add("R-PLAN-06")
                                if (isRecoveryDay) add("R-RACE-04")
                            },
                            evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
                            targets = PrescriptionPolicy.targets(
                                type = WorkoutType.EASY,
                                profile = input.profile,
                                durationMin = duration,
                            ),
                        )
                    }
                }
            }
        }

        val taperActive = race?.isInTaperWindow(input.today) == true
        val availabilityCeiling = sessions.sumOf { it.durationMin ?: 0 }
        val postRecovery = isPostRecoveryWeek(combinedReferences, weekStart)
        val volumeDecision = VolumeProgression().decide(
            targetWeekStart = weekStart,
            availabilityCeilingMinutes = availabilityCeiling,
            references = volumeReferences,
            previousWeek = input.previousWeek,
            gates = VolumeGates(
                targetWeekBlock = volumeWeekBlock(
                    race,
                    recoveryActive,
                    deload,
                    weekStart,
                    days,
                    postRecovery = postRecovery,
                    mesoPhase = meso.phase,
                ),
                readinessGreen = input.readiness.level == ReadinessLevel.GREEN &&
                    input.readiness.flags.none {
                        it == ReadinessFlag.ILLNESS_OR_INJURY ||
                            it == ReadinessFlag.SHORT_SLEEP ||
                            it == ReadinessFlag.NO_QUALITY
                    },
                loadKnown = loadTrusted,
                acwrReliable = acwrReliable,
                acwrInRange = acwrInRange,
                monotonyOk = monotonyOk,
                strainOk = strainOk,
            ),
        )
        val volumeAdjusted = fitToVolume(sessions, volumeDecision.minutes, longFloor)
        val originalDurations = volumeAdjusted.sessions.associate { it.date to it.durationMin }
        val scaled = volumeAdjusted.sessions.map { session ->
            when {
                race != null && race.isInTaperWindow(session.date) && session.durationMin != null ->
                    session.copy(durationMin = scaledMinutes(session, race.taperFactor(session.date)))

                deload && session.durationMin != null ->
                    session.copy(durationMin = scaledMinutes(session, DELOAD_FACTOR))

                else -> session
            }
        }
        val adjusted = applyLongRunHonesty(
            sessions = scaled,
            profile = input.profile,
            availability = availability,
            reducedByPeriodization = reducedByPeriodization,
        ).map { session ->
            session
                .withQualityCopy(input.profile, race, deload, originalDurations[session.date], racePacePlan)
                .withFittedEasyStructure()
                .withTargetsForDuration(input.profile, racePacePlan)
        }
        val weeklyMinutes = adjusted.sumOf { it.durationMin ?: 0 }
        val longMinutes = adjusted.firstOrNull { it.type == WorkoutType.LONG }?.durationMin
        val qualityCount = adjusted.count { it.type == WorkoutType.QUALITY }
        val baseConfidence = cappedConfidence(input.readiness.confidence, DataConfidence.MEDIUM)
        val confidence = when {
            // Without a volume base the week still comes from availability; the
            // rationale says so, and confidence must not claim otherwise.
            !volumeDecision.hasHistory -> DataConfidence.LOW

            !loadTrusted -> downgrade(baseConfidence)

            else -> baseConfidence
        }
        val rationale = buildString {
            append("Fase ${meso.phase}. ")
            if (meso.fallbackUsed) {
                append("Sem histórico suficiente para mesociclo; usando posição legada. ")
            }
            if (macroIntent != null) {
                val totalBlocks = macroPlan.weeks.maxOf { it.blockIndex }
                append("Bloco ${macroIntent.blockIndex} de $totalBlocks. ")
                if (macroIntent.weeksToRace == 0) {
                    append("É a semana da prova. ")
                } else if (macroIntent.weeksToRace == 1) {
                    append("Falta 1 semana para a prova. ")
                } else {
                    append("Faltam ${macroIntent.weeksToRace} semanas para a prova. ")
                }
            }
            if (recoveryActive) {
                append(
                    if (fullyRecovering) {
                        "Recuperação após a prova: sessões curtas, sem treino forte e sem longão. "
                    } else {
                        "Recuperação após a prova em parte da semana: sem treino forte e sem " +
                            "longão nesses dias. "
                    },
                )
            } else if (taperActive) {
                val days = race?.daysTo(input.today) ?: 0
                if (days == 0L) {
                    append("Prova hoje: volume reduzido. ")
                } else {
                    append("Semana de prova em $days dias: volume reduzido. ")
                }
            } else if (deload) {
                append(
                    "Semana de descarga após ${meso.strongStreak} semanas de carga demonstrada: " +
                        "volume −${((1.0 - DELOAD_FACTOR) * 100).roundToInt()}%, " +
                        "intensidade mantida. ",
                )
            }
            if (postRaceDays.isNotEmpty()) {
                append("Após a prova: só sessões leves e curtas. ")
            }
            append(
                volumeRationale(
                    volumeDecision,
                    volumeAdjusted.droppedSessions,
                    volumeAdjusted.minimalWeek,
                    extraTrainingNote(volumeDecision, volumeReferences, combinedReferences),
                ),
            )
            append(" ")
            if (raceGoalWithoutDate) {
                append(
                    "Sua meta é uma prova, mas está sem data: o plano não reduz o volume antes " +
                        "dela. Com a data, o app ajusta as semanas anteriores. ",
                )
            }
            if (eventDaysSkipped) {
                append("Dias de prova não recebem sessão de treino. ")
            }
            if (dayTrimmed) {
                append("Plano criado hoje: dias anteriores não entram nesta semana. ")
            }
            append("${adjusted.size} dias, $weeklyMinutes min no total. ")
            if (qualityCount > 0) {
                val qualityDaysLabel = adjusted
                    .filter { it.type == WorkoutType.QUALITY }
                    .joinToString(" e ") { dayLabel(it.date.dayOfWeek) }
                append("Qualidade planejada para $qualityDaysLabel; o dia confirma com prontidão e carga. ")
            } else {
                append("Sem treino forte: ")
                append(
                    when {
                        input.profile.experience == Experience.BEGINNER -> "iniciante prioriza base."

                        days.size < 3 -> "menos de 3 dias disponíveis."

                        meso.phase == MesoPhase.RECOVERY ->
                            "semana de recuperação: sem treino forte."

                        meso.phase == MesoPhase.TAPER || isRaceWeek ->
                            "semana de prova: sem dia elegível para a micro-dose."

                        meso.phase == MesoPhase.BASE && qualitySelection.isEmpty() ->
                            "a primeira metade da base é só fácil, longão e strides; " +
                                "o fartlek leve entra na segunda metade."

                        input.longAlreadyDone ->
                            "longão já concluído nesta semana."

                        else -> "sem dia seguro separado do longão."
                    },
                )
                append(" ")
            }
            if (structuralQualityEligible && qualityCount > 0) {
                when {
                    !secondQualityAllowed -> append(
                        progressionBlockedRationale(
                            days = days.size,
                            hasPreviousWeek = input.previousWeek != null,
                            completionRatio = completionRatio,
                            taperLimitsQuality = taperLimitsQuality,
                            load = input.load,
                        ),
                    )

                    qualityCount >= 2 ->
                        append("Progressão liberada: você concluiu pelo menos 80% da semana anterior. ")

                    // C4-59/FR-412: the gate is open, but the plan may still
                    // hold one opportunity. Name the real limit instead of
                    // implying a second quality that does not exist.
                    qualitySelection.size < 2 -> append(
                        "Progressão liberada, mas o catálogo desta fase tem 1 estímulo; " +
                            "a semana tem 1 oportunidade. ",
                    )

                    else -> append(
                        "Progressão liberada, mas a agenda não tem outro dia seguro separado do longão; " +
                            "a semana tem 1 oportunidade. ",
                    )
                }
            }
            if (input.dataIncomplete) {
                append(
                    "Dados de sincronização incompletos: o plano será reavaliado " +
                        "quando chegarem. ",
                )
            }
        }
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = adjusted,
            weeklyMinutes = weeklyMinutes,
            longRunMinutes = longMinutes,
            qualityCount = qualityCount,
            rationale = rationale,
            confidence = confidence,
            ruleIds = buildList {
                add("R-PLAN-02")
                add("R-PLAN-03")
                add("R-PLAN-06")
                if (qualityCount > 0) add("R-PER-03")
                if (volumeDecision.hasHistory) add("R-PLAN-01")
                if (!loadTrusted) add("R-DATA-01")
                if (loadState == LoadDataState.KNOWN && !loadTrusted) add("R-LOAD-04")
                if (loadTrusted && !permitsQuality) add("R-LOAD-03")
                if (recoveryActive) add("R-RACE-04")
                if (raceGoalWithoutDate) add("R-PLAN-11")
                if (taperActive) add("R-PLAN-05")
                if (taperActive) add("R-PLAN-10")
                if (deload) add("R-PLAN-04")
                if (macroIntent != null) add("R-PER-01")
                if (macroIntent != null) add("R-PER-02")
                if (input.dataIncomplete) add("R-DATA-01")
            }.distinct(),
            evidenceIds = buildList {
                add("EV-SEILER-2010")
                add("EV-DANIELS-2014")
                add("EV-PFITZINGER-2008")
                if (volumeDecision.hasHistory) add("EV-GABBETT-2016")
                if (!loadTrusted || (loadTrusted && !permitsQuality)) {
                    add("EV-IMPELLIZZERI-2020")
                }
                if (loadTrusted && !permitsQuality) add("EV-FOSTER-2001")
                if (loadState == LoadDataState.KNOWN && !loadTrusted) add("EV-FOSTER-2001")
                if (taperActive) {
                    add("EV-BOSQUET-2007")
                    add("EV-MUJIKA-2003")
                }
                if (deload) add("EV-ISSURIN-2010")
                if (macroIntent != null && !deload) add("EV-ISSURIN-2010")
                if (macroIntent != null && qualityCount > 0 && !(loadTrusted && !permitsQuality)) {
                    add("EV-FOSTER-2001")
                }
                if (input.dataIncomplete) add("EV-IMPELLIZZERI-2020")
            }.distinct(),
            createdOn = input.today,
            engineVersion = ENGINE_VERSION,
        )
    }

    private fun selectQualityDays(days: List<DayOfWeek>, longDay: DayOfWeek?, maxQualities: Int): Set<DayOfWeek> {
        val selected = mutableListOf<DayOfWeek>()
        days.forEach { day ->
            if (selected.size >= maxQualities) return@forEach
            if (day == longDay || (longDay != null && adjacentDays(day, longDay))) return@forEach
            if (selected.any { adjacentDays(it, day) }) return@forEach
            selected += day
        }
        return selected.toSet()
    }

    /**
     * Resolves the catalog's `GOAL_*` marker into the current prediction
     * (FR-411). The template stays static and testable; only a `RACE_PACE`
     * template is rewritten, and only its `RACE_PACE` block changes.
     */
    private fun SessionTemplate.withRacePace(plan: RacePacePlan?): SessionTemplate {
        if (plan == null || qualityKind != QualityKind.RACE_PACE) return this
        val source = structure ?: return this
        return copy(
            structure = source.copy(
                blocks = source.blocks.map { block ->
                    if (block.kind == BlockKind.RACE_PACE) block.copy(target = plan.blockTarget) else block
                },
            ),
            ruleIds = (ruleIds + plan.ruleIds).distinct(),
            evidenceIds = (evidenceIds + plan.evidenceIds).distinct(),
        )
    }

    /**
     * Auditable reason the opportunity exists (ADR 0044 §2.2). The first slot
     * is structural; the second names the progression proof. The micro-dose is
     * structural as well and stays in the taper/race week.
     */
    private fun qualityPlanDecision(template: SessionTemplate): PlanDecision = when {
        template.qualityKind == QualityKind.STRIDES_ONLY -> PlanDecision(
            reason = PlanDecisionReason.QUALITY_OPPORTUNITY,
            detail = "Micro-dose estrutural da semana de prova; o dia confirma por segurança.",
        )

        template.role == SessionRole.QUALITY_SECOND -> PlanDecision(
            reason = PlanDecisionReason.PROGRESSION_COMPLETED,
            detail = "Segunda oportunidade: semana anterior com pelo menos 80% de conclusão com " +
                "evidência e carga confiável em 0,8–1,3.",
        )

        else -> PlanDecision(
            reason = PlanDecisionReason.QUALITY_OPPORTUNITY,
            detail = "Oportunidade estrutural da semana; o dia confirma com prontidão e carga.",
        )
    }

    /**
     * C4-06/FR-412 (P-12): the "no second quality" copy names the real blocker,
     * never a generic history note when history exists. Priority follows the
     * `secondQualityAllowed` conjuncts; only the first failing gate is named so
     * the rationale cannot contradict the facts.
     */
    private fun progressionBlockedRationale(
        days: Int,
        hasPreviousWeek: Boolean,
        completionRatio: Double,
        taperLimitsQuality: Boolean,
        load: LoadSnapshot?,
    ): String {
        if (days < 4) {
            return "Sem progressão: para 2 estímulos, o app precisa de 4+ dias; " +
                "com $days dias, a semana tem 1 oportunidade. "
        }
        if (!hasPreviousWeek) {
            return "Sem progressão: ainda não há semana concluída para liberar o 2º estímulo; " +
                "a semana tem 1 oportunidade. "
        }
        if (completionRatio < PROGRESSION_COMPLETION) {
            val percent = (completionRatio * 100).roundToInt()
            return "Sem progressão: a semana anterior ficou em $percent% de conclusão com " +
                "evidência (abaixo de 80%); a semana tem 1 oportunidade. "
        }
        if (taperLimitsQuality) {
            return "Sem progressão: semana de prova/taper tem 1 estímulo; a semana tem 1 oportunidade. "
        }
        return progressionBlockedByLoad(load)
    }

    private fun progressionBlockedByLoad(load: LoadSnapshot?): String {
        val loadState = load?.dataState ?: LoadDataState.INSUFFICIENT
        val loadTrusted = load?.objectiveDataState == LoadDataState.KNOWN
        val monotonyOk = load?.monotonyOk ?: false
        val strainSpikeReliable = load?.let { it.strainSpike && it.strainReliable } ?: false
        return when {
            load == null ->
                "Sem progressão: carga em calibração (sem dados de carga); " +
                    "a qualidade continua planejada, o dia decide. "

            loadState == LoadDataState.STALE ->
                "Sem progressão: fonte de carga desatualizada; " +
                    "a qualidade continua planejada, o dia decide. "

            loadState == LoadDataState.INSUFFICIENT -> {
                val withLoad = load.coverage.daysWithLoad
                val window = load.coverage.daysInWindow
                "Sem progressão: carga em calibração ($withLoad de $window dias de histórico); " +
                    "a qualidade continua planejada, o dia decide. "
            }

            !loadTrusted ->
                "Sem progressão: carga recente vem do esforço percebido; " +
                    "a qualidade continua planejada, o dia decide. "

            !monotonyOk -> {
                val monotony = load.monotony
                if (monotony == null) {
                    "Sem progressão: a monotonia não é calculável nesta semana; " +
                        "a qualidade continua planejada, o dia decide. "
                } else {
                    "Sem progressão: a semana variou pouco (monotonia ≥2,0); " +
                        "a qualidade continua planejada, o dia decide. "
                }
            }

            strainSpikeReliable ->
                "Sem progressão: a carga concentrou demais nesta semana; " +
                    "a qualidade continua planejada, o dia decide. "

            !load.acwrReliable ->
                "Sem progressão: ACWR em calibração; " +
                    "a qualidade continua planejada, o dia decide. "

            load.acwr != null -> {
                val value = decimal(load.acwr)
                "Sem progressão: ACWR $value fora da faixa 0,8–1,3; " +
                    "a qualidade continua planejada, o dia decide. "
            }

            else ->
                "Sem progressão: carga recente fora da faixa; " +
                    "a qualidade continua planejada, o dia decide. "
        }
    }

    private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.2f", value).replace('.', ',')

    /**
     * Quality copy comes from the executable structure, never from a fixed
     * block (N-03). The same text describes the real repetitions, recovery and
     * cool-down after volume/taper/deload fitting, so the plan cannot promise
     * more work than the session duration holds.
     *
     * The taper/deload note only appears when the session was actually
     * shortened: a 25-minute quality is already at the floor and must not claim
     * a cut.
     */
    private fun qualityRationale(
        profile: AthleteProfile,
        durationMin: Int,
        structure: WorkoutStructure,
        reduction: QualityReduction?,
        extraNote: String? = null,
    ): String {
        require(durationMin >= MIN_QUALITY_MIN) {
            "Quality duration must be at least $MIN_QUALITY_MIN minutes"
        }
        val advanced = profile.experience == Experience.ADVANCED
        val note = when (reduction) {
            QualityReduction.TAPER -> "Semana de prova: ${qualityReductionNote(advanced)}"
            QualityReduction.DELOAD -> "Semana de descarga: ${qualityReductionNote(advanced)}"
            null -> null
        }
        return listOfNotNull(note, describeStructure(structure), extraNote, treadmillQualityHint(profile))
            .joinToString(" ")
    }

    private fun qualityReductionNote(advanced: Boolean): String =
        if (advanced) "menos repetições, mesmo esforço." else "bloco mais curto, mesmo esforço."

    private fun describeStructure(structure: WorkoutStructure): String = structure.steps().joinToString(" ")

    private fun treadmillQualityHint(profile: AthleteProfile): String? {
        if (!profile.treadmillAccess) return null
        return "Na esteira, se preferir: use inclinação de 5% a 10% em esforço 8 de 10" +
            treadmillSpeedHint(profile.thresholdPace) + "."
    }

    /**
     * Prescribes the treadmill by speed when threshold pace is calibrated
     * (`R-PLAN-08`); incline stays subjective to avoid inventing intensity.
     */
    private fun treadmillSpeedHint(thresholdPace: Pace?): String {
        val pace = thresholdPace ?: return ""
        if (pace.secondsPerKm !in 150..900) return ""
        val threshold = PaceZoneCalculator.ranges(pace).first { it.zone == PaceZone.T }
        val slowKmh = 3_600.0 / threshold.maxSecPerKm
        val fastKmh = 3_600.0 / threshold.minSecPerKm
        return String.format(PT_BR, " ou %.1f–%.1f km/h com inclinação de 1%%", slowKmh, fastKmh)
    }

    /**
     * Rebuilds the plan text after volume, taper or deload scaling. The plan
     * must not keep a block that no longer fits the final duration (N-03).
     */
    private fun PlannedSession.withQualityCopy(
        profile: AthleteProfile,
        race: RacePlan?,
        deload: Boolean,
        originalDurationMin: Int?,
        racePacePlan: RacePacePlan?,
    ): PlannedSession {
        if (type != WorkoutType.QUALITY || durationMin == null) return this
        val inTaper = race?.isInTaperWindow(date) == true
        val weekRules = buildList {
            if (inTaper) add("R-PLAN-05")
            if (deload && !inTaper) add("R-PLAN-04")
        }
        val fitted = structure?.fittedTo(durationMin)
        // Micro-dose de taper tem rationale próprio e duração fixa: só agrega
        // regras e reajusta a estrutura (que já nasce em 25 min).
        if (title == MICRO_DOSE_TITLE && durationMin <= MICRO_DOSE_MIN) {
            return copy(
                ruleIds = (ruleIds + weekRules).distinct(),
                structure = fitted,
            )
        }
        val reduction = when {
            originalDurationMin == null || durationMin >= originalDurationMin -> null
            inTaper -> QualityReduction.TAPER
            deload -> QualityReduction.DELOAD
            else -> null
        }
        return copy(
            rationale = fitted?.let {
                qualityRationale(
                    profile = profile,
                    durationMin = durationMin,
                    structure = it,
                    reduction = reduction,
                    extraNote = racePacePlan?.note.takeIf { qualityKind == QualityKind.RACE_PACE },
                )
            } ?: rationale,
            ruleIds = (ruleIds + weekRules).distinct(),
            structure = fitted,
        )
    }

    /**
     * Refits an easy executable shape after volume/taper scaling (C4-19).
     * Quality sessions keep their own copy path; a duration that cannot hold
     * warm-up and cool-down drops the shape instead of promising extra time.
     */
    private fun PlannedSession.withFittedEasyStructure(): PlannedSession {
        val current = structure ?: return this
        val minutes = durationMin ?: return this
        if (type != WorkoutType.EASY) return this
        val fitted = runCatching { current.fittedTo(minutes) }.getOrNull()
        return copy(structure = fitted)
    }

    private fun adjacentDays(a: DayOfWeek, b: DayOfWeek): Boolean {
        val diff = kotlin.math.abs(a.value - b.value)
        return diff == 1 || diff == 6
    }

    /**
     * ADR 0045 §2.1: availability is the primary key; ties prefer the weekend
     * and, inside the weekend, the later day. Recovery days never receive the
     * long run (ADR 0037).
     */
    private fun selectLongDay(
        days: List<DayOfWeek>,
        availability: Map<DayOfWeek, Int>,
        recovery: RecoveryWindow?,
        weekStart: LocalDate,
    ): DayOfWeek? = days
        .filter { day ->
            val date = weekStart.plusDays((day.value - 1).toLong())
            recovery?.allowsLongRun(date) != false
        }
        .maxWithOrNull(
            compareBy(
                { day: DayOfWeek -> availability.getValue(day) },
                { day: DayOfWeek -> weekendPriority(day) },
                { day: DayOfWeek -> day.value },
            ),
        )

    /** Saturday/Sunday > Friday > the rest (ADR 0045 §2.1). */
    private fun weekendPriority(day: DayOfWeek): Int = when (day) {
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> 2
        DayOfWeek.FRIDAY -> 1
        else -> 0
    }

    /**
     * Duration of a non-long session; the long day is sized separately from
     * these durations (ADR 0045 §2.2).
     */
    private fun plannedDuration(day: DayOfWeek, date: LocalDate, available: Int, plan: DurationPlan): Int = when {
        day in plan.postRaceDays -> {
            val cap = plan.recovery?.maxSessionMinutes(date)
            minOf(available, POST_RACE_EASY_MAX_MIN).let { if (cap != null) minOf(it, cap) else it }
        }

        day in plan.qualityDays -> available.coerceIn(MIN_QUALITY_MIN, MAX_QUALITY_MIN)

        day in plan.microDoseDays -> MICRO_DOSE_MIN

        day == plan.stridesDay -> minOf(available, MICRO_DOSE_MIN)

        else -> {
            val cap = plan.recovery?.maxSessionMinutes(date)
            available.coerceIn(MIN_EASY_MIN, MAX_EASY_MIN).let { if (cap != null) minOf(it, cap) else it }
        }
    }

    private data class DurationPlan(
        val qualityDays: Set<DayOfWeek>,
        val microDoseDays: Set<DayOfWeek>,
        val stridesDay: DayOfWeek?,
        val postRaceDays: List<DayOfWeek>,
        val recovery: RecoveryWindow?,
    )

    /** Product floor: 45 min for beginner/intermediate, 60 for advanced (FR-402). */
    private fun longRunFloor(experience: Experience): Int = longRunFloorMinutes(experience)

    private fun longRunCap(experience: Experience): Int = when (experience) {
        Experience.BEGINNER, Experience.INTERMEDIATE -> 150
        Experience.ADVANCED -> 180
    }

    /**
     * ADR 0045 §2.2. Availability is a hard ceiling; the floor beats the
     * 25–30% share target in small weeks; the long run prefers to be at least
     * five minutes longer than the longest other session. Deload/taper lower
     * the floor to 30 and let the post-scaling title stay honest.
     */
    private fun longRunTarget(
        maxAvailable: Int,
        experience: Experience,
        otherDurations: Collection<Int>,
        reducedByPeriodization: Boolean,
    ): Int {
        val floor = if (reducedByPeriodization) MIN_LONG_MIN else longRunFloor(experience)
        val otherMinutes = otherDurations.sum()
        val longestOther = otherDurations.maxOrNull() ?: 0
        val shareTarget = (otherMinutes * LONG_TARGET_SHARE / (1.0 - LONG_TARGET_SHARE)).roundToInt()
        val target = maxOf(floor, longestOther + LONG_LONGER_MARGIN, shareTarget)
        return minOf(maxAvailable, longRunCap(experience), target)
    }

    /**
     * Rebuilds the long-run title and copy from the final duration (ADR 0045
     * §2.3). Below the floor, the plan never claims a long run: it explains the
     * agenda limit and records `LONG_DAY_TOO_SHORT`. A deload/taper cut is not
     * an agenda limit, so it gets the honest title without the agenda hint.
     *
     * The long run is also kept as the longest session or tied with it (§2.7):
     * a per-date taper can shorten it more than an earlier easy day.
     */
    private fun applyLongRunHonesty(
        sessions: List<PlannedSession>,
        profile: AthleteProfile,
        availability: Map<DayOfWeek, Int>,
        reducedByPeriodization: Boolean,
    ): List<PlannedSession> {
        val long = sessions.firstOrNull { it.type == WorkoutType.LONG && it.durationMin != null }
            ?: return sessions
        val duration = long.durationMin ?: return sessions
        val longestOther = sessions
            .filter { it.type != WorkoutType.LONG }
            .maxOfOrNull { it.durationMin ?: 0 } ?: 0
        val floor = longRunFloor(profile.experience)
        val dayMax = availability[long.date.dayOfWeek] ?: duration
        val copy = when {
            duration >= floor && duration >= longestOther -> LongRunCopy(
                title = LONG_RUN_TITLE,
                rationale = "Longão em ritmo fácil. Piso de $floor min preservado; 25–30% do " +
                    "volume é um alvo, não um teto. Teto de ${longRunCap(profile.experience)} min.",
                decision = null,
            )

            duration < floor && reducedByPeriodization -> LongRunCopy(
                title = LONG_RUN_HONEST_TITLE,
                rationale = "Corrida mais longa em ritmo fácil: a semana reduzida encurta a " +
                    "sessão para $duration min; o piso de $floor min volta na semana normal.",
                decision = null,
            )

            duration < longestOther -> LongRunCopy(
                title = LONG_RUN_HONEST_TITLE,
                rationale = "Corrida mais longa em ritmo fácil: $duration min, abaixo de outra " +
                    "sessão de $longestOther min.",
                decision = PlanDecision(
                    reason = PlanDecisionReason.LONG_DAY_TOO_SHORT,
                    detail = "O longão ficou em $duration min, abaixo de outra sessão de $longestOther min.",
                ),
            )

            else -> {
                val day = dayLabel(long.date.dayOfWeek)
                val ceiling = minOf(floor + LONG_FLOOR_HINT_RANGE, longRunCap(profile.experience))
                LongRunCopy(
                    title = LONG_RUN_HONEST_TITLE,
                    rationale = "Corrida mais longa em ritmo fácil: seu dia mais longo tem " +
                        "$dayMax min; o piso para um longão é $floor min. Aumente a " +
                        "disponibilidade de $day para $floor–$ceiling min.",
                    decision = PlanDecision(
                        reason = PlanDecisionReason.LONG_DAY_TOO_SHORT,
                        detail = "Seu dia mais longo tem $dayMax min; o piso para um longão é $floor min.",
                    ),
                )
            }
        }
        return sessions.map { session ->
            if (session.type != WorkoutType.LONG) {
                val minutes = session.durationMin
                if (minutes != null && minutes > duration) {
                    session.copy(durationMin = duration)
                } else {
                    session
                }
            } else {
                session.copy(title = copy.title, rationale = copy.rationale, planDecision = copy.decision)
            }
        }
    }

    private fun dayLabel(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "segunda-feira"
        DayOfWeek.TUESDAY -> "terça-feira"
        DayOfWeek.WEDNESDAY -> "quarta-feira"
        DayOfWeek.THURSDAY -> "quinta-feira"
        DayOfWeek.FRIDAY -> "sexta-feira"
        DayOfWeek.SATURDAY -> "sábado"
        DayOfWeek.SUNDAY -> "domingo"
    }

    private data class LongRunCopy(val title: String, val rationale: String, val decision: PlanDecision?)

    /**
     * Distance targets are derived from duration; after volume/taper/deload
     * scaling they must be rebuilt so the session never shows a stale distance.
     */
    private fun PlannedSession.withTargetsForDuration(
        profile: AthleteProfile,
        racePacePlan: RacePacePlan?,
    ): PlannedSession = copy(
        targets = PrescriptionPolicy.targets(
            type = type,
            profile = profile,
            durationMin = durationMin,
            racePace = racePacePlan.takeIf { qualityKind == QualityKind.RACE_PACE },
        ),
    )

    /** Volume-only reduction; intensity is preserved by keeping the session type. */
    private fun scaledMinutes(session: PlannedSession, factor: Double): Int {
        val current = requireNotNull(session.durationMin) { "Scaled session must have a duration" }
        val floor = when (session.type) {
            WorkoutType.QUALITY -> MIN_QUALITY_MIN
            WorkoutType.LONG -> MIN_LONG_MIN
            else -> MIN_EASY_MIN
        }
        return maxOf(floor, (current * factor).roundToInt())
    }

    /**
     * Scales the availability layout down to the volume target (ADR 0038).
     * The long run is the anchor: it shrinks proportionally but never below
     * its floor (45/60; 30 in deload/taper) and never grows. The other sessions
     * fit what remains of the target. If the floors do not fit, the shortest
     * easy days leave the week; quality and the long run are never dropped and
     * a minimal week keeps its floors and says the volume is above the target
     * (ADR 0045 §2.4).
     */
    private fun fitToVolume(sessions: List<PlannedSession>, targetMinutes: Int, longFloor: Int): FittedVolume {
        val current = sessions.sumOf { it.durationMin ?: 0 }
        if (current == 0 || targetMinutes >= current) {
            return FittedVolume(sessions, droppedSessions = 0, minimalWeek = false)
        }
        val factor = targetMinutes.toDouble() / current
        val long = sessions.firstOrNull { it.type == WorkoutType.LONG && it.durationMin != null }
        val longCurrent = long?.durationMin ?: 0
        val longMinutes = if (long == null) 0 else scaledLongMinutes(longCurrent, longFloor, factor)
        val otherCurrent = current - longCurrent
        val otherTarget = targetMinutes - longMinutes
        val otherFactor = if (otherCurrent == 0) {
            0.0
        } else {
            (otherTarget.toDouble() / otherCurrent).coerceIn(0.0, 1.0)
        }
        val scaled = sessions.map { session -> scaleToVolume(session, longMinutes, otherFactor) }
        val droppedVolume = dropShortestEasy(scaled, targetMinutes)
        return FittedVolume(
            sessions = droppedVolume.sessions.sortedBy { it.date },
            droppedSessions = droppedVolume.dropped,
            minimalWeek = droppedVolume.sessions.sumOf { it.durationMin ?: 0 } > targetMinutes,
        )
    }

    /** The long run shrinks proportionally but never below its floor or above its current dose. */
    private fun scaledLongMinutes(longCurrent: Int, longFloor: Int, factor: Double): Int {
        val ownFloor = minOf(longFloor, longCurrent)
        return minOf(longCurrent, maxOf(ownFloor, (longCurrent * factor).roundToInt()))
    }

    private fun scaleToVolume(session: PlannedSession, longMinutes: Int, otherFactor: Double): PlannedSession {
        val minutes = session.durationMin ?: return session
        if (session.type == WorkoutType.LONG) return session.copy(durationMin = longMinutes)
        val floor = if (session.type == WorkoutType.QUALITY) MIN_QUALITY_MIN else MIN_EASY_MIN
        return session.copy(durationMin = maxOf(floor, (minutes * otherFactor).roundToInt()))
    }

    /** Keep at least one easy day; quality and the long run are never dropped. */
    private fun dropShortestEasy(sessions: List<PlannedSession>, targetMinutes: Int): DroppedVolume {
        var current = sessions
        var total = current.sumOf { it.durationMin ?: 0 }
        var dropped = 0
        val easyByDuration = current
            .filter { it.type == WorkoutType.EASY }
            .sortedBy { it.durationMin ?: 0 }
        for (easy in easyByDuration) {
            if (total <= targetMinutes) break
            if (current.count { it.type == WorkoutType.EASY } <= 1) break
            current = current.filterNot { it.date == easy.date }
            total -= easy.durationMin ?: 0
            dropped++
        }
        return DroppedVolume(current, dropped)
    }

    private data class DroppedVolume(val sessions: List<PlannedSession>, val dropped: Int)

    private data class FittedVolume(
        val sessions: List<PlannedSession>,
        val droppedSessions: Int,
        val minimalWeek: Boolean,
    )

    private enum class QualityReduction { TAPER, DELOAD }

    /**
     * A deload, taper, race, peak or recovery week never grows. A race week is
     * labeled first because it is also inside the taper window. The first week
     * after a recovery week also never grows (R-RACE-04): the athlete resumes
     * quality but without progression. PEAK keeps the volume stable
     * (R-PLAN-04 v2 direction, ADR 0041).
     */
    private fun volumeWeekBlock(
        race: RacePlan?,
        recoveryActive: Boolean,
        deload: Boolean,
        weekStart: LocalDate,
        days: List<DayOfWeek>,
        postRecovery: Boolean = false,
        mesoPhase: MesoPhase = MesoPhase.BASE,
    ): VolumeGrowthBlock {
        if (race != null && race.targetDate in weekStart..weekStart.plusDays(6)) {
            return VolumeGrowthBlock.RACE_WEEK
        }
        val dates = days.map { weekStart.plusDays((it.value - 1).toLong()) }
        if (race != null && dates.any { race.isInTaperWindow(it) }) return VolumeGrowthBlock.TAPER
        if (recoveryActive) return VolumeGrowthBlock.RECOVERY
        if (postRecovery) return VolumeGrowthBlock.RECOVERY
        if (mesoPhase == MesoPhase.PEAK) return VolumeGrowthBlock.PEAK
        if (deload) return VolumeGrowthBlock.DELOAD
        return VolumeGrowthBlock.NONE
    }

    /**
     * First week after a recovery week (gap 1–7 days after the end of the most
     * recent week carrying R-RACE-04). Derived from persisted weeks, never from
     * a volatile counter (ADR 0041 direction).
     */
    private fun isPostRecoveryWeek(references: List<WeeklyPlan>, targetWeekStart: LocalDate): Boolean {
        val latestEnd = references
            .filter { "R-RACE-04" in it.ruleIds }
            .maxOfOrNull { it.weekStart.plusDays(6) }
            ?: return false
        val gap = java.time.temporal.ChronoUnit.DAYS.between(latestEnd, targetWeekStart)
        return gap in 1..7
    }

    private fun volumeRationale(
        decision: VolumeDecision,
        droppedSessions: Int,
        minimalWeek: Boolean,
        extraNote: String?,
    ): String {
        val base = decision.baseMinutes
        val week = decision.baseWeekStart?.format(WEEK_LABEL)
        val droppedHint = if (droppedSessions > 0) {
            " A sua disponibilidade permite mais dias; esta semana usa o volume que cabe nela."
        } else {
            ""
        }
        val minimalHint = if (minimalWeek) {
            " Semana mínima: o volume fica acima do alvo para preservar o piso do longão e a " +
                "dose mínima de cada sessão; isso não é progressão."
        } else {
            ""
        }
        val extraHint = if (extraNote != null) " $extraNote" else ""
        val suffix = "$droppedHint$minimalHint$extraHint"
        return when {
            base == null || week == null ->
                "Volume definido pela sua disponibilidade: ainda não há semana concluída " +
                    "com evidência para ajustar.$suffix"

            decision.block in SPECIAL_VOLUME_BLOCKS ->
                "Volume: base de $base min concluídos na semana de $week; sem aumento nesta " +
                    "semana${volumeBlockHint(decision.block)}.$suffix"

            decision.cappedByAvailability ->
                "Volume: base de $base min concluídos na semana de $week; limitado pela sua " +
                    "disponibilidade em ${decision.minutes} min.$suffix"

            decision.growthPercent > 0 ->
                "Volume: base de $base min concluídos na semana de $week; " +
                    "+${decision.growthPercent}% nesta semana, alvo de ${decision.minutes} min.$suffix"

            else ->
                "Volume: base de $base min concluídos na semana de $week; alvo mantido em " +
                    "${decision.minutes} min${volumeBlockHint(decision.block)}.$suffix"
        }
    }

    /**
     * FR-408 (ADR 0050 §2.2): credits the unplanned run without inflating
     * adherence. The base already contains every strong minute, so the extra is
     * the base minus the planned strong minutes the athlete actually completed;
     * a LEGACY completion is never strong and cannot appear here.
     */
    private fun extraTrainingNote(
        decision: VolumeDecision,
        volumeReferences: List<VolumeReference>,
        plans: List<WeeklyPlan>,
    ): String? {
        val baseWeekStart = decision.baseWeekStart ?: return null
        val baseReference = volumeReferences.firstOrNull { it.weekStart == baseWeekStart } ?: return null
        val basePlan = plans.firstOrNull { it.weekStart == baseWeekStart } ?: return null
        val doneStrong = basePlan.sessions.filter {
            it.status == SessionStatus.DONE && it.completionEvidence?.isStrong == true
        }
        val extra = baseReference.strongMinutes - doneStrong.sumOf { it.durationMin ?: 0 }
        if (extra <= 0) return null
        val trainable = basePlan.trainableSessions().size
        return "Seu treino extra contou para o volume (+$extra min além do plano), " +
            "mas a adesão considera só o plano (${doneStrong.size}/$trainable)."
    }

    private fun volumeBlockHint(block: VolumeGrowthBlock): String = when (block) {
        VolumeGrowthBlock.NONE,
        VolumeGrowthBlock.NO_HISTORY,
        -> ""

        VolumeGrowthBlock.NO_PREVIOUS_WEEK -> " (sem semana anterior concluída)"

        VolumeGrowthBlock.PREVIOUS_WEEK_BELOW_80 -> " (semana anterior abaixo de 80% de conclusão com evidência)"

        VolumeGrowthBlock.PREVIOUS_WEEK_SPECIAL -> " (semana anterior de descarga ou prova)"

        VolumeGrowthBlock.DELOAD -> " (descarga)"

        VolumeGrowthBlock.PEAK -> " (pico: volume estável)"

        VolumeGrowthBlock.TAPER, VolumeGrowthBlock.RACE_WEEK -> " (prova)"

        VolumeGrowthBlock.RECOVERY -> " (recuperação após a prova)"

        VolumeGrowthBlock.READINESS -> " (prontidão não está alta)"

        VolumeGrowthBlock.LOAD_UNKNOWN -> " (dados de carga insuficientes)"

        VolumeGrowthBlock.ACWR_UNRELIABLE -> " (histórico de carga em calibração)"

        VolumeGrowthBlock.ACWR_OUT_OF_RANGE -> " (carga recente fora da faixa)"

        VolumeGrowthBlock.MONOTONY_HIGH -> " (semana variou pouco)"

        VolumeGrowthBlock.STRAIN_SPIKE -> " (carga concentrou demais)"
    }

    private fun cappedConfidence(confidence: DataConfidence, cap: DataConfidence): DataConfidence {
        val rank = mapOf(DataConfidence.LOW to 0, DataConfidence.MEDIUM to 1, DataConfidence.HIGH to 2)
        return if (rank.getValue(confidence) <= rank.getValue(cap)) confidence else cap
    }

    private fun downgrade(confidence: DataConfidence): DataConfidence = when (confidence) {
        DataConfidence.HIGH -> DataConfidence.MEDIUM
        DataConfidence.MEDIUM, DataConfidence.LOW -> DataConfidence.LOW
    }

    companion object {
        /** Version of the weekly generation rules; persisted with each plan revision. */
        const val ENGINE_VERSION = "weekly-plan-v7"

        private const val RECOVERY_TITLE = "Recuperação: fácil e curto"
        private const val MICRO_DOSE_TITLE = "Strides curtos"
        private const val LONG_RUN_TITLE = "Longão"
        private const val LONG_RUN_HONEST_TITLE = "Corrida mais longa"
        private const val MICRO_DOSE_MIN = 25

        /** EASY curto após a prova na mesma semana (decisão do usuário 0, ADR 0043 §3.3). */
        private const val POST_RACE_EASY_MAX_MIN = 30
        private const val MIN_EASY_MIN = 20
        private const val MAX_EASY_MIN = 60
        private const val MIN_QUALITY_MIN = 25
        private const val MAX_QUALITY_MIN = 45
        private const val MIN_LONG_MIN = 30

        /** Long-run target: 28% of the final week, so the share is a target, not a ceiling. */
        private const val LONG_TARGET_SHARE = 0.28
        private const val LONG_LONGER_MARGIN = 5
        private const val LONG_FLOOR_HINT_RANGE = 15
        private const val PROGRESSION_COMPLETION = 0.80

        /** Consistent weeks before the beginner swaps strides for intro fartlek (ADR 0047 §2.2). */
        private const val INTRO_FARTLEK_CONSISTENT_WEEKS = 4
        private const val DELOAD_FACTOR = 0.75
        private val SPECIAL_VOLUME_BLOCKS = setOf(
            VolumeGrowthBlock.DELOAD,
            VolumeGrowthBlock.PEAK,
            VolumeGrowthBlock.TAPER,
            VolumeGrowthBlock.RACE_WEEK,
            VolumeGrowthBlock.RECOVERY,
        )
        private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
        private val WEEK_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", PT_BR)
    }
}

/**
 * Long-run floor by experience (ADR 0045 §2.2). Kept at package level so the
 * replanner can bound a legacy long-run correction without duplicating the
 * product constant.
 */
internal fun longRunFloorMinutes(experience: Experience): Int = when (experience) {
    Experience.BEGINNER, Experience.INTERMEDIATE -> 45
    Experience.ADVANCED -> 60
}
