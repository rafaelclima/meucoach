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
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Fatia 5 — ADR 0043: strides do iniciante como EASY (TDD RED).
 *
 * Contrato (decisão do usuário 0): iniciante em `BASE`/`BUILD`/`PEAK` ganha
 * uma sessão EASY de até 25 min titulada "Strides curtos" no dia leve mais
 * curto (teto da disponibilidade intacto); tipo EASY de propósito — sobrevive
 * à guarda `R-PRESC-04` do dia e não conta como qualidade. Sem 2+ dias fáceis
 * além do longão, não há strides. Não iniciantes seguem intactos.
 */
class BeginnerStridesTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 9)

    @Test
    fun `beginner gets an easy strides session in base`() {
        val plan = generator.generate(
            input(
                availability = mapOf(
                    DayOfWeek.MONDAY to 40,
                    DayOfWeek.WEDNESDAY to 40,
                    DayOfWeek.FRIDAY to 40,
                    DayOfWeek.SUNDAY to 90,
                ),
            ),
        )

        val strides = plan.sessions.single { it.title == "Strides curtos" }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(strides.durationMin).isEqualTo(25)
        assertThat(strides.date.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(strides.rationale).contains("Estímulo neuromuscular curto")
        assertThat(strides.ruleIds).contains("R-PER-02")
        assertThat(plan.qualityCount).isZero()
    }

    @Test
    fun `beginner strides respect a short availability day`() {
        val plan = generator.generate(
            input(
                availability = mapOf(
                    DayOfWeek.MONDAY to 20,
                    DayOfWeek.WEDNESDAY to 40,
                    DayOfWeek.SUNDAY to 90,
                ),
            ),
        )

        val strides = plan.sessions.single { it.title == "Strides curtos" }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(strides.durationMin).isEqualTo(20)
    }

    @Test
    fun `no strides day without room beyond the long run`() {
        val plan = generator.generate(
            input(
                availability = mapOf(
                    DayOfWeek.MONDAY to 40,
                    DayOfWeek.SUNDAY to 90,
                ),
            ),
        )

        assertThat(plan.sessions.map { it.title }).doesNotContain("Strides curtos")
        assertThat(plan.qualityCount).isZero()
    }

    @Test
    fun `beginner strides also apply in build`() {
        val history = (1..4).map { loadWeek(monday.minusWeeks(it.toLong())) }

        val plan = generator.generate(
            input(
                availability = mapOf(
                    DayOfWeek.MONDAY to 40,
                    DayOfWeek.WEDNESDAY to 40,
                    DayOfWeek.FRIDAY to 40,
                    DayOfWeek.SUNDAY to 90,
                ),
                recentWeeks = history,
                previousWeek = history.last(),
            ),
        )

        assertThat(plan.rationale).contains("Fase BUILD")
        val strides = plan.sessions.single { it.title == "Strides curtos" }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(plan.qualityCount).isZero()
    }

    @Test
    fun `non beginners keep their week without easy strides`() {
        val plan = generator.generate(
            input(
                experience = Experience.INTERMEDIATE,
                availability = mapOf(
                    DayOfWeek.MONDAY to 40,
                    DayOfWeek.WEDNESDAY to 40,
                    DayOfWeek.FRIDAY to 40,
                    DayOfWeek.SUNDAY to 90,
                ),
            ),
        )

        assertThat(plan.sessions.map { it.title }).doesNotContain("Strides curtos")
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
        experience: Experience = Experience.BEGINNER,
        availability: Map<DayOfWeek, Int>,
        recentWeeks: List<WeeklyPlan> = emptyList(),
        previousWeek: WeeklyPlan? = null,
    ) = WeeklyPlanInput(
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
        load = null,
        weekStart = monday,
        today = monday,
        previousWeek = previousWeek,
        recentWeeks = recentWeeks,
        race = null,
    )
}
