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
    fun `generated plan carries the engine version`() {
        val plan = generator.generate(
            input(
                profile = profile(experience = Experience.INTERMEDIATE),
                readiness = readiness(ReadinessLevel.GREEN),
            ),
        )

        assertThat(plan.engineVersion).isEqualTo(WeeklyPlanGenerator.ENGINE_VERSION)
    }

    @Test
    fun `weekly engine version is weekly-plan-v9`() {
        // ADR 0066: the taper baseline and copy changed the weekly output.
        // Older rows are upgraded by PlanUpgrade on opening (ADR 0048).
        assertThat(WeeklyPlanGenerator.ENGINE_VERSION).isEqualTo("weekly-plan-v9")
    }

    @Test
    fun `dataIncomplete adds an explicit note with R-DATA-01`() {
        val plan = generator.generate(
            input(
                profile = profile(experience = Experience.INTERMEDIATE),
                readiness = readiness(ReadinessLevel.GREEN),
                dataIncomplete = true,
            ),
        )

        assertThat(plan.rationale).contains("sincronização")
        assertThat(plan.rationale).contains("reavaliado")
        assertThat(plan.ruleIds).contains("R-DATA-01")
        assertThat(plan.evidenceIds).contains("EV-IMPELLIZZERI-2020")
    }

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
        // Segunda é strides (EASY, 25 min): 25 + 40 + longão de 45 (piso do ADR 0045).
        assertThat(plan.sessions.first { it.date == monday }.title).isEqualTo("Acelerações curtas")
        assertThat(plan.longRunMinutes).isEqualTo(45)
        assertThat(plan.weeklyMinutes).isEqualTo(110)
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
    fun `skips the long run when one was already done this week`() {
        val availability = mapOf(
            DayOfWeek.TUESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 40,
        )
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = availability,
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ).copy(longAlreadyDone = true),
        )

        assertThat(plan.sessions.none { it.type == WorkoutType.LONG }).isTrue()
        assertThat(plan.longRunMinutes).isNull()
        assertThat(plan.sessions.map { it.date.dayOfWeek })
            .containsExactly(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)
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
                race = buildRace(),
            ),
        )

        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.sessions.first { it.type == WorkoutType.QUALITY }.date)
            .isEqualTo(monday)
        // ADR 0045: o share do longão segue os outros reais — com o strides de
        // quarta (25 no lugar de 40), o alvo cai para 45 (C4-18).
        assertThat(plan.longRunMinutes).isEqualTo(45)
        assertThat(plan.sessions.none { it.type == WorkoutType.QUALITY && it.date.dayOfWeek == DayOfWeek.FRIDAY })
            .isTrue()
    }

    @Test
    fun `keeps the structural opportunity on amber readiness for the day to decide`() {
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
                load = load(acwr = 1.0),
                race = buildRace(),
            ),
        )

        // ADR 0044 §2.1: the plan is structural; Monday's amber readiness is the
        // day gate's input (QualityGate, C4-04), not a generation veto.
        val quality = plan.sessions.single { it.type == WorkoutType.QUALITY }
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(quality.planDecision?.reason).isEqualTo(PlanDecisionReason.QUALITY_OPPORTUNITY)
        assertThat(plan.rationale).contains("o dia confirma")
    }

    @Test
    fun `reliable high ACWR keeps the opportunity for the day to decide`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.6),
                race = buildRace(),
            ),
        )

        // R-LOAD-03 v3: 1.6 blocks progression and is evaluated by the day; the
        // structural opportunity remains so the day can keep or downgrade it.
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.sessions.single { it.type == WorkoutType.QUALITY }.planDecision?.reason)
            .isEqualTo(PlanDecisionReason.QUALITY_OPPORTUNITY)
        assertThat(plan.ruleIds).contains("R-LOAD-03")
    }

    @Test
    fun `unreliable high ACWR does not block weekly quality`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.6, daysWithoutData = 2),
                race = buildRace(),
            ),
        )

        // ADR 0046: without the full 28-day window the ratio may not veto.
        assertThat(plan.qualityCount).isEqualTo(1)
    }

    @Test
    fun `high monotony keeps the opportunity for the day to decide`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0, monotony = 2.5, strain = 900.0),
                race = buildRace(),
            ),
        )

        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.sessions.single { it.type == WorkoutType.QUALITY }.planDecision?.reason)
            .isEqualTo(PlanDecisionReason.QUALITY_OPPORTUNITY)
        assertThat(plan.ruleIds).contains("R-LOAD-03")
    }

    @Test
    fun `strain spike keeps the opportunity for the day to decide`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                // baseline semanal = 50 * 7 = 350 em série real; aqui o snapshot
                // injeta strain alto com crônico positivo via helper abaixo.
                load = load(acwr = 1.0, monotony = 1.5, strain = 900.0).copy(chronicAverage28d = 50.0),
                race = buildRace(),
            ),
        )

        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.ruleIds).contains("R-LOAD-03")
    }

    @Test
    fun `stale load keeps the structural opportunity and lowers confidence`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0, sourceFresh = false),
                race = buildRace(),
            ),
        )

        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.rationale).contains("o dia confirma")
        assertThat(plan.ruleIds).contains("R-DATA-01")
        assertThat(plan.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `insufficient load coverage keeps the structural opportunity`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = null, daysWithLoad = 7),
                race = buildRace(),
            ),
        )

        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.ruleIds).contains("R-DATA-01")
        assertThat(plan.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `low ACWR alone does not block weekly quality when monotony and strain are ok`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 0.79),
                race = buildRace(),
            ),
        )

        // R-LOAD-03 v2: ACWR baixo sozinho apenas mantém volume, não veta qualidade.
        assertThat(plan.qualityCount).isEqualTo(1)
    }

    @Test
    fun `accepts quality across the old ACWR bounds when monotony and strain are ok`() {
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
                race = buildRace(),
            ),
        )
        val upper = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.3),
                race = buildRace(),
            ),
        )
        val above = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.31),
                race = buildRace(),
            ),
        )

        assertThat(lower.qualityCount).isEqualTo(1)
        assertThat(upper.qualityCount).isEqualTo(1)
        assertThat(above.qualityCount).isEqualTo(1)
    }

    @Test
    fun `illness keeps the structural opportunity for the day to stop`() {
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
                load = load(acwr = 1.0),
                race = buildRace(),
            ),
        )

        // Safety belongs to the day: the plan may hold the opportunity, but
        // DailyPrescriptionEngine returns rest when illness is present.
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.rationale).contains("o dia confirma")
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
    fun `says a race goal without a date has no volume reduction`() {
        val raceProfile = profile(
            experience = Experience.BEGINNER,
            availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
        ).copy(
            goal = com.meucoach.core.model.TrainingGoal(
                type = com.meucoach.core.model.GoalType.TEN_K,
                targetDate = null,
            ),
        )

        val plan = generator.generate(input(profile = raceProfile, readiness = readiness(ReadinessLevel.GREEN)))

        assertThat(plan.rationale).contains("Sua meta é uma prova, mas está sem data")
        assertThat(plan.ruleIds).contains("R-PLAN-11")
        assertThat(plan.ruleIds).doesNotContain("R-PLAN-05")
    }

    @Test
    fun `does not claim a missing date when general fitness is the goal`() {
        val general = profile(
            experience = Experience.BEGINNER,
            availability = mapOf(DayOfWeek.MONDAY to 40),
        ).copy(
            goal = com.meucoach.core.model.TrainingGoal(type = com.meucoach.core.model.GoalType.GENERAL_FITNESS),
        )

        val plan = generator.generate(input(profile = general, readiness = readiness(ReadinessLevel.GREEN)))

        assertThat(plan.ruleIds).doesNotContain("R-PLAN-11")
        assertThat(plan.rationale).doesNotContain("sem data")
    }

    @Test
    fun `does not claim a missing date when the goal already has one`() {
        val dated = profile(
            experience = Experience.BEGINNER,
            availability = mapOf(DayOfWeek.MONDAY to 40),
        ).copy(
            goal = com.meucoach.core.model.TrainingGoal(
                type = com.meucoach.core.model.GoalType.TEN_K,
                targetDate = monday.plusDays(40),
            ),
        )

        val plan = generator.generate(input(profile = dated, readiness = readiness(ReadinessLevel.GREEN)))

        assertThat(plan.ruleIds).doesNotContain("R-PLAN-11")
    }

    @Test
    fun `reduces volume during taper when the three-week average exists`() {
        val baseProfile = profile(
            experience = Experience.BEGINNER,
            availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
        )
        val references = listOf(
            volumeReference(monday.minusWeeks(4), 100),
            volumeReference(monday.minusWeeks(3), 120),
            volumeReference(monday.minusWeeks(2), 140),
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
                volumeReferences = references,
            ),
        )

        assertThat(taper.weeklyMinutes).isLessThan(baseline.weeklyMinutes)
        assertThat(taper.rationale).contains("Semana de prova", "média das 3 semanas", "120 min")
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

        // A semana da prova agora tem EASY curto após a corrida (ADR 0043 §3.3);
        // a intenção do corte profundo vale para o volume PRÉ-prova.
        val raceDate = monday.plusDays(3)
        val preRaceMinutes = raceWeek.sessions
            .filter { it.date < raceDate }
            .sumOf { it.durationMin ?: 0 }
        assertThat(preRaceMinutes).isLessThan(tenDays.weeklyMinutes)
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
                race = buildRace(),
            ),
        )
        val advanced = generator.generate(
            input(
                profile = profile(Experience.ADVANCED, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                race = buildRace(),
            ),
        )

        assertThat(intermediate.sessions.first { it.type == WorkoutType.QUALITY }.title)
            .isEqualTo("Ritmo forte")
        assertThat(advanced.sessions.first { it.type == WorkoutType.QUALITY }.title)
            .isEqualTo("Intervalado")
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
                race = buildRace(),
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
                race = buildRace(),
            ),
        )

        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.rationale).contains("Sem progressão")
    }

    @Test
    fun `keeps one quality without a previous week or with a reliable high ACWR`() {
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
                race = buildRace(),
            ),
        )
        val highAcwr = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.6),
                previousWeek = previousWeek(completed = 4, total = 4),
                race = buildRace(),
            ),
        )

        // ADR 0044/0046: a reliable 1.6 blocks progression (the second slot) but
        // not the structural opportunity; the day evaluates the kept one.
        assertThat(withoutHistory.qualityCount).isEqualTo(1)
        assertThat(highAcwr.qualityCount).isEqualTo(1)
        assertThat(highAcwr.sessions.single { it.type == WorkoutType.QUALITY }.planDecision?.reason)
            .isEqualTo(PlanDecisionReason.QUALITY_OPPORTUNITY)
    }

    @Test
    fun `blocked second quality by unreliable load states calibration not history`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        )
        val previous = previousWeek(completed = 4, total = 4)
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0, daysWithoutData = 2),
                previousWeek = previous,
                recentWeeks = listOf(previous),
                race = buildRace(),
            ),
        )

        // C4-06/FR-412: history exists (4/4), so the copy must name the real
        // blocker (calibration), never "até existir histórico".
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.rationale).contains("Sem progressão")
        assertThat(plan.rationale).contains("calibração")
        assertThat(plan.rationale).doesNotContain("até existir histórico")
    }

    @Test
    fun `blocked second quality by high ACWR states the value not history`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        )
        val previous = previousWeek(completed = 4, total = 4)
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.6),
                previousWeek = previous,
                recentWeeks = listOf(previous),
                race = buildRace(),
            ),
        )

        // C4-06/P-12: with 4/4 history, "até existir histórico" contradicts
        // the facts; the copy must carry the failing ACWR value.
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.rationale).contains("Sem progressão")
        assertThat(plan.rationale).contains("1,6")
        assertThat(plan.rationale).doesNotContain("até existir histórico")
    }

    @Test
    fun `released progression names the single safe slot without a volume claim`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.TUESDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.THURSDAY to 40,
        )
        val previous = previousWeek(completed = 4, total = 4)
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = listOf(previous),
                race = buildRace(),
            ),
        )

        // C4-59/FR-412: the progression gate is open, but Monday→Thursday has no
        // second day away from the Thursday long run. The copy must name the
        // agenda limit instead of implying a second quality.
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.rationale).contains(
            "Progressão liberada",
            "a agenda não tem outro dia seguro separado do longão",
            "a semana tem 1 oportunidade",
        )
        assertThat(plan.rationale).doesNotContain("O volume segue limitado pela disponibilidade")
    }

    @Test
    fun `released progression names a one-stimulus base catalog`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        )
        val previous = previousWeek(completed = 4, total = 4)
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = listOf(previous),
                race = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(56)),
            ),
        )

        // C4-59/FR-412: the second half of BASE has only the light fartlek.
        // The open progression gate must say that, not imply a second quality.
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.rationale).contains(
            "Progressão liberada",
            "o catálogo desta fase tem 1 estímulo",
            "a semana tem 1 oportunidade",
        )
    }

    @Test
    fun `deload progression copy does not blame availability for the volume`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        )
        val history = listOf(
            volumeWeek(monday.minusWeeks(3), List(5) { 40 }),
            volumeWeek(monday.minusWeeks(2), List(5) { 40 }),
            volumeWeek(monday.minusWeeks(1), List(5) { 40 }),
        )
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = history.last(),
                recentWeeks = history,
                race = buildRace(),
            ),
        )

        // C4-59/FR-412: a deload week has two opportunities but the volume was
        // limited by the deload, not by availability. The old generic sentence
        // contradicted the line right above it.
        assertThat(plan.qualityCount).isEqualTo(2)
        assertThat(plan.rationale).contains(
            "Semana de descarga",
            "sem aumento nesta semana (descarga)",
            "Progressão liberada",
        )
        assertThat(plan.rationale).doesNotContain("O volume segue limitado pela disponibilidade")
    }

    @Test
    fun `does not claim a high monotony when it cannot be calculated`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        )
        val previous = previousWeek(completed = 4, total = 4)
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0, monotony = null),
                previousWeek = previous,
                recentWeeks = listOf(previous),
                race = buildRace(),
            ),
        )

        // C4-59/FR-412: null monotony blocks conservatively, but it is not
        // evidence that the week crossed the 2.0 threshold.
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.rationale).contains("monotonia não é calculável")
        assertThat(plan.rationale).doesNotContain("monotonia ≥2,0")
    }

    @Test
    fun `distinguishes strong completion from a completed week with manual evidence`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        )
        val previous = previousWeek(completed = 4, total = 4, evidence = CompletionEvidence.MANUAL)
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = listOf(previous),
                race = buildRace(),
            ),
        )

        // C4-59/FR-412: the week was completed 4/4, but MANUAL is not strong
        // evidence. The copy must not say the week itself had 0% completion.
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.rationale).contains("0% de conclusão com evidência")
        assertThat(plan.rationale).doesNotContain("0% de conclusão (abaixo")
    }

    @Test
    fun `omits the progression line without a structural opportunity`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.BEGINNER,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
            ),
        )

        // C4-06: without a first opportunity there is no second slot to
        // discuss; the "Sem treino forte" reason already explains the week.
        assertThat(plan.qualityCount).isZero()
        assertThat(plan.rationale).contains("iniciante")
        assertThat(plan.rationale).doesNotContain("Sem progressão")
        assertThat(plan.rationale).doesNotContain("Progressão liberada")
    }

    @Test
    fun `cites R-PER-03 when the week has a quality opportunity`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                race = buildRace(),
            ),
        )

        // C4-06/FR-412: the opportunity copy ("o dia confirma") must be backed
        // by its rule id, and every emitted id must be catalogued.
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.rationale).contains("o dia confirma")
        assertThat(plan.ruleIds).contains("R-PER-03")
        assertThat(ExplanationCatalog.hasRule("R-PER-03")).isTrue()
        (plan.ruleIds + plan.sessions.flatMap { it.ruleIds }).forEach { id ->
            assertThat(ExplanationCatalog.hasRule(id)).describedAs(id).isTrue()
        }
        (plan.evidenceIds + plan.sessions.flatMap { it.evidenceIds }).forEach { id ->
            assertThat(ExplanationCatalog.hasEvidence(id)).describedAs(id).isTrue()
        }
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
                race = buildRace(),
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(quality.rationale).contains(
            "Na esteira, se preferir",
            "inclinação de 5% a 10%",
            "9,7–10,0 km/h com inclinação de 1%",
        )
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
                race = buildRace(),
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
                race = buildRace(),
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(quality.rationale).doesNotContain("esteira")
        assertThat(quality.ruleIds).doesNotContain("R-PLAN-08")
    }

    @Test
    fun `deloads after three consecutive load weeks from history`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val twoLoads = listOf(
            volumeWeek(monday.minusWeeks(2), List(4) { 30 }),
            volumeWeek(monday.minusWeeks(1), List(4) { 30 }),
        )
        val threeLoads = listOf(
            volumeWeek(monday.minusWeeks(3), List(4) { 30 }),
            volumeWeek(monday.minusWeeks(2), List(4) { 30 }),
            volumeWeek(monday.minusWeeks(1), List(4) { 30 }),
        )
        val baseline = generator.generate(
            input(
                profile = profile(Experience.BEGINNER, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                previousWeek = twoLoads.last(),
                recentWeeks = twoLoads,
            ),
        )
        val deload = generator.generate(
            input(
                profile = profile(Experience.BEGINNER, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                previousWeek = threeLoads.last(),
                recentWeeks = threeLoads,
            ),
        )

        // Segunda é strides (EASY, 25 min): base 110 com piso de 45 (ADR 0045);
        // descarga de 25%: segunda 20 (piso), fácil 30 e longão 34 (45 × 0,75).
        assertThat(baseline.weeklyMinutes).isEqualTo(110)
        assertThat(deload.weeklyMinutes).isEqualTo(84)
        assertThat(deload.rationale).contains("Semana de descarga após 3 semanas", "−25%", "Fase BASE")
        assertThat(deload.ruleIds).contains("R-PLAN-04")
        assertThat(deload.evidenceIds).contains("EV-ISSURIN-2010")
        assertThat(baseline.ruleIds).doesNotContain("R-PLAN-04")
    }

    @Test
    fun `falls back to the legacy counter without history for one release`() {
        @Suppress("DEPRECATION")
        val deload = generator.generate(
            WeeklyPlanInput(
                profile = profile(
                    Experience.BEGINNER,
                    mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                weekStart = monday,
                today = monday,
                weekNumberInBlock = 4,
            ),
        )

        assertThat(deload.ruleIds).contains("R-PLAN-04")
        assertThat(deload.rationale).contains("Sem histórico suficiente", "Fase BASE")
    }

    @Test
    fun `deload keeps the quality type and reduces its dose`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val history = listOf(
            volumeWeek(monday.minusWeeks(3), List(5) { 40 }),
            volumeWeek(monday.minusWeeks(2), List(5) { 40 }),
            volumeWeek(monday.minusWeeks(1), List(5) { 40 }),
        )
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                recentWeeks = history,
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(quality.durationMin).isEqualTo(30)
        assertThat(quality.title).isEqualTo("Fartlek leve")
        assertThat(quality.qualityKind).isEqualTo(QualityKind.FARTLEK)
        assertThat(quality.structure?.plannedSeconds).isLessThanOrEqualTo(30L * 60L)
        assertThat(quality.rationale)
            .contains("Semana de descarga: bloco mais curto, mesmo esforço.")
        assertThat(plan.ruleIds).contains("R-PLAN-04")
    }

    @Test
    fun `taper takes precedence over deload`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val history = listOf(
            volumeWeek(monday.minusWeeks(3), List(4) { 30 }),
            volumeWeek(monday.minusWeeks(2), List(4) { 30 }),
            volumeWeek(monday.minusWeeks(1), List(4) { 30 }),
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
                previousWeek = history.last(),
                recentWeeks = history,
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
                race = buildRace(),
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
    fun `interval quality carries I pace and Z5 instead of the generic T target`() {
        val weekStart = LocalDate.of(2026, 11, 23)
        val race = RacePlan("race-1", RaceDistance.FIVE_K, LocalDate.of(2026, 12, 20))
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.TUESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SUNDAY to 40,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                weekStart = weekStart,
                race = race,
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(quality.qualityKind).isEqualTo(QualityKind.INTERVALS)
        assertThat(quality.targets.primary).isEqualTo(TargetKind.PACE)
        assertThat(quality.targets.zone).isEqualTo("Z5")
        assertThat(quality.targets.pace?.fast?.secondsPerKm).isEqualTo(331)
        assertThat(quality.targets.pace?.slow?.secondsPerKm).isEqualTo(342)
        assertThat(quality.targets.heartRate?.minBpm).isEqualTo(180)
        assertThat(quality.targets.heartRate?.maxBpm).isNull()
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

    // --- Volume progression (ADR 0038, FR-004) -------------------------------

    @Test
    fun `subjective-only load conserves volume and never grows it (ADR 0039)`() {
        val profile = profile(
            experience = Experience.INTERMEDIATE,
            availability = mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 90,
            ),
        )
        val previous = previousWeek(completed = 4, total = 4)
        val plan = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0, subjectiveDaysInWindow = 12),
                previousWeek = previous,
                recentWeeks = listOf(previous),
            ),
        )

        // D5: sRPE conserves — the base week is kept, never accelerated.
        // D2: the fit keeps every day; the week lands exactly on the target.
        assertThat(plan.weeklyMinutes).isEqualTo(120)
        assertThat(plan.sessions).hasSize(4)
        assertThat(plan.rationale).doesNotContain("+6%")
        assertThat(plan.rationale).contains("dados de carga insuficientes", "Entregue 120 min")
        assertThat(plan.ruleIds).contains("R-LOAD-04")
        assertThat(ExplanationCatalog.hasRule("R-LOAD-04")).isTrue()
    }

    @Test
    fun `an unreliable acwr maintains volume instead of growing`() {
        val profile = profile(
            experience = Experience.INTERMEDIATE,
            availability = mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 90,
            ),
        )
        val previous = previousWeek(completed = 4, total = 4)
        val plan = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0, daysWithoutData = 2),
                previousWeek = previous,
                recentWeeks = listOf(previous),
            ),
        )

        // ADR 0046: progression needs a reliable 28-day baseline.
        // D2: the fit keeps every day; the week lands exactly on the target.
        assertThat(plan.weeklyMinutes).isEqualTo(120)
        assertThat(plan.sessions).hasSize(4)
        assertThat(plan.rationale).contains("em calibração", "Entregue 120 min")
        assertThat(plan.rationale).doesNotContain("+6%")
        assertThat(plan.ruleIds).contains("R-PLAN-01")
    }

    @Test
    fun `grows six percent over the completed week instead of the availability`() {
        val profile = profile(
            experience = Experience.INTERMEDIATE,
            availability = mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 90,
            ),
        )
        val previous = previousWeek(completed = 4, total = 4)
        val plan = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = listOf(previous),
                race = buildRace(),
            ),
        )

        assertThat(plan.weeklyMinutes).isEqualTo(127)
        assertThat(plan.rationale).contains(
            "base de 120 min",
            "+6%",
            "entregue 127 min",
            "alvo de 127 min",
        )
        assertThat(plan.ruleIds).contains("R-PLAN-01")
        assertThat(plan.evidenceIds).contains("EV-GABBETT-2016")
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        // O piso de 45 do longão ancora o corte; o restante cabe em 82 min.
        assertThat(quality.durationMin).isEqualTo(27)
        assertThat(quality.targets).isEqualTo(
            PrescriptionPolicy.targets(
                WorkoutType.QUALITY,
                profile,
                quality.durationMin,
                qualityKind = quality.qualityKind,
            ),
        )
    }

    @Test
    fun `does not jump to the new availability`() {
        val availability = DayOfWeek.entries
            .filter { it != DayOfWeek.SUNDAY }
            .associateWith { 60 }
        val previous = previousWeek(completed = 4, total = 4)
        val plan = generator.generate(
            input(
                profile = profile(Experience.INTERMEDIATE, availability),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = listOf(previous),
                race = buildRace(),
            ),
        )

        // D2: no day is dropped to fit 127; the floors anchor the week at 155,
        // still far below the new 360 min availability.
        assertThat(plan.weeklyMinutes).isEqualTo(155)
        assertThat(plan.sessions).hasSize(6)
        assertThat(plan.rationale).contains(
            "base de 120 min",
            "alvo de 127 min",
            "Semana mínima",
            "entregue 155 min",
        )
        assertThat(plan.rationale).doesNotContain("permite mais dias")
    }

    @Test
    fun `maintains volume when the previous week is below eighty percent`() {
        val previous = previousWeek(completed = 2, total = 4)
        val plan = generator.generate(
            input(
                profile = profile(
                    Experience.INTERMEDIATE,
                    mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SUNDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = listOf(previous),
                race = buildRace(),
            ),
        )

        // Base 60 (2 de 4 sessões fortes), alvo mantido em 60; os pisos somam
        // 110 e a copy declara a semana mínima, sem prometer progressão (D2).
        assertThat(plan.weeklyMinutes).isEqualTo(110)
        assertThat(plan.rationale).contains(
            "alvo mantido em 60 min",
            "abaixo de 80%",
            "Entregue 110 min",
            "Semana mínima",
        )
        assertThat(plan.rationale).doesNotContain("+6%")
    }

    @Test
    fun `keeps the availability volume without a completed week`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    Experience.INTERMEDIATE,
                    mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SUNDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
            ),
        )

        // Semana sem histórico: 40 + 25 strides + 40 + 45 = 150 (C4-18).
        assertThat(plan.weeklyMinutes).isEqualTo(150)
        assertThat(plan.rationale).contains("ainda não há semana concluída")
        assertThat(plan.ruleIds).doesNotContain("R-PLAN-01")
        assertThat(plan.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `does not grow during a deload week`() {
        val history = listOf(
            volumeWeek(monday.minusWeeks(3), List(4) { 30 }),
            volumeWeek(monday.minusWeeks(2), List(4) { 30 }),
            volumeWeek(monday.minusWeeks(1), List(4) { 30 }),
        )
        val previous = history.last()
        val plan = generator.generate(
            input(
                profile = profile(
                    Experience.INTERMEDIATE,
                    mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SUNDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = history,
            ),
        )

        assertThat(plan.weeklyMinutes).isLessThan(128)
        assertThat(plan.rationale).contains("sem aumento nesta semana (descarga)")
        assertThat(plan.rationale).doesNotContain("+6%")
    }

    @Test
    fun `does not grow during the race week or the taper`() {
        val profile = profile(
            Experience.INTERMEDIATE,
            mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 90,
            ),
        )
        val previous = previousWeek(completed = 4, total = 4)
        val raceWeek = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = listOf(previous),
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(5)),
            ),
        )
        val taper = generator.generate(
            input(
                profile = profile,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = listOf(previous),
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(10)),
            ),
        )

        assertThat(raceWeek.rationale).contains("sem aumento nesta semana (prova)")
        assertThat(taper.rationale).contains("sem aumento nesta semana (prova)")
        assertThat(raceWeek.rationale).doesNotContain("+6%")
        assertThat(taper.rationale).doesNotContain("+6%")
    }

    @Test
    fun `does not grow during post-race recovery`() {
        val previous = previousWeek(completed = 4, total = 4)
        val plan = generator.generate(
            input(
                profile = profile(
                    Experience.INTERMEDIATE,
                    mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SUNDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = listOf(previous),
                recovery = recovery(RaceDistance.TEN_K),
            ),
        )

        assertThat(plan.rationale).contains("sem aumento nesta semana (recuperação após a prova)")
        assertThat(plan.rationale).doesNotContain("+6%")
    }

    @Test
    fun `does not grow with amber readiness`() {
        val previous = previousWeek(completed = 4, total = 4)
        val plan = generator.generate(
            input(
                profile = profile(
                    Experience.INTERMEDIATE,
                    mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SUNDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.AMBER, setOf(ReadinessFlag.NO_QUALITY)),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = listOf(previous),
            ),
        )

        // D2: the fit keeps every day and lands exactly on the maintained target.
        assertThat(plan.weeklyMinutes).isEqualTo(120)
        assertThat(plan.sessions).hasSize(4)
        assertThat(plan.rationale).contains("alvo mantido em 120 min", "prontidão não está alta")
    }

    @Test
    fun `does not grow with high ACWR or stale load`() {
        val previous = previousWeek(completed = 4, total = 4)
        val highAcwr = generator.generate(
            input(
                profile = profile(
                    Experience.INTERMEDIATE,
                    mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SUNDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.6),
                previousWeek = previous,
                recentWeeks = listOf(previous),
            ),
        )
        val staleLoad = generator.generate(
            input(
                profile = profile(
                    Experience.INTERMEDIATE,
                    mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SUNDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0, sourceFresh = false),
                previousWeek = previous,
                recentWeeks = listOf(previous),
            ),
        )

        assertThat(highAcwr.rationale).contains("carga recente fora da faixa")
        assertThat(staleLoad.rationale).contains("dados de carga insuficientes")
        // D2: no day is dropped; both weeks land exactly on the target.
        assertThat(highAcwr.weeklyMinutes).isEqualTo(120)
        assertThat(staleLoad.weeklyMinutes).isEqualTo(120)
        assertThat(highAcwr.sessions).hasSize(4)
        assertThat(staleLoad.sessions).hasSize(4)
    }

    @Test
    fun `caps the volume at the availability ceiling`() {
        val previous = previousWeek(completed = 4, total = 4)
        val plan = generator.generate(
            input(
                profile = profile(
                    Experience.INTERMEDIATE,
                    mapOf(
                        DayOfWeek.MONDAY to 30,
                        DayOfWeek.WEDNESDAY to 30,
                        DayOfWeek.SATURDAY to 40,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = listOf(previous),
            ),
        )

        // O longão de 40 é o mais longo que a agenda permite (piso 45 não cabe):
        // 25 strides + 30 fácil + 40 = 95, o teto real da disponibilidade (C4-18).
        assertThat(plan.weeklyMinutes).isEqualTo(95)
        assertThat(plan.rationale).contains("limitado pela sua disponibilidade em 95 min")
        assertThat(plan.ruleIds).contains("R-PLAN-01")
    }

    @Test
    fun `an older completed week sets a bounded base`() {
        val previous = previousWeek(completed = 2, total = 4)
        val older = volumeWeek(monday.minusWeeks(2), List(5) { 40 })
        val plan = generator.generate(
            input(
                profile = profile(
                    Experience.INTERMEDIATE,
                    mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SUNDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                previousWeek = previous,
                recentWeeks = listOf(previous, older),
            ),
        )

        assertThat(plan.rationale).contains("base de 200 min")
        // C4-18: o teto agora é a disponibilidade com o longão de 45 e o
        // strides de 25 no lugar de um fácil 40: 40 + 25 + 40 + 45 = 150 min.
        assertThat(plan.weeklyMinutes).isEqualTo(150)
        assertThat(plan.rationale).contains("limitado pela sua disponibilidade em 150 min")
    }

    private fun volumeReference(weekStart: LocalDate, strongMinutes: Int) = VolumeReference(
        weekStart = weekStart,
        plannedMinutes = strongMinutes,
        executedMinutes = strongMinutes,
        strongMinutes = strongMinutes,
        strongSessions = 4,
        plannedSessions = 4,
        createdOn = weekStart,
        specialWeek = false,
    )

    private fun volumeWeek(weekStart: LocalDate, durations: List<Int>): WeeklyPlan {
        val sessions = durations.mapIndexed { index, minutes ->
            PlannedSession(
                date = weekStart.plusDays(index.toLong()),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = minutes,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "previous-$index",
            )
        }
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = durations.sum(),
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana anterior.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )
    }

    private fun previousWeek(
        completed: Int,
        total: Int,
        evidence: CompletionEvidence = CompletionEvidence.ACTIVITY,
    ): WeeklyPlan {
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
                completionEvidence = if (done) evidence else null,
                activityId = if (done && evidence == CompletionEvidence.ACTIVITY) "previous-$index" else null,
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
    fun `keeps a micro-dose inside the cutoff instead of zero quality in the race week`() {
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
        // R-PLAN-05/10 v2: dentro do corte vale 1 micro-dose ≤25 min, nunca zero.
        val micro = insideCutoff.sessions.filter { it.type == WorkoutType.QUALITY }
        assertThat(micro).hasSize(1)
        assertThat(micro.single().title).isEqualTo("Acelerações curtas")
        assertThat(micro.single().durationMin).isEqualTo(25)
        assertThat(insideCutoff.ruleIds).contains("R-PLAN-05", "R-PLAN-10")
    }

    @Test
    fun `a full taper week without history keeps the agenda dose`() {
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
        assertThat(mondaySession.durationMin).isEqualTo(40)
        assertThat(wednesdaySession.durationMin).isEqualTo(40)
        assertThat(plan.rationale).contains("Taper sem histórico")
        assertThat(plan.rationale).doesNotContain("média das 3 semanas")
    }

    @Test
    fun `quality copy fits a 25 minute session and avoids zone codes`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 25,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                race = buildRace(),
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(quality.durationMin).isEqualTo(25)
        assertThat(quality.title).isEqualTo("Ritmo forte")
        assertThat(quality.rationale).contains("Aqueça 10 min em ritmo leve")
        assertThat(quality.rationale).contains("Corra 10 min em ritmo forte sustentado")
        assertThat(quality.rationale).contains("Termine com 5 min em ritmo leve")
        assertThat(quality.rationale).doesNotContain("20 min")
        assertThat(quality.rationale).doesNotContain("Z3–Z4")
    }

    @Test
    fun `quality copy fits a 45 minute session`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 45,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                race = buildRace(),
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(quality.durationMin).isEqualTo(45)
        assertThat(quality.rationale).contains("Corra 20 min em ritmo forte sustentado")
        assertThat(quality.rationale).contains("Complete os 10 min restantes em ritmo leve")
    }

    @Test
    fun `advanced quality scales repetitions with the session duration`() {
        fun qualityFor(minutes: Int): PlannedSession {
            val plan = generator.generate(
                input(
                    profile = profile(
                        experience = Experience.ADVANCED,
                        availability = mapOf(
                            DayOfWeek.MONDAY to minutes,
                            DayOfWeek.WEDNESDAY to 40,
                            DayOfWeek.SATURDAY to 90,
                        ),
                    ),
                    readiness = readiness(ReadinessLevel.GREEN),
                    load = load(1.0),
                    race = buildRace(),
                ),
            )
            return plan.sessions.first { it.type == WorkoutType.QUALITY }
        }

        val short = qualityFor(25)
        val long = qualityFor(45)

        assertThat(short.title).isEqualTo("Intervalado")
        assertThat(short.rationale).contains("Faça 2 × 3 min em ritmo de intervalado")
        assertThat(long.rationale).contains("Faça 6 × 3 min em ritmo de intervalado")
        assertThat(short.rationale).doesNotContain("trote")
    }

    @Test
    fun `tapered quality is shorter and explains the reduction`() {
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
                load = load(1.0),
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(12)),
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(quality.durationMin).isEqualTo(25)
        assertThat(quality.title).isEqualTo("Acelerações curtas")
        assertThat(quality.qualityKind).isEqualTo(QualityKind.STRIDES_ONLY)
        assertThat(quality.rationale).contains("micro-dose", "Faça 6 × 20 s")
        assertThat(quality.ruleIds).contains("R-PLAN-05")
        // The taper micro-dose is effort-led; the old T/Z3–Z4 target was the
        // generic quality fallback, not a strides prescription.
        assertThat(quality.targets.zone).isNull()
        assertThat(quality.targets.rpe).isEqualTo(8)
        assertThat(quality.targets.primary).isEqualTo(TargetKind.RPE)
    }

    @Test
    fun `tapered quality that cannot shrink does not claim a reduction`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 25,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(12)),
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(quality.durationMin).isEqualTo(25)
        assertThat(quality.title).isEqualTo("Acelerações curtas")
        assertThat(quality.qualityKind).isEqualTo(QualityKind.STRIDES_ONLY)
        assertThat(quality.rationale).doesNotContain("bloco mais curto")
        assertThat(quality.rationale).contains("micro-dose", "Faça 6 × 20 s")
        assertThat(quality.ruleIds).contains("R-PLAN-05")
    }

    @Test
    fun `treadmill quality copy explains the option without zone codes`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                    treadmillAccess = true,
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(1.0),
                race = buildRace(),
            ),
        )

        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        assertThat(quality.rationale).contains("Na esteira, se preferir")
        assertThat(quality.rationale).contains("inclinação de 5% a 10%")
        assertThat(quality.rationale).contains("esforço 8 de 10")
        assertThat(quality.rationale).contains("com inclinação de 1%")
        assertThat(quality.rationale).doesNotContain("Z3–Z4")
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

        // ADR 0066: without a three-week baseline the taper does not invent a
        // percentage, so the day keeps the agenda dose (40 min).
        assertThat(marathon.sessions.first { it.date == monday }.durationMin).isEqualTo(40)
        assertThat(tenK.sessions.first { it.date == monday }.durationMin).isEqualTo(40)
        assertThat(marathon.rationale).contains("Taper sem histórico")
        assertThat(tenK.rationale).doesNotContain("Taper sem histórico")
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
        // Segunda é o dia de strides do iniciante (EASY, 25 min, sem taper).
        assertThat(plan.sessions.first { it.date == monday }.durationMin).isEqualTo(25)
        assertThat(plan.sessions.first { it.date == monday }.title).isEqualTo("Acelerações curtas")
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
        assertThat(legacyPlan.rationale).contains("Taper sem histórico")
        assertThat(eventPlan.rationale).doesNotContain("Taper sem histórico")
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

        assertThat(plan.sessions.map { it.durationMin }).containsExactly(30, 30, 30, 30, 30)
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
        // 5k agora tem janela de 3 dias: prova no sábado → seg/ter/qua em
        // recuperação, qui/sex livres. Qui recebe qualidade, sábado o longão.
        val postRace = RecoveryWindow(CompletedRace("race-1", RaceDistance.FIVE_K, monday.minusDays(2)))
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.THURSDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                recovery = postRace,
                race = buildRace(),
            ),
        )

        val mondaySession = plan.sessions.first { it.date == monday }
        assertThat(mondaySession.title).isEqualTo("Recuperação: fácil e curto")
        assertThat(mondaySession.durationMin).isEqualTo(30)
        assertThat(plan.sessions.none { it.date == monday.plusDays(1) }).isTrue()
        assertThat(plan.qualityCount).isEqualTo(1)
        // ADR 0045: o sábado de 90 recebe o piso de 45; a recuperação limita
        // segunda/terça e a qualidade cai na quarta.
        assertThat(plan.longRunMinutes).isEqualTo(45)
        assertThat(plan.rationale).contains("em parte da semana")
    }

    @Test
    fun `first week after a recovery week maintains volume without progression`() {
        // Semana anterior com R-RACE-04 terminou domingo; a semana alvo começa
        // segunda (gap 1 dia) → pós-recovery: mantém, sem +6%. A base vem da
        // semana útil anterior (recovery não é referência, ADR 0038).
        val recoveryWeek = previousWeek(completed = 4, total = 4).copy(
            ruleIds = listOf("R-ZONE-01", "R-RACE-04"),
        )
        val baseWeek = previousWeek(completed = 4, total = 4).copy(
            weekStart = monday.minusWeeks(2),
            createdOn = monday.minusWeeks(2),
            sessions = previousWeek(completed = 4, total = 4).sessions.map {
                it.copy(date = it.date.minusWeeks(1))
            },
        )
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.TUESDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.THURSDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                previousWeek = recoveryWeek,
                recentWeeks = listOf(recoveryWeek, baseWeek),
            ),
        )

        assertThat(plan.ruleIds).contains("R-PLAN-01")
        assertThat(plan.rationale).contains("recuperação após a prova")
        assertThat(plan.rationale).doesNotContain("+6%")
    }

    private fun recovery(distance: RaceDistance): RecoveryWindow =
        RecoveryWindow(CompletedRace("race-1", distance, monday.minusDays(1)))

    @Suppress("DEPRECATION")
    private fun input(
        profile: AthleteProfile,
        readiness: ReadinessResult,
        load: LoadSnapshot? = null,
        weekStart: LocalDate = monday,
        today: LocalDate = weekStart,
        previousWeek: WeeklyPlan? = null,
        recentWeeks: List<WeeklyPlan> = emptyList(),
        race: RacePlan? = null,
        eventDates: Set<LocalDate> = emptySet(),
        recovery: RecoveryWindow? = null,
        dataIncomplete: Boolean = false,
        volumeReferences: List<VolumeReference>? = null,
    ) = WeeklyPlanInput(
        profile = profile,
        readiness = readiness,
        load = load,
        weekStart = weekStart,
        today = today,
        previousWeek = previousWeek,
        recentWeeks = recentWeeks,
        race = race,
        eventDates = eventDates,
        recovery = recovery,
        dataIncomplete = dataIncomplete,
        volumeReferences = volumeReferences,
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

    private fun load(
        acwr: Double?,
        daysWithLoad: Int = 12,
        sourceFresh: Boolean = true,
        subjectiveDaysInWindow: Int = 0,
        monotony: Double? = 1.2,
        strain: Double? = 400.0,
        daysWithoutData: Int = 0,
    ) = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 0.0,
        atl = 0.0,
        tsb = 0.0,
        acuteAverage7d = 0.0,
        chronicAverage28d = 0.0,
        acwr = acwr,
        monotony = monotony,
        missingDays = 28 - daysWithLoad,
        coverage = coverage(
            daysWithLoad = daysWithLoad,
            sourceFresh = sourceFresh,
            daysWithoutData = daysWithoutData,
        ),
        subjectiveDaysInWindow = subjectiveDaysInWindow,
        strain = strain,
    )

    private fun buildRace(): RacePlan = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(34))

    private fun coverage(daysWithLoad: Int, sourceFresh: Boolean = true, daysWithoutData: Int = 0): LoadCoverage {
        val daysInWindow = 28
        val withoutData = if (sourceFresh) daysWithoutData else daysInWindow - daysWithLoad
        val daysWithoutActivity = if (sourceFresh) daysInWindow - daysWithLoad - withoutData else 0
        return LoadCoverage(
            daysInWindow = daysInWindow,
            daysWithLoad = daysWithLoad,
            daysWithoutActivity = daysWithoutActivity,
            daysWithoutData = withoutData,
            lastActivityAt = if (daysWithLoad > 0) java.time.Instant.parse("2026-10-30T10:00:00Z") else null,
            sourceFresh = sourceFresh,
        )
    }
}
