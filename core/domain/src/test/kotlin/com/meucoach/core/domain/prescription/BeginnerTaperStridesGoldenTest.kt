package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
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
 * G-BEGINNER-TAPER-01 (ADR 0069): the beginner gets at most one EASY strides
 * session (<=25 min, with an executable structure) in the taper/race week and
 * never a QUALITY. Without a safe 20–25 min day the session is honestly omitted.
 */
class BeginnerTaperStridesGoldenTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `A the taper keeps one easy strides session without quality`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.TUESDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.THURSDAY to 25,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val plan = plan(raceOffsetDays = 10, availability = availability)

        val strides = plan.sessions.single { it.title == "Acelerações curtas" }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(strides.qualityKind).isNull()
        assertThat(strides.durationMin).isEqualTo(25)
        assertThat(strides.structure).isNotNull
        assertThat(strides.rationale).contains("acelerações", "sem treino forte")
        assertThat(plan.sessions.none { it.type == WorkoutType.QUALITY }).isTrue()
        assertThat(plan.qualityCount).isZero()
        assertCeiling(plan, availability)
    }

    @Test
    fun `B without a safe twenty minute day the strides are omitted with copy`() {
        val availability = (0..4).associate { DayOfWeek.of(it + 1) to 15 }
        val plan = plan(raceOffsetDays = 10, availability = availability)

        assertThat(plan.sessions.none { it.title == "Acelerações curtas" }).isTrue()
        assertThat(plan.sessions.none { it.type == WorkoutType.QUALITY }).isTrue()
        assertThat(plan.qualityCount).isZero()
        assertThat(plan.rationale).contains("sem acelerações", "nenhum treino forte foi inventado")
        assertThat(plan.sessions.all { (it.durationMin ?: 0) <= 15 }).isTrue()
        assertThat(plan.sessions.none { it.structure == null && it.type == WorkoutType.QUALITY }).isTrue()
    }

    @Test
    fun `C the race week keeps one short dose and says it is race week`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 20,
            DayOfWeek.WEDNESDAY to 40,
        )
        val plan = plan(raceOffsetDays = 4, availability = availability)

        val strides = plan.sessions.single { it.title == "Acelerações curtas" }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(strides.qualityKind).isNull()
        assertThat(strides.durationMin).isEqualTo(20)
        assertThat(strides.structure).isNotNull
        assertThat(strides.rationale).contains("semana da prova")
        assertThat(plan.sessions.none { it.type == WorkoutType.QUALITY }).isTrue()
        assertThat(plan.qualityCount).isZero()
        assertCeiling(plan, availability)
    }

    private fun plan(raceOffsetDays: Long, availability: Map<DayOfWeek, Int>): WeeklyPlan = generator.generate(
        WeeklyPlanInput(
            profile = profile(availability),
            readiness = readiness(),
            weekStart = monday,
            today = monday,
            race = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(raceOffsetDays)),
        ),
    )

    private fun assertCeiling(plan: WeeklyPlan, availability: Map<DayOfWeek, Int>) {
        plan.sessions.forEach { session ->
            assertThat(session.durationMin)
                .isLessThanOrEqualTo(availability.getValue(session.date.dayOfWeek))
        }
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
        experience = Experience.BEGINNER,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(availability),
    )
}
