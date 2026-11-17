package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * C4-02 — the weekly plan separates the structural opportunity from the day
 * gate (ADR 0044 §2.1–2.3, FR-401).
 *
 * The first opportunity depends on level, days, phase, spacing and
 * availability; it never inherits Monday's readiness or load. The day keeps or
 * downgrades it through [QualityGate]/[DailyPrescriptionEngine]. Only the
 * second opportunity asks for progression proof (completion, reliable ACWR in
 * 0.8–1.3 and no taper).
 */
class StructuralQualityTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `first opportunity exists without a check-in and without load`() {
        val plan = plan(
            readiness = readiness(
                level = ReadinessLevel.AMBER,
                components = emptyMap(),
                flags = setOf(ReadinessFlag.INSUFFICIENT_DATA, ReadinessFlag.SUBJECTIVE_MISSING),
            ),
            load = null,
        )

        val quality = plan.sessions.single { it.type == WorkoutType.QUALITY }

        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(quality.qualityKind).isNotNull
        assertThat(quality.structure).isNotNull
        assertThat(quality.planDecision?.reason).isEqualTo(PlanDecisionReason.QUALITY_OPPORTUNITY)
        assertThat(quality.planDecision?.detail).contains("o dia confirma")
        assertThat(plan.rationale).contains("Qualidade planejada", "o dia confirma")
    }

    @Test
    fun `first opportunity survives a reliable high ACWR and high monotony`() {
        val highAcwr = plan(
            load = load(acwr = 1.6),
        )
        val highMonotony = plan(
            load = load(acwr = 1.0, monotony = 2.5, strain = 900.0),
        )

        assertThat(highAcwr.qualityCount).isEqualTo(1)
        assertThat(highMonotony.qualityCount).isEqualTo(1)
        assertThat(highAcwr.sessions.single { it.type == WorkoutType.QUALITY }.planDecision?.reason)
            .isEqualTo(PlanDecisionReason.QUALITY_OPPORTUNITY)
    }

    @Test
    fun `ambers and red readiness keep the structural opportunity for the day`() {
        val amber = plan(readiness = readiness(level = ReadinessLevel.AMBER))
        val red = plan(readiness = readiness(level = ReadinessLevel.RED))
        val illness = plan(
            readiness = readiness(
                level = ReadinessLevel.GREEN,
                flags = setOf(ReadinessFlag.ILLNESS_OR_INJURY),
            ),
        )

        assertThat(amber.qualityCount).isEqualTo(1)
        assertThat(red.qualityCount).isEqualTo(1)
        assertThat(illness.qualityCount).isEqualTo(1)
    }

    @Test
    fun `second opportunity needs completion and a reliable ACWR inside the progression band`() {
        val fullWeek = priorWeek(completed = 4, total = 4)
        val halfWeek = priorWeek(completed = 2, total = 4)
        val progressed = plan(
            load = load(acwr = 1.0),
            previousWeek = fullWeek,
        )
        val lowCompletion = plan(
            load = load(acwr = 1.0),
            previousWeek = halfWeek,
        )
        val outsideBand = plan(
            load = load(acwr = 1.4),
            previousWeek = fullWeek,
        )
        val unreliable = plan(
            load = load(acwr = 1.0, daysWithoutData = 1),
            previousWeek = fullWeek,
        )

        assertThat(progressed.qualityCount).isEqualTo(2)
        assertThat(progressed.sessions.count { it.type == WorkoutType.QUALITY }).isEqualTo(2)
        val second = progressed.sessions
            .single { it.planDecision?.reason == PlanDecisionReason.PROGRESSION_COMPLETED }
        assertThat(second.ruleIds).contains("R-PER-02")

        assertThat(lowCompletion.qualityCount).isEqualTo(1)
        assertThat(outsideBand.qualityCount).isEqualTo(1)
        assertThat(unreliable.qualityCount).isEqualTo(1)
    }

    @Test
    fun `three days never carry two opportunities even with progression`() {
        val plan = plan(
            availability = mapOf(
                DayOfWeek.TUESDAY to 40,
                DayOfWeek.THURSDAY to 40,
                DayOfWeek.SUNDAY to 40,
            ),
            load = load(acwr = 1.0),
            previousWeek = priorWeek(completed = 3, total = 3),
        )

        assertThat(plan.qualityCount).isEqualTo(1)
    }

    @Test
    fun `tap and race weeks expose only the structural micro-dose`() {
        val taper = plan(
            raceDays = 12,
            distance = RaceDistance.TEN_K,
            readiness = readiness(
                level = ReadinessLevel.AMBER,
                components = emptyMap(),
                flags = setOf(ReadinessFlag.INSUFFICIENT_DATA, ReadinessFlag.SUBJECTIVE_MISSING),
            ),
            load = null,
        )
        val raceWeek = plan(raceDays = 3)

        val taperQuality = taper.sessions.single { it.type == WorkoutType.QUALITY }
        assertThat(taperQuality.qualityKind).isEqualTo(QualityKind.STRIDES_ONLY)
        assertThat(taperQuality.durationMin).isLessThanOrEqualTo(25)
        assertThat(taperQuality.planDecision?.reason).isEqualTo(PlanDecisionReason.QUALITY_OPPORTUNITY)
        assertThat(
            raceWeek.sessions.filter { it.type == WorkoutType.QUALITY }.all {
                it.qualityKind == QualityKind.STRIDES_ONLY
            },
        ).isTrue()
        assertThat(raceWeek.sessions.none { it.date == raceWeek.weekStart.plusDays(3) }).isTrue()
    }

    @Test
    fun `recovery and beginner weeks keep zero opportunities`() {
        val recovery = plan(raceDays = null, recovery = recovery(RaceDistance.FIVE_K))
        val beginner = plan(level = Experience.BEGINNER)

        assertThat(recovery.qualityCount).isZero()
        assertThat(beginner.qualityCount).isZero()
        assertThat(beginner.sessions.none { it.type == WorkoutType.QUALITY }).isTrue()
    }

    private fun plan(
        raceDays: Int? = 34,
        distance: RaceDistance = RaceDistance.FIVE_K,
        level: Experience = Experience.INTERMEDIATE,
        readiness: ReadinessResult = readiness(),
        load: LoadSnapshot? = load(),
        previousWeek: WeeklyPlan? = null,
        recovery: RecoveryWindow? = null,
        availability: Map<DayOfWeek, Int> = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        ),
    ): WeeklyPlan = generator.generate(
        WeeklyPlanInput(
            profile = AthleteProfile(
                id = "athlete-1",
                birthDate = LocalDate.of(1988, 1, 1),
                sex = Sex.MALE,
                restingHeartRate = 58,
                maxHeartRateObserved = 187,
                lactateThresholdHeartRate = 180,
                thresholdPace = Pace.fromSecondsPerKm(360),
                experience = level,
                timezone = "America/Sao_Paulo",
                weeklyAvailability = WeeklyAvailability(availability),
            ),
            readiness = readiness,
            load = load,
            weekStart = monday,
            today = monday,
            previousWeek = previousWeek,
            recovery = recovery,
            race = raceDays?.let { RacePlan("race-1", distance, monday.plusDays(it.toLong())) },
        ),
    )

    private fun readiness(
        level: ReadinessLevel = ReadinessLevel.GREEN,
        components: Map<ReadinessComponent, Int> = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        flags: Set<ReadinessFlag> = emptySet(),
    ) = ReadinessResult(
        score = if (level == ReadinessLevel.GREEN) 80 else 55,
        level = level,
        confidence = DataConfidence.HIGH,
        components = components,
        missing = if (components.isEmpty()) ReadinessComponent.entries.toSet() else emptySet(),
        flags = flags,
    )

    private fun load(acwr: Double = 1.0, monotony: Double = 1.2, strain: Double = 400.0, daysWithoutData: Int = 0) =
        LoadSnapshot(
            daily = emptyList(),
            ctlSeries = emptyList(),
            atlSeries = emptyList(),
            ctl = 0.0,
            atl = 0.0,
            tsb = 0.0,
            acuteAverage7d = 0.0,
            chronicAverage28d = 0.0,
            acwr = acwr,
            monotony = monotony,
            missingDays = 16,
            coverage = LoadCoverage(
                daysInWindow = 28,
                daysWithLoad = 12 - daysWithoutData,
                daysWithoutActivity = 16,
                daysWithoutData = daysWithoutData,
                lastActivityAt = java.time.Instant.parse("2026-10-30T10:00:00Z"),
                sourceFresh = true,
            ),
            subjectiveDaysInWindow = 0,
            strain = strain,
        )

    private fun priorWeek(completed: Int, total: Int, weekStart: LocalDate = monday.minusWeeks(1)): WeeklyPlan {
        val sessions = (0 until total).map { index ->
            PlannedSession(
                date = weekStart.plusDays(index.toLong()),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = if (index < completed) SessionStatus.DONE else SessionStatus.PLANNED,
                completionEvidence = if (index < completed) CompletionEvidence.ACTIVITY else null,
                activityId = if (index < completed) "activity-$weekStart-$index" else null,
            )
        }
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana anterior.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = weekStart,
        )
    }

    private fun recovery(distance: RaceDistance) =
        RecoveryWindow(CompletedRace("race-1", distance, monday.minusDays(2)))
}
