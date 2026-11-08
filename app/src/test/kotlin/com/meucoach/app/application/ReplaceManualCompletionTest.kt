package com.meucoach.app.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.plannedSessionId
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.SaveActivityResult
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
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
class ReplaceManualCompletionTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.of(2026, 11, 6)
    private val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: MeuCoachDatabase
    private lateinit var activities: ActivityRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var executions: SessionExecutionRepository
    private lateinit var replace: ReplaceManualCompletion

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activities = ActivityRepository(
            database = database,
            clock = clock,
            zoneId = zone,
            idGenerator = { "activity-1" },
        )
        plans = TrainingPlanRepository(database, clock)
        executions = SessionExecutionRepository(database, clock)
        replace = ReplaceManualCompletion(plans, activities, executions)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `replaces a manual completion with the real run`(): Unit = runBlocking {
        plans.saveWeek(plan())
        executions.recordManualExecution(sessionId(), durationMin = 40, rpe = 6)
        val id = saveRun(today)

        val result = replace(request(id))

        assertThat(result).isInstanceOf(ReplaceManualCompletionResult.Replaced::class.java)
        val session = plans.getWeek(weekStart())!!.sessions.single()
        assertThat(session.status).isEqualTo(SessionStatus.DONE)
        assertThat(session.completionEvidence).isEqualTo(CompletionEvidence.ACTIVITY)
        assertThat(session.activityId).isEqualTo(id)
        assertThat(executions.getForActivity(id)?.source?.name).isEqualTo("ACTIVITY")
    }

    @Test
    fun `rejects a session without manual evidence`(): Unit = runBlocking {
        plans.saveWeek(plan())
        val id = saveRun(today)

        val result = replace(request(id))

        assertThat(result).isInstanceOf(ReplaceManualCompletionResult.Rejected::class.java)
        assertThat(plans.getWeek(weekStart())!!.sessions.single().status)
            .isEqualTo(SessionStatus.PLANNED)
    }

    @Test
    fun `rejects an activity from another day`(): Unit = runBlocking {
        plans.saveWeek(plan())
        executions.recordManualExecution(sessionId(), durationMin = 40, rpe = 6)
        val id = saveRun(today.minusDays(1))

        val result = replace(request(id))

        assertThat(result).isInstanceOf(ReplaceManualCompletionResult.Rejected::class.java)
        assertThat(plans.getWeek(weekStart())!!.sessions.single().completionEvidence)
            .isEqualTo(CompletionEvidence.MANUAL)
    }

    private fun request(activityId: String) = ReplaceManualCompletionRequest(
        date = today,
        today = today,
        activityId = activityId,
        zoneId = zone,
    )

    private suspend fun saveRun(date: LocalDate): String {
        val saved = activities.saveImportedActivity(
            ImportedActivity(
                source = ActivitySource.FIT,
                sport = Sport.RUNNING,
                startTime = date.atTime(7, 0).atZone(zone).toInstant(),
                durationSec = 2_400,
                distanceM = 6_000.0,
                averagePace = Pace.fromSecondsPerKm(400),
            ),
        )
        return (saved as SaveActivityResult.Saved).id
    }

    private fun plan(): WeeklyPlan = WeeklyPlan(
        weekStart = weekStart(),
        sessions = listOf(
            PlannedSession(
                date = today,
                type = WorkoutType.EASY,
                title = "Fácil e conversável",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
        ),
        weeklyMinutes = 40,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Sem progressão.",
        confidence = com.meucoach.core.model.DataConfidence.MEDIUM,
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun sessionId(): String = plannedSessionId(weekStart(), today)

    private fun weekStart(): LocalDate = today.with(
        java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
    )
}
