package com.meucoach.app.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionClosureReason
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompleteSessionTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var plans: TrainingPlanRepository
    private lateinit var activities: ActivityRepository
    private lateinit var useCase: CompleteSession
    private val clock = Clock.fixed(Instant.parse("2026-11-04T13:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 4)
    private val monday = LocalDate.of(2026, 11, 2)
    private val zone = ZoneId.of("America/Sao_Paulo")

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
            zoneId = zone,
            idGenerator = { "activity-1" },
        )
        val executions = SessionExecutionRepository(database, clock)
        useCase = CompleteSession(plans, activities, executions)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a done session can only be created with evidence`(): Unit = runBlocking {
        plans.saveWeek(plan())

        val guided = useCase(request(evidence = CompletionEvidence.GUIDED))
        val legacy = useCase(request(evidence = CompletionEvidence.LEGACY_UNVERIFIED))

        assertThat(guided).isInstanceOf(CompleteSessionResult.Rejected::class.java)
        assertThat(legacy).isInstanceOf(CompleteSessionResult.Rejected::class.java)
        assertThat(session(today).status).isEqualTo(SessionStatus.PLANNED)
    }

    @Test
    fun `links an imported run and marks the session verified`(): Unit = runBlocking {
        plans.saveWeek(plan())
        activities.saveImportedActivity(activity())

        val result = useCase(
            request(
                evidence = CompletionEvidence.ACTIVITY,
                activityId = "activity-1",
            ),
        )

        assertThat(result).isInstanceOf(CompleteSessionResult.Completed::class.java)
        val stored = session(today)
        assertThat(stored.status).isEqualTo(SessionStatus.DONE)
        assertThat(stored.completionEvidence).isEqualTo(CompletionEvidence.ACTIVITY)
        assertThat(stored.activityId).isEqualTo("activity-1")
        assertThat(stored.executionId).isNotNull()
    }

    @Test
    fun `manual completion requires duration and RPE`(): Unit = runBlocking {
        plans.saveWeek(plan())

        val invalid = useCase(
            request(evidence = CompletionEvidence.MANUAL, durationMin = 0, rpe = 11),
        )
        assertThat(invalid).isInstanceOf(CompleteSessionResult.Rejected::class.java)
        assertThat(session(today).status).isEqualTo(SessionStatus.PLANNED)

        val valid = useCase(
            request(evidence = CompletionEvidence.MANUAL, durationMin = 30, rpe = 6),
        )
        assertThat(valid).isInstanceOf(CompleteSessionResult.Completed::class.java)
        assertThat((valid as CompleteSessionResult.Completed).warning)
            .contains("não entra em volume")
        assertThat(session(today).completionEvidence).isEqualTo(CompletionEvidence.MANUAL)
        assertThat(session(today).activityId).isNull()
    }

    @Test
    fun `rest or illness closes the day and blocks completion`(): Unit = runBlocking {
        plans.saveWeek(plan())

        val rest = useCase(
            request(
                evidence = CompletionEvidence.MANUAL,
                durationMin = 30,
                rpe = 5,
                prescription = restPrescription(),
            ),
        )

        assertThat(rest).isInstanceOf(CompleteSessionResult.Closed::class.java)
        assertThat((rest as CompleteSessionResult.Closed).reason)
            .isEqualTo(SessionClosureReason.REST_PRESCRIBED)
        val stored = session(today)
        assertThat(stored.status).isEqualTo(SessionStatus.CLOSED)
        assertThat(stored.closureReason).isEqualTo(SessionClosureReason.REST_PRESCRIBED)
    }

    @Test
    fun `illness closes the day with its own reason`(): Unit = runBlocking {
        plans.saveWeek(plan())

        val result = useCase(
            request(
                evidence = CompletionEvidence.MANUAL,
                durationMin = 30,
                rpe = 5,
                illnessOrInjury = true,
            ),
        )

        assertThat(result).isInstanceOf(CompleteSessionResult.Closed::class.java)
        assertThat((result as CompleteSessionResult.Closed).reason)
            .isEqualTo(SessionClosureReason.ILLNESS_OR_INJURY)
        assertThat(session(today).closureReason).isEqualTo(SessionClosureReason.ILLNESS_OR_INJURY)
    }

    @Test
    fun `future sessions are blocked and yesterday is allowed with a warning`(): Unit = runBlocking {
        val yesterday = today.minusDays(1)
        val tomorrow = today.plusDays(1)
        plans.saveWeek(
            plan().copy(sessions = listOf(session(yesterday), session(today), session(tomorrow))),
        )

        val future = useCase(
            request(
                evidence = CompletionEvidence.MANUAL,
                durationMin = 30,
                rpe = 5,
                date = tomorrow,
            ),
        )
        assertThat(future).isInstanceOf(CompleteSessionResult.Rejected::class.java)

        val past = useCase(
            request(
                evidence = CompletionEvidence.MANUAL,
                durationMin = 30,
                rpe = 5,
                date = yesterday,
            ),
        )
        assertThat(past).isInstanceOf(CompleteSessionResult.Completed::class.java)
        assertThat((past as CompleteSessionResult.Completed).warning)
            .contains("Concluindo um treino")
    }

    @Test
    fun `an activity already linked to another session is rejected`(): Unit = runBlocking {
        plans.saveWeek(
            plan().copy(sessions = listOf(session(today), session(today.plusDays(1)))),
        )
        activities.saveImportedActivity(activity())
        useCase(request(evidence = CompletionEvidence.ACTIVITY, activityId = "activity-1"))

        val second = useCase(
            request(
                evidence = CompletionEvidence.ACTIVITY,
                activityId = "activity-1",
            ),
        )

        assertThat(second).isInstanceOf(CompleteSessionResult.Rejected::class.java)
    }

    private fun request(
        evidence: CompletionEvidence,
        activityId: String? = null,
        durationMin: Int? = null,
        rpe: Int? = null,
        date: LocalDate = today,
        prescription: DailyPrescription? = null,
        illnessOrInjury: Boolean = false,
    ) = CompleteSessionRequest(
        date = date,
        today = today,
        evidence = evidence,
        activityId = activityId,
        durationMin = durationMin,
        rpe = rpe,
        prescription = prescription,
        illnessOrInjury = illnessOrInjury,
        zoneId = zone,
    )

    private suspend fun session(date: LocalDate) =
        plans.getWeek(date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)))
            ?.sessions
            ?.first { it.date == date }
            ?: PlannedSession(
                date = date,
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            )

    private suspend fun plan() = WeeklyPlan(
        weekStart = monday,
        sessions = listOf(session(today)),
        weeklyMinutes = 30,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Plano.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun restPrescription() = DailyPrescription(
        date = today,
        plannedSessionId = "2026-11-02-2026-11-04",
        plannedType = WorkoutType.EASY,
        plannedTitle = "Fácil",
        plannedDurationMin = 30,
        prescribedType = WorkoutType.REST,
        title = "Descanso",
        prescribedDurationMin = null,
        targets = PrescriptionTargets(),
        adjustment = PrescriptionAdjustment.REST,
        rationale = "Prontidão baixa.",
        ruleIds = listOf("R-READY-03"),
        evidenceIds = listOf("EV-SAW-2016"),
        confidence = DataConfidence.HIGH,
        engineVersion = "test",
        inputsHash = "hash",
    )

    private fun activity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-11-04T10:00:00Z"),
        durationSec = 1_800,
        distanceM = 5_000.0,
        averagePace = Pace.fromSecondsPerKm(360),
    )
}
