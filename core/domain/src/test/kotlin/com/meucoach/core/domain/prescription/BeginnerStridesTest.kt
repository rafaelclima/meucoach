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
 * Fatia 5 — ADR 0043 + C4-18 (ADR 0047 §2.2): strides como EASY para todos os
 * níveis em `BASE`/`BUILD`/`PEAK`, e intro fartlek do iniciante após 4 semanas
 * consistentes.
 *
 * Contrato: todo nível ganha uma sessão EASY de até 25 min no dia leve mais
 * curto além do longão (teto da disponibilidade intacto), fora de dias de
 * qualidade/micro-dose/recuperação; tipo EASY de propósito — sobrevive à
 * guarda `R-PRESC-04` do dia e não conta como qualidade. Sem 2+ dias fáceis
 * além do longão e fora dos dias fortes, não há strides. O iniciante com 4
 * semanas ≥80% de conclusão forte troca os strides pelo intro fartlek
 * ("Rodagem com estímulos"), ainda `EASY` e ainda sem qualidade.
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

        val strides = plan.sessions.single { it.title == "Acelerações curtas" }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(strides.durationMin).isEqualTo(25)
        assertThat(strides.date.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(strides.rationale).contains("Acelerações curtas no fim da corrida leve")
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

        val strides = plan.sessions.single { it.title == "Acelerações curtas" }
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

        assertThat(plan.sessions.map { it.title }).doesNotContain("Acelerações curtas")
        assertThat(plan.qualityCount).isZero()
    }

    @Test
    fun `beginner strides also apply in build`() {
        val history = (1..3).map { loadWeek(monday.minusWeeks(it.toLong())) } +
            loadWeek(monday.minusWeeks(4), doneCount = 1)

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

        assertThat(plan.rationale).contains("Etapa: Construção")
        val strides = plan.sessions.single { it.title == "Acelerações curtas" }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(plan.qualityCount).isZero()
    }

    @Test
    fun `beginner with four consistent weeks swaps strides for intro fartlek`() {
        val history = (1..4).map { loadWeek(monday.minusWeeks(it.toLong())) }

        val plan = generator.generate(
            input(
                availability = mapOf(
                    DayOfWeek.MONDAY to 20,
                    DayOfWeek.WEDNESDAY to 40,
                    DayOfWeek.SUNDAY to 90,
                ),
                recentWeeks = history,
                previousWeek = history.last(),
            ),
        )

        val intro = plan.sessions.single { it.title == "Rodagem com estímulos" }
        assertThat(intro.type).isEqualTo(WorkoutType.EASY)
        assertThat(intro.durationMin).isEqualTo(20)
        assertThat(intro.date.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(intro.ruleIds).contains("R-PER-02")
        assertThat(plan.sessions.map { it.title }).doesNotContain("Acelerações curtas")
        assertThat(plan.qualityCount).isZero()
    }

    @Test
    fun `beginner with three consistent weeks keeps strides`() {
        val history = (1..3).map { loadWeek(monday.minusWeeks(it.toLong())) }

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

        val strides = plan.sessions.single { it.title == "Acelerações curtas" }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(plan.sessions.map { it.title }).doesNotContain("Rodagem com estímulos")
        assertThat(plan.qualityCount).isZero()
    }

    @Test
    fun `intermediate gets an easy strides day and keeps quality`() {
        val plan = generator.generate(
            input(
                experience = Experience.INTERMEDIATE,
                availability = mapOf(
                    DayOfWeek.MONDAY to 40,
                    DayOfWeek.WEDNESDAY to 40,
                    DayOfWeek.FRIDAY to 40,
                    DayOfWeek.SATURDAY to 90,
                ),
                load = load(),
                race = race(),
            ),
        )

        val quality = plan.sessions.single { it.type == WorkoutType.QUALITY }
        assertThat(quality.date.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        val strides = plan.sessions.single { it.title == "Acelerações curtas" }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(strides.durationMin).isEqualTo(25)
        assertThat(strides.date.dayOfWeek).isEqualTo(DayOfWeek.WEDNESDAY)
        assertThat(plan.qualityCount).isEqualTo(1)
    }

    private fun loadWeek(weekStart: LocalDate, minutes: Int = 120, doneCount: Int = 4): WeeklyPlan {
        val sessions = (0 until 4).map { index ->
            val done = index < doneCount
            PlannedSession(
                date = weekStart.plusDays(index.toLong()),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = minutes / 4,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = if (done) SessionStatus.DONE else SessionStatus.PLANNED,
                completionEvidence = if (done) CompletionEvidence.ACTIVITY else null,
                activityId = if (done) "activity-$weekStart-$index" else null,
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
        load: LoadSnapshot? = null,
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
    )

    private fun load(): LoadSnapshot = LoadSnapshot(
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
        subjectiveDaysInWindow = 0,
        strain = 400.0,
    )

    private fun race(): RacePlan = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(34))
}
