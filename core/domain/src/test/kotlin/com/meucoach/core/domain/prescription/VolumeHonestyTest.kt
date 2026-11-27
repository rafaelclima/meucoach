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
 * ALG-03/D2: the delivered week must not undershoot the base when the target
 * was at least the base, and the copy must use the delivered volume and the
 * real percentage. A one-minute overshoot is trimmed, never resolved by
 * dropping a whole session.
 */
class VolumeHonestyTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `target at or above the base never delivers below the base`() {
        val scenarios = listOf(
            Scenario(
                base = 120,
                availability = mapOf(
                    DayOfWeek.MONDAY to 40,
                    DayOfWeek.WEDNESDAY to 40,
                    DayOfWeek.FRIDAY to 40,
                    DayOfWeek.SUNDAY to 90,
                ),
            ),
            Scenario(
                base = 120,
                availability = mapOf(
                    DayOfWeek.MONDAY to 60,
                    DayOfWeek.WEDNESDAY to 60,
                    DayOfWeek.SUNDAY to 150,
                ),
            ),
            Scenario(
                base = 190,
                availability = mapOf(
                    DayOfWeek.MONDAY to 60,
                    DayOfWeek.WEDNESDAY to 60,
                    DayOfWeek.FRIDAY to 60,
                    DayOfWeek.SUNDAY to 150,
                ),
            ),
            Scenario(
                base = 190,
                availability = mapOf(
                    DayOfWeek.MONDAY to 60,
                    DayOfWeek.TUESDAY to 60,
                    DayOfWeek.THURSDAY to 60,
                    DayOfWeek.FRIDAY to 60,
                    DayOfWeek.SUNDAY to 150,
                ),
            ),
            Scenario(
                base = 200,
                availability = mapOf(
                    DayOfWeek.MONDAY to 40,
                    DayOfWeek.TUESDAY to 40,
                    DayOfWeek.WEDNESDAY to 40,
                    DayOfWeek.THURSDAY to 40,
                    DayOfWeek.FRIDAY to 40,
                    DayOfWeek.SUNDAY to 90,
                ),
            ),
        )

        scenarios.forEach { scenario ->
            val plan = generate(scenario.base, scenario.availability)
            assertThat(plan.rationale).contains("você fez ${scenario.base} min")
            assertThat(plan.weeklyMinutes)
                .withFailMessage(
                    "base ${scenario.base}, delivered ${plan.weeklyMinutes}: ${plan.rationale}",
                )
                .isGreaterThanOrEqualTo(scenario.base)
        }
    }

    @Test
    fun `capped target below the base never claims growth`() {
        val plan = generate(base = 190, availability = mapOf(DayOfWeek.MONDAY to 30, DayOfWeek.SUNDAY to 90))

        assertThat(plan.rationale).contains("limitado pela sua disponibilidade")
        assertThat(Regex("""\+\d+%""").containsMatchIn(plan.rationale)).isFalse()
    }

    @Test
    fun `one minute overshoot drops no session and lands on the target`() {
        val sessions = listOf(
            session(DayOfWeek.MONDAY, WorkoutType.EASY, 60),
            session(DayOfWeek.WEDNESDAY, WorkoutType.EASY, 60),
            session(DayOfWeek.FRIDAY, WorkoutType.QUALITY, 32),
            session(DayOfWeek.SUNDAY, WorkoutType.LONG, 50),
        )

        val fitted = generator.fitToVolume(sessions, targetMinutes = 201, longFloor = 45)

        // The 202→201 rounding must not remove the strides/easy day.
        assertThat(fitted.sessions).hasSize(4)
        assertThat(fitted.sessions.map { it.date })
            .containsExactlyElementsOf(sessions.map { it.date })
        assertThat(fitted.sessions.sumOf { it.durationMin ?: 0 }).isIn(201, 202)
    }

    @Test
    fun `rounding below the target is filled back without growing past the layout`() {
        val sessions = listOf(
            session(DayOfWeek.MONDAY, WorkoutType.EASY, 40),
            session(DayOfWeek.WEDNESDAY, WorkoutType.EASY, 40),
            session(DayOfWeek.FRIDAY, WorkoutType.EASY, 40),
        )

        val fitted = generator.fitToVolume(sessions, targetMinutes = 100, longFloor = 45)

        assertThat(fitted.sessions).hasSize(3)
        assertThat(fitted.sessions.sumOf { it.durationMin ?: 0 }).isEqualTo(100)
        assertThat(fitted.sessions).allMatch { it.durationMin!! <= 40 }
    }

    @Test
    fun `availability cap and ACWR are both named in the copy`() {
        val previous = completedWeek(200)
        val plan = generator.generate(
            input(
                profile = profile(
                    availability = mapOf(
                        DayOfWeek.MONDAY to 60,
                        DayOfWeek.WEDNESDAY to 60,
                        DayOfWeek.SUNDAY to 150,
                    ),
                ),
                previousWeek = previous,
                load = load(acwr = 1.7),
            ),
        )

        assertThat(plan.rationale).contains(
            "limitado pela sua disponibilidade",
            "carga recente fora da faixa",
        )
    }

    private fun generate(base: Int, availability: Map<DayOfWeek, Int>): WeeklyPlan {
        val previous = completedWeek(base)
        return generator.generate(
            input(
                profile = profile(availability),
                previousWeek = previous,
                load = load(acwr = 1.0),
            ),
        )
    }

    private data class Scenario(val base: Int, val availability: Map<DayOfWeek, Int>)

    private fun session(day: DayOfWeek, type: WorkoutType, minutes: Int) = PlannedSession(
        date = monday.plusDays((day.value - 1).toLong()),
        type = type,
        title = if (type == WorkoutType.QUALITY) "Qualidade" else "Rodagem",
        durationMin = minutes,
        rationale = "Teste.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun input(profile: AthleteProfile, previousWeek: WeeklyPlan, load: LoadSnapshot) = WeeklyPlanInput(
        profile = profile,
        readiness = readiness(),
        load = load,
        weekStart = monday,
        today = monday,
        previousWeek = previousWeek,
        recentWeeks = listOf(previousWeek),
        race = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(34)),
    )

    private fun completedWeek(minutes: Int): WeeklyPlan {
        val sessionsPerWeek = 4
        val base = minutes / sessionsPerWeek
        val remainder = minutes % sessionsPerWeek
        val weekStart = monday.minusWeeks(1)
        val sessions = (0 until sessionsPerWeek).map { index ->
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
                activityId = "previous-$index",
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

    private fun readiness() = ReadinessResult(
        score = 80,
        level = ReadinessLevel.GREEN,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = emptySet(),
    )

    private fun profile(availability: Map<DayOfWeek, Int>) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(availability),
    )

    private fun load(acwr: Double) = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 0.0,
        atl = 0.0,
        tsb = 0.0,
        acuteAverage7d = 0.0,
        chronicAverage28d = 0.0,
        acwr = acwr,
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
        subjectiveDaysInWindow = 0,
        strain = 400.0,
    )
}
