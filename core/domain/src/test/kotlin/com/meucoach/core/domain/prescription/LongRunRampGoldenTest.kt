package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
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
 * G-LONG-RUN-RAMP-01 (ADR 0067): the beginner long run grows at most 10 min
 * over the last normal long run; 45 is the floor, the day availability and the
 * level ceiling are hard caps, and a short reference lets the floor win.
 */
class LongRunRampGoldenTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)
    private val openSunday = mapOf(
        DayOfWeek.MONDAY to 50,
        DayOfWeek.TUESDAY to 50,
        DayOfWeek.THURSDAY to 50,
        DayOfWeek.SATURDAY to 50,
        DayOfWeek.SUNDAY to 90,
    )
    private val shortDays = mapOf(
        DayOfWeek.MONDAY to 40,
        DayOfWeek.TUESDAY to 40,
        DayOfWeek.THURSDAY to 40,
        DayOfWeek.SATURDAY to 40,
        DayOfWeek.SUNDAY to 90,
    )

    @Test
    fun `A a normal ramp caps the long run at ten minutes more`() {
        val plan = plan(lastLongRunMinutes = 45, availability = openSunday)

        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(longRun.durationMin).isEqualTo(55)
        assertThat(longRun.durationMin).isBetween(45, 55)
        assertThat(longRun.rationale).contains("Último longão de 45", "+10 min")
        assertCeiling(plan, openSunday)
    }

    @Test
    fun `B an agenda of fifty minutes limits the long run`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.TUESDAY to 40,
            DayOfWeek.THURSDAY to 40,
            DayOfWeek.SATURDAY to 40,
            DayOfWeek.SUNDAY to 50,
        )
        val plan = plan(lastLongRunMinutes = 45, availability = availability)

        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(longRun.durationMin).isEqualTo(50)
        assertThat(longRun.rationale).contains("Agenda limita")
        assertThat(longRun.rationale).doesNotContain("até 55")
        assertCeiling(plan, availability)
    }

    @Test
    fun `C without a reference the first long run is the floor`() {
        val plan = plan(lastLongRunMinutes = null, availability = shortDays)

        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(longRun.durationMin).isEqualTo(45)
        assertThat(longRun.rationale).contains("Primeiro longão")
        assertCeiling(plan, shortDays)
    }

    @Test
    fun `D the floor wins when the last long run was too short`() {
        val plan = plan(lastLongRunMinutes = 30, availability = shortDays)

        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(longRun.durationMin).isEqualTo(45)
        assertThat(longRun.rationale).contains("Piso de 45", "último longão foi 30")
        assertCeiling(plan, shortDays)
    }

    private fun plan(lastLongRunMinutes: Int?, availability: Map<DayOfWeek, Int>): WeeklyPlan {
        val previous = lastLongRunMinutes?.let { longWeek(monday.minusWeeks(1), it) }
        return generator.generate(
            WeeklyPlanInput(
                profile = profile(availability),
                readiness = readiness(),
                weekStart = monday,
                today = monday,
                previousWeek = previous,
                recentWeeks = listOfNotNull(previous),
                // The volume base is deliberately high: this golden isolates the
                // long-run ramp, not the weekly volume trim.
                volumeReferences = listOf(
                    VolumeReference(
                        weekStart = monday.minusWeeks(2),
                        plannedMinutes = 300,
                        executedMinutes = 300,
                        strongMinutes = 300,
                        strongSessions = 4,
                        plannedSessions = 4,
                        createdOn = monday.minusWeeks(2),
                        specialWeek = false,
                    ),
                ),
            ),
        )
    }

    private fun assertCeiling(plan: WeeklyPlan, availability: Map<DayOfWeek, Int>) {
        plan.sessions.forEach { session ->
            assertThat(session.durationMin)
                .isLessThanOrEqualTo(availability.getValue(session.date.dayOfWeek))
        }
    }

    private fun longWeek(weekStart: LocalDate, minutes: Int): WeeklyPlan {
        val longRun = PlannedSession(
            date = weekStart.plusDays(6),
            type = WorkoutType.LONG,
            title = "Longão",
            durationMin = minutes,
            rationale = "Longão em ritmo fácil.",
            ruleIds = listOf("R-PLAN-03"),
            evidenceIds = listOf("EV-PFITZINGER-2008"),
            status = SessionStatus.DONE,
            completionEvidence = CompletionEvidence.ACTIVITY,
            activityId = "activity-$weekStart",
        )
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = listOf(longRun),
            weeklyMinutes = minutes,
            longRunMinutes = minutes,
            qualityCount = 0,
            rationale = "Semana normal.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-PLAN-03"),
            evidenceIds = listOf("EV-PFITZINGER-2008"),
            createdOn = weekStart,
        )
    }

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
        experience = Experience.BEGINNER,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(availability),
    )
}
