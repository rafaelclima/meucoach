package com.meucoach.app.application

import com.meucoach.core.data.db.entity.plannedSessionId
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Distance
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * ADR 0050 §2.1/§2.5: the app projection turns imported activities into
 * [com.meucoach.core.domain.prescription.StrongRun] and weekly
 * [com.meucoach.core.domain.prescription.VolumeReference]s. Planned and extra
 * runs count for volume; only the linked one is marked as planned.
 */
class VolumeReferenceProjectionTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val zone = ZoneOffset.UTC

    @Test
    fun `projects imported running activities into strong runs and references`() {
        val plan = plan(
            sessions = listOf(
                session(dayOffset = 0, type = WorkoutType.EASY, minutes = 40),
                session(dayOffset = 2, type = WorkoutType.QUALITY, minutes = 30),
                session(dayOffset = 5, type = WorkoutType.LONG, minutes = 45),
            ),
        )
        val activities = listOf(
            activity(id = "monday", start = "2026-11-02T10:00:00Z", minutes = 40),
            activity(id = "thursday", start = "2026-11-05T10:00:00Z", minutes = 28, source = ActivitySource.TCX),
            activity(
                id = "saturday",
                start = "2026-11-07T10:00:00Z",
                minutes = 45,
                sport = Sport.TREADMILL,
                source = ActivitySource.GPX,
            ),
        )

        val projection = VolumeReferenceProjection.build(listOf(plan), activities, zone)

        val reference = projection.references.single()
        assertThat(reference.plannedMinutes).isEqualTo(115)
        assertThat(reference.strongMinutes).isEqualTo(113)
        assertThat(reference.executedMinutes).isEqualTo(113)
        assertThat(reference.strongSessions).isEqualTo(3)
        assertThat(reference.plannedSessions).isEqualTo(3)
        assertThat(reference.createdOn).isEqualTo(monday)
        assertThat(projection.strongRuns.map { it.durationMin }).containsExactly(40, 28, 45)
        assertThat(projection.strongRuns.map { it.source })
            .containsExactly(ActivitySource.FIT, ActivitySource.TCX, ActivitySource.GPX)
    }

    @Test
    fun `links an activity to its planned session and keeps extra runs unplanned`() {
        val plan = plan(
            sessions = listOf(
                session(
                    dayOffset = 0,
                    type = WorkoutType.EASY,
                    minutes = 40,
                    status = SessionStatus.DONE,
                    activityId = "planned",
                ),
            ),
        )
        val activities = listOf(
            activity(id = "planned", start = "2026-11-02T10:00:00Z", minutes = 40),
            activity(id = "extra", start = "2026-11-04T10:00:00Z", minutes = 28),
        )

        val projection = VolumeReferenceProjection.build(listOf(plan), activities, zone)

        val reference = projection.references.single()
        assertThat(reference.strongSessions).isEqualTo(2)
        assertThat(reference.strongMinutes).isEqualTo(68)
        // Adherence stays 1/1: the extra run never inflates the planned count.
        assertThat(reference.plannedSessions).isEqualTo(1)
        assertThat(projection.strongRuns.map { it.plannedSessionId })
            .containsExactly(plannedSessionId(monday, monday), null)
    }

    @Test
    fun `excludes walking other and manual activities`() {
        val activities = listOf(
            activity(id = "walk", start = "2026-11-02T10:00:00Z", minutes = 60, sport = Sport.WALKING),
            activity(id = "other", start = "2026-11-03T10:00:00Z", minutes = 60, sport = Sport.OTHER),
            activity(
                id = "manual",
                start = "2026-11-04T10:00:00Z",
                minutes = 40,
                source = ActivitySource.MANUAL,
            ),
            activity(id = "run", start = "2026-11-05T10:00:00Z", minutes = 30),
        )

        val projection = VolumeReferenceProjection.build(emptyList(), activities, zone)

        assertThat(projection.strongRuns).hasSize(1)
        assertThat(projection.strongRuns.single().durationMin).isEqualTo(30)
        assertThat(projection.references).isEmpty()
    }

    @Test
    fun `does not double count the activity linked to a done session`() {
        val plan = plan(
            sessions = listOf(
                session(
                    dayOffset = 0,
                    type = WorkoutType.EASY,
                    minutes = 40,
                    status = SessionStatus.DONE,
                    activityId = "linked",
                ),
            ),
        )
        val activities = listOf(
            activity(id = "linked", start = "2026-11-02T10:00:00Z", minutes = 40),
            activity(id = "extra", start = "2026-11-04T10:00:00Z", minutes = 28),
        )

        val projection = VolumeReferenceProjection.build(listOf(plan), activities, zone)

        val reference = projection.references.single()
        assertThat(reference.strongSessions).isEqualTo(2)
        assertThat(reference.strongMinutes).isEqualTo(68)
        assertThat(projection.strongRuns).hasSize(2)
    }

    @Test
    fun `builds one reference per week and flags special weeks`() {
        val previous = plan(
            weekStart = monday.minusWeeks(1),
            sessions = listOf(
                session(
                    dayOffset = 0,
                    type = WorkoutType.EASY,
                    minutes = 60,
                    weekStart = monday.minusWeeks(1),
                ),
            ),
            ruleIds = listOf("R-ZONE-01"),
        )
        val current = plan(weekStart = monday, ruleIds = listOf("R-ZONE-01", "R-PLAN-04"))
        val activities = listOf(
            activity(id = "old", start = "2026-10-28T10:00:00Z", minutes = 60),
            activity(id = "new", start = "2026-11-03T10:00:00Z", minutes = 40),
        )

        val projection = VolumeReferenceProjection.build(listOf(current, previous), activities, zone)

        assertThat(projection.references.map { it.weekStart })
            .containsExactly(monday.minusWeeks(1), monday)
        assertThat(projection.references.map { it.specialWeek }).containsExactly(false, true)
    }

    @Test
    fun `is deterministic regardless of activities and plans order`() {
        val plan = plan()
        val activities = listOf(
            activity(id = "a", start = "2026-11-02T10:00:00Z", minutes = 40),
            activity(id = "b", start = "2026-11-03T10:00:00Z", minutes = 30),
            activity(id = "c", start = "2026-11-04T10:00:00Z", minutes = 28),
        )

        val forward = VolumeReferenceProjection.build(listOf(plan), activities, zone)
        val reversed = VolumeReferenceProjection.build(listOf(plan), activities.reversed(), zone)
        val shuffled = VolumeReferenceProjection.build(
            listOf(plan),
            listOf(activities[2], activities[0], activities[1]),
            zone,
        )

        assertThat(reversed).isEqualTo(forward)
        assertThat(shuffled).isEqualTo(forward)
    }

    @Test
    fun `skips an activity shorter than one minute`() {
        val activity = ActivitySummary(
            id = "short",
            source = ActivitySource.FIT,
            sport = Sport.RUNNING,
            startTime = Instant.parse("2026-11-02T10:00:00Z"),
            durationSec = 59,
            distance = Distance(100.0),
        )

        val projection = VolumeReferenceProjection.build(emptyList(), listOf(activity), zone)

        assertThat(projection.strongRuns).isEmpty()
    }

    @Test
    fun `a run in a week without a plan stays visible but is not a reference`() {
        val activities = listOf(activity(id = "extra", start = "2026-11-03T10:00:00Z", minutes = 28))

        val projection = VolumeReferenceProjection.build(emptyList(), activities, zone)

        assertThat(projection.strongRuns.single().durationMin).isEqualTo(28)
        assertThat(projection.references).isEmpty()
    }

    private fun plan(
        weekStart: LocalDate = monday,
        sessions: List<PlannedSession> = listOf(
            session(dayOffset = 0, type = WorkoutType.EASY, minutes = 40),
        ),
        ruleIds: List<String> = listOf("R-ZONE-01"),
    ) = WeeklyPlan(
        weekStart = weekStart,
        sessions = sessions,
        weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = ruleIds,
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = weekStart,
    )

    private fun session(
        dayOffset: Long,
        type: WorkoutType,
        minutes: Int,
        status: SessionStatus = SessionStatus.PLANNED,
        activityId: String? = null,
        weekStart: LocalDate = monday,
    ) = PlannedSession(
        date = weekStart.plusDays(dayOffset),
        type = type,
        title = "Sessão",
        durationMin = minutes,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = status,
        completionEvidence = if (status == SessionStatus.DONE) CompletionEvidence.ACTIVITY else null,
        activityId = activityId,
    )

    private fun activity(
        id: String,
        start: String,
        minutes: Int,
        source: ActivitySource = ActivitySource.FIT,
        sport: Sport = Sport.RUNNING,
    ) = ActivitySummary(
        id = id,
        source = source,
        sport = sport,
        startTime = Instant.parse(start),
        durationSec = minutes * 60,
        distance = Distance(5_000.0),
    )
}
