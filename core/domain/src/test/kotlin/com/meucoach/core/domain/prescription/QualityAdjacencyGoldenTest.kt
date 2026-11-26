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
 * G-QUALITY-ADJACENCY-01 (ADR 0068): a trainable long run on Sunday blocks
 * Monday quality. With another safe day the quality moves and the copy names
 * the previous long run; without one the week has zero QUALITY and explains.
 * Without a known previous week the plan never invents a block.
 */
class QualityAdjacencyGoldenTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `A a Sunday long run moves Monday quality to a safe day`() {
        val plan = plan(
            history = buildHistory(),
            availability = mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.TUESDAY to 40,
                DayOfWeek.THURSDAY to 40,
                DayOfWeek.SATURDAY to 90,
            ),
        )

        val qualities = plan.sessions.filter { it.type == WorkoutType.QUALITY }
        assertThat(qualities).isNotEmpty
        assertThat(qualities.map { it.date.dayOfWeek })
            .doesNotContain(DayOfWeek.MONDAY)
            .allMatch { it == DayOfWeek.TUESDAY || it == DayOfWeek.THURSDAY }
        assertThat(plan.rationale).contains("Qualidade movida", "longão de domingo")
        assertCeiling(
            plan,
            mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.TUESDAY to 40,
                DayOfWeek.THURSDAY to 40,
                DayOfWeek.SATURDAY to 90,
            ),
        )
    }

    @Test
    fun `B without another safe day the week has no quality`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 20,
            DayOfWeek.SATURDAY to 90,
        )
        val plan = plan(history = buildHistory(), availability = availability)

        assertThat(plan.sessions.none { it.type == WorkoutType.QUALITY }).isTrue()
        assertThat(plan.qualityCount).isZero()
        assertThat(plan.rationale).contains("sem dia seguro", "longão de domingo")
        assertCeiling(plan, availability)
        assertStimulusHasStructure(plan)
    }

    @Test
    fun `C without a previous week Monday keeps the quality`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 20,
            DayOfWeek.SATURDAY to 90,
        )
        val history = (2..5).map { loadWeek(monday.minusWeeks(it.toLong())) }
        val plan = plan(history = history, availability = availability)

        val qualities = plan.sessions.filter { it.type == WorkoutType.QUALITY }
        assertThat(qualities).isNotEmpty
        assertThat(qualities.map { it.date.dayOfWeek }).contains(DayOfWeek.MONDAY)
        assertThat(plan.rationale).doesNotContain("longão de domingo")
        assertCeiling(plan, availability)
    }

    @Test
    fun `D a non-trainable Sunday long does not block Monday`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 20,
            DayOfWeek.SATURDAY to 90,
        )
        val history = listOf(
            loadWeek(monday.minusWeeks(5)),
            loadWeek(monday.minusWeeks(4)),
            loadWeek(monday.minusWeeks(3)),
            closedLongWeek(monday.minusWeeks(1)),
        )
        val plan = plan(history = history, availability = availability)

        val qualities = plan.sessions.filter { it.type == WorkoutType.QUALITY }
        assertThat(qualities.map { it.date.dayOfWeek }).contains(DayOfWeek.MONDAY)
        assertThat(plan.rationale).doesNotContain("longão de domingo")
        assertCeiling(plan, availability)
    }

    private fun plan(history: List<WeeklyPlan>, availability: Map<DayOfWeek, Int>): WeeklyPlan = generator.generate(
        WeeklyPlanInput(
            profile = profile(availability),
            readiness = readiness(),
            weekStart = monday,
            today = monday,
            previousWeek = history.firstOrNull { it.weekStart == monday.minusWeeks(1) },
            recentWeeks = history,
        ),
    )

    private fun buildHistory(): List<WeeklyPlan> = listOf(
        loadWeek(monday.minusWeeks(5)),
        loadWeek(monday.minusWeeks(4)),
        loadWeek(monday.minusWeeks(3)),
        longWeek(monday.minusWeeks(1)),
    )

    private fun assertCeiling(plan: WeeklyPlan, availability: Map<DayOfWeek, Int>) {
        plan.sessions.forEach { session ->
            assertThat(session.durationMin)
                .isLessThanOrEqualTo(availability.getValue(session.date.dayOfWeek))
        }
    }

    private fun assertStimulusHasStructure(plan: WeeklyPlan) {
        plan.sessions
            .filter { it.type == WorkoutType.QUALITY || it.title == "Acelerações curtas" }
            .forEach { assertThat(it.structure).isNotNull }
    }

    private fun loadWeek(weekStart: LocalDate): WeeklyPlan {
        val sessions = (0..3).map { index ->
            easy(weekStart.plusDays(index.toLong()), 30)
        }
        return week(weekStart, sessions, sessions.sumOf { it.durationMin ?: 0 })
    }

    private fun longWeek(weekStart: LocalDate): WeeklyPlan {
        val sessions = listOf(
            easy(weekStart, 30),
            easy(weekStart.plusDays(2), 30),
            easy(weekStart.plusDays(4), 30),
            PlannedSession(
                date = weekStart.plusDays(6),
                type = WorkoutType.LONG,
                title = "Longão",
                durationMin = 60,
                rationale = "Longão em ritmo fácil.",
                ruleIds = listOf("R-PLAN-03"),
                evidenceIds = listOf("EV-PFITZINGER-2008"),
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "long-$weekStart",
            ),
        )
        return week(weekStart, sessions, sessions.sumOf { it.durationMin ?: 0 })
    }

    private fun closedLongWeek(weekStart: LocalDate): WeeklyPlan {
        val sessions = listOf(
            easy(weekStart, 30),
            easy(weekStart.plusDays(2), 30),
            PlannedSession(
                date = weekStart.plusDays(6),
                type = WorkoutType.LONG,
                title = "Longão",
                durationMin = 60,
                rationale = "Longão em ritmo fácil.",
                ruleIds = listOf("R-PLAN-03"),
                evidenceIds = listOf("EV-PFITZINGER-2008"),
                status = SessionStatus.CLOSED,
                closureReason = SessionClosureReason.ILLNESS_OR_INJURY,
                closedAt = weekStart.plusDays(6).atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
            ),
        )
        return week(weekStart, sessions, sessions.sumOf { it.durationMin ?: 0 })
    }

    private fun easy(date: LocalDate, minutes: Int) = PlannedSession(
        date = date,
        type = WorkoutType.EASY,
        title = "Rodagem",
        durationMin = minutes,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = SessionStatus.DONE,
        completionEvidence = CompletionEvidence.ACTIVITY,
        activityId = "easy-$date",
    )

    private fun week(weekStart: LocalDate, sessions: List<PlannedSession>, minutes: Int) = WeeklyPlan(
        weekStart = weekStart,
        sessions = sessions,
        weeklyMinutes = minutes,
        longRunMinutes = sessions.firstOrNull { it.type == WorkoutType.LONG }?.durationMin,
        qualityCount = 0,
        rationale = "Semana.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = weekStart,
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
