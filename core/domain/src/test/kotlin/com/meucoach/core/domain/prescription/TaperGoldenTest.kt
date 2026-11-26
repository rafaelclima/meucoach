package com.meucoach.core.domain.prescription

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
 * G-TAPER-01 (ADR 0066): the taper baseline is the average of the three
 * eligible weeks before the first taper week, with a named fallback to the
 * largest recent strong week and no invented percentage without history.
 *
 * The availability is still a hard ceiling and the per-session floors are
 * executable minimums: when the floors prevent the target band, the plan
 * declares a minimal week instead of promising a cut it did not deliver.
 */
class TaperGoldenTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)
    private val fiveSixty = mapOf(
        DayOfWeek.MONDAY to 60,
        DayOfWeek.TUESDAY to 60,
        DayOfWeek.WEDNESDAY to 60,
        DayOfWeek.THURSDAY to 60,
        DayOfWeek.FRIDAY to 60,
    )

    @Test
    fun `A three eligible weeks set the average baseline and target`() {
        val references = listOf(
            reference(monday.minusWeeks(3), 100),
            reference(monday.minusWeeks(2), 120),
            reference(monday.minusWeeks(1), 140),
        )

        val taper = generate(weekStart = monday, references = references)
        val raceWeek = generate(weekStart = monday.plusWeeks(1), references = references)

        assertThat(taper.rationale)
            .contains("média das 3 semanas", "120 min", "Alvo desta semana: 66 min")
            .contains("Semana mínima")
        assertThat(taper.weeklyMinutes).isEqualTo(115)
        assertCeiling(taper, fiveSixty)

        assertThat(raceWeek.rationale)
            .contains("média das 3 semanas", "120 min", "Alvo desta semana: 54 min")
            .contains("Semana mínima")
        assertThat(raceWeek.weeklyMinutes).isEqualTo(115)
        assertCeiling(raceWeek, fiveSixty)
    }

    @Test
    fun `B two eligible weeks fall back to the largest recent strong week`() {
        val references = listOf(
            reference(monday.minusWeeks(2), 100),
            reference(monday.minusWeeks(1), 140),
        )

        val taper = generate(weekStart = monday, references = references)

        assertThat(taper.rationale)
            .contains("maior semana forte recente", "140 min", "Alvo desta semana: 77 min")
            .doesNotContain("média das 3 semanas")
        assertThat(taper.weeklyMinutes).isEqualTo(115)
        assertCeiling(taper, fiveSixty)
    }

    @Test
    fun `C without eligible history the taper does not invent a percentage`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 50,
            DayOfWeek.TUESDAY to 50,
            DayOfWeek.WEDNESDAY to 50,
            DayOfWeek.THURSDAY to 50,
            DayOfWeek.FRIDAY to 50,
        )
        val plan = generate(
            weekStart = monday,
            references = emptyList(),
            availability = availability,
        )

        assertThat(plan.rationale)
            .contains("sem histórico")
            .doesNotContain("média das 3 semanas", "maior semana forte recente", "−45", "45–55")
        assertThat(plan.weeklyMinutes).isEqualTo(220)
        assertThat(plan.confidence).isEqualTo(DataConfidence.LOW)
        assertCeiling(plan, availability)
    }

    private fun generate(
        weekStart: LocalDate,
        references: List<VolumeReference>,
        availability: Map<DayOfWeek, Int> = fiveSixty,
    ): WeeklyPlan = generator.generate(
        WeeklyPlanInput(
            profile = profile(availability),
            readiness = readiness(),
            weekStart = weekStart,
            today = weekStart,
            volumeReferences = references,
            race = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(14)),
        ),
    )

    private fun assertCeiling(plan: WeeklyPlan, availability: Map<DayOfWeek, Int>) {
        plan.sessions.forEach { session ->
            assertThat(session.durationMin)
                .isLessThanOrEqualTo(availability.getValue(session.date.dayOfWeek))
        }
    }

    private fun reference(weekStart: LocalDate, strongMinutes: Int) = VolumeReference(
        weekStart = weekStart,
        plannedMinutes = strongMinutes,
        executedMinutes = strongMinutes,
        strongMinutes = strongMinutes,
        strongSessions = 4,
        plannedSessions = 4,
        createdOn = weekStart,
        specialWeek = false,
    )

    private fun readiness() = ReadinessResult(
        score = 80,
        level = ReadinessLevel.GREEN,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = emptySet(),
    )

    private fun profile(availability: Map<DayOfWeek, Int>) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(availability),
    )
}
