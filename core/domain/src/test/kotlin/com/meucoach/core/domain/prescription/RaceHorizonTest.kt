package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
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
 * FR-411 / ADR 0043: a distant race no longer freezes the week in BASE.
 *
 * The general mode keeps the ADR 0041 BASE/BUILD rotation (with the structural
 * quality opportunity) until the longer macro table starts. A short 5K entry
 * still gets a short BASE and only one PEAK week.
 */
class RaceHorizonTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val generator = WeeklyPlanGenerator()

    @Test
    fun `a distant 5K keeps the general rotation and offers quality`() {
        val plan = generator.generate(
            input(
                race = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusWeeks(25).plusDays(6)),
                references = (1L..5L).map { weeksAgo -> reference(monday.minusWeeks(weeksAgo)) },
            ),
        )

        assertThat(plan.rationale).contains("Fase BUILD")
        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.sessions).anyMatch { it.type == WorkoutType.QUALITY }
    }

    @Test
    fun `a six week 5K enters the short table and gets one peak week`() {
        val macro = MacroPlanResolver.resolve(
            RacePlan("race-1", RaceDistance.FIVE_K, monday.plusWeeks(5).plusDays(6)),
            monday,
        )

        assertThat(macro.isGeneral).isFalse()
        assertThat(macro.weeks.count { it.phase == MesoPhase.PEAK }).isEqualTo(1)
        assertThat(macro.weeks.first().phase).isEqualTo(MesoPhase.BASE)
    }

    private fun input(race: RacePlan, references: List<VolumeReference>) = WeeklyPlanInput(
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
        weekStart = monday,
        today = monday,
        race = race,
        volumeReferences = references,
    )

    private fun reference(weekStart: LocalDate) = VolumeReference(
        weekStart = weekStart,
        plannedMinutes = 160,
        executedMinutes = 120,
        strongMinutes = 120,
        strongSessions = 1,
        plannedSessions = 4,
        createdOn = weekStart,
        specialWeek = false,
    )
}
