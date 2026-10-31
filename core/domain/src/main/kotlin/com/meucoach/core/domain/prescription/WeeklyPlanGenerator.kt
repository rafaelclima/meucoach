package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.domain.zones.PaceZoneCalculator
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.PaceZone
import com.meucoach.core.model.ReadinessLevel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

enum class SessionStatus {
    PLANNED,
    DONE,
    SKIPPED,
}

data class PlannedSession(
    val date: LocalDate,
    val type: WorkoutType,
    val title: String,
    val durationMin: Int?,
    val rationale: String,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val status: SessionStatus = SessionStatus.PLANNED,
    val skipReason: String? = null,
) {
    init {
        require(title.isNotBlank()) { "Session title must not be blank" }
        require(ruleIds.isNotEmpty()) { "Session must cite at least one rule" }
        require(evidenceIds.isNotEmpty()) { "Session must cite at least one evidence" }
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
    val weekStart: LocalDate = LocalDate.now(),
    val previousWeek: WeeklyPlan? = null,
)

/**
 * Builds a conservative week from the profile availability.
 *
 * The plan never increases volume without completion history. It allows at most
 * one quality session, only on a green day with calibration-adjacent guardrails,
 * and keeps it away from the long run.
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
        )
        if (availability.isEmpty()) {
            return base.copy(
                rationale = "Configure sua disponibilidade semanal para gerar um plano. " +
                    "Sem isso, o app não inventa dias de treino.",
            )
        }

        val days = availability.keys.toList()
        val longDay = days.maxBy { day -> availability.getValue(day) }
        val safetyBlock = input.readiness.flags.any {
            it == ReadinessFlag.ILLNESS_OR_INJURY || it == ReadinessFlag.NO_QUALITY
        } || input.readiness.level != ReadinessLevel.GREEN
        val acwr = input.load?.acwr
        val qualityAllowed = !safetyBlock &&
            input.profile.experience != Experience.BEGINNER &&
            (acwr == null || acwr in ACWR_MIN..ACWR_MAX) &&
            days.size >= 3
        val completionRatio = input.previousWeek?.completionRatio() ?: 0.0
        val progressed = qualityAllowed && completionRatio >= PROGRESSION_COMPLETION && days.size >= 4
        val qualityDays = if (qualityAllowed) {
            selectQualityDays(
                days = days,
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
                day in qualityDays -> PlannedSession(
                    date = date,
                    type = WorkoutType.QUALITY,
                    title = qualityTitle(input.profile.experience),
                    durationMin = available.coerceIn(MIN_QUALITY_MIN, MAX_QUALITY_MIN),
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
                )

                day == longDay -> PlannedSession(
                    date = date,
                    type = WorkoutType.LONG,
                    title = "Longão",
                    durationMin = initialLongMinutes(available, input.profile.experience),
                    rationale = "Longão em Z2, 25–30% do volume semanal. Iniciante ≤ 150 min.",
                    ruleIds = listOf("R-PLAN-03"),
                    evidenceIds = listOf("EV-PFITZINGER-2008", "EV-DANIELS-2014"),
                )

                else -> PlannedSession(
                    date = date,
                    type = WorkoutType.EASY,
                    title = "Fácil em Z2",
                    durationMin = available.coerceIn(MIN_EASY_MIN, MAX_EASY_MIN),
                    rationale = "Base aeróbica conversável; sem qualidade neste dia.",
                    ruleIds = listOf("R-ZONE-01", "R-PLAN-06"),
                    evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
                )
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
        val taper = taperFor(input.profile.goal, weekStart)
        val adjusted = if (taper == null) {
            limitedLong
        } else {
            limitedLong.map { session ->
                if (session.type == WorkoutType.QUALITY || session.durationMin == null) {
                    session
                } else {
                    session.copy(
                        durationMin = maxOf(
                            MIN_EASY_MIN,
                            (session.durationMin * taper.factor).roundToInt(),
                        ),
                    )
                }
            }
        }
        val weeklyMinutes = adjusted.sumOf { it.durationMin ?: 0 }
        val longMinutes = adjusted.firstOrNull { it.type == WorkoutType.LONG }?.durationMin
        val qualityCount = adjusted.count { it.type == WorkoutType.QUALITY }
        val confidence = cappedConfidence(input.readiness.confidence, DataConfidence.MEDIUM)
        val rationale = buildString {
            if (taper != null) {
                append("Taper para a prova em ${taper.daysToRace} dias: volume reduzido. ")
            }
            append("${adjusted.size} dias, $weeklyMinutes min no total. ")
            if (qualityCount == 0) {
                append("Sem qualidade: ")
                append(
                    when {
                        safetyBlock -> "prontidão não está verde."
                        input.profile.experience == Experience.BEGINNER -> "iniciante prioriza base."
                        (acwr ?: 0.0) > ACWR_MAX -> "ACWR alto."
                        days.size < 3 -> "menos de 3 dias disponíveis."
                        else -> "sem dia seguro separado do longão."
                    },
                )
                append(" ")
            }
            if (progressed) {
                append("Progressão por dose de qualidade: semana anterior ≥80% concluída. ")
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
                if (taper != null) add("R-PLAN-05")
            },
            evidenceIds = buildList {
                add("EV-SEILER-2010")
                add("EV-DANIELS-2014")
                add("EV-PFITZINGER-2008")
                if (taper != null) {
                    add("EV-BOSQUET-2007")
                    add("EV-MUJIKA-2003")
                }
            },
        )
    }

    private fun WeeklyPlan.completionRatio(): Double {
        val planned = sessions.filter { it.type != WorkoutType.REST }
        if (planned.isEmpty()) return 0.0
        return planned.count { it.status == SessionStatus.DONE }.toDouble() / planned.size
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
            Experience.ADVANCED -> "5×3 min em Z4–Z5 com 2 min de trote entre eles, em dia separado do longão."
            else -> "20 min contínuos em Z3–Z4, com aquecimento e desaquecimento, em dia separado do longão."
        }
        if (!profile.treadmillAccess) return base
        return base + " Na esteira (opção para calor): 5–10% de inclinação em RPE 8/10" +
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

    private data class Taper(val factor: Double, val daysToRace: Long)

    private fun taperFor(goal: com.meucoach.core.model.TrainingGoal?, weekStart: LocalDate): Taper? {
        val target = goal?.targetDate ?: return null
        val window = if (goal.type == com.meucoach.core.model.GoalType.MARATHON) 21 else 14
        val days = java.time.temporal.ChronoUnit.DAYS.between(weekStart, target)
        if (days !in 0..window.toLong()) return null
        val factor = if (days <= 7) 0.45 else 0.55
        return Taper(factor = factor, daysToRace = days)
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

    private fun cappedConfidence(confidence: DataConfidence, cap: DataConfidence): DataConfidence {
        val rank = mapOf(DataConfidence.LOW to 0, DataConfidence.MEDIUM to 1, DataConfidence.HIGH to 2)
        return if (rank.getValue(confidence) <= rank.getValue(cap)) confidence else cap
    }

    private companion object {
        const val MIN_EASY_MIN = 20
        const val MAX_EASY_MIN = 60
        const val MIN_QUALITY_MIN = 25
        const val MAX_QUALITY_MIN = 45
        const val MIN_LONG_MIN = 30
        const val LONG_SHARE_RATIO = 3.0 / 7.0
        const val PROGRESSION_COMPLETION = 0.80
        const val ACWR_MIN = 0.8
        const val ACWR_MAX = 1.3
        val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
    }
}
