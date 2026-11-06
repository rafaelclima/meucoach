package com.meucoach.core.domain.prescription

import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class SessionSwapperTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val wednesday = monday.plusDays(2)
    private val saturday = monday.plusDays(5)

    @Test
    fun `swaps planned sessions and keeps their dates`() {
        val plan = plan(
            session(monday, WorkoutType.EASY, "Fácil e conversável", 40),
            session(wednesday, WorkoutType.QUALITY, "Tempo", 30),
            session(saturday, WorkoutType.LONG, "Longão", 80),
        )

        val result = SessionSwapper.swap(plan, monday, wednesday, today = monday)

        assertThat(result).isInstanceOf(SwapResult.Applied::class.java)
        val swapped = (result as SwapResult.Applied).plan
        assertThat(swapped.sessions.first { it.date == monday }.type).isEqualTo(WorkoutType.QUALITY)
        assertThat(swapped.sessions.first { it.date == monday }.title).isEqualTo("Tempo")
        assertThat(swapped.sessions.first { it.date == wednesday }.type).isEqualTo(WorkoutType.EASY)
        assertThat(swapped.weeklyMinutes).isEqualTo(150)
        assertThat(swapped.qualityCount).isEqualTo(1)
        assertThat(swapped.longRunMinutes).isEqualTo(80)
    }

    @Test
    fun `rejects past or completed sessions`() {
        val plan = plan(
            session(monday, WorkoutType.EASY, "Fácil e conversável", 40),
            session(wednesday, WorkoutType.QUALITY, "Tempo", 30),
        )

        assertThat(SessionSwapper.swap(plan, monday, wednesday, today = monday.plusDays(1)))
            .isInstanceOf(SwapResult.Rejected::class.java)

        val completed = plan.copy(
            sessions = plan.sessions.map {
                if (it.date == monday) {
                    it.copy(
                        status = SessionStatus.DONE,
                        completionEvidence = com.meucoach.core.model.CompletionEvidence.ACTIVITY,
                        activityId = "activity-1",
                    )
                } else {
                    it
                }
            },
        )
        val rejected = SessionSwapper.swap(completed, monday, wednesday, today = monday)
        assertThat((rejected as SwapResult.Rejected).reason).contains("planejadas")
    }

    @Test
    fun `rejects a quality next to the long run`() {
        val sunday = monday.plusDays(6)
        val plan = plan(
            session(monday, WorkoutType.EASY, "Fácil e conversável", 40),
            session(wednesday, WorkoutType.QUALITY, "Tempo", 30),
            session(saturday, WorkoutType.LONG, "Longão", 80),
            session(sunday, WorkoutType.EASY, "Fácil e conversável", 40),
        )

        val result = SessionSwapper.swap(plan, wednesday, sunday, today = monday)

        assertThat((result as SwapResult.Rejected).reason).contains("longão")
    }

    @Test
    fun `rejects consecutive qualities`() {
        val tuesday = monday.plusDays(1)
        val thursday = monday.plusDays(3)
        val plan = plan(
            session(monday, WorkoutType.QUALITY, "Tempo", 30),
            session(tuesday, WorkoutType.EASY, "Fácil e conversável", 40),
            session(thursday, WorkoutType.QUALITY, "Tempo", 30),
            session(saturday, WorkoutType.LONG, "Longão", 80),
        )

        val result = SessionSwapper.swap(plan, tuesday, thursday, today = monday)

        assertThat((result as SwapResult.Rejected).reason).contains("consecutivos")
    }

    @Test
    fun `rejects the same day, rest days and missing sessions`() {
        val plan = plan(
            session(monday, WorkoutType.EASY, "Fácil e conversável", 40),
            session(wednesday, WorkoutType.REST, "Descanso", null),
        )

        assertThat(SessionSwapper.swap(plan, monday, monday, today = monday))
            .isInstanceOf(SwapResult.Rejected::class.java)
        assertThat(SessionSwapper.swap(plan, monday, wednesday, today = monday))
            .isInstanceOf(SwapResult.Rejected::class.java)
        assertThat(SessionSwapper.swap(plan, monday, saturday, today = monday))
            .isInstanceOf(SwapResult.Rejected::class.java)
    }

    private fun plan(vararg sessions: PlannedSession) = WeeklyPlan(
        weekStart = monday,
        sessions = sessions.toList(),
        weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
        longRunMinutes = sessions.firstOrNull { it.type == WorkoutType.LONG }?.durationMin,
        qualityCount = sessions.count { it.type == WorkoutType.QUALITY },
        rationale = "Plano.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun session(date: LocalDate, type: WorkoutType, title: String, durationMin: Int?) = PlannedSession(
        date = date,
        type = type,
        title = title,
        durationMin = durationMin,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )
}
