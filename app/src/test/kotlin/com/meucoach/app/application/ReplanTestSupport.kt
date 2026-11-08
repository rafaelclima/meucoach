package com.meucoach.app.application

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import java.time.DayOfWeek
import java.time.LocalDate

internal val REPLAN_TODAY: LocalDate = LocalDate.of(2026, 11, 4)
internal val REPLAN_WEEK_START: LocalDate = LocalDate.of(2026, 11, 2)

internal fun replanProfile(fridayMinutes: Int = 30): AthleteProfile = AthleteProfile(
    id = "athlete-1",
    birthDate = LocalDate.of(1988, 1, 1),
    sex = Sex.MALE,
    restingHeartRate = 58,
    maxHeartRateObserved = 187,
    lactateThresholdHeartRate = 180,
    thresholdPace = Pace.fromSecondsPerKm(360),
    experience = Experience.INTERMEDIATE,
    timezone = "UTC",
    weeklyAvailability = WeeklyAvailability(
        mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.TUESDAY to 35,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to fridayMinutes,
        ),
    ),
)

internal fun replanCheckIn(date: LocalDate = REPLAN_TODAY, illness: Boolean = false) = DailyCheckIn(
    date = date,
    sleepQuality = 4,
    fatigue = 2,
    soreness = 1,
    stress = 2,
    illnessOrInjury = illness,
)

internal fun replanPlan(
    weekStart: LocalDate = REPLAN_WEEK_START,
    createdOn: LocalDate = weekStart,
    sessions: List<PlannedSession> = listOf(
        easySession(weekStart),
        easySession(weekStart.plusDays(1), durationMin = 35),
        easySession(weekStart.plusDays(2), durationMin = 40),
        qualitySession(weekStart.plusDays(4)),
    ),
) = WeeklyPlan(
    weekStart = weekStart,
    sessions = sessions,
    weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
    longRunMinutes = sessions.firstOrNull { it.type == WorkoutType.LONG }?.durationMin,
    qualityCount = sessions.count { it.type == WorkoutType.QUALITY },
    rationale = "Semana original.",
    confidence = DataConfidence.LOW,
    ruleIds = listOf("R-PLAN-02", "R-PLAN-03", "R-ZONE-01"),
    evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
    createdOn = createdOn,
)

internal fun easySession(
    date: LocalDate,
    durationMin: Int = 30,
    status: SessionStatus = SessionStatus.PLANNED,
    evidence: CompletionEvidence? = null,
) = PlannedSession(
    date = date,
    type = WorkoutType.EASY,
    title = "Fácil e conversável",
    durationMin = durationMin,
    rationale = "Base.",
    ruleIds = listOf("R-ZONE-01"),
    evidenceIds = listOf("EV-SEILER-2010"),
    status = status,
    completionEvidence = evidence,
)

internal fun qualitySession(date: LocalDate, durationMin: Int = 30, status: SessionStatus = SessionStatus.PLANNED) =
    PlannedSession(
        date = date,
        type = WorkoutType.QUALITY,
        title = "Tempo",
        durationMin = durationMin,
        rationale = "Qualidade.",
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-DANIELS-2014"),
        status = status,
    )
