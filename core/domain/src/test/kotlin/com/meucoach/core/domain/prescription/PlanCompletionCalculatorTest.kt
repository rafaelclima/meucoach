package com.meucoach.core.domain.prescription

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.LocalDate

class PlanCompletionCalculatorTest {
    private val calculator = PlanCompletionCalculator()
    private val today = LocalDate.of(2026, 11, 4)
    private val weekStart = LocalDate.of(2026, 11, 2)

    @Test
    fun `summarizes only past sessions of each week`() {
        val current = plan(
            weekStart = weekStart,
            sessions = listOf(
                session(weekStart, SessionStatus.DONE),
                session(weekStart.plusDays(3), SessionStatus.PLANNED),
            ),
        )
        val previous = plan(
            weekStart = weekStart.minusWeeks(1),
            sessions = listOf(
                session(weekStart.minusWeeks(1), SessionStatus.DONE),
                session(weekStart.minusWeeks(1).plusDays(2), SessionStatus.SKIPPED),
                session(weekStart.minusWeeks(1).plusDays(4), SessionStatus.PLANNED),
            ),
        )
        val twoWeeksAgo = plan(
            weekStart = weekStart.minusWeeks(2),
            sessions = listOf(
                session(weekStart.minusWeeks(2), SessionStatus.DONE),
                session(weekStart.minusWeeks(2).plusDays(2), SessionStatus.DONE),
            ),
        )

        val completion = calculator.calculate(
            plans = listOf(current, previous, twoWeeksAgo),
            today = today,
            weeks = 8,
        )

        assertThat(completion.weeks).hasSize(8)
        assertThat(completion.weeks.last().weekStart).isEqualTo(weekStart)
        assertThat(completion.weeks.last().planned).isEqualTo(1)
        assertThat(completion.weeks.last().done).isEqualTo(1)
        assertThat(completion.weeks[6].planned).isEqualTo(3)
        assertThat(completion.weeks[6].completionRatio).isCloseTo(1.0 / 3.0, within(0.001))
        assertThat(completion.weeks[5].completionRatio).isEqualTo(1.0)
        assertThat(completion.weeks[4].planned).isZero()
        assertThat(completion.weeks[4].completionRatio).isNull()
        assertThat(completion.planned).isEqualTo(6)
        assertThat(completion.done).isEqualTo(4)
        assertThat(completion.skipped).isEqualTo(1)
    }

    @Test
    fun `ignores rest sessions and plans outside the window`() {
        val plan = plan(
            weekStart = weekStart,
            sessions = listOf(
                session(weekStart, SessionStatus.PLANNED, WorkoutType.REST),
                session(weekStart.plusDays(1), SessionStatus.DONE),
            ),
        )
        val old = plan(
            weekStart = weekStart.minusWeeks(10),
            sessions = listOf(session(weekStart.minusWeeks(10), SessionStatus.DONE)),
        )

        val completion = calculator.calculate(listOf(plan, old), today, weeks = 4)

        assertThat(completion.weeks).hasSize(4)
        assertThat(completion.planned).isEqualTo(1)
        assertThat(completion.done).isEqualTo(1)
        assertThat(completion.completionRatio).isEqualTo(1.0)
    }

    @Test
    fun `reports no ratio without planned sessions`() {
        val completion = calculator.calculate(emptyList(), today, weeks = 4)

        assertThat(completion.planned).isZero()
        assertThat(completion.completionRatio).isNull()
        assertThat(completion.weeks).allMatch { it.completionRatio == null }
    }

    @Test
    fun `rejects invalid windows`() {
        assertThatThrownBy { calculator.calculate(emptyList(), today, weeks = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { calculator.calculate(emptyList(), today, weeks = 27) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun plan(weekStart: LocalDate, sessions: List<PlannedSession>) = WeeklyPlan(
        weekStart = weekStart,
        sessions = sessions,
        weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
        longRunMinutes = null,
        qualityCount = sessions.count { it.type == WorkoutType.QUALITY },
        rationale = "Plano.",
        confidence = com.meucoach.core.model.DataConfidence.MEDIUM,
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun session(date: LocalDate, status: SessionStatus, type: WorkoutType = WorkoutType.EASY) = PlannedSession(
        date = date,
        type = type,
        title = if (type == WorkoutType.REST) "Descanso" else "Fácil",
        durationMin = if (type == WorkoutType.REST) null else 30,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = status,
    )
}
