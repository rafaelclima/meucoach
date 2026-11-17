package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
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

/**
 * C4-19 — estrutura nas sessões fáceis de strides/intro (ADR 0047 §2.1, FR-403).
 *
 * O gerador deve anexar a estrutura do catálogo à sessão EASY de strides ou
 * intro fartlek, ajustada à duração real, sem prometer mais tempo do que cabe.
 */
class EasyStructureTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 9)

    @Test
    fun `strides session carries a fitted structure that fits the duration`() {
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
        val structure = requireNotNull(strides.structure) { "Strides must carry a structure" }
        assertThat(structure.totalMin).isEqualTo(strides.durationMin)
        assertThat(structure.plannedSeconds).isLessThanOrEqualTo(strides.durationMin.orZero() * 60L)
        assertThat(structure.steps()).isNotEmpty
    }

    @Test
    fun `short availability still fits the strides structure`() {
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
        val structure = requireNotNull(strides.structure) { "Strides must carry a structure" }
        assertThat(structure.totalMin).isEqualTo(20)
        assertThat(structure.plannedSeconds).isLessThanOrEqualTo(20L * 60L)
    }

    @Test
    fun `intro fartlek carries a fitted structure without promising extra time`() {
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

        val intro = plan.sessions.single { it.title == "Fácil com surtos" }
        val structure = requireNotNull(intro.structure) { "Intro must carry a structure" }
        assertThat(structure.totalMin).isEqualTo(intro.durationMin)
        assertThat(structure.plannedSeconds).isLessThanOrEqualTo(intro.durationMin.orZero() * 60L)
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
                completionEvidence = com.meucoach.core.model.CompletionEvidence.ACTIVITY,
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
            experience = Experience.BEGINNER,
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
            subjectiveDaysInWindow = 0,
            strain = 400.0,
        ),
        weekStart = monday,
        today = monday,
        previousWeek = previousWeek,
        recentWeeks = recentWeeks,
    )

    private fun Int?.orZero(): Int = this ?: 0
}
