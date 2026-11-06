package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Regression scenarios prepared for cycle 1 (C1-02/C1-03).
 *
 * The executable tests lock invariants that must survive the new prescription
 * pipeline. Tests annotated with [Ignore] are **expected failures**: they
 * describe behavior that is not implemented yet and must be enabled as each
 * C1 gate is delivered. They are intentionally kept in the suite so the
 * expected contract does not depend on prose alone.
 */
class Cycle1RegressionTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)
    private val thursday = monday.plusDays(3)

    @Test
    fun `plans only the configured availability days`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(plan.sessions.map { it.date.dayOfWeek })
            .containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY)
        assertThat(plan.sessions).allMatch { it.date in monday..monday.plusDays(6) }
    }

    @Test
    fun `never places quality on consecutive days or next to the long run`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SUNDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                previousWeek = previousWeek(completed = 4, total = 4),
            ),
        )

        val qualityDays = plan.sessions.filter { it.type == WorkoutType.QUALITY }.map { it.date.dayOfWeek }
        assertThat(plan.qualityCount).isBetween(1, 2)
        assertThat(qualityDays).doesNotContain(DayOfWeek.SUNDAY)
        assertThat(qualityDays.zipWithNext().none { (first, second) -> adjacent(first, second) }).isTrue()
        assertThat(
            plan.sessions.none {
                it.type == WorkoutType.QUALITY && adjacent(it.date.dayOfWeek, DayOfWeek.SUNDAY)
            },
        ).isTrue()
    }

    @Test
    fun `does not create quality when readiness is red`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.RED),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(plan.qualityCount).isZero()
        assertThat(plan.sessions.none { it.type == WorkoutType.QUALITY }).isTrue()
    }

    @Test
    fun `completion ignores future sessions and rest days`() {
        val plan = WeeklyPlan(
            weekStart = monday,
            sessions = listOf(
                session(monday, SessionStatus.DONE),
                session(monday.plusDays(1), SessionStatus.PLANNED, WorkoutType.REST),
                session(thursday, SessionStatus.PLANNED),
            ),
            weeklyMinutes = 60,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Plano.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )

        val completion = PlanCompletionCalculator().calculate(
            plans = listOf(plan),
            today = thursday.minusDays(1),
            weeks = 4,
        )

        assertThat(completion.weeks.last().planned).isEqualTo(1)
        assertThat(completion.weeks.last().done).isEqualTo(1)
    }

    @Ignore("Falha esperada — C1-02: hoje ACWR desconhecido (null) libera qualidade; o gate deve bloquear.")
    @Test
    fun `quality is blocked when acute chronic load is unknown`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = null,
            ),
        )

        assertThat(plan.qualityCount).isZero()
    }

    @Ignore("Falha esperada — C1-02: cobertura insuficiente ainda libera qualidade; LoadSnapshot ignora missingDays.")
    @Test
    fun `quality is blocked when load coverage is insufficient`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0, missingDays = 26),
            ),
        )

        assertThat(plan.qualityCount).isZero()
    }

    @Ignore("Falha esperada — C1-03/N-02: o gerador ainda normaliza para segunda e inclui dias passados.")
    @Test
    fun `plan created midweek does not include days before today`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                weekStart = thursday,
            ),
        )

        assertThat(plan.sessions.map { it.date }).allMatch { !it.isBefore(thursday) }
    }

    private fun input(
        profile: AthleteProfile,
        readiness: ReadinessResult,
        load: LoadSnapshot? = null,
        weekStart: LocalDate = monday,
        previousWeek: WeeklyPlan? = null,
        weekNumberInBlock: Int = 1,
    ) = WeeklyPlanInput(
        profile = profile,
        readiness = readiness,
        load = load,
        weekStart = weekStart,
        previousWeek = previousWeek,
        weekNumberInBlock = weekNumberInBlock,
    )

    private fun readiness(
        level: ReadinessLevel,
        flags: Set<ReadinessFlag> = emptySet(),
        confidence: DataConfidence = DataConfidence.HIGH,
    ) = ReadinessResult(
        score = when (level) {
            ReadinessLevel.GREEN -> 80
            ReadinessLevel.AMBER -> 55
            ReadinessLevel.RED -> 20
        },
        level = level,
        confidence = confidence,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = flags,
    )

    private fun profile(experience: Experience, availability: Map<DayOfWeek, Int>) = AthleteProfile(
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
    )

    private fun load(acwr: Double, missingDays: Int = 0) = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 0.0,
        atl = 0.0,
        tsb = 0.0,
        acuteAverage7d = 0.0,
        chronicAverage28d = 0.0,
        acwr = acwr,
        monotony = null,
        missingDays = missingDays,
    )

    private fun previousWeek(completed: Int, total: Int): WeeklyPlan {
        val previousMonday = monday.minusWeeks(1)
        val sessions = (0 until total).map { index ->
            session(
                date = previousMonday.plusDays(index.toLong()),
                status = if (index < completed) SessionStatus.DONE else SessionStatus.SKIPPED,
            )
        }
        return WeeklyPlan(
            weekStart = previousMonday,
            sessions = sessions,
            weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana anterior.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )
    }

    private fun session(date: LocalDate, status: SessionStatus, type: WorkoutType = WorkoutType.EASY) = PlannedSession(
        date = date,
        type = type,
        title = if (type == WorkoutType.REST) "Descanso" else "Fácil",
        durationMin = if (type == WorkoutType.REST) null else 30,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = status,
    )

    private fun adjacent(first: DayOfWeek, second: DayOfWeek): Boolean {
        val difference = kotlin.math.abs(first.value - second.value)
        return difference == 1 || difference == 6
    }
}
