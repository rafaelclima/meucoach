package com.meucoach.app.ui.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.CompleteSession
import com.meucoach.app.application.ReplaceManualCompletion
import com.meucoach.app.importer.ImportedRunPromptStore
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.RaceEventRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportedRunPromptViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.of(2026, 11, 6)
    private val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
    private val timeProvider = FakeTimeProvider(
        instant = today.atTime(12, 0).atZone(zone).toInstant(),
        zone = zone,
    )
    private lateinit var database: MeuCoachDatabase
    private lateinit var activities: ActivityRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var prescriptions: DailyPrescriptionRepository
    private lateinit var executions: SessionExecutionRepository
    private lateinit var store: ImportedRunPromptStore
    private lateinit var viewModel: ImportedRunPromptViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
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
        checkIns = CheckInRepository(database, clock)
        prescriptions = DailyPrescriptionRepository(database, clock)
        executions = SessionExecutionRepository(database, clock)
        store = ImportedRunPromptStore(context, "prompt-vm-${System.nanoTime()}")
        viewModel = ImportedRunPromptViewModel(
            activities = activities,
            plans = plans,
            prescriptions = prescriptions,
            checkIns = checkIns,
            executions = executions,
            completeSession = CompleteSession(plans, activities, executions, RaceEventRepository(database, clock)),
            replaceManualCompletion = ReplaceManualCompletion(plans, activities, executions),
            store = store,
            timeProvider = timeProvider,
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `offers to link a run imported today while the session is planned`(): Unit = runBlocking {
        plans.saveWeek(plan())
        val id = saveRun()
        store.add(id)

        val state = withTimeout(TIMEOUT_MILLIS) {
            viewModel.uiState.first { it.prompt is ImportedRunPromptUi.Offer }
        }

        val offer = state.prompt as ImportedRunPromptUi.Offer
        assertThat(offer.activityId).isEqualTo(id)
        assertThat(offer.sessionTitle).isEqualTo("Fácil e conversável")
        assertThat(offer.sessionDurationMin).isEqualTo(40)
    }

    @Test
    fun `warns instead of offering when today is already done`(): Unit = runBlocking {
        plans.saveWeek(donePlan(evidence = CompletionEvidence.MANUAL))
        val id = saveRun()
        store.add(id)

        val state = withTimeout(TIMEOUT_MILLIS) {
            viewModel.uiState.first { it.prompt is ImportedRunPromptUi.AlreadyDone }
        }

        val prompt = state.prompt as ImportedRunPromptUi.AlreadyDone
        assertThat(prompt.evidenceLabel).isEqualTo("sem registro de corrida")
    }

    @Test
    fun `linking turns the run into today's verified session`(): Unit = runBlocking {
        plans.saveWeek(plan())
        val id = saveRun()
        store.add(id)
        withTimeout(TIMEOUT_MILLIS) { viewModel.uiState.first { it.prompt is ImportedRunPromptUi.Offer } }

        viewModel.link()

        val state = withTimeout(TIMEOUT_MILLIS) {
            viewModel.uiState.first { it.prompt == null && it.message != null }
        }
        val session = plans.getWeek(weekStart())!!.sessions.single()
        assertThat(session.status).isEqualTo(SessionStatus.DONE)
        assertThat(session.completionEvidence).isEqualTo(CompletionEvidence.ACTIVITY)
        assertThat(session.activityId).isEqualTo(id)
        assertThat(state.message).contains("vinculada")
        assertThat(store.pending.value).isEmpty()
    }

    @Test
    fun `replaces an accidental manual record with the imported run`(): Unit = runBlocking {
        plans.saveWeek(plan())
        executions.recordManualExecution(
            plannedSessionId = com.meucoach.core.data.db.entity.plannedSessionId(weekStart(), today),
            durationMin = 40,
            rpe = 6,
        )
        val id = saveRun()
        store.add(id)
        val prompt = withTimeout(TIMEOUT_MILLIS) {
            viewModel.uiState.first { it.prompt is ImportedRunPromptUi.AlreadyDone }
        }.prompt as ImportedRunPromptUi.AlreadyDone
        assertThat(prompt.canReplace).isTrue()

        viewModel.replaceManualRecord()

        val state = withTimeout(TIMEOUT_MILLIS) {
            viewModel.uiState.first { it.prompt == null && it.message != null }
        }
        val session = plans.getWeek(weekStart())!!.sessions.single()
        assertThat(session.status).isEqualTo(SessionStatus.DONE)
        assertThat(session.completionEvidence).isEqualTo(CompletionEvidence.ACTIVITY)
        assertThat(session.activityId).isEqualTo(id)
        val execution = executions.getForActivity(id)!!
        assertThat(execution.source.name).isEqualTo("ACTIVITY")
        assertThat(execution.rpe).isNull()
        assertThat(state.message).contains("substituído")
        assertThat(store.pending.value).isEmpty()
    }

    @Test
    fun `keeping the run clears the prompt without changing the session`(): Unit = runBlocking {
        plans.saveWeek(plan())
        val id = saveRun()
        store.add(id)
        withTimeout(TIMEOUT_MILLIS) { viewModel.uiState.first { it.prompt is ImportedRunPromptUi.Offer } }

        viewModel.keepAsFreeRun()

        val state = withTimeout(TIMEOUT_MILLIS) { viewModel.uiState.first { it.prompt == null } }
        assertThat(state.message).isNull()
        assertThat(store.pending.value).isEmpty()
        assertThat(plans.getWeek(weekStart())!!.sessions.single().status).isEqualTo(SessionStatus.PLANNED)
    }

    private suspend fun saveRun(): String {
        val saved = activities.saveImportedActivity(
            ImportedActivity(
                source = ActivitySource.FIT,
                sport = Sport.RUNNING,
                startTime = today.atTime(7, 0).atZone(zone).toInstant(),
                durationSec = 2_400,
                distanceM = 6_000.0,
                averagePace = Pace.fromSecondsPerKm(400),
            ),
        )
        return (saved as SaveActivityResult.Saved).id
    }

    private fun plan(): WeeklyPlan = planWithSession(
        PlannedSession(
            date = today,
            type = WorkoutType.EASY,
            title = "Fácil e conversável",
            durationMin = 40,
            rationale = "Base.",
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        ),
    )

    private fun donePlan(evidence: CompletionEvidence): WeeklyPlan = planWithSession(
        PlannedSession(
            date = today,
            type = WorkoutType.EASY,
            title = "Fácil e conversável",
            durationMin = 40,
            rationale = "Base.",
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            status = SessionStatus.DONE,
            completionEvidence = evidence,
        ),
    )

    private fun planWithSession(session: PlannedSession): WeeklyPlan {
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = listOf(session),
            weeklyMinutes = 40,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Sem progressão.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )
    }

    private fun weekStart(): LocalDate = today.with(
        java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
    )

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
