package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
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
        assertThat(plan.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `keeps low confidence when recovery data is partial`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40),
                ),
                readiness = readiness(
                    ReadinessLevel.GREEN,
                    confidence = DataConfidence.LOW,
                ),
            ),
        )

        assertThat(plan.confidence).isEqualTo(DataConfidence.LOW)
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
        assertThat(plan.rationale).contains("não está alta")
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
        assertThat(plan.rationale).contains("sua carga subiu rápido")
    }

    @Test
    fun `blocks quality when load data is stale`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0, sourceFresh = false),
            ),
        )

        assertThat(plan.qualityCount).isZero()
        assertThat(plan.rationale).contains("dados de carga estão desatualizados")
        assertThat(plan.ruleIds).contains("R-DATA-01")
        assertThat(plan.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `blocks quality when coverage is below eight days`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = null, daysWithLoad = 7),
            ),
        )

        assertThat(plan.qualityCount).isZero()
        assertThat(plan.rationale).contains("faltam dados de carga", "8 dias")
        assertThat(plan.ruleIds).contains("R-DATA-01")
        assertThat(plan.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `blocks quality when ACWR is below the safe range`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 0.79),
            ),
        )

        assertThat(plan.qualityCount).isZero()
        assertThat(plan.rationale).contains("carga recente está baixa")
        assertThat(plan.ruleIds).contains("R-LOAD-03")
    }

    @Test
    fun `accepts quality at the exact ACWR bounds`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val lower = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 0.8),
            ),
        )
        val upper = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.3),
            ),
        )
        val above = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.31),
            ),
        )

        assertThat(lower.qualityCount).isEqualTo(1)
        assertThat(upper.qualityCount).isEqualTo(1)
        assertThat(above.qualityCount).isZero()
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
        assertThat(plan.rationale).contains("não está alta")
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
        assertThat(taper.rationale).contains("Semana de prova")
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

        assertThat(plan.rationale).doesNotContain("Semana de prova")
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

    @Test
    fun `adds a second quality after a fully completed week`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        )
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previousWeek(completed = 4, total = 4),
            ),
        )

        assertThat(plan.qualityCount).isEqualTo(2)
        assertThat(plan.rationale).contains("concluiu pelo menos 80%")
        val qualityDates = plan.sessions.filter { it.type == WorkoutType.QUALITY }.map { it.date.dayOfWeek }
        assertThat(qualityDates).doesNotContain(DayOfWeek.SUNDAY)
        assertThat(qualityDates.zipWithNext().none { (a, b) -> adjacent(a, b) }).isTrue()
    }

    @Test
    fun `keeps one quality when completion is below eighty percent`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        )
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previousWeek(completed = 2, total = 4),
            ),
        )

        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.rationale).contains("Sem progressão")
    }

    @Test
    fun `keeps one quality without a previous week or with high ACWR`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        )
        val withoutHistory = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
            ),
        )
        val highAcwr = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.6),
                previousWeek = previousWeek(completed = 4, total = 4),
            ),
        )

        assertThat(withoutHistory.qualityCount).isEqualTo(1)
        assertThat(highAcwr.qualityCount).isZero()
    }

    @Test
    fun `treadmill access adds a heat-proof quality option with speed`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability, treadmillAccess = true),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(quality.rationale).contains("esteira", "5–10% de inclinação", "9,7–10,0 km/h a 1%")
        assertThat(quality.ruleIds).contains("R-PLAN-08")
        assertThat(quality.evidenceIds).contains("EV-ELY-2007")
    }

    @Test
    fun `treadmill protocol works without a calibrated threshold pace`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability, treadmillAccess = true)
                    .copy(thresholdPace = null),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(quality.rationale).contains("esforço 8 de 10")
        assertThat(quality.rationale).doesNotContain("km/h")
    }

    @Test
    fun `without treadmill access the quality session stays outdoor only`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(quality.rationale).doesNotContain("esteira")
        assertThat(quality.ruleIds).doesNotContain("R-PLAN-08")
    }

    @Test
    fun `deloads volume every fourth week in the block`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val baseline = generator.generate(
            input(
                profile = profile(Experience.BEGINNER, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                weekNumberInBlock = 3,
            ),
        )
        val deload = generator.generate(
            input(
                profile = profile(Experience.BEGINNER, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                weekNumberInBlock = 4,
            ),
        )

        assertThat(baseline.weeklyMinutes).isEqualTo(114)
        assertThat(deload.weeklyMinutes).isEqualTo(86)
        assertThat(deload.rationale).contains("Semana de descarga (4 de 4 no bloco)", "−25%")
        assertThat(deload.ruleIds).contains("R-PLAN-04")
        assertThat(deload.evidenceIds).contains("EV-ISSURIN-2010")
        assertThat(baseline.ruleIds).doesNotContain("R-PLAN-04")
    }

    @Test
    fun `deload keeps the quality type and reduces its dose`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                weekNumberInBlock = 4,
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(quality.durationMin).isEqualTo(30)
        assertThat(quality.title).isEqualTo("Tempo")
        assertThat(plan.ruleIds).contains("R-PLAN-04")
    }

    @Test
    fun `taper takes precedence over deload`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val plan = generator.generate(
            input(
                profile = profile(Experience.BEGINNER, availability).copy(
                    goal = com.meucoach.core.model.TrainingGoal(
                        type = com.meucoach.core.model.GoalType.FIVE_K,
                        targetDate = monday.plusDays(10),
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                weekNumberInBlock = 4,
            ),
        )

        assertThat(plan.rationale).contains("Semana de prova")
        assertThat(plan.rationale).doesNotContain("Semana de descarga")
        assertThat(plan.ruleIds).contains("R-PLAN-05")
        assertThat(plan.ruleIds).doesNotContain("R-PLAN-04")
    }

    @Test
    fun `a plan created midweek starts today and records its creation date`() {
        val thursday = monday.plusDays(3)
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.THURSDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                weekStart = monday,
                today = thursday,
            ),
        )

        assertThat(plan.weekStart).isEqualTo(monday)
        assertThat(plan.createdOn).isEqualTo(thursday)
        assertThat(plan.sessions.map { it.date })
            .containsExactly(thursday, monday.plusDays(5))
        assertThat(plan.sessions).noneMatch { it.date.isBefore(thursday) }
        assertThat(plan.rationale).contains("Plano criado hoje")
    }

    @Test
    fun `planned sessions carry zone and calibrated targets`() {
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
                previousWeek = previousWeek(completed = 4, total = 4),
            ),
        )

        val easy = plan.sessions.first { it.type == WorkoutType.EASY }
        assertThat(easy.targets.zone).isEqualTo("Z2")
        assertThat(easy.targets.pace).isNotNull
        assertThat(easy.targets.heartRate).isNotNull
        assertThat(easy.targets.distanceKm).isNotNull
        assertThat(easy.targets.rpe).isNull()

        val longRun = plan.sessions.first { it.type == WorkoutType.LONG }
        assertThat(longRun.targets.zone).isEqualTo("Z2")
        assertThat(longRun.targets.pace).isNotNull

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(quality.targets.zone).isEqualTo("Z3–Z4")
        assertThat(quality.targets.pace).isNotNull
        assertThat(quality.targets.heartRate).isNotNull
    }

    @Test
    fun `planned sessions keep RPE when the profile is not calibrated`() {
        val uncalibrated = profile(
            experience = Experience.INTERMEDIATE,
            availability = mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.SATURDAY to 90,
            ),
        ).copy(thresholdPace = null, lactateThresholdHeartRate = null)

        val plan = generator.generate(
            input(
                profile = uncalibrated,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        val easy = plan.sessions.first { it.type == WorkoutType.EASY }
        assertThat(easy.targets.pace).isNull()
        assertThat(easy.targets.heartRate).isNull()
        assertThat(easy.targets.rpe).isEqualTo(4)
        assertThat(easy.targets.distanceKm).isNull()
    }

    private fun previousWeek(completed: Int, total: Int): WeeklyPlan {
        val previousMonday = monday.minusWeeks(1)
        val sessions = (0 until total).map { index ->
            val done = index < completed
            PlannedSession(
                date = previousMonday.plusDays(index.toLong()),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = if (done) SessionStatus.DONE else SessionStatus.PLANNED,
                completionEvidence = if (done) CompletionEvidence.ACTIVITY else null,
                activityId = if (done) "previous-$index" else null,
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

    private fun adjacent(a: DayOfWeek, b: DayOfWeek): Boolean {
        val diff = kotlin.math.abs(a.value - b.value)
        return diff == 1 || diff == 6
    }

    @Test
    fun `never creates a session on or after the race date in the same week`() {
        val profile = profile(
            experience = Experience.BEGINNER,
            availability = mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.SATURDAY to 90,
            ),
        )
        val plan = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(5)),
            ),
        )

        assertThat(plan.sessions.map { it.date }).containsExactly(monday, monday.plusDays(2))
        assertThat(plan.sessions).noneMatch { it.date.isAfter(monday.plusDays(4)) }
        assertThat(plan.rationale).contains("Dias de prova não recebem sessão de treino")
        assertThat(plan.ruleIds).contains("R-PLAN-05", "R-PLAN-10")
    }

    @Test
    fun `removes a quality inside the cutoff and keeps none in the race week`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val profile = profile(Experience.INTERMEDIATE, availability)
        val outsideCutoff = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(12)),
            ),
        )
        val insideCutoff = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(10)),
            ),
        )

        assertThat(outsideCutoff.sessions.filter { it.type == WorkoutType.QUALITY }).isNotEmpty
        assertThat(insideCutoff.sessions.filter { it.type == WorkoutType.QUALITY }).isEmpty()
        assertThat(insideCutoff.ruleIds).contains("R-PLAN-10")
    }

    @Test
    fun `applies the taper factor by session date`() {
        val profile = profile(
            experience = Experience.BEGINNER,
            availability = mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.SATURDAY to 90,
            ),
        )
        val plan = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(9)),
            ),
        )

        val mondaySession = plan.sessions.first { it.date == monday }
        val wednesdaySession = plan.sessions.first { it.date == monday.plusDays(2) }
        assertThat(mondaySession.durationMin).isEqualTo(22)
        assertThat(wednesdaySession.durationMin).isEqualTo(20)
    }

    @Test
    fun `uses the 21 day marathon window`() {
        val profile = profile(
            experience = Experience.BEGINNER,
            availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 60),
        )
        val target = monday.plusDays(20)
        val marathon = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                race = RacePlan("race-1", RaceDistance.MARATHON, target),
            ),
        )
        val tenK = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                race = RacePlan("race-2", RaceDistance.TEN_K, target),
            ),
        )

        assertThat(marathon.sessions.first { it.date == monday }.durationMin).isEqualTo(22)
        assertThat(tenK.sessions.first { it.date == monday }.durationMin).isEqualTo(40)
        assertThat(marathon.ruleIds).contains("R-PLAN-05")
        assertThat(tenK.ruleIds).doesNotContain("R-PLAN-05")
    }

    @Test
    fun `skips any other event date without a taper`() {
        val profile = profile(
            experience = Experience.BEGINNER,
            availability = mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.TUESDAY to 40,
                DayOfWeek.THURSDAY to 90,
            ),
        )
        val plan = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                eventDates = setOf(monday.plusDays(2)),
            ),
        )

        assertThat(plan.sessions.map { it.date }).containsExactly(monday, monday.plusDays(1), monday.plusDays(3))
        assertThat(plan.sessions.first { it.date == monday }.durationMin).isEqualTo(40)
        assertThat(plan.ruleIds).doesNotContain("R-PLAN-05")
    }

    @Test
    fun `a persisted event stops the legacy goal from planning`() {
        val profile = profile(
            experience = Experience.BEGINNER,
            availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40),
        ).copy(
            goal = com.meucoach.core.model.TrainingGoal(
                type = com.meucoach.core.model.GoalType.FIVE_K,
                targetDate = monday.plusDays(10),
                targetDistanceM = 5_000.0,
            ),
        )
        val legacyPlan = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
            ),
        )
        val eventPlan = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                eventDates = setOf(monday.plusDays(2)),
            ),
        )

        assertThat(legacyPlan.ruleIds).contains("R-PLAN-05")
        assertThat(eventPlan.ruleIds).doesNotContain("R-PLAN-05")
        assertThat(eventPlan.sessions.first { it.date == monday }.durationMin)
            .isGreaterThan(legacyPlan.sessions.first { it.date == monday }.durationMin)
    }

    @Test
    fun `post-race recovery blocks quality and the long run`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 50,
                        DayOfWeek.WEDNESDAY to 50,
                        DayOfWeek.FRIDAY to 50,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                recovery = recovery(RaceDistance.MARATHON),
            ),
        )

        assertThat(plan.qualityCount).isZero()
        assertThat(plan.longRunMinutes).isNull()
        assertThat(plan.sessions.map { it.type }).containsOnly(WorkoutType.EASY)
        assertThat(plan.rationale).contains("Recuperação após a prova: sessões curtas")
        assertThat(plan.ruleIds).contains("R-RACE-04")
    }

    @Test
    fun `caps easy sessions by recovery phase`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 50,
                        DayOfWeek.TUESDAY to 50,
                        DayOfWeek.WEDNESDAY to 50,
                        DayOfWeek.THURSDAY to 50,
                        DayOfWeek.FRIDAY to 50,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                recovery = recovery(RaceDistance.HALF_MARATHON),
            ),
        )

        assertThat(plan.sessions.map { it.durationMin }).containsExactly(30, 30, 30, 45, 45)
        assertThat(plan.sessions.map { it.title }).containsOnly("Recuperação: fácil e curto")
        assertThat(plan.sessions.all { "R-RACE-04" in it.ruleIds }).isTrue()
    }

    @Test
    fun `skips the long run when every available day is recovery`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.TUESDAY to 40),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                recovery = recovery(RaceDistance.FIVE_K),
            ),
        )

        assertThat(plan.sessions.map { it.type }).containsExactly(WorkoutType.EASY, WorkoutType.EASY)
        assertThat(plan.longRunMinutes).isNull()
        assertThat(plan.weeklyMinutes).isEqualTo(60)
    }

    @Test
    fun `returns to normal training after the recovery window`() {
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
                recovery = recovery(RaceDistance.FIVE_K),
            ),
        )

        val mondaySession = plan.sessions.first { it.date == monday }
        assertThat(mondaySession.title).isEqualTo("Recuperação: fácil e curto")
        assertThat(mondaySession.durationMin).isEqualTo(30)
        assertThat(plan.sessions.none { it.date == monday.plusDays(1) }).isTrue()
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.longRunMinutes).isEqualTo(47)
        assertThat(plan.rationale).contains("em parte da semana")
    }

    private fun recovery(distance: RaceDistance): RecoveryWindow =
        RecoveryWindow(CompletedRace("race-1", distance, monday.minusDays(1)))

    private fun input(
        profile: AthleteProfile,
        readiness: ReadinessResult,
        load: LoadSnapshot? = null,
        weekStart: LocalDate = monday,
        today: LocalDate = weekStart,
        previousWeek: WeeklyPlan? = null,
        weekNumberInBlock: Int = 1,
        race: RacePlan? = null,
        eventDates: Set<LocalDate> = emptySet(),
        recovery: RecoveryWindow? = null,
    ) = WeeklyPlanInput(
        profile = profile,
        readiness = readiness,
        load = load,
        weekStart = weekStart,
        today = today,
        previousWeek = previousWeek,
        weekNumberInBlock = weekNumberInBlock,
        race = race,
        eventDates = eventDates,
        recovery = recovery,
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

    private fun profile(
        experience: Experience = Experience.BEGINNER,
        availability: Map<DayOfWeek, Int> = mapOf(DayOfWeek.MONDAY to 40),
        treadmillAccess: Boolean = false,
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
        treadmillAccess = treadmillAccess,
    )

    private fun load(acwr: Double?, daysWithLoad: Int = 12, sourceFresh: Boolean = true) = LoadSnapshot(
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
        missingDays = 28 - daysWithLoad,
        coverage = coverage(daysWithLoad = daysWithLoad, sourceFresh = sourceFresh),
    )

    private fun coverage(daysWithLoad: Int, sourceFresh: Boolean = true): LoadCoverage {
        val daysInWindow = 28
        val daysWithoutActivity = if (sourceFresh) daysInWindow - daysWithLoad else 0
        val daysWithoutData = if (sourceFresh) 0 else daysInWindow - daysWithLoad
        return LoadCoverage(
            daysInWindow = daysInWindow,
            daysWithLoad = daysWithLoad,
            daysWithoutActivity = daysWithoutActivity,
            daysWithoutData = daysWithoutData,
            lastActivityAt = if (daysWithLoad > 0) java.time.Instant.parse("2026-10-30T10:00:00Z") else null,
            sourceFresh = sourceFresh,
        )
    }
}
