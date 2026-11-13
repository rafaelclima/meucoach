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
    val targets: PrescriptionTargets = PrescriptionTargets(),
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
) {
    init {
        require(title.isNotBlank()) { "Session title must not be blank" }
        require(ruleIds.isNotEmpty()) { "Session must cite at least one rule" }
        require(evidenceIds.isNotEmpty()) { "Session must cite at least one evidence" }
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
    /** 1-based position in the current block; every 4th week is a deload. */
    val weekNumberInBlock: Int = 1,
    /** Active race for taper/cutoff; falls back to the legacy profile goal when absent. */
    val race: RacePlan? = null,
    /** Every event date that must not receive an ordinary training session. */
    val eventDates: Set<LocalDate> = emptySet(),
    /** Post-race recovery window (ADR 0037); caps sessions and blocks quality/long run. */
    val recovery: RecoveryWindow? = null,
    /**
     * Previous persisted weeks, most recent first or any order (ADR 0038).
     * The volume base is derived from their strong-evidence completions.
     */
    val recentWeeks: List<WeeklyPlan> = emptyList(),
) {
    init {
        require(weekNumberInBlock >= 1) { "Week number in block must be >= 1" }
    }
}

/**
 * Builds a conservative week from the profile availability.
 *
 * The availability is a hard ceiling. Volume grows at most 6% over the highest
 * recently completed week and only with a green, evidenced, known-load week
 * (ADR 0038); otherwise it is maintained. The plan allows at most two quality
 * sessions, only on a green day with calibration-adjacent guardrails, and keeps
 * them away from the long run. Every fourth consecutive week is a deload. A
 * taper always takes precedence over the deload.
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
        val days = daysAfterToday.filter { day ->
            val date = weekStart.plusDays((day.value - 1).toLong())
            date !in eventDates && (race == null || !race.isRaceDayOrLater(date))
        }
        if (days.isEmpty()) {
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
        val longDay = days
            .filter { day ->
                val date = weekStart.plusDays((day.value - 1).toLong())
                recovery?.allowsLongRun(date) != false
            }
            .maxByOrNull { day -> availability.getValue(day) }
        val recoveryDays = days.map { weekStart.plusDays((it.value - 1).toLong()) }
            .filter { recovery?.isActive(it) == true }
        val recoveryActive = recoveryDays.isNotEmpty()
        val fullyRecovering = days.isNotEmpty() && recoveryDays.size == days.size
        val safetyBlock = input.readiness.flags.any {
            it == ReadinessFlag.ILLNESS_OR_INJURY || it == ReadinessFlag.NO_QUALITY
        } || input.readiness.level != ReadinessLevel.GREEN
        val loadState = input.load?.dataState ?: LoadDataState.INSUFFICIENT
        val loadTrusted = loadState == LoadDataState.KNOWN
        val acwr = input.load?.acwr
        val acwrInRange = acwr != null && acwr in ACWR_MIN..ACWR_MAX
        val qualityAllowed = !safetyBlock &&
            input.profile.experience != Experience.BEGINNER &&
            loadTrusted &&
            acwrInRange &&
            days.size >= 3
        val completionRatio = input.previousWeek?.strongCompletionRatio() ?: 0.0
        val progressed = qualityAllowed && completionRatio >= PROGRESSION_COMPLETION && days.size >= 4
        val deload = input.weekNumberInBlock % DELOAD_EVERY_WEEKS == 0
        val qualityCandidateDays = days.filter { day ->
            val date = weekStart.plusDays((day.value - 1).toLong())
            (race?.allowsQuality(date) ?: true) && (recovery?.allowsQuality(date) ?: true)
        }
        val qualityDays = if (qualityAllowed) {
            selectQualityDays(
                days = qualityCandidateDays,
                longDay = longDay,
                maxQualities = if (progressed) 2 else 1,
            )
        } else {
            emptySet()
        }

        val sessions = days.map { day ->
            val date = weekStart.plusDays((day.value - 1).toLong())
            val available = availability.getValue(day)
            when {
                day in qualityDays -> {
                    val duration = available.coerceIn(MIN_QUALITY_MIN, MAX_QUALITY_MIN)
                    PlannedSession(
                        date = date,
                        type = WorkoutType.QUALITY,
                        title = qualityTitle(input.profile.experience),
                        durationMin = duration,
                        rationale = qualityRationale(input.profile, duration, null),
                        ruleIds = buildList {
                            add("R-PLAN-02")
                            add("R-PLAN-06")
                            if (input.profile.treadmillAccess) add("R-PLAN-08")
                        },
                        evidenceIds = buildList {
                            add("EV-SEILER-2010")
                            add("EV-DANIELS-2014")
                            if (input.profile.treadmillAccess) add("EV-ELY-2007")
                        },
                        targets = PrescriptionPolicy.targets(
                            type = WorkoutType.QUALITY,
                            profile = input.profile,
                            durationMin = duration,
                        ),
                    )
                }

                day == longDay -> {
                    val duration = initialLongMinutes(available, input.profile.experience)
                    PlannedSession(
                        date = date,
                        type = WorkoutType.LONG,
                        title = "Longão",
                        durationMin = duration,
                        rationale = "Longão em ritmo fácil, 25–30% do volume semanal. Iniciante ≤ 150 min.",
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
                    val duration = available.coerceIn(MIN_EASY_MIN, MAX_EASY_MIN)
                        .let { minutes -> recoveryCap?.let { cap -> minOf(minutes, cap) } ?: minutes }
                    PlannedSession(
                        date = date,
                        type = WorkoutType.EASY,
                        title = if (isRecoveryDay) RECOVERY_TITLE else "Fácil e conversável",
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

        val easyMinutes = sessions
            .filter { it.type != WorkoutType.LONG }
            .sumOf { it.durationMin ?: 0 }
        val maxLong = maxOf(MIN_LONG_MIN, (easyMinutes * LONG_SHARE_RATIO).roundToInt())
        val limitedLong = sessions.map { session ->
            if (session.type == WorkoutType.LONG && session.durationMin != null) {
                if (session.durationMin > maxLong) session.copy(durationMin = maxLong) else session
            } else {
                session
            }
        }
        val taperActive = race?.isInTaperWindow(input.today) == true
        val availabilityCeiling = limitedLong.sumOf { it.durationMin ?: 0 }
        val volumeDecision = VolumeProgression().decide(
            targetWeekStart = weekStart,
            availabilityCeilingMinutes = availabilityCeiling,
            references = (input.recentWeeks + listOfNotNull(input.previousWeek)).distinctBy { it.weekStart },
            gates = VolumeGates(
                targetWeekBlock = volumeWeekBlock(race, recoveryActive, deload, weekStart, days),
                readinessGreen = input.readiness.level == ReadinessLevel.GREEN &&
                    input.readiness.flags.none {
                        it == ReadinessFlag.ILLNESS_OR_INJURY ||
                            it == ReadinessFlag.SHORT_SLEEP ||
                            it == ReadinessFlag.NO_QUALITY
                    },
                loadKnown = loadTrusted,
                acwrInRange = acwrInRange,
            ),
        )
        val volumeAdjusted = fitToVolume(limitedLong, volumeDecision.minutes)
        val adjusted = volumeAdjusted.sessions.map { session ->
            val scaled = when {
                race != null && race.isInTaperWindow(session.date) && session.durationMin != null ->
                    session.copy(durationMin = scaledMinutes(session, race.taperFactor(session.date)))

                deload && session.durationMin != null ->
                    session.copy(durationMin = scaledMinutes(session, DELOAD_FACTOR))

                else -> session
            }
            scaled.withQualityCopy(input.profile, race, deload, session.durationMin)
        }.map { it.withTargetsForDuration(input.profile) }
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
                    "Semana de descarga (${input.weekNumberInBlock} de 4 no bloco): volume −" +
                        "${((1.0 - DELOAD_FACTOR) * 100).roundToInt()}%, intensidade mantida. ",
                )
            }
            append(volumeRationale(volumeDecision, volumeAdjusted.droppedSessions))
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
            if (qualityCount == 0) {
                append("Sem treino forte: ")
                append(
                    when {
                        safetyBlock -> "prontidão não está alta."

                        input.profile.experience == Experience.BEGINNER -> "iniciante prioriza base."

                        loadState == LoadDataState.STALE ->
                            "seus dados de carga estão desatualizados; importe atividades recentes."

                        loadState == LoadDataState.INSUFFICIENT ->
                            "faltam dados de carga: são necessários ${LoadSnapshot.MIN_DAYS_WITH_LOAD} " +
                                "dias com carga em 28."

                        acwr != null && acwr > ACWR_MAX -> "sua carga subiu rápido."

                        acwr != null && acwr < ACWR_MIN -> "sua carga recente está baixa."

                        days.size < 3 -> "menos de 3 dias disponíveis."

                        else -> "sem dia seguro separado do longão."
                    },
                )
                append(" ")
            }
            if (progressed) {
                append("Progressão liberada: você concluiu pelo menos 80% da semana anterior. ")
                append("O volume segue limitado pela disponibilidade.")
            } else {
                append("Sem progressão até existir histórico de conclusão.")
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
                if (volumeDecision.hasHistory) add("R-PLAN-01")
                if (!loadTrusted) add("R-DATA-01")
                if (loadTrusted && !acwrInRange) add("R-LOAD-03")
                if (recoveryActive) add("R-RACE-04")
                if (raceGoalWithoutDate) add("R-PLAN-11")
                if (taperActive) add("R-PLAN-05")
                if (taperActive) add("R-PLAN-10")
                if (deload && !taperActive) add("R-PLAN-04")
            },
            evidenceIds = buildList {
                add("EV-SEILER-2010")
                add("EV-DANIELS-2014")
                add("EV-PFITZINGER-2008")
                if (volumeDecision.hasHistory) add("EV-GABBETT-2016")
                if (!loadTrusted || (loadTrusted && !acwrInRange)) {
                    add("EV-IMPELLIZZERI-2020")
                }
                if (taperActive) {
                    add("EV-BOSQUET-2007")
                    add("EV-MUJIKA-2003")
                }
                if (deload && !taperActive) add("EV-ISSURIN-2010")
            },
            createdOn = input.today,
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

    private fun qualityTitle(experience: Experience): String = when (experience) {
        Experience.ADVANCED -> "Intervalos"
        else -> "Ritmo forte"
    }

    /**
     * Quality sessions are described from their real duration, never from a
     * fixed block (N-03). Ten minutes are reserved for the warm-up; the
     * continuous block uses the rest minus a five-minute cool-down, and
     * intervals use 3 min of work with 2 min of easy running, scaled between
     * three and five repetitions. This is a product convention derived from
     * the examples in SPORTS_SCIENCE §8.4, not a new physiological claim.
     *
     * The taper note only appears when the session was actually shortened: a
     * 25-minute quality is already at the floor and must not claim a cut.
     */
    private fun qualityRationale(profile: AthleteProfile, durationMin: Int, reduction: QualityReduction?): String {
        require(durationMin >= MIN_QUALITY_MIN) {
            "Quality duration must be at least $MIN_QUALITY_MIN minutes"
        }
        val advanced = profile.experience == Experience.ADVANCED
        val base = if (advanced) {
            val reps = intervalRepetitions(durationMin)
            "Aqueça $QUALITY_WARMUP_MIN min em ritmo leve, faça $reps repetições de " +
                "$INTERVAL_WORK_MIN min fortes com $INTERVAL_RECOVERY_MIN min leves entre elas " +
                "e complete o restante em ritmo leve."
        } else {
            val work = durationMin - QUALITY_WARMUP_MIN - QUALITY_COOLDOWN_MIN
            "Aqueça $QUALITY_WARMUP_MIN min em ritmo leve, corra $work min em esforço " +
                "moderado a forte e termine com $QUALITY_COOLDOWN_MIN min em ritmo leve."
        }
        val note = when (reduction) {
            QualityReduction.TAPER -> "Semana de prova: ${qualityReductionNote(advanced)}"
            QualityReduction.DELOAD -> "Semana de descarga: ${qualityReductionNote(advanced)}"
            null -> null
        }
        return listOfNotNull(note, base, treadmillQualityHint(profile)).joinToString(" ")
    }

    private fun qualityReductionNote(advanced: Boolean): String =
        if (advanced) "menos repetições, mesmo esforço." else "bloco mais curto, mesmo esforço."

    private fun intervalRepetitions(durationMin: Int): Int {
        val available = durationMin - QUALITY_WARMUP_MIN
        val cycle = INTERVAL_WORK_MIN + INTERVAL_RECOVERY_MIN
        return ((available + INTERVAL_RECOVERY_MIN) / cycle).coerceIn(MIN_INTERVALS, MAX_INTERVALS)
    }

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
    ): PlannedSession {
        if (type != WorkoutType.QUALITY || durationMin == null) return this
        val inTaper = race?.isInTaperWindow(date) == true
        val reduction = when {
            originalDurationMin == null || durationMin >= originalDurationMin -> null
            inTaper -> QualityReduction.TAPER
            deload -> QualityReduction.DELOAD
            else -> null
        }
        val weekRules = buildList {
            if (inTaper) add("R-PLAN-05")
            if (deload && !inTaper) add("R-PLAN-04")
        }
        return copy(
            rationale = qualityRationale(profile, durationMin, reduction),
            ruleIds = (ruleIds + weekRules).distinct(),
        )
    }

    private fun adjacentDays(a: DayOfWeek, b: DayOfWeek): Boolean {
        val diff = kotlin.math.abs(a.value - b.value)
        return diff == 1 || diff == 6
    }

    private fun initialLongMinutes(available: Int, experience: Experience): Int {
        val cap = when (experience) {
            Experience.BEGINNER -> 150
            Experience.INTERMEDIATE -> 150
            Experience.ADVANCED -> 180
        }
        return available.coerceIn(MIN_LONG_MIN, cap)
    }

    /**
     * Distance targets are derived from duration; after volume/taper/deload
     * scaling they must be rebuilt so the session never shows a stale distance.
     */
    private fun PlannedSession.withTargetsForDuration(profile: AthleteProfile): PlannedSession =
        copy(targets = PrescriptionPolicy.targets(type, profile, durationMin))

    /** Volume-only reduction; intensity is preserved by keeping the session type. */
    private fun scaledMinutes(session: PlannedSession, factor: Double): Int {
        val current = requireNotNull(session.durationMin) { "Scaled session must have a duration" }
        val floor = if (session.type == WorkoutType.QUALITY) MIN_QUALITY_MIN else MIN_EASY_MIN
        return maxOf(floor, (current * factor).roundToInt())
    }

    /**
     * Scales the availability layout down to the volume target (ADR 0038).
     * Proportional scaling keeps the type mix and the long-run share. When the
     * type floors do not fit the target, the shortest easy days leave the week:
     * the demonstrated volume is the dose, availability is only a ceiling.
     */
    private fun fitToVolume(sessions: List<PlannedSession>, targetMinutes: Int): FittedVolume {
        val current = sessions.sumOf { it.durationMin ?: 0 }
        if (current == 0 || targetMinutes >= current) return FittedVolume(sessions, droppedSessions = 0)
        val factor = targetMinutes.toDouble() / current
        var scaled = sessions.map { session ->
            val minutes = session.durationMin ?: return@map session
            val floor = when (session.type) {
                WorkoutType.QUALITY -> MIN_QUALITY_MIN
                WorkoutType.LONG -> MIN_LONG_MIN
                else -> MIN_EASY_MIN
            }
            session.copy(durationMin = maxOf(floor, (minutes * factor).roundToInt()))
        }
        var total = scaled.sumOf { it.durationMin ?: 0 }
        var dropped = 0
        // Keep at least one easy day; quality and the long run are never dropped.
        val easyByDuration = scaled
            .filter { it.type == WorkoutType.EASY }
            .sortedBy { it.durationMin ?: 0 }
        for (easy in easyByDuration) {
            if (total <= targetMinutes) break
            if (scaled.count { it.type == WorkoutType.EASY } <= 1) break
            scaled = scaled.filterNot { it.date == easy.date }
            total -= easy.durationMin ?: 0
            dropped++
        }
        return FittedVolume(scaled.sortedBy { it.date }, droppedSessions = dropped)
    }

    private data class FittedVolume(val sessions: List<PlannedSession>, val droppedSessions: Int)

    private enum class QualityReduction { TAPER, DELOAD }

    /**
     * A deload, taper, race or recovery week never grows. A race week is labeled
     * first because it is also inside the taper window.
     */
    private fun volumeWeekBlock(
        race: RacePlan?,
        recoveryActive: Boolean,
        deload: Boolean,
        weekStart: LocalDate,
        days: List<DayOfWeek>,
    ): VolumeGrowthBlock {
        if (race != null && race.targetDate in weekStart..weekStart.plusDays(6)) {
            return VolumeGrowthBlock.RACE_WEEK
        }
        val dates = days.map { weekStart.plusDays((it.value - 1).toLong()) }
        if (race != null && dates.any { race.isInTaperWindow(it) }) return VolumeGrowthBlock.TAPER
        if (recoveryActive) return VolumeGrowthBlock.RECOVERY
        if (deload) return VolumeGrowthBlock.DELOAD
        return VolumeGrowthBlock.NONE
    }

    private fun volumeRationale(decision: VolumeDecision, droppedSessions: Int): String {
        val base = decision.baseMinutes
        val week = decision.baseWeekStart?.format(WEEK_LABEL)
        val droppedHint = if (droppedSessions > 0) {
            " A sua disponibilidade permite mais dias; esta semana usa o volume que cabe nela."
        } else {
            ""
        }
        if (base == null || week == null) {
            return "Volume definido pela sua disponibilidade: ainda não há semana concluída " +
                "com evidência para ajustar."
        }
        val specialWeek = decision.block in SPECIAL_VOLUME_BLOCKS
        if (specialWeek) {
            return "Volume: base de $base min concluídos na semana de $week; sem aumento nesta " +
                "semana${volumeBlockHint(decision.block)}.$droppedHint"
        }
        if (decision.cappedByAvailability) {
            return "Volume: base de $base min concluídos na semana de $week; limitado pela sua " +
                "disponibilidade em ${decision.minutes} min."
        }
        if (decision.growthPercent > 0) {
            return "Volume: base de $base min concluídos na semana de $week; " +
                "+${decision.growthPercent}% nesta semana, alvo de ${decision.minutes} min.$droppedHint"
        }
        return "Volume: base de $base min concluídos na semana de $week; alvo mantido em " +
            "${decision.minutes} min${volumeBlockHint(decision.block)}.$droppedHint"
    }

    private fun volumeBlockHint(block: VolumeGrowthBlock): String = when (block) {
        VolumeGrowthBlock.NONE,
        VolumeGrowthBlock.NO_HISTORY,
        -> ""

        VolumeGrowthBlock.NO_PREVIOUS_WEEK -> " (sem semana anterior concluída)"

        VolumeGrowthBlock.PREVIOUS_WEEK_BELOW_80 -> " (semana anterior abaixo de 80%)"

        VolumeGrowthBlock.PREVIOUS_WEEK_SPECIAL -> " (semana anterior de descarga ou prova)"

        VolumeGrowthBlock.DELOAD -> " (descarga)"

        VolumeGrowthBlock.TAPER, VolumeGrowthBlock.RACE_WEEK -> " (prova)"

        VolumeGrowthBlock.RECOVERY -> " (recuperação após a prova)"

        VolumeGrowthBlock.READINESS -> " (prontidão não está alta)"

        VolumeGrowthBlock.LOAD_UNKNOWN -> " (dados de carga insuficientes)"

        VolumeGrowthBlock.ACWR_OUT_OF_RANGE -> " (carga recente fora da faixa)"
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
        const val ENGINE_VERSION = "weekly-plan-v4"

        private const val RECOVERY_TITLE = "Recuperação: fácil e curto"
        private const val MIN_EASY_MIN = 20
        private const val MAX_EASY_MIN = 60
        private const val MIN_QUALITY_MIN = 25
        private const val MAX_QUALITY_MIN = 45
        private const val QUALITY_WARMUP_MIN = 10
        private const val QUALITY_COOLDOWN_MIN = 5
        private const val INTERVAL_WORK_MIN = 3
        private const val INTERVAL_RECOVERY_MIN = 2
        private const val MIN_INTERVALS = 3
        private const val MAX_INTERVALS = 5
        private const val MIN_LONG_MIN = 30
        private const val LONG_SHARE_RATIO = 3.0 / 7.0
        private const val PROGRESSION_COMPLETION = 0.80
        private const val ACWR_MIN = 0.8
        private const val ACWR_MAX = 1.3
        private const val DELOAD_EVERY_WEEKS = 4
        private const val DELOAD_FACTOR = 0.75
        private val SPECIAL_VOLUME_BLOCKS = setOf(
            VolumeGrowthBlock.DELOAD,
            VolumeGrowthBlock.TAPER,
            VolumeGrowthBlock.RACE_WEEK,
            VolumeGrowthBlock.RECOVERY,
        )
        private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
        private val WEEK_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", PT_BR)
    }
}
