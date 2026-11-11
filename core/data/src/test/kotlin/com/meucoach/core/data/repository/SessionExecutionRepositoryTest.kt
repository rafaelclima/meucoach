package com.meucoach.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceCompletionSource
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.Sport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionExecutionRepositoryTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var plans: TrainingPlanRepository
    private lateinit var activities: ActivityRepository
    private lateinit var executions: SessionExecutionRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-02T13:00:00Z"), ZoneOffset.UTC)
    private val monday = LocalDate.of(2026, 11, 2)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        plans = TrainingPlanRepository(database, clock)
        activities = ActivityRepository(
            database = database,
            clock = clock,
            zoneId = ZoneId.of("America/Sao_Paulo"),
            idGenerator = { "activity-1" },
        )
        executions = SessionExecutionRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `records an activity execution and marks the session as verified`(): Unit = runBlocking {
        plans.saveWeek(plan())
        activities.saveImportedActivity(activity())

        val outcome = executions.recordActivityExecution(
            plannedSessionId = "2026-11-02-2026-11-02",
            activityId = "activity-1",
            startedAt = Instant.parse("2026-11-02T10:00:00Z"),
            durationSec = 1_800,
            distanceM = 5_000.0,
        )

        assertThat(outcome).isInstanceOf(CompletionOutcome.Recorded::class.java)
        val execution = (outcome as CompletionOutcome.Recorded).execution
        assertThat(execution.source.name).isEqualTo("ACTIVITY")
        assertThat(execution.activityId).isEqualTo("activity-1")
        assertThat(execution.distanceM).isEqualTo(5_000.0)
        assertThat(execution.endedAt).isEqualTo(Instant.parse("2026-11-02T10:30:00Z"))
        assertThat(executions.getForActivity("activity-1")?.id).isEqualTo(execution.id)
        assertThat(executions.getForSession("2026-11-02-2026-11-02")?.id).isEqualTo(execution.id)
        assertThat(executions.observeLinkedActivityIds().first()).containsExactly("activity-1")

        val session = plans.getWeek(monday)?.sessions?.first()
        assertThat(session?.status).isEqualTo(SessionStatus.DONE)
        assertThat(session?.completionEvidence).isEqualTo(CompletionEvidence.ACTIVITY)
        assertThat(session?.activityId).isEqualTo("activity-1")
        assertThat(session?.executionId).isEqualTo(execution.id)
    }

    @Test
    fun `records a manual execution without inventing distance`(): Unit = runBlocking {
        plans.saveWeek(plan())

        val outcome = executions.recordManualExecution(
            plannedSessionId = "2026-11-02-2026-11-02",
            durationMin = 35,
            rpe = 6,
        )

        val execution = (outcome as CompletionOutcome.Recorded).execution
        assertThat(execution.source.name).isEqualTo("MANUAL")
        assertThat(execution.activityId).isNull()
        assertThat(execution.distanceM).isNull()
        assertThat(execution.rpe).isEqualTo(6)
        assertThat(execution.durationSec).isEqualTo(2_100)
        val session = plans.getWeek(monday)?.sessions?.first()
        assertThat(session?.status).isEqualTo(SessionStatus.DONE)
        assertThat(session?.completionEvidence).isEqualTo(CompletionEvidence.MANUAL)
        assertThat(session?.activityId).isNull()
    }

    @Test
    fun `replaces a manual record with the imported run`(): Unit = runBlocking {
        plans.saveWeek(plan())
        activities.saveImportedActivity(activity())
        val manual = executions.recordManualExecution(
            "2026-11-02-2026-11-02",
            durationMin = 35,
            rpe = 6,
        ) as CompletionOutcome.Recorded

        val outcome = executions.replaceManualWithActivity(
            plannedSessionId = "2026-11-02-2026-11-02",
            activityId = "activity-1",
            startedAt = Instant.parse("2026-11-02T10:00:00Z"),
            durationSec = 1_800,
            distanceM = 5_000.0,
        )

        assertThat(outcome).isInstanceOf(CompletionOutcome.Recorded::class.java)
        val execution = (outcome as CompletionOutcome.Recorded).execution
        assertThat(execution.id).isEqualTo(manual.execution.id)
        assertThat(execution.source.name).isEqualTo("ACTIVITY")
        assertThat(execution.activityId).isEqualTo("activity-1")
        assertThat(execution.distanceM).isEqualTo(5_000.0)
        assertThat(execution.rpe).isNull()
        assertThat(executions.getForActivity("activity-1")?.id).isEqualTo(execution.id)
        assertThat(executions.getForSession("2026-11-02-2026-11-02")?.id).isEqualTo(execution.id)

        val session = plans.getWeek(monday)?.sessions?.first()
        assertThat(session?.status).isEqualTo(SessionStatus.DONE)
        assertThat(session?.completionEvidence).isEqualTo(CompletionEvidence.ACTIVITY)
        assertThat(session?.activityId).isEqualTo("activity-1")
        assertThat(session?.executionId).isEqualTo(execution.id)
    }

    @Test
    fun `only a manual completion can be replaced`(): Unit = runBlocking {
        plans.saveWeek(plan())
        activities.saveImportedActivity(activity())

        val planned = executions.replaceManualWithActivity(
            plannedSessionId = "2026-11-02-2026-11-02",
            activityId = "activity-1",
            startedAt = Instant.parse("2026-11-02T10:00:00Z"),
            durationSec = 1_800,
            distanceM = 5_000.0,
        )
        assertThat(planned).isInstanceOf(CompletionOutcome.Rejected::class.java)

        executions.recordActivityExecution(
            "2026-11-02-2026-11-03",
            "activity-1",
            Instant.parse("2026-11-02T10:00:00Z"),
            1_800,
            5_000.0,
        )
        val verified = executions.replaceManualWithActivity(
            plannedSessionId = "2026-11-02-2026-11-03",
            activityId = "activity-1",
            startedAt = Instant.parse("2026-11-02T10:00:00Z"),
            durationSec = 1_800,
            distanceM = 5_000.0,
        )
        assertThat(verified).isInstanceOf(CompletionOutcome.Rejected::class.java)
    }

    @Test
    fun `replacement rejects an activity already linked to another session`(): Unit = runBlocking {
        plans.saveWeek(plan())
        activities.saveImportedActivity(activity())
        executions.recordActivityExecution(
            "2026-11-02-2026-11-03",
            "activity-1",
            Instant.parse("2026-11-02T10:00:00Z"),
            1_800,
            5_000.0,
        )
        executions.recordManualExecution("2026-11-02-2026-11-02", durationMin = 35, rpe = 6)

        val outcome = executions.replaceManualWithActivity(
            plannedSessionId = "2026-11-02-2026-11-02",
            activityId = "activity-1",
            startedAt = Instant.parse("2026-11-02T10:00:00Z"),
            durationSec = 1_800,
            distanceM = 5_000.0,
        )

        assertThat(outcome).isInstanceOf(CompletionOutcome.ActivityAlreadyLinked::class.java)
        val session = plans.getWeek(monday)?.sessions?.first()
        assertThat(session?.completionEvidence).isEqualTo(CompletionEvidence.MANUAL)
        assertThat(session?.activityId).isNull()
    }

    @Test
    fun `an activity cannot be linked to two sessions`(): Unit = runBlocking {
        plans.saveWeek(plan())
        activities.saveImportedActivity(activity())
        executions.recordActivityExecution(
            "2026-11-02-2026-11-02",
            "activity-1",
            Instant.parse("2026-11-02T10:00:00Z"),
            1_800,
            5_000.0,
        )

        val second = executions.recordActivityExecution(
            "2026-11-02-2026-11-03",
            "activity-1",
            Instant.parse("2026-11-02T10:00:00Z"),
            1_800,
            5_000.0,
        )

        assertThat(second).isInstanceOf(CompletionOutcome.ActivityAlreadyLinked::class.java)
        assertThat(plans.getWeek(monday)?.sessions?.last()?.status).isEqualTo(SessionStatus.PLANNED)
    }

    @Test
    fun `an activity linked to a race cannot complete a session`(): Unit = runBlocking {
        plans.saveWeek(plan())
        activities.saveImportedActivity(activity())
        val races = RaceEventRepository(database, clock)
        val race = races.create(NewRaceEvent(RaceDistance.TEN_K, monday)) as RaceEventOutcome.Saved
        races.complete(race.event.id, "activity-1", RaceCompletionSource.AUTO_IMPORT)

        val outcome = executions.recordActivityExecution(
            "2026-11-02-2026-11-02",
            "activity-1",
            Instant.parse("2026-11-02T10:00:00Z"),
            1_800,
            5_000.0,
        )

        assertThat(outcome).isInstanceOf(CompletionOutcome.ActivityAlreadyLinked::class.java)
        assertThat(plans.getWeek(monday)?.sessions?.first()?.status).isEqualTo(SessionStatus.PLANNED)
    }

    @Test
    fun `a race linked activity cannot replace a manual completion`(): Unit = runBlocking {
        plans.saveWeek(plan())
        activities.saveImportedActivity(activity())
        executions.recordManualExecution("2026-11-02-2026-11-02", durationMin = 40, rpe = 5)
        val races = RaceEventRepository(database, clock)
        val race = races.create(NewRaceEvent(RaceDistance.TEN_K, monday)) as RaceEventOutcome.Saved
        races.complete(race.event.id, "activity-1", RaceCompletionSource.AUTO_IMPORT)

        val outcome = executions.replaceManualWithActivity(
            "2026-11-02-2026-11-02",
            "activity-1",
            Instant.parse("2026-11-02T10:00:00Z"),
            1_800,
            5_000.0,
        )

        assertThat(outcome).isInstanceOf(CompletionOutcome.ActivityAlreadyLinked::class.java)
        val session = plans.getWeek(monday)?.sessions?.first()
        assertThat(session?.completionEvidence).isEqualTo(CompletionEvidence.MANUAL)
        assertThat(session?.activityId).isNull()
    }

    @Test
    fun `a session cannot be completed twice`(): Unit = runBlocking {
        plans.saveWeek(plan())
        activities.saveImportedActivity(activity())
        executions.recordActivityExecution(
            "2026-11-02-2026-11-02",
            "activity-1",
            Instant.parse("2026-11-02T10:00:00Z"),
            1_800,
            5_000.0,
        )

        val second = executions.recordManualExecution("2026-11-02-2026-11-02", durationMin = 40, rpe = 5)

        assertThat(second).isInstanceOf(CompletionOutcome.AlreadyCompleted::class.java)
    }

    @Test
    fun `closes a planned session with its reason`(): Unit = runBlocking {
        plans.saveWeek(plan())

        val closed = executions.closeSession(
            "2026-11-02-2026-11-02",
            SessionClosureReason.USER_SKIPPED,
            note = "dor no joelho",
        )

        assertThat(closed).isTrue()
        val session = plans.getWeek(monday)?.sessions?.first()
        assertThat(session?.status).isEqualTo(SessionStatus.CLOSED)
        assertThat(session?.closureReason).isEqualTo(SessionClosureReason.USER_SKIPPED)
        assertThat(session?.skipReason).isEqualTo("dor no joelho")
        assertThat(session?.closedAt).isEqualTo(clock.instant())
        assertThat(executions.recordManualExecution("2026-11-02-2026-11-02", 30, 5))
            .isInstanceOf(CompletionOutcome.AlreadyCompleted::class.java)
    }

    @Test
    fun `trash suspends evidence, restore returns it and purge removes the link`(): Unit = runBlocking {
        plans.saveWeek(plan())
        activities.saveImportedActivity(activity())
        executions.recordActivityExecution(
            "2026-11-02-2026-11-02",
            "activity-1",
            Instant.parse("2026-11-02T10:00:00Z"),
            1_800,
            5_000.0,
        )

        activities.deleteActivity("activity-1")
        val suspended = plans.getWeek(monday)?.sessions?.first()
        assertThat(suspended?.completionEvidence).isEqualTo(CompletionEvidence.LEGACY_UNVERIFIED)
        assertThat(suspended?.activityId).isEqualTo("activity-1")

        activities.restoreActivity("activity-1")
        val restored = plans.getWeek(monday)?.sessions?.first()
        assertThat(restored?.completionEvidence).isEqualTo(CompletionEvidence.ACTIVITY)

        activities.deleteActivity("activity-1")
        activities.purgeActivity("activity-1")
        val purged = plans.getWeek(monday)?.sessions?.first()
        assertThat(purged?.status).isEqualTo(SessionStatus.DONE)
        assertThat(purged?.completionEvidence).isEqualTo(CompletionEvidence.LEGACY_UNVERIFIED)
        assertThat(purged?.activityId).isNull()
        assertThat(purged?.executionId).isNull()
        assertThat(database.sessionExecutionDao().getAll()).isEmpty()
    }

    private fun plan() = WeeklyPlan(
        weekStart = monday,
        sessions = listOf(
            session(monday),
            session(monday.plusDays(1)),
        ),
        weeklyMinutes = 60,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Plano.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun session(date: LocalDate) = PlannedSession(
        date = date,
        type = WorkoutType.EASY,
        title = "Fácil",
        durationMin = 30,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun activity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-11-02T10:00:00Z"),
        durationSec = 1_800,
        distanceM = 5_000.0,
        averagePace = Pace.fromSecondsPerKm(360),
    )
}
