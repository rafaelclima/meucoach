package com.meucoach.core.domain.prescription

import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

/**
 * ADR 0050 §2.1/§2.5: the weekly volume reference is built from the real strong
 * runs (planned or not), never from the plan alone and never from a click.
 */
class VolumeReferenceTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `builds the reference from planned and extra strong minutes`() {
        val plan = plan(
            sessions = listOf(
                session(dayOffset = 0, type = WorkoutType.EASY, minutes = 40),
                session(dayOffset = 2, type = WorkoutType.QUALITY, minutes = 30),
                session(dayOffset = 5, type = WorkoutType.LONG, minutes = 45),
            ),
        )
        val runs = listOf(
            run(monday, 40),
            run(monday.plusDays(2), 30),
            run(monday.plusDays(3), 28),
            run(monday.plusDays(5), 45),
            run(monday.minusDays(1), 90),
        )

        val reference = VolumeReferenceBuilder.build(monday, plan, runs)

        assertThat(reference.weekStart).isEqualTo(monday)
        assertThat(reference.plannedMinutes).isEqualTo(115)
        assertThat(reference.executedMinutes).isEqualTo(143)
        assertThat(reference.strongMinutes).isEqualTo(143)
        assertThat(reference.strongSessions).isEqualTo(4)
        assertThat(reference.plannedSessions).isEqualTo(3)
        assertThat(reference.createdOn).isEqualTo(monday)
        assertThat(reference.specialWeek).isFalse()
    }

    @Test
    fun `a week without a plan keeps the real volume but no plan metadata`() {
        val reference = VolumeReferenceBuilder.build(
            weekStart = monday,
            plan = null,
            runs = listOf(run(monday.plusDays(1), 33)),
        )

        assertThat(reference.plannedMinutes).isNull()
        assertThat(reference.executedMinutes).isEqualTo(33)
        assertThat(reference.strongMinutes).isEqualTo(33)
        assertThat(reference.strongSessions).isEqualTo(1)
        assertThat(reference.plannedSessions).isZero()
        assertThat(reference.createdOn).isNull()
        assertThat(reference.specialWeek).isFalse()
    }

    @Test
    fun `special weeks are flagged from the plan rules`() {
        val specialRules = listOf("R-PLAN-04", "R-PLAN-05", "R-PLAN-10", "R-RACE-04")

        specialRules.forEach { ruleId ->
            val reference = VolumeReferenceBuilder.build(
                weekStart = monday,
                plan = plan(ruleIds = listOf("R-ZONE-01", ruleId)),
                runs = listOf(run(monday, 40)),
            )

            assertThat(reference.specialWeek).describedAs(ruleId).isTrue()
        }
    }

    @Test
    fun `only trainable planned sessions count`() {
        val plan = plan(
            createdOn = monday,
            sessions = listOf(
                session(dayOffset = -1, type = WorkoutType.EASY, minutes = 40),
                session(dayOffset = 0, type = WorkoutType.REST, minutes = null),
                session(
                    dayOffset = 1,
                    type = WorkoutType.EASY,
                    minutes = 30,
                    status = SessionStatus.CLOSED,
                    closureReason = SessionClosureReason.ILLNESS_OR_INJURY,
                ),
                session(dayOffset = 2, type = WorkoutType.QUALITY, minutes = 30),
                session(
                    dayOffset = 3,
                    type = WorkoutType.EASY,
                    minutes = 30,
                    status = SessionStatus.CLOSED,
                    closureReason = SessionClosureReason.USER_SKIPPED,
                ),
            ),
            weeklyMinutes = 130,
        )

        val reference = VolumeReferenceBuilder.build(monday, plan, emptyList())

        // The day before `createdOn`, the REST day and the non-trainable closure
        // are not doses; a user-skipped day still is (it was part of the plan).
        assertThat(reference.plannedSessions).isEqualTo(2)
    }

    @Test
    fun `runs outside the week are ignored`() {
        val reference = VolumeReferenceBuilder.build(
            weekStart = monday,
            plan = plan(),
            runs = listOf(
                run(monday.minusDays(1), 60),
                run(monday.plusDays(7), 60),
                run(monday.plusDays(6), 30),
            ),
        )

        assertThat(reference.strongSessions).isEqualTo(1)
        assertThat(reference.strongMinutes).isEqualTo(30)
    }

    @Test
    fun `the projection is independent of the run order`() {
        val runs = listOf(
            run(monday, 40),
            run(monday.plusDays(2), 30),
            run(monday.plusDays(3), 28),
            run(monday.plusDays(5), 45),
        )

        val forward = VolumeReferenceBuilder.build(monday, plan(), runs)
        val reversed = VolumeReferenceBuilder.build(monday, plan(), runs.reversed())
        val shuffled = VolumeReferenceBuilder.build(
            monday,
            plan(),
            listOf(runs[2], runs[0], runs[3], runs[1]),
        )

        assertThat(reversed).isEqualTo(forward)
        assertThat(shuffled).isEqualTo(forward)
    }

    @Test
    fun `rejects a strong run without duration`() {
        assertThatThrownBy {
            StrongRun(date = monday, durationMin = 0, source = ActivitySource.FIT)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("duration")
    }

    @Test
    fun `rejects a manual activity as strong evidence`() {
        assertThatThrownBy {
            StrongRun(date = monday, durationMin = 40, source = ActivitySource.MANUAL)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Manual")
    }

    @Test
    fun `rejects a week that does not start on Monday`() {
        assertThatThrownBy {
            VolumeReferenceBuilder.build(monday.plusDays(1), plan(), emptyList())
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Monday")
    }

    @Test
    fun `rejects a reference week that does not start on Monday`() {
        assertThatThrownBy {
            reference().copy(weekStart = monday.plusDays(1))
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Monday")
    }

    @Test
    fun `rejects inconsistent minute totals`() {
        assertThatThrownBy {
            VolumeReference(
                weekStart = monday,
                plannedMinutes = 100,
                executedMinutes = 50,
                strongMinutes = 60,
                strongSessions = 1,
                plannedSessions = 2,
                createdOn = monday,
                specialWeek = false,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Strong minutes")
    }

    @Test
    fun `rejects negative or inconsistent reference fields`() {
        val valid = reference()

        val invalid = listOf<() -> VolumeReference>(
            { valid.copy(plannedMinutes = -1) },
            { valid.copy(executedMinutes = -1) },
            { valid.copy(strongMinutes = -1) },
            { valid.copy(strongSessions = -1) },
            { valid.copy(strongMinutes = 0, strongSessions = -1) },
            { valid.copy(plannedSessions = -1) },
            { valid.copy(strongMinutes = 10, strongSessions = 0) },
        )

        invalid.forEach { make ->
            assertThatThrownBy { make() }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `rejects a blank planned session link`() {
        assertThatThrownBy {
            StrongRun(
                date = monday,
                durationMin = 40,
                source = ActivitySource.FIT,
                plannedSessionId = " ",
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Planned session")
    }

    @Test
    fun `hasPlan tells a reference from a planless week`() {
        assertThat(reference().hasPlan).isTrue()
        assertThat(reference(plannedMinutes = null).hasPlan).isFalse()
    }

    private fun reference(plannedMinutes: Int? = 100) = VolumeReference(
        weekStart = monday,
        plannedMinutes = plannedMinutes,
        executedMinutes = 40,
        strongMinutes = 40,
        strongSessions = 1,
        plannedSessions = 2,
        createdOn = monday,
        specialWeek = false,
    )

    private fun plan(
        sessions: List<PlannedSession> = listOf(
            session(dayOffset = 0, type = WorkoutType.EASY, minutes = 40),
        ),
        weeklyMinutes: Int = sessions.sumOf { it.durationMin ?: 0 },
        createdOn: LocalDate = monday,
        ruleIds: List<String> = listOf("R-ZONE-01"),
    ) = WeeklyPlan(
        weekStart = monday,
        sessions = sessions,
        weeklyMinutes = weeklyMinutes,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = ruleIds,
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = createdOn,
    )

    private fun session(
        dayOffset: Long,
        type: WorkoutType,
        minutes: Int?,
        status: SessionStatus = SessionStatus.PLANNED,
        closureReason: SessionClosureReason? = null,
        activityId: String? = null,
    ) = PlannedSession(
        date = monday.plusDays(dayOffset),
        type = type,
        title = "Sessão",
        durationMin = minutes,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = status,
        completionEvidence = if (status == SessionStatus.DONE) {
            com.meucoach.core.model.CompletionEvidence.ACTIVITY
        } else {
            null
        },
        activityId = activityId,
        closureReason = closureReason,
    )

    private fun run(
        date: LocalDate,
        durationMin: Int,
        source: ActivitySource = ActivitySource.FIT,
        plannedSessionId: String? = null,
    ) = StrongRun(
        date = date,
        durationMin = durationMin,
        source = source,
        plannedSessionId = plannedSessionId,
    )
}
