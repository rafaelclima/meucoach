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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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
) {
    init {
        require(weekNumberInBlock >= 1) { "Week number in block must be >= 1" }
    }
}

/**
 * Builds a conservative week from the profile availability.
 *
 * The plan never increases volume without completion history. It allows at most
 * two quality sessions, only on a green day with calibration-adjacent guardrails,
 * and keeps them away from the long run. Every fourth consecutive week is a
 * deload. A taper always takes precedence over the deload.
 */
class WeeklyPlanGenerator {
    fun generate(input: WeeklyPlanInput): WeeklyPlan {
        val weekStart = input.weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val availability = input.profile.weeklyAvailability.minutesByDay
            .filterKeys { it in DayOfWeek.entries }
            .toSortedMap()
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
        val longDay = days.maxBy { day -> availability.getValue(day) }
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
        val completionRatio = input.previousWeek?.completionRatio() ?: 0.0
        val progressed = qualityAllowed && completionRatio >= PROGRESSION_COMPLETION && days.size >= 4
        val deload = input.weekNumberInBlock % DELOAD_EVERY_WEEKS == 0
        val qualityCandidateDays = days.filter { day ->
            val date = weekStart.plusDays((day.value - 1).toLong())
            race?.allowsQuality(date) ?: true
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
                        rationale = qualityRationale(input.profile),
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
                    val duration = available.coerceIn(MIN_EASY_MIN, MAX_EASY_MIN)
                    PlannedSession(
                        date = date,
                        type = WorkoutType.EASY,
                        title = "Fácil e conversável",
                        durationMin = duration,
                        rationale = "Base aeróbica conversável; sem treino forte neste dia.",
                        ruleIds = listOf("R-ZONE-01", "R-PLAN-06"),
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
        val adjusted = limitedLong.map { session ->
            when {
                race != null && race.isInTaperWindow(session.date) && session.durationMin != null ->
                    session.copy(durationMin = scaledMinutes(session, race.taperFactor(session.date)))

                deload && session.durationMin != null ->
                    session.copy(durationMin = scaledMinutes(session, DELOAD_FACTOR))

                else -> session
            }
        }
        val weeklyMinutes = adjusted.sumOf { it.durationMin ?: 0 }
        val longMinutes = adjusted.firstOrNull { it.type == WorkoutType.LONG }?.durationMin
        val qualityCount = adjusted.count { it.type == WorkoutType.QUALITY }
        val baseConfidence = cappedConfidence(input.readiness.confidence, DataConfidence.MEDIUM)
        val confidence = if (loadTrusted) baseConfidence else downgrade(baseConfidence)
        val rationale = buildString {
            if (taperActive) {
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
                if (!loadTrusted) add("R-DATA-01")
                if (loadTrusted && !acwrInRange) add("R-LOAD-03")
                if (taperActive) add("R-PLAN-05")
                if (taperActive) add("R-PLAN-10")
                if (deload && !taperActive) add("R-PLAN-04")
            },
            evidenceIds = buildList {
                add("EV-SEILER-2010")
                add("EV-DANIELS-2014")
                add("EV-PFITZINGER-2008")
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

    private fun WeeklyPlan.completionRatio(): Double {
        val planned = sessions
            .filter { it.type != WorkoutType.REST }
            .filterNot { it.status == SessionStatus.CLOSED && it.closureReason?.isNonTrainable == true }
        if (planned.isEmpty()) return 0.0
        // Only strong evidence unlocks progression (ADR 0024): a click cannot.
        return planned.count {
            it.status == SessionStatus.DONE && it.completionEvidence?.isStrong == true
        }.toDouble() / planned.size
    }

    private fun selectQualityDays(days: List<DayOfWeek>, longDay: DayOfWeek, maxQualities: Int): Set<DayOfWeek> {
        val selected = mutableListOf<DayOfWeek>()
        days.forEach { day ->
            if (selected.size >= maxQualities) return@forEach
            if (day == longDay || adjacentDays(day, longDay)) return@forEach
            if (selected.any { adjacentDays(it, day) }) return@forEach
            selected += day
        }
        return selected.toSet()
    }

    private fun qualityTitle(experience: Experience): String = when (experience) {
        Experience.ADVANCED -> "Intervalos"
        else -> "Tempo"
    }

    private fun qualityRationale(profile: AthleteProfile): String {
        val base = when (profile.experience) {
            Experience.ADVANCED ->
                "5×3 min forte (Z4–Z5) com 2 min de trote entre eles, em dia separado do longão."

            else ->
                "20 min contínuos em esforço moderado a forte (Z3–Z4), com aquecimento e " +
                    "desaquecimento, em dia separado do longão."
        }
        if (!profile.treadmillAccess) return base
        return base + " Na esteira (opção para calor): 5–10% de inclinação em esforço 8 de 10" +
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
        return String.format(PT_BR, " ou %.1f–%.1f km/h a 1%%", slowKmh, fastKmh)
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

    /** Volume-only reduction; intensity is preserved by keeping the session type. */
    private fun scaledMinutes(session: PlannedSession, factor: Double): Int {
        val current = requireNotNull(session.durationMin) { "Scaled session must have a duration" }
        val floor = if (session.type == WorkoutType.QUALITY) MIN_QUALITY_MIN else MIN_EASY_MIN
        return maxOf(floor, (current * factor).roundToInt())
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
        const val ENGINE_VERSION = "weekly-plan-v1"

        private const val MIN_EASY_MIN = 20
        private const val MAX_EASY_MIN = 60
        private const val MIN_QUALITY_MIN = 25
        private const val MAX_QUALITY_MIN = 45
        private const val MIN_LONG_MIN = 30
        private const val LONG_SHARE_RATIO = 3.0 / 7.0
        private const val PROGRESSION_COMPLETION = 0.80
        private const val ACWR_MIN = 0.8
        private const val ACWR_MAX = 1.3
        private const val DELOAD_EVERY_WEEKS = 4
        private const val DELOAD_FACTOR = 0.75
        private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
    }
}
