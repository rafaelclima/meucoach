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
import com.meucoach.core.model.DataConfidence
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
import java.time.temporal.TemporalAdjusters

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepairLegacyCompletionTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.of(2026, 11, 6)
    private val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: MeuCoachDatabase
    private lateinit var activities: ActivityRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var executions: SessionExecutionRepository
    private lateinit var repair: RepairLegacyCompletion

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
        repair = RepairLegacyCompletion(plans, activities, executions)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `repairs a same-day legacy completion after confirmation`(): Unit = runBlocking {
        plans.saveWeek(legacyPlan(today))
        val id = saveRun(today)

        val result = repair(request(id, confirmed = true))

        assertThat(result).isInstanceOf(RepairLegacyCompletionResult.Repaired::class.java)
        val session = plans.getWeek(weekStart())!!.sessions.single()
        assertThat(session.status).isEqualTo(SessionStatus.DONE)
        assertThat(session.completionEvidence).isEqualTo(CompletionEvidence.ACTIVITY)
        assertThat(session.activityId).isEqualTo(id)
        assertThat(session.executionId).isNotNull()
        val execution = executions.getForActivity(id)!!
        assertThat(execution.source.name).isEqualTo("ACTIVITY")
        assertThat(execution.distanceM).isEqualTo(6_000.0)
        assertThat(executions.getForSession(sessionId())?.id).isEqualTo(execution.id)
    }

    @Test
    fun `repairing twice does not duplicate the execution or the link`(): Unit = runBlocking {
        plans.saveWeek(legacyPlan(today))
        val id = saveRun(today)
        repair(request(id, confirmed = true))

        val second = repair(request(id, confirmed = true))

        assertThat(second).isInstanceOf(RepairLegacyCompletionResult.Rejected::class.java)
        assertThat(executions.listAll()).hasSize(1)
        assertThat(plans.getWeek(weekStart())!!.sessions.single().activityId).isEqualTo(id)
    }

    @Test
    fun `rejects a legacy repair without confirmation`(): Unit = runBlocking {
        plans.saveWeek(legacyPlan(today))
        val id = saveRun(today)

        val result = repair(request(id, confirmed = false))

        assertThat(result).isInstanceOf(RepairLegacyCompletionResult.Rejected::class.java)
        val session = plans.getWeek(weekStart())!!.sessions.single()
        assertThat(session.completionEvidence).isEqualTo(CompletionEvidence.LEGACY_UNVERIFIED)
        assertThat(session.activityId).isNull()
        assertThat(session.executionId).isNull()
        assertThat(executions.getForActivity(id)).isNull()
    }

    @Test
    fun `rejects migrated history from another day`(): Unit = runBlocking {
        val oldDate = today.minusDays(3)
        plans.saveWeek(legacyPlan(oldDate))
        val id = saveRun(oldDate)

        val result = repair(
            RepairLegacyCompletionRequest(
                date = oldDate,
                today = today,
                activityId = id,
                confirmed = true,
                zoneId = zone,
            ),
        )

        assertThat(result).isInstanceOf(RepairLegacyCompletionResult.Rejected::class.java)
        assertThat((result as RepairLegacyCompletionResult.Rejected).message)
            .contains("histórico migrado")
        val session = plans.getWeek(weekStart(oldDate))!!.sessions.single()
        assertThat(session.completionEvidence).isEqualTo(CompletionEvidence.LEGACY_UNVERIFIED)
        assertThat(session.activityId).isNull()
        assertThat(executions.getForActivity(id)).isNull()
    }

    @Test
    fun `rejects an activity from another day`(): Unit = runBlocking {
        plans.saveWeek(legacyPlan(today))
        val id = saveRun(today.minusDays(1))

        val result = repair(request(id, confirmed = true))

        assertThat(result).isInstanceOf(RepairLegacyCompletionResult.Rejected::class.java)
        assertThat((result as RepairLegacyCompletionResult.Rejected).message).contains("outro dia")
        assertThat(plans.getWeek(weekStart())!!.sessions.single().completionEvidence)
            .isEqualTo(CompletionEvidence.LEGACY_UNVERIFIED)
    }

    @Test
    fun `rejects an activity that is not a run`(): Unit = runBlocking {
        plans.saveWeek(legacyPlan(today))
        val saved = activities.saveImportedActivity(
            ImportedActivity(
                source = ActivitySource.FIT,
                sport = Sport.WALKING,
                startTime = today.atTime(7, 0).atZone(zone).toInstant(),
                durationSec = 2_400,
                distanceM = 20_000.0,
            ),
        )
        val id = (saved as SaveActivityResult.Saved).id

        val result = repair(request(id, confirmed = true))

        assertThat(result).isInstanceOf(RepairLegacyCompletionResult.Rejected::class.java)
        assertThat((result as RepairLegacyCompletionResult.Rejected).message).contains("Só corridas")
    }

    @Test
    fun `rejects a completion that is not legacy`(): Unit = runBlocking {
        plans.saveWeek(legacyPlan(today, evidence = CompletionEvidence.MANUAL))
        val id = saveRun(today)

        val result = repair(request(id, confirmed = true))

        assertThat(result).isInstanceOf(RepairLegacyCompletionResult.Rejected::class.java)
        assertThat(plans.getWeek(weekStart())!!.sessions.single().completionEvidence)
            .isEqualTo(CompletionEvidence.MANUAL)
    }

    private fun request(activityId: String, confirmed: Boolean) = RepairLegacyCompletionRequest(
        date = today,
        today = today,
        activityId = activityId,
        confirmed = confirmed,
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

    private fun legacyPlan(date: LocalDate, evidence: CompletionEvidence = CompletionEvidence.LEGACY_UNVERIFIED) =
        WeeklyPlan(
            weekStart = weekStart(date),
            sessions = listOf(
                PlannedSession(
                    date = date,
                    type = WorkoutType.LONG,
                    title = "Longão",
                    durationMin = 60,
                    rationale = "Base.",
                    ruleIds = listOf("R-PLAN-01"),
                    evidenceIds = listOf("EV-SEILER-2010"),
                    status = SessionStatus.DONE,
                    completionEvidence = evidence,
                ),
            ),
            weeklyMinutes = 60,
            longRunMinutes = 60,
            qualityCount = 0,
            rationale = "Sem progressão.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-PLAN-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )

    private fun sessionId(): String = plannedSessionId(weekStart(), today)

    private fun weekStart(date: LocalDate = today): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
}
