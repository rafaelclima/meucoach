package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
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
 * C4-09..C4-12 — long-run sizing (ADR 0045, FR-402).
 *
 * The long day is the highest availability day, ties prefer the weekend and
 * then the later day. The session has a 45/60 min floor (30 in deload/taper)
 * and is never shorter than the longest other session. Availability stays a
 * hard ceiling; below the floor the plan says "Corrida mais longa" and records
 * `LONG_DAY_TOO_SHORT` instead of pretending it is a long run.
 */
class LongRunSizingTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `long day tie prefers the weekend and the later day`() {
        val plan = plan(
            experience = Experience.INTERMEDIATE,
            availability = mapOf(
                DayOfWeek.TUESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 40,
            ),
        )

        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(longRun.date.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(longRun.durationMin).isEqualTo(40)
        assertThat(longRun.title).isEqualTo("Corrida mais longa")
    }

    @Test
    fun `availability outranks the weekend preference`() {
        val plan = plan(
            experience = Experience.INTERMEDIATE,
            availability = mapOf(
                DayOfWeek.TUESDAY to 90,
                DayOfWeek.FRIDAY to 60,
                DayOfWeek.SUNDAY to 60,
            ),
        )

        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(longRun.date.dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
        assertThat(longRun.durationMin).isEqualTo(65)
        assertThat(longRun.title).isEqualTo("Longão")
    }

    @Test
    fun `G-PER-04 two forties plus ninety gives a forty five minute long run`() {
        val plan = plan(
            experience = Experience.INTERMEDIATE,
            availability = mapOf(
                DayOfWeek.TUESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SATURDAY to 90,
            ),
            load = load(),
            race = buildRace(),
        )

        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(longRun.date.dayOfWeek).isEqualTo(DayOfWeek.SATURDAY)
        assertThat(longRun.durationMin).isEqualTo(45)
        assertThat(longRun.title).isEqualTo("Longão")
        assertThat(longRun.planDecision).isNull()
        assertThat(plan.weeklyMinutes).isEqualTo(125)
        assertThat(plan.longRunMinutes).isEqualTo(45)
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.sessions.single { it.type == WorkoutType.QUALITY }.date.dayOfWeek)
            .isEqualTo(DayOfWeek.TUESDAY)
        assertThat(plan.sessions.single { it.type == WorkoutType.EASY }.durationMin).isEqualTo(40)
    }

    @Test
    fun `three forties keep the honest title and explain the agenda limit`() {
        val plan = plan(
            experience = Experience.INTERMEDIATE,
            availability = mapOf(
                DayOfWeek.TUESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 40,
            ),
        )

        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(longRun.date.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(longRun.durationMin).isEqualTo(40)
        assertThat(longRun.title).isEqualTo("Corrida mais longa")
        assertThat(longRun.planDecision?.reason).isEqualTo(PlanDecisionReason.LONG_DAY_TOO_SHORT)
        assertThat(longRun.rationale)
            .contains("40 min", "piso para um longão é 45 min", "domingo")
        assertThat(plan.sessions.map { it.title }).doesNotContain("Longão")
        // C4-18: o intermediário ganha strides (terça 25 no lugar de 40).
        assertThat(plan.weeklyMinutes).isEqualTo(105)
    }

    @Test
    fun `four sixty minute days keep the long run as the longest with strides`() {
        val plan = plan(
            experience = Experience.INTERMEDIATE,
            availability = mapOf(
                DayOfWeek.TUESDAY to 60,
                DayOfWeek.THURSDAY to 60,
                DayOfWeek.SATURDAY to 60,
                DayOfWeek.SUNDAY to 60,
            ),
        )

        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(longRun.date.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(longRun.durationMin).isEqualTo(60)
        assertThat(longRun.title).isEqualTo("Longão")
        // C4-18: terça vira strides (25 no lugar de 60); o share do longão é
        // alvo sobre os outros reais, não teto — 60/205 em vez de 60/240.
        assertThat(plan.weeklyMinutes).isEqualTo(205)
        assertThat(longRun.durationMin!!.toDouble() / plan.weeklyMinutes).isEqualTo(60.0 / 205.0)
        assertThat(longRun.durationMin).isGreaterThanOrEqualTo(
            plan.sessions.filter { it.type != WorkoutType.LONG }.maxOf { it.durationMin!! },
        )
    }

    @Test
    fun `advanced floor is sixty and stays below the day ceiling`() {
        val withRoom = plan(
            experience = Experience.ADVANCED,
            availability = mapOf(
                DayOfWeek.TUESDAY to 50,
                DayOfWeek.FRIDAY to 50,
                DayOfWeek.SUNDAY to 90,
            ),
        )
        val withoutRoom = plan(
            experience = Experience.ADVANCED,
            availability = mapOf(
                DayOfWeek.TUESDAY to 50,
                DayOfWeek.WEDNESDAY to 50,
                DayOfWeek.FRIDAY to 50,
            ),
        )

        assertThat(withRoom.sessions.single { it.type == WorkoutType.LONG }.durationMin).isEqualTo(60)
        assertThat(withRoom.sessions.single { it.type == WorkoutType.LONG }.title).isEqualTo("Longão")

        val honest = withoutRoom.sessions.single { it.type == WorkoutType.LONG }
        assertThat(honest.durationMin).isEqualTo(50)
        assertThat(honest.title).isEqualTo("Corrida mais longa")
        assertThat(honest.planDecision?.reason).isEqualTo(PlanDecisionReason.LONG_DAY_TOO_SHORT)
    }

    @Test
    fun `fitToVolume keeps every session and trims the overshoot when floors allow`() {
        val previous = completedWeek(minutes = 130)
        val plan = plan(
            experience = Experience.INTERMEDIATE,
            availability = mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.TUESDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.THURSDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 90,
            ),
            load = load(),
            previousWeek = previous,
            race = buildRace(),
        )

        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(plan.rationale).contains("alvo de 138 min", "Semana mínima", "entregue 155 min")
        assertThat(plan.weeklyMinutes).isEqualTo(155)
        assertThat(plan.qualityCount).isEqualTo(2)
        // D2: no day is dropped to fit the target; the floors anchor the week.
        assertThat(plan.sessions).hasSize(6)
        assertThat(plan.sessions.count { it.type == WorkoutType.EASY }).isEqualTo(3)
        assertThat(plan.sessions.any { it.date.dayOfWeek == DayOfWeek.MONDAY }).isTrue()
        assertThat(longRun.durationMin).isEqualTo(45)
        assertThat(longRun.durationMin).isGreaterThanOrEqualTo(
            plan.sessions.filter { it.type != WorkoutType.LONG }.maxOf { it.durationMin!! },
        )
    }

    @Test
    fun `minimal week preserves the floors and declares the volume above target`() {
        val previous = completedWeek(minutes = 100)
        val plan = plan(
            experience = Experience.INTERMEDIATE,
            availability = mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.TUESDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.THURSDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 90,
            ),
            load = load(),
            previousWeek = previous,
            race = buildRace(),
        )

        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(plan.rationale).contains(
            "alvo de 106 min",
            "entregue 155 min",
            "Semana mínima",
            "não é progressão",
        )
        assertThat(plan.weeklyMinutes).isEqualTo(155)
        assertThat(plan.sessions).hasSize(6)
        assertThat(plan.qualityCount).isEqualTo(2)
        assertThat(longRun.durationMin).isEqualTo(45)
        assertThat(longRun.durationMin).isGreaterThanOrEqualTo(
            plan.sessions.filter { it.type != WorkoutType.LONG }.maxOf { it.durationMin!! },
        )
    }

