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
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeeklyPlanGeneratorTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `builds a conservative beginner week`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.BEGINNER,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
            ),
        )

        assertThat(plan.weekStart).isEqualTo(monday)
        assertThat(plan.sessions).hasSize(3)
        assertThat(plan.qualityCount).isZero()
        assertThat(plan.sessions.map { it.type })
            .containsExactly(WorkoutType.EASY, WorkoutType.EASY, WorkoutType.LONG)
        assertThat(plan.longRunMinutes).isEqualTo(34)
        assertThat(plan.weeklyMinutes).isEqualTo(114)
        assertThat(plan.rationale).contains("iniciante")
        assertThat(plan.confidence).isEqualTo(DataConfidence.MEDIUM)
    }

    @Test
    fun `allows one quality session away from the long run`() {
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
                load = load(acwr = 1.0),
            ),
        )

        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.sessions.first { it.type == WorkoutType.QUALITY }.date)
            .isEqualTo(monday)
        assertThat(plan.longRunMinutes).isEqualTo(51)
        assertThat(plan.sessions.none { it.type == WorkoutType.QUALITY && it.date.dayOfWeek == DayOfWeek.FRIDAY })
            .isTrue()
    }

    @Test
    fun `blocks quality on amber readiness`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(
                    ReadinessLevel.AMBER,
                    flags = setOf(ReadinessFlag.NO_QUALITY),
                ),
            ),
        )

        assertThat(plan.qualityCount).isZero()
        assertThat(plan.rationale).contains("não está verde")
    }

    @Test
    fun `blocks quality with high ACWR`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.6),
            ),
        )

        assertThat(plan.qualityCount).isZero()
        assertThat(plan.rationale).contains("ACWR alto")
    }

    @Test
    fun `blocks quality with illness`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(
                    ReadinessLevel.GREEN,
                    flags = setOf(ReadinessFlag.ILLNESS_OR_INJURY, ReadinessFlag.NO_QUALITY),
                ),
            ),
        )

        assertThat(plan.qualityCount).isZero()
        assertThat(plan.rationale).contains("não está verde")
    }

    @Test
    fun `returns an explicit empty plan without availability`() {
        val plan = generator.generate(
            input(
                profile = profile(availability = emptyMap()),
                readiness = readiness(ReadinessLevel.GREEN),
            ),
        )

        assertThat(plan.sessions).isEmpty()
        assertThat(plan.weeklyMinutes).isZero()
        assertThat(plan.ruleIds).contains("R-DATA-01")
        assertThat(plan.rationale).contains("disponibilidade")
    }

    @Test
    fun `normalizes the week to Monday`() {
        val plan = generator.generate(
            input(
                profile = profile(availability = mapOf(DayOfWeek.WEDNESDAY to 40)),
                readiness = readiness(ReadinessLevel.GREEN),
                weekStart = LocalDate.of(2026, 11, 4),
            ),
        )

        assertThat(plan.weekStart).isEqualTo(monday)
        assertThat(plan.sessions.single().date).isEqualTo(LocalDate.of(2026, 11, 4))
    }

    @Test
    fun `keeps the long run within thirty percent of the week`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.BEGINNER,
                    availability = mapOf(
                        DayOfWeek.TUESDAY to 60,
                        DayOfWeek.THURSDAY to 60,
                        DayOfWeek.SATURDAY to 60,
                        DayOfWeek.SUNDAY to 60,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
            ),
        )

        assertThat(plan.weeklyMinutes).isPositive()
        val share = plan.longRunMinutes!!.toDouble() / plan.weeklyMinutes
        assertThat(share).isBetween(0.25, 0.31)
    }

    @Test
    fun `reduces volume during taper`() {
        val baseProfile = profile(
            experience = Experience.BEGINNER,
            availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
        )
        val baseline = generator.generate(input(profile = baseProfile, readiness = readiness(ReadinessLevel.GREEN)))
        val taper = generator.generate(
            input(
                profile = baseProfile.copy(
                    goal = com.meucoach.core.model.TrainingGoal(
                        type = com.meucoach.core.model.GoalType.FIVE_K,
                        targetDate = monday.plusDays(10),
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
            ),
        )

        assertThat(taper.weeklyMinutes).isLessThan(baseline.weeklyMinutes)
        assertThat(taper.rationale).contains("Taper")
        assertThat(taper.ruleIds).contains("R-PLAN-05")
        assertThat(taper.evidenceIds).contains("EV-BOSQUET-2007", "EV-MUJIKA-2003")
    }

    @Test
    fun `applies a deeper cut on race week`() {
        val profile = profile(
            experience = Experience.BEGINNER,
            availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
        )
        val tenDays = generator.generate(
            input(
                profile = profile.copy(
                    goal = com.meucoach.core.model.TrainingGoal(
                        type = com.meucoach.core.model.GoalType.FIVE_K,
                        targetDate = monday.plusDays(10),
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
            ),
        )
        val raceWeek = generator.generate(
            input(
                profile = profile.copy(
                    goal = com.meucoach.core.model.TrainingGoal(
                        type = com.meucoach.core.model.GoalType.FIVE_K,
                        targetDate = monday.plusDays(3),
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
            ),
        )

        assertThat(raceWeek.weeklyMinutes).isLessThan(tenDays.weeklyMinutes)
    }

    @Test
    fun `does not taper a distant race`() {
        val profile = profile(
            experience = Experience.BEGINNER,
            availability = mapOf(DayOfWeek.MONDAY to 40),
        ).copy(
            goal = com.meucoach.core.model.TrainingGoal(
                type = com.meucoach.core.model.GoalType.TEN_K,
                targetDate = monday.plusDays(40),
            ),
        )

        val plan = generator.generate(input(profile, readiness(ReadinessLevel.GREEN)))

        assertThat(plan.rationale).doesNotContain("Taper")
        assertThat(plan.ruleIds).doesNotContain("R-PLAN-05")
    }

    @Test
    fun `names quality sessions by experience`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val intermediate = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
            ),
        )
        val advanced = generator.generate(
            input(
                profile = profile(Experience.ADVANCED, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
            ),
        )

        assertThat(intermediate.sessions.first { it.type == WorkoutType.QUALITY }.title)
            .isEqualTo("Tempo")
        assertThat(advanced.sessions.first { it.type == WorkoutType.QUALITY }.title)
            .isEqualTo("Intervalos")
    }

    private fun input(
        profile: AthleteProfile,
        readiness: ReadinessResult,
        load: LoadSnapshot? = null,
        weekStart: LocalDate = monday,
    ) = WeeklyPlanInput(profile, readiness, load, weekStart)

    private fun readiness(level: ReadinessLevel, flags: Set<ReadinessFlag> = emptySet()) = ReadinessResult(
        score = when (level) {
            ReadinessLevel.GREEN -> 80
            ReadinessLevel.AMBER -> 55
            ReadinessLevel.RED -> 20
        },
        level = level,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = flags,
    )

    private fun profile(
        experience: Experience = Experience.BEGINNER,
        availability: Map<DayOfWeek, Int> = mapOf(DayOfWeek.MONDAY to 40),
    ) = AthleteProfile(
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
        monotony = null,
        missingDays = 0,
    )
}
