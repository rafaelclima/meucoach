package com.meucoach.core.domain.prescription

import com.meucoach.core.model.CompletionEvidence
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
                session(weekStart.minusWeeks(1).plusDays(2), SessionStatus.CLOSED),
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
    fun `excludes event dates from both numerator and denominator`() {
        val plan = plan(
            weekStart = weekStart,
            sessions = listOf(
                session(weekStart, SessionStatus.DONE),
                session(weekStart.plusDays(1), SessionStatus.PLANNED),
                session(weekStart.plusDays(2), SessionStatus.DONE),
            ),
        )

        val completion = calculator.calculate(
            plans = listOf(plan),
            today = today,
            weeks = 1,
            excludedDates = setOf(weekStart.plusDays(2)),
        )

        assertThat(completion.planned).isEqualTo(2)
        assertThat(completion.done).isEqualTo(1)
        assertThat(completion.completionRatio).isEqualTo(0.5)
    }

    @Test
    fun `exclusion also removes a done session on an event date`() {
        val plan = plan(
            weekStart = weekStart,
            sessions = listOf(
                session(weekStart, SessionStatus.DONE),
                session(weekStart.plusDays(1), SessionStatus.DONE),
            ),
        )

        val completion = calculator.calculate(
            plans = listOf(plan),
            today = today,
            weeks = 1,
            excludedDates = setOf(weekStart, weekStart.plusDays(1)),
        )

        assertThat(completion.planned).isZero()
        assertThat(completion.done).isZero()
        assertThat(completion.completionRatio).isNull()
    }

    @Test
    fun `reports no ratio without planned sessions`() {
        val completion = calculator.calculate(emptyList(), today, weeks = 4)

        assertThat(completion.planned).isZero()
        assertThat(completion.completionRatio).isNull()
        assertThat(completion.weeks).allMatch { it.completionRatio == null }
    }

    @Test
    fun `separates verified, manual and legacy completions`() {
        val plan = plan(
            weekStart = weekStart,
            sessions = listOf(
                session(weekStart, SessionStatus.DONE).copy(
                    completionEvidence = CompletionEvidence.ACTIVITY,
                    activityId = "activity-1",
                ),
                session(weekStart.plusDays(1), SessionStatus.DONE).copy(
                    completionEvidence = CompletionEvidence.MANUAL,
                ),
                session(weekStart.plusDays(2), SessionStatus.DONE),
                session(
                    weekStart.plusDays(3),
                    SessionStatus.CLOSED,
                ).copy(closureReason = SessionClosureReason.USER_SKIPPED),
                session(
                    weekStart.plusDays(4),
                    SessionStatus.CLOSED,
                ).copy(closureReason = SessionClosureReason.ILLNESS_OR_INJURY),
                session(
                    weekStart.plusDays(5),
                    SessionStatus.CLOSED,
                ).copy(closureReason = SessionClosureReason.REST_PRESCRIBED),
                session(weekStart.plusDays(6), SessionStatus.PLANNED),
            ),
        )

        val week = calculator.calculate(listOf(plan), today = weekStart.plusDays(6), weeks = 1).weeks.single()

        assertThat(week.planned).isEqualTo(5)
        assertThat(week.done).isEqualTo(3)
        assertThat(week.verified).isEqualTo(1)
        assertThat(week.manual).isEqualTo(1)
        assertThat(week.legacy).isEqualTo(1)
        assertThat(week.skipped).isEqualTo(1)
    }

    @Test
    fun `rejects invalid windows`() {
        assertThatThrownBy { calculator.calculate(emptyList(), today, weeks = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { calculator.calculate(emptyList(), today, weeks = 27) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a plan created midweek does not count the days before it`() {
        val createdOn = weekStart.plusDays(3)
        val midweek = plan(
            weekStart = weekStart,
            createdOn = createdOn,
            sessions = listOf(
                session(weekStart, SessionStatus.DONE),
                session(weekStart.plusDays(1), SessionStatus.CLOSED),
                session(createdOn, SessionStatus.DONE),
            ),
        )

        val completion = calculator.calculate(listOf(midweek), today = createdOn, weeks = 4)

        assertThat(completion.weeks.last().planned).isEqualTo(1)
        assertThat(completion.weeks.last().done).isEqualTo(1)
        assertThat(completion.weeks.last().skipped).isZero()
        assertThat(completion.completionRatio).isEqualTo(1.0)
    }

    private fun plan(weekStart: LocalDate, sessions: List<PlannedSession>, createdOn: LocalDate = weekStart) =
        WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
            longRunMinutes = null,
            qualityCount = sessions.count { it.type == WorkoutType.QUALITY },
            rationale = "Plano.",
            confidence = com.meucoach.core.model.DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = createdOn,
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
        completionEvidence = if (status == SessionStatus.DONE) {
            CompletionEvidence.LEGACY_UNVERIFIED
        } else {
            null
        },
        closureReason = if (status == SessionStatus.CLOSED) SessionClosureReason.USER_SKIPPED else null,
        closedAt = if (status == SessionStatus.CLOSED) java.time.Instant.EPOCH else null,
    )
}
