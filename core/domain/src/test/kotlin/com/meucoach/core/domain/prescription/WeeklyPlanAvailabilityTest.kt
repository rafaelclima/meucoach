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
 * D1/ALG-01: availability is a hard ceiling on every path. A slot below the
 * executable minimum removes the day instead of inflating a session.
 */
class WeeklyPlanAvailabilityTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `E14 removes ten minute days instead of inflating them`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 10,
            DayOfWeek.WEDNESDAY to 10,
            DayOfWeek.SATURDAY to 90,
        )
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
            ),
        )

        assertThat(plan.sessions.map { it.date.dayOfWeek })
            .doesNotContain(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        assertThat(plan.rationale).contains("20 min", "o app não estica")
        assertCeiling(plan, availability)
        assertStimulusHasStructure(plan)
    }

    @Test
    fun `E18 keeps quality off a twenty minute slot`() {
        val availability = mapOf(
            DayOfWeek.TUESDAY to 20,
            DayOfWeek.THURSDAY to 40,
            DayOfWeek.SATURDAY to 40,
            DayOfWeek.SUNDAY to 90,
        )
        val history = (1..4).map { loadWeek(monday.minusWeeks(it.toLong())) }
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                previousWeek = history.last(),
                recentWeeks = history,
            ),
        )

        val tuesday = plan.sessions.firstOrNull { it.date.dayOfWeek == DayOfWeek.TUESDAY }
        if (tuesday != null) {
            assertThat(tuesday.type).isEqualTo(WorkoutType.EASY)
            assertThat(tuesday.durationMin).isLessThanOrEqualTo(20)
        }
        assertThat(plan.sessions.filter { it.type == WorkoutType.QUALITY }).isNotEmpty
        assertThat(plan.sessions.filter { it.type == WorkoutType.QUALITY })
            .allMatch { it.date.dayOfWeek != DayOfWeek.TUESDAY }
        assertThat(plan.rationale).contains("Etapa: Construção")
        assertCeiling(plan, availability)
        assertStimulusHasStructure(plan)
    }

    @Test
    fun `taper micro-dose ignores a ten minute slot`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 10,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(10)),
            ),
        )

        assertThat(plan.sessions.map { it.date.dayOfWeek }).doesNotContain(DayOfWeek.MONDAY)
        assertThat(plan.sessions.none { it.durationMin == 25 && it.date.dayOfWeek == DayOfWeek.MONDAY }).isTrue()
        assertCeiling(plan, availability)
        assertStimulusHasStructure(plan)
    }

    @Test
    fun `a ten minute strides day is removed instead of losing its structure`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 10,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
            ),
        )

        assertThat(plan.sessions.map { it.date.dayOfWeek }).doesNotContain(DayOfWeek.MONDAY)
        val strides = plan.sessions.single { it.title == "Acelerações curtas" }
        assertThat(strides.durationMin).isEqualTo(25)
        assertThat(strides.structure).isNotNull
        assertCeiling(plan, availability)
        assertStimulusHasStructure(plan)
    }

    @Test
    fun `every session respects the availability across levels and phases`() {
        val shortSlots = listOf(1, 10, 19, 20, 21, 25, 30, 40, 90)
        val history = (1..4).map { loadWeek(monday.minusWeeks(it.toLong())) }
        for (experience in Experience.entries) {
            for (minutes in shortSlots) {
                val availability = mapOf(
                    DayOfWeek.MONDAY to minutes,
                    DayOfWeek.WEDNESDAY to 40,
                    DayOfWeek.FRIDAY to 40,
                    DayOfWeek.SUNDAY to 90,
                )
                for (raceOffset in listOf(null, 10L, 34L)) {
                    val plan = generator.generate(
                        input(
                            profile = profile(experience, availability),
                            readiness = readiness(ReadinessLevel.GREEN),
                            previousWeek = history.last(),
                            recentWeeks = history,
                            race = raceOffset?.let {
                                RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(it))
                            },
                        ),
                    )

                    assertCeiling(plan, availability)
                    assertStimulusHasStructure(plan)
                    if (minutes < 20) {
                        assertThat(plan.sessions.map { it.date.dayOfWeek })
                            .doesNotContain(DayOfWeek.MONDAY)
                    }
                }
            }
        }
    }

    private fun assertCeiling(plan: WeeklyPlan, availability: Map<DayOfWeek, Int>) {
        plan.sessions.forEach { session ->
            val available = availability.getValue(session.date.dayOfWeek)
            val duration = requireNotNull(session.durationMin) {
                "Weekly plan sessions must carry a duration: ${session.title}"
            }
            assertThat(duration).isLessThanOrEqualTo(available)
        }
    }

    private fun assertStimulusHasStructure(plan: WeeklyPlan) {
        plan.sessions.forEach { session ->
            val stimulus = session.type == WorkoutType.QUALITY ||
                session.title == "Acelerações curtas" ||
                session.title == "Rodagem com estímulos"
            if (stimulus) {
                assertThat(session.structure).isNotNull
            }
        }
    }

    private fun loadWeek(weekStart: LocalDate): WeeklyPlan {
        val sessions = (0 until 4).map { index ->
            PlannedSession(
                date = weekStart.plusDays(index.toLong()),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
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
            weeklyMinutes = 120,
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
        profile: AthleteProfile,
        readiness: ReadinessResult,
        previousWeek: WeeklyPlan? = null,
        recentWeeks: List<WeeklyPlan> = emptyList(),
        race: RacePlan? = null,
    ) = WeeklyPlanInput(
        profile = profile,
        readiness = readiness,
        weekStart = monday,
        today = monday,
        previousWeek = previousWeek,
        recentWeeks = recentWeeks,
        race = race,
    )

    private fun readiness(level: ReadinessLevel) = ReadinessResult(
        score = when (level) {
            ReadinessLevel.GREEN -> 80
            ReadinessLevel.AMBER -> 55
            ReadinessLevel.RED -> 20
        },
        level = level,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = emptySet(),
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
}
