package com.meucoach.core.domain.prescription

import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class LongRunReferenceTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `a real strong completion wins over the planned duration`() {
        val reference = LongRunReferenceResolver.resolve(
            listOf(week(monday.minusWeeks(1), 45, done = true)),
            monday,
        )

        assertThat(reference).isNotNull
        assertThat(reference!!.minutes).isEqualTo(45)
        assertThat(reference.source).isEqualTo(LongRunReferenceSource.REAL_STRONG)
    }

    @Test
    fun `without a strong completion the planned long run is the reference`() {
        val reference = LongRunReferenceResolver.resolve(
            listOf(week(monday.minusWeeks(1), 45, done = false)),
            monday,
        )

        assertThat(reference!!.source).isEqualTo(LongRunReferenceSource.PLANNED)
    }

    @Test
    fun `a special week is not a long-run reference`() {
        val taper = week(monday.minusWeeks(1), 30, done = true, ruleIds = listOf("R-PLAN-05"))
        val normal = week(monday.minusWeeks(2), 45, done = true)

        val reference = LongRunReferenceResolver.resolve(listOf(taper, normal), monday)

        assertThat(reference!!.date).isEqualTo(normal.weekStart.plusDays(6))
        assertThat(reference.minutes).isEqualTo(45)
    }

    @Test
    fun `a non-trainable closure is not a dose and older weeks stay in the window`() {
        val closed = week(
            monday.minusWeeks(1),
            30,
            done = false,
            closureReason = SessionClosureReason.ILLNESS_OR_INJURY,
        )
        val older = week(monday.minusWeeks(4), 50, done = true)
        val outside = week(monday.minusWeeks(5), 60, done = true)

        val reference = LongRunReferenceResolver.resolve(listOf(closed, older, outside), monday)

        assertThat(reference!!.minutes).isEqualTo(50)
    }

    @Test
    fun `without a normal long run there is no reference`() {
        val reference = LongRunReferenceResolver.resolve(
            listOf(week(monday.minusWeeks(5), 60, done = true)),
            monday,
        )

        assertThat(reference).isNull()
    }

    private fun week(
        weekStart: LocalDate,
        minutes: Int,
        done: Boolean,
        ruleIds: List<String> = listOf("R-PLAN-03"),
        closureReason: SessionClosureReason? = null,
    ): WeeklyPlan {
        val longRun = PlannedSession(
            date = weekStart.plusDays(6),
            type = WorkoutType.LONG,
            title = "Longão",
            durationMin = minutes,
            rationale = "Longão em ritmo fácil.",
            ruleIds = listOf("R-PLAN-03"),
            evidenceIds = listOf("EV-PFITZINGER-2008"),
            status = when {
                closureReason != null -> SessionStatus.CLOSED
                done -> SessionStatus.DONE
                else -> SessionStatus.PLANNED
            },
            completionEvidence = if (done) CompletionEvidence.ACTIVITY else null,
            activityId = if (done) "activity-$weekStart" else null,
            closureReason = closureReason,
            closedAt = closureReason?.let {
                weekStart.plusDays(6).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
            },
        )
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = listOf(longRun),
            weeklyMinutes = minutes,
            longRunMinutes = minutes,
            qualityCount = 0,
            rationale = "Semana.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = ruleIds,
            evidenceIds = listOf("EV-PFITZINGER-2008"),
            createdOn = weekStart,
        )
    }
}
