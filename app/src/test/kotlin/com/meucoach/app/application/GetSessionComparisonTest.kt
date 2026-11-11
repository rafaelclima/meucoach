package com.meucoach.app.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CompletionOutcome
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.feedback.ComparisonVerdict
import com.meucoach.core.domain.feedback.SessionComparisonKind
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.HeartRateTarget
import com.meucoach.core.domain.prescription.PaceTarget
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GetSessionComparisonTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var activities: ActivityRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var executions: SessionExecutionRepository
    private lateinit var prescriptions: DailyPrescriptionRepository
    private lateinit var comparison: GetSessionComparison

    private val clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC)
    private val date = LocalDate.parse("2026-10-24")

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
            zoneId = ZoneId.of("America/Sao_Paulo"),
            idGenerator = { "activity-1" },
        )
        plans = TrainingPlanRepository(database = database, clock = clock)
        executions = SessionExecutionRepository(
            database = database,
            clock = clock,
            idGenerator = { "execution-1" },
        )
        prescriptions = DailyPrescriptionRepository(
            database = database,
            clock = clock,
            idGenerator = { "prescription-1" },
        )
        comparison = GetSessionComparison(activities, executions, prescriptions, plans)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `prefers the prescription of the day over the week session`() {
        runBlocking {
            saveActivity()
            savePlan(status = SessionStatus.DONE, activityId = "activity-1", title = "Fácil 40 min", duration = 40)
            prescriptions.append(prescription(title = "Fácil reduzido", durationMin = 30))

            val result = comparison.forActivity(activity())!!

            assertThat(result.kind).isEqualTo(SessionComparisonKind.LINKED)
            assertThat(result.plan?.title).isEqualTo("Fácil reduzido")
            assertThat(result.durationPercentOfPlan).isEqualTo(100)
            assertThat(result.distanceVerdict).isEqualTo(ComparisonVerdict.BELOW)
            assertThat(result.paceVerdict).isEqualTo(ComparisonVerdict.WITHIN)
            assertThat(result.heartRateVerdict).isEqualTo(ComparisonVerdict.WITHIN)
            assertThat(result.confidence).isEqualTo(DataConfidence.HIGH)
        }
    }

    @Test
    fun `uses the week session when the prescription was never persisted`() {
        runBlocking {
            saveActivity()
            savePlan(status = SessionStatus.DONE, activityId = "activity-1", title = "Fácil 40 min", duration = 40)

            val result = comparison.forActivity(activity())!!

            assertThat(result.kind).isEqualTo(SessionComparisonKind.LINKED)
            assertThat(result.plan?.title).isEqualTo("Fácil 40 min")
            assertThat(result.durationPercentOfPlan).isEqualTo(75)
        }
    }

    @Test
    fun `links the activity through the recorded execution`() {
        runBlocking {
            saveActivity()
            savePlan(status = SessionStatus.PLANNED, activityId = null, title = "Fácil 40 min", duration = 40)
            val recorded = executions.recordActivityExecution(
                plannedSessionId = "2026-10-19-2026-10-24",
                activityId = "activity-1",
                startedAt = Instant.parse("2026-10-24T08:17:10Z"),
                durationSec = 1_800,
                distanceM = 5_000.0,
            )
            assertThat(recorded).isInstanceOf(CompletionOutcome.Recorded::class.java)

            val result = comparison.forActivity(activity())!!

            assertThat(result.kind).isEqualTo(SessionComparisonKind.LINKED)
            assertThat(result.plan?.title).isEqualTo("Fácil 40 min")
        }
    }

    @Test
    fun `never compares a run that is not the evidence of the day`() {
        runBlocking {
            saveActivity()
            savePlan(status = SessionStatus.PLANNED, activityId = null, title = "Fácil 40 min", duration = 40)

            val result = comparison.forActivity(activity())!!

            assertThat(result.kind).isEqualTo(SessionComparisonKind.FREE_RUN)
            assertThat(result.plan).isNull()
            assertThat(result.durationVerdict).isNull()
        }
    }

    @Test
    fun `reports a run without any plan as free run`() {
        runBlocking {
            saveActivity()

            val result = comparison.forActivity(activity())!!

            assertThat(result.kind).isEqualTo(SessionComparisonKind.FREE_RUN)
            assertThat(result.actualDurationSec).isEqualTo(1_800)
            assertThat(result.actualDistanceM).isEqualTo(5_000.0)
        }
    }

    @Test
    fun `does not fall back to the session when the day was prescribed as rest`() {
        runBlocking {
            saveActivity()
            savePlan(status = SessionStatus.DONE, activityId = "activity-1", title = "Fácil 40 min", duration = 40)
            prescriptions.append(prescription(type = WorkoutType.REST, title = "Descanso", durationMin = null))

            val result = comparison.forActivity(activity())!!

            assertThat(result.kind).isEqualTo(SessionComparisonKind.FREE_RUN)
            assertThat(result.plan).isNull()
        }
    }

    private suspend fun saveActivity() {
        activities.saveImportedActivity(importedActivity(), "sha")
    }

    private suspend fun activity() = checkNotNull(activities.openActivity("activity-1"))

    private suspend fun savePlan(status: SessionStatus, activityId: String?, title: String, duration: Int) {
        plans.saveWeek(
            WeeklyPlan(
                weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                sessions = listOf(
                    PlannedSession(
                        date = date,
                        type = WorkoutType.EASY,
                        title = title,
                        durationMin = duration,
                        rationale = "Teste determinístico.",
                        ruleIds = listOf("R-ZONE-01"),
                        evidenceIds = listOf("EV-FRIEL-2018"),
                        targets = targets(),
                        status = status,
                        completionEvidence = if (status == SessionStatus.DONE) CompletionEvidence.ACTIVITY else null,
                        activityId = activityId,
                    ),
                ),
                weeklyMinutes = duration,
                longRunMinutes = null,
                qualityCount = 0,
                rationale = "Semana de teste.",
                confidence = DataConfidence.HIGH,
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-FRIEL-2018"),
            ),
        )
    }

    private fun prescription(
        title: String = "Fácil reduzido",
        durationMin: Int? = 30,
        type: WorkoutType = WorkoutType.EASY,
    ) = DailyPrescription(
        date = date,
        plannedSessionId = "2026-10-19-2026-10-24",
        plannedType = WorkoutType.EASY,
        plannedTitle = "Fácil 40 min",
        plannedDurationMin = 40,
        prescribedType = type,
        title = title,
        prescribedDurationMin = durationMin,
        targets = if (type == WorkoutType.REST) PrescriptionTargets() else targets(),
        adjustment = if (type == WorkoutType.REST) PrescriptionAdjustment.REST else PrescriptionAdjustment.REDUCE,
        rationale = "Plano reduzido.",
        ruleIds = listOf("R-PRESC-02"),
        evidenceIds = listOf("EV-FRIEL-2018"),
        confidence = DataConfidence.HIGH,
        engineVersion = "daily-prescription-v2",
        inputsHash = "hash",
    )

    private fun targets() = PrescriptionTargets(
        pace = PaceTarget(fast = Pace.fromSecondsPerKm(350), slow = Pace.fromSecondsPerKm(390)),
        heartRate = HeartRateTarget(140, 155),
        zone = "Z2",
        distanceKm = 6.5,
    )

    private fun importedActivity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-10-24T08:17:10Z"),
        durationSec = 1_800,
        distanceM = 5_000.0,
        averageHeartRate = 150,
        maxHeartRate = 170,
        averagePace = Pace.fromSecondsPerKm(360),
    )
}
