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
 * Fatia 4 — ADR 0043: papéis da biblioteca na montagem (TDD RED).
 *
 * Contrato: títulos das sessões vêm da [SessionLibrary] (fonte única); `BASE`
 * não agenda qualidade mesmo com progressão liberada (a biblioteca não tem
 * qualidade principal na base); `R-PER-02` é citado quando a preparação molda
 * a semana. Durações continuam pela disponibilidade vigente e o agendamento de
 * strides para iniciante fica para a fatia 5 (interage com `R-PRESC-04`).
 */
class SessionRolesTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 9)

    @Test
    fun `base phase without race keeps guardrail-gated quality`() {
        val history = listOf(loadWeek(monday.minusWeeks(1)))

        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE),
                readiness = readiness(ReadinessLevel.GREEN),
                load = trustedLoad(),
                recentWeeks = history,
                previousWeek = history.last(),
            ),
        )

        // A fase não veta qualidade fora de preparação: valem os guardrails
        // (verde, carga objetiva, 3+ dias). Suprimir na BASE exige decisão
        // explícita (fatia futura).
        assertThat(plan.sessions.map { it.type }).contains(WorkoutType.QUALITY)
        assertThat(plan.ruleIds).doesNotContain("R-PER-02")
        assertThat(plan.rationale).doesNotContain("Bloco")
    }

    @Test
    fun `prep quality titles come from the session library`() {
        // 5k a 34 dias: semana atual é a 4ª da tabela (BUILD), faltam 4 semanas.
        val race = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(34))

        val plan = generator.generate(
            input(
                profile = profile(Experience.ADVANCED),
                readiness = readiness(ReadinessLevel.GREEN),
                load = trustedLoad(),
                previousWeek = loadWeek(monday.minusWeeks(1)),
                race = race,
            ),
        )

        val libraryTitle = SessionLibrary.templates(MesoPhase.BUILD, Experience.ADVANCED)
            .first { it.role == SessionRole.QUALITY_MAIN }.title
        val qualities = plan.sessions.filter { it.type == WorkoutType.QUALITY }
        assertThat(qualities).isNotEmpty()
        assertThat(qualities.map { it.title }).containsOnly(libraryTitle)
        assertThat(plan.rationale).contains("Bloco 1 de 3", "Faltam 4 semanas para a prova")
        assertThat(plan.ruleIds).contains("R-PER-02")
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

    private fun profile(experience: Experience) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = experience,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 90,
            ),
        ),
    )

    private fun readiness(level: ReadinessLevel) = ReadinessResult(
        score = 80,
        level = level,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = emptySet(),
    )

    private fun trustedLoad() = LoadSnapshot(
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

    private fun input(
        profile: AthleteProfile,
        readiness: ReadinessResult,
        load: com.meucoach.core.domain.load.LoadSnapshot? = null,
        weekStart: LocalDate = monday,
        today: LocalDate = weekStart,
        previousWeek: WeeklyPlan? = null,
        recentWeeks: List<WeeklyPlan> = emptyList(),
        race: RacePlan? = null,
    ) = WeeklyPlanInput(
        profile = profile,
        readiness = readiness,
        load = load,
        weekStart = weekStart,
        today = today,
        previousWeek = previousWeek,
        recentWeeks = recentWeeks,
        race = race,
    )
}