    @Test
    fun `deload shortens the long run and uses the honest title`() {
        val history = listOf(
            completedWeek(monday.minusWeeks(3), minutes = 120),
            completedWeek(monday.minusWeeks(2), minutes = 120),
            completedWeek(monday.minusWeeks(1), minutes = 120),
        )
        val plan = plan(
            experience = Experience.BEGINNER,
            availability = mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.SATURDAY to 90,
            ),
            previousWeek = history.last(),
            recentWeeks = history,
        )

        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(plan.ruleIds).contains("R-PLAN-04")
        // ADR 0067: without a normal long-run reference the first long run is
        // the floor; the deload reduces that floor to 30.
        assertThat(longRun.durationMin).isEqualTo(30)
        assertThat(longRun.title).isEqualTo("Corrida mais longa")
        // The limit is the deload, not the agenda: no agenda hint is recorded.
        assertThat(longRun.planDecision).isNull()
        assertThat(plan.weeklyMinutes).isEqualTo(80)
    }

    private fun plan(
        experience: Experience,
        availability: Map<DayOfWeek, Int>,
        load: LoadSnapshot? = null,
        previousWeek: WeeklyPlan? = null,
        recentWeeks: List<WeeklyPlan> = emptyList(),
        race: RacePlan? = null,
    ) = generator.generate(
        WeeklyPlanInput(
            profile = AthleteProfile(
                id = "athlete-1",
                birthDate = LocalDate.of(1988, 1, 1),
                sex = Sex.MALE,
                restingHeartRate = 58,
                maxHeartRateObserved = 187,
                lactateThresholdHeartRate = 180,
                thresholdPace = Pace.fromSecondsPerKm(360),
                experience = experience,
                timezone = "America/Sao_Paulo",
                weeklyAvailability = WeeklyAvailability(availability),
            ),
            readiness = ReadinessResult(
                score = 80,
                level = ReadinessLevel.GREEN,
                confidence = DataConfidence.HIGH,
                components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
                missing = emptySet(),
                flags = emptySet(),
            ),
            load = load,
            weekStart = monday,
            today = monday,
            previousWeek = previousWeek,
            recentWeeks = recentWeeks,
            race = race,
        ),
    )

    /** A usable prior week whose completed strong volume is [minutes]. */
    private fun completedWeek(weekStart: LocalDate = monday.minusWeeks(1), minutes: Int): WeeklyPlan {
        val base = minutes / SESSIONS_PER_WEEK
        val remainder = minutes % SESSIONS_PER_WEEK
        val sessions = (0 until SESSIONS_PER_WEEK).map { index ->
            PlannedSession(
                date = weekStart.plusDays(index.toLong()),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = base + if (index < remainder) 1 else 0,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "previous-$weekStart-$index",
            )
        }
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = minutes,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana anterior.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = weekStart,
        )
    }

    private fun load() = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 0.0,
        atl = 0.0,
        tsb = 0.0,
        acuteAverage7d = 0.0,
        chronicAverage28d = 0.0,
        acwr = 1.0,
        monotony = 1.2,
        missingDays = 16,
        coverage = LoadCoverage(
            daysInWindow = 28,
            daysWithLoad = 12,
            daysWithoutActivity = 16,
            daysWithoutData = 0,
            lastActivityAt = java.time.Instant.parse("2026-10-30T10:00:00Z"),
            sourceFresh = true,
        ),
        strain = 400.0,
    )

    private fun buildRace(): RacePlan = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(34))

    private companion object {
        const val SESSIONS_PER_WEEK = 4
    }
}
