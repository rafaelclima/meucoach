package com.meucoach.core.domain.prescription

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
 * Fatia 3 — ADR 0043: fiação macro no gerador (TDD RED).
 *
 * Contrato: com prova dentro do horizonte, a fase da semana vem do
 * [MacroPlanResolver] (a tabela aprovada prevalece sobre o ramo de prova do
 * [MesoCycle]), o rationale declara `Bloco X de Y` + contagem regressiva e o
 * plano cita `R-PER-01`. Sem prova, além do horizonte ou passada, o mesociclo
 * segue como antes, sem moldura de bloco. Proteções de carga por dia
 * (taper, cutoff, recovery, deload por streak) não mudam.
 */
class MacroWiringTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 9)

    @Test
    fun `macro phase wins over meso history in race prep`() {
        val history = listOf(
            loadWeek(monday.minusWeeks(3)),
            loadWeek(monday.minusWeeks(2)),
            loadWeek(monday.minusWeeks(1)),
        )
        val race = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(55))

        val plan = generator.generate(
            input(recentWeeks = history, previousWeek = history.last(), race = race),
        )

        assertThat(plan.rationale).contains("Fase BASE", "Bloco 1 de 4", "Faltam 7 semanas para a prova")
        assertThat(plan.ruleIds).contains("R-PER-01")
        assertThat(plan.evidenceIds).contains("EV-ISSURIN-2010")
    }

    @Test
    fun `peak week declares the block and keeps volume stable`() {
        val base = loadWeek(monday.minusWeeks(1), minutes = 120)
        val race = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(20))

        val plan = generator.generate(
            input(recentWeeks = listOf(base), previousWeek = base, race = race),
        )

        assertThat(plan.rationale).contains("Fase PEAK", "Bloco 1 de 2", "Faltam 2 semanas para a prova")
        assertThat(plan.ruleIds).contains("R-PER-01")
        assertThat(plan.ruleIds).doesNotContain("R-PLAN-04")
        assertThat(plan.weeklyMinutes).isLessThanOrEqualTo(128)
    }

    @Test
    fun `no race keeps the meso phase without block framing`() {
        val history = listOf(
            loadWeek(monday.minusWeeks(3)),
            loadWeek(monday.minusWeeks(2)),
            loadWeek(monday.minusWeeks(1)),
        )

        val plan = generator.generate(input(recentWeeks = history, previousWeek = history.last()))

        assertThat(plan.rationale).contains("Fase BASE")
        assertThat(plan.rationale).doesNotContain("Bloco")
        assertThat(plan.ruleIds).doesNotContain("R-PER-01")
    }

    @Test
    fun `race beyond the horizon stays on meso without framing`() {
        val history = listOf(
            loadWeek(monday.minusWeeks(3)),
            loadWeek(monday.minusWeeks(2)),
            loadWeek(monday.minusWeeks(1)),
        )
        // FR-411: o horizonte de 5K agora é 12 semanas; uma prova a 90 dias
        // (12,8 semanas) continua além dele.
        val race = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(90))

        val plan = generator.generate(
            input(recentWeeks = history, previousWeek = history.last(), race = race),
        )

        assertThat(plan.rationale).doesNotContain("Bloco")
        assertThat(plan.ruleIds).doesNotContain("R-PER-01")
        assertThat(plan.ruleIds).contains("R-PLAN-04")
    }

    @Test
    fun `race week stays taper with block framing`() {
        val race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(2))

        val plan = generator.generate(input(race = race))

        assertThat(plan.rationale).contains("Fase TAPER", "Bloco 1 de 1")
        assertThat(plan.ruleIds).contains("R-PER-01")
    }

    private fun loadWeek(weekStart: LocalDate, minutes: Int = 120): WeeklyPlan {
        val sessions = (0 until 4).map { index ->
            PlannedSession(
                date = weekStart.plusDays(index.toLong()),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = minutes / 4,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "activity-$weekStart-$index",
            )
        }
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = minutes,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana de carga.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = weekStart,
        )
    }

    private fun input(
        recentWeeks: List<WeeklyPlan> = emptyList(),
        previousWeek: WeeklyPlan? = null,
        weekStart: LocalDate = monday,
        today: LocalDate = weekStart,
        race: RacePlan? = null,
    ) = WeeklyPlanInput(
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
        weekStart = weekStart,
        today = today,
        previousWeek = previousWeek,
        recentWeeks = recentWeeks,
        race = race,
    )
}
