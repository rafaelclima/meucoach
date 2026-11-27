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
 * Fatia 3 — ADR 0041: mesociclo por histórico real (TDD RED).
 *
 * Contrato: `MesoPhase` + deload derivam de `recentWeeks` reais (até 12
 * semanas), não do contador `weekNumberInBlock`. Taper e recovery prevalecem;
 * semana de prova nunca é deload; `PEAK` mantém volume estável.
 */
class MesoCycleTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `three consecutive load weeks trigger a deload from history`() {
        val recent = listOf(
            loadWeek(monday.minusWeeks(3)),
            loadWeek(monday.minusWeeks(2)),
            loadWeek(monday.minusWeeks(1)),
        )
        val plan = generator.generate(
            input(recentWeeks = recent, previousWeek = recent.last()),
        )

        assertThat(plan.ruleIds).contains("R-PLAN-04")
        assertThat(plan.rationale).contains("descarga")
    }

    @Test
    fun `a two week hole resets the deload streak`() {
        val recent = listOf(
            loadWeek(monday.minusWeeks(5)),
            loadWeek(monday.minusWeeks(4)),
            loadWeek(monday.minusWeeks(1)),
        )
        val plan = generator.generate(
            input(recentWeeks = recent, previousWeek = recent.last()),
        )

        assertThat(plan.ruleIds).doesNotContain("R-PLAN-04")
    }

    @Test
    fun `taper overlaps and prevails over a due deload`() {
        val recent = listOf(
            loadWeek(monday.minusWeeks(3)),
            loadWeek(monday.minusWeeks(2)),
            loadWeek(monday.minusWeeks(1)),
        )
        val plan = generator.generate(
            input(
                recentWeeks = recent,
                previousWeek = recent.last(),
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(10)),
            ),
        )

        assertThat(plan.ruleIds).contains("R-PLAN-05")
        assertThat(plan.ruleIds).doesNotContain("R-PLAN-04")
    }

    @Test
    fun `race week is never a deload`() {
        val recent = listOf(
            loadWeek(monday.minusWeeks(3)),
            loadWeek(monday.minusWeeks(2)),
            loadWeek(monday.minusWeeks(1)),
        )
        val plan = generator.generate(
            input(
                recentWeeks = recent,
                previousWeek = recent.last(),
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(2)),
            ),
        )

        assertThat(plan.ruleIds).doesNotContain("R-PLAN-04")
    }

    @Test
    fun `recovery prevails over a due deload`() {
        val recent = listOf(
            loadWeek(monday.minusWeeks(3)),
            loadWeek(monday.minusWeeks(2)),
            loadWeek(monday.minusWeeks(1)),
        )
        val plan = generator.generate(
            input(
                recentWeeks = recent,
                previousWeek = recent.last(),
                recovery = RecoveryWindow(
                    CompletedRace("race-1", RaceDistance.TEN_K, monday.minusDays(1)),
                ),
            ),
        )

        assertThat(plan.ruleIds).contains("R-RACE-04")
        assertThat(plan.ruleIds).doesNotContain("R-PLAN-04")
    }

    @Test
    fun `without history the phase is BASE without deload`() {
        val first = generator.generate(input())
        val second = generator.generate(input())

        assertThat(first.ruleIds).doesNotContain("R-PLAN-04")
        assertThat(first.rationale).contains("Etapa: Base")
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `G-PER-01 eight synthetic weeks with a hole deload the correct week`() {
        val w1 = loadWeek(monday.minusWeeks(8))
        val w2 = loadWeek(monday.minusWeeks(7))
        val w3 = loadWeek(monday.minusWeeks(6))
        // Buraco: semanas -5 e -4 sem plano nem conclusão forte.
        val w6 = loadWeek(monday.minusWeeks(3))
        val w7 = loadWeek(monday.minusWeeks(2))
        val w8 = loadWeek(monday.minusWeeks(1))
        val history = listOf(w1, w2, w3, w6, w7, w8)

        val target = generator.generate(
            input(recentWeeks = history, previousWeek = w8),
        )
        // 3 cargas seguidas (w6–w8) → w9 é descarga.
        assertThat(target.ruleIds).contains("R-PLAN-04")

        val midHistory = listOf(w1, w2, w3, w6, w7)
        val mid = generator.generate(
            input(
                recentWeeks = midHistory,
                previousWeek = w7,
                weekStart = monday.minusWeeks(1),
                today = monday.minusWeeks(1),
            ),
        )
        // w8 tinha só 2 cargas seguidas (w6–w7) → sem descarga.
        assertThat(mid.ruleIds).doesNotContain("R-PLAN-04")
    }

    @Test
    fun `PEAK keeps volume stable at most base times 1_06`() {
        val base = loadWeek(monday.minusWeeks(1), minutes = 120)
        val plan = generator.generate(
            input(
                recentWeeks = listOf(base),
                previousWeek = base,
                // A 20 dias: semana PEAK pela tabela macro (bloco antes do taper).
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(20)),
            ),
        )

        assertThat(plan.rationale).contains("Etapa: Pico")
        assertThat(plan.weeklyMinutes).isLessThanOrEqualTo(128)
        assertThat(plan.rationale).doesNotContain("+6%")
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

    @Test
    fun `an unplanned strong run is a load week`() {
        val reference = reference(monday.minusWeeks(1), strongSessions = 1)

        assertThat(MesoCycle.isLoadWeek(reference)).isTrue()
    }

    @Test
    fun `a plan created the day before the week is a load week`() {
        val reference = reference(monday.minusWeeks(1), createdOn = monday.minusWeeks(1).minusDays(1))

        assertThat(MesoCycle.isLoadWeek(reference)).isTrue()
    }

    @Test
    fun `a planless or special week is not a load week`() {
        assertThat(MesoCycle.isLoadWeek(reference(monday, createdOn = null))).isFalse()
        assertThat(MesoCycle.isLoadWeek(reference(monday, specialWeek = true))).isFalse()
        assertThat(MesoCycle.isLoadWeek(reference(monday, strongSessions = 0))).isFalse()
        assertThat(MesoCycle.isLoadWeek(reference(monday, createdOn = monday.plusDays(2)))).isFalse()
    }

    @Test
    fun `three weeks with only extra runs still trigger a deload`() {
        val recent = listOf(
            loadWeek(monday.minusWeeks(3)),
            loadWeek(monday.minusWeeks(2)),
            loadWeek(monday.minusWeeks(1)),
        )
        val references = (1L..3L).map { weeksAgo ->
            reference(monday.minusWeeks(weeksAgo), strongSessions = 1, strongMinutes = 35)
        }
        val plan = generator.generate(
            input(
                recentWeeks = recent,
                previousWeek = recent.last(),
                volumeReferences = references,
            ),
        )

        assertThat(plan.ruleIds).contains("R-PLAN-04")
        assertThat(plan.rationale).contains("descarga")
    }

    private fun input(
        recentWeeks: List<WeeklyPlan> = emptyList(),
        previousWeek: WeeklyPlan? = null,
        weekStart: LocalDate = monday,
        today: LocalDate = weekStart,
        race: RacePlan? = null,
        recovery: RecoveryWindow? = null,
        volumeReferences: List<VolumeReference>? = null,
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
        load = LoadSnapshot(
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
            strain = 400.0,
        ),
        weekStart = weekStart,
        today = today,
        previousWeek = previousWeek,
        recentWeeks = recentWeeks,
        race = race,
        recovery = recovery,
        volumeReferences = volumeReferences,
    )

    private fun reference(
        weekStart: LocalDate,
        strongSessions: Int = 1,
        strongMinutes: Int = if (strongSessions > 0) 35 else 0,
        createdOn: LocalDate? = weekStart,
        specialWeek: Boolean = false,
    ) = VolumeReference(
        weekStart = weekStart,
        plannedMinutes = 160,
        executedMinutes = strongMinutes,
        strongMinutes = strongMinutes,
        strongSessions = strongSessions,
        plannedSessions = 4,
        createdOn = createdOn,
        specialWeek = specialWeek,
    )
}
