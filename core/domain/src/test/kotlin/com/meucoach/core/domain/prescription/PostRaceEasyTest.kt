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
 * Fatia 6 — ADR 0043 §3.3: EASY curto após a prova na mesma semana (TDD RED).
 *
 * Contrato (decisão do usuário 0): dia da prova continua sem sessão; dias após
 * a prova na mesma semana recebem só EASY curto regenerativo (teto de 30 min,
 * sem qualidade/longão); antes da prova, nada muda. Semana seguinte continua
 * recuperação (`R-RACE-04`).
 */
class PostRaceEasyTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 9)

    @Test
    fun `race midweek allows short easy sessions after it`() {
        val race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(2))

        val plan = generator.generate(input(race = race))

        val friday = plan.sessions.single { it.date == monday.plusDays(4) }
        val sunday = plan.sessions.single { it.date == monday.plusDays(6) }
        assertThat(friday.type).isEqualTo(WorkoutType.EASY)
        assertThat(sunday.type).isEqualTo(WorkoutType.EASY)
        assertThat(friday.durationMin).isLessThanOrEqualTo(30)
        assertThat(sunday.durationMin).isLessThanOrEqualTo(30)
        assertThat(plan.sessions.map { it.type }).doesNotContain(WorkoutType.QUALITY)
        assertThat(plan.rationale).contains("Após a prova")
    }

    @Test
    fun `no long run after the race`() {
        val race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(2))

        val plan = generator.generate(input(race = race))

        assertThat(plan.sessions.filter { it.type == WorkoutType.LONG }).hasSize(1)
        assertThat(plan.sessions.single { it.type == WorkoutType.LONG }.date)
            .isBefore(race.targetDate)
    }

    @Test
    fun `race sunday keeps the week unchanged when nothing follows it`() {
        val race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(6))

        val plan = generator.generate(input(race = race))

        assertThat(plan.sessions.map { it.type }).contains(WorkoutType.LONG)
        assertThat(plan.sessions.filter { it.type == WorkoutType.LONG }.single().date)
            .isBefore(race.targetDate)
        assertThat(plan.rationale).doesNotContain("Após a prova")
    }

    private fun input(race: RacePlan) = WeeklyPlanInput(
        profile = AthleteProfile(
            id = "athlete-1",
            birthDate = LocalDate.of(1988, 1, 1),
            sex = Sex.MALE,
            restingHeartRate = 58,
            maxHeartRateObserved = 187,
            lactateThresholdHeartRate = 180,
            thresholdPace = Pace.fromSecondsPerKm(360),
            experience = Experience.INTERMEDIATE,
            timezone = "America/Sao_Paulo",
            weeklyAvailability = WeeklyAvailability(
                mapOf(
                    DayOfWeek.MONDAY to 40,
                    DayOfWeek.WEDNESDAY to 40,
                    DayOfWeek.FRIDAY to 40,
                    DayOfWeek.SUNDAY to 90,
                ),
            ),
        ),
        readiness = ReadinessResult(
            score = 80,
            level = ReadinessLevel.GREEN,
            confidence = DataConfidence.HIGH,
            components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
            missing = emptySet(),
            flags = emptySet(),
        ),
        load = null,
        weekStart = monday,
        today = monday,
        previousWeek = null,
        recentWeeks = emptyList(),
        race = race,
    )
}
