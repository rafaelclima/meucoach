package com.meucoach.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.health.HealthSyncStore
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.HeatLevel
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import com.meucoach.core.model.Sport
import com.meucoach.core.model.WeeklyAvailability
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
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
class UserDataRepositoryTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var userData: UserDataRepository
    private lateinit var healthStore: HealthSyncStore
    private lateinit var activities: ActivityRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var metrics: DailyMetricsRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var prescriptions: DailyPrescriptionRepository
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
        healthStore = HealthSyncStore(context, prefsName = "health-sync-${System.nanoTime()}")
        userData = UserDataRepository(database, healthStore, clock)
        activities = ActivityRepository(
            database = database,
            clock = clock,
            zoneId = ZoneId.of("America/Sao_Paulo"),
            idGenerator = { "activity-1" },
        )
        profiles = ProfileRepository(database, clock)
        checkIns = CheckInRepository(database, clock)
        metrics = DailyMetricsRepository(database, clock)
        plans = TrainingPlanRepository(database, clock)
        prescriptions = DailyPrescriptionRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `exports every table as json`(): Unit = runBlocking {
        seedEverything()

        val export = JSONObject(userData.exportJson())

        assertThat(export.getInt("schemaVersion")).isEqualTo(MeuCoachDatabase.VERSION)
        assertThat(export.getString("exportedAt")).isEqualTo(clock.instant().toString())
        assertThat(export.getJSONObject("profile").getString("timezone")).isEqualTo("America/Sao_Paulo")
        assertThat(export.getJSONObject("profile").getJSONObject("weeklyAvailability").getInt("MONDAY"))
            .isEqualTo(40)
        assertThat(export.getJSONObject("profile").getJSONObject("goal").getString("type"))
            .isEqualTo("GENERAL_FITNESS")
        assertThat(export.getJSONArray("checkIns").getJSONObject(0).getInt("sleepQuality")).isEqualTo(4)
        assertThat(export.getJSONArray("dailyMetrics").getJSONObject(0).getInt("sleepDurationMin"))
            .isEqualTo(450)
        val plan = export.getJSONArray("weeklyPlans").getJSONObject(0)
        assertThat(plan.getString("weekStart")).isEqualTo(monday.toString())
        assertThat(plan.getJSONArray("ruleIds").getString(0)).isEqualTo("R-PLAN-03")
        assertThat(plan.getJSONArray("sessions").length()).isEqualTo(2)
        val activity = export.getJSONArray("activities").getJSONObject(0)
        assertThat(activity.getDouble("distanceM")).isEqualTo(5_000.0)
        assertThat(activity.getInt("sampleCount")).isEqualTo(0)
        assertThat(export.getJSONArray("importJobs").length()).isEqualTo(1)
        val prescription = export.getJSONArray("dailyPrescriptions").getJSONObject(0)
        assertThat(prescription.getString("date")).isEqualTo(monday.toString())
        assertThat(prescription.getString("adjustment")).isEqualTo("KEEP")
        val execution = export.getJSONArray("sessionExecutions").getJSONObject(0)
        assertThat(execution.getString("plannedSessionId")).isEqualTo("2026-11-02-2026-11-02")
        assertThat(execution.getString("source")).isEqualTo("ACTIVITY")
        val doneSession = plan.getJSONArray("sessions").getJSONObject(0)
        assertThat(doneSession.getString("status")).isEqualTo("DONE")
        assertThat(doneSession.getString("completionEvidence")).isEqualTo("ACTIVITY")
    }

    @Test
    fun `default export excludes the trash and its import jobs`(): Unit = runBlocking {
        seedEverything()
        val saved = database.activityDao().getAll().single()
        activities.deleteActivity(saved.id)

        val default = JSONObject(userData.exportJson())
        val complete = JSONObject(userData.exportJson(includeTrash = true))

        assertThat(default.getJSONArray("activities").length()).isZero()
        assertThat(default.getJSONArray("importJobs").length()).isZero()
        assertThat(complete.getJSONArray("activities").length()).isEqualTo(1)
        assertThat(complete.getJSONArray("importJobs").length()).isEqualTo(1)
    }

    @Test
    fun `deletes activities, profile, check-ins, metrics and plans`(): Unit = runBlocking {
        seedEverything()

        val deletion = userData.deleteAllData()

        assertThat(deletion.activities).isEqualTo(1)
        assertThat(deletion.checkIns).isEqualTo(1)
        assertThat(deletion.metrics).isEqualTo(1)
        assertThat(deletion.plans).isEqualTo(1)
        assertThat(deletion.prescriptions).isEqualTo(1)
        assertThat(deletion.executions).isEqualTo(1)
        assertThat(deletion.profileDeleted).isTrue()
        assertThat(deletion.removedAnything).isTrue()
        assertThat(database.activityDao().getAllIncludingDeleted()).isEmpty()
        assertThat(database.importJobDao().getAll()).isEmpty()
        assertThat(database.profileDao().get()).isNull()
        assertThat(database.dailyCheckInDao().getAll()).isEmpty()
        assertThat(database.dailyMetricsDao().getAll()).isEmpty()
        assertThat(database.trainingPlanDao().getAllPlans()).isEmpty()
        assertThat(database.trainingPlanDao().getAllSessions()).isEmpty()
        assertThat(database.sessionExecutionDao().getAll()).isEmpty()
        assertThat(database.dailyPrescriptionDao().getAll()).isEmpty()
    }

    @Test
    fun `resets the health connect watermark when deleting everything`(): Unit = runBlocking {
        healthStore.markSynced(Instant.parse("2026-11-02T12:00:00Z"), LocalDate.of(2026, 11, 2))
        seedEverything()

        userData.deleteAllData()

        assertThat(healthStore.lastSyncedDate()).isNull()
    }

    @Test
    fun `reports an empty deletion when there is nothing stored`(): Unit = runBlocking {
        val deletion = userData.deleteAllData()

        assertThat(deletion.removedAnything).isFalse()
        assertThat(JSONObject(userData.exportJson()).getJSONArray("activities").length()).isZero()
    }

    private suspend fun seedEverything() {
        profiles.saveProfile(profile())
        checkIns.save(
            DailyCheckIn(
                date = monday,
                sleepQuality = 4,
                fatigue = 2,
                soreness = 2,
                stress = 2,
                heatLevel = HeatLevel.HOT,
            ),
        )
        metrics.saveAll(
            listOf(
                DailyMetrics(
                    date = monday,
                    sleepDurationMin = 450,
                    restingHeartRate = 58.0,
                    hrvRmssdMs = 62.0,
                    spo2Avg = 97.0,
                ),
            ),
        )
        plans.saveWeek(plan())
        prescriptions.append(prescription())
        val saved = activities.saveImportedActivity(activity()) as SaveActivityResult.Saved
        database.importJobDao().insert(com.meucoach.core.data.db.testJob(saved.id))
        SessionExecutionRepository(database, clock).recordActivityExecution(
            plannedSessionId = "2026-11-02-2026-11-02",
            activityId = saved.id,
            startedAt = Instant.parse("2026-11-02T10:00:00Z"),
            durationSec = 1_800,
            distanceM = 5_000.0,
        )
    }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        thresholdPace = Pace.fromSecondsPerKm(360),
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(
                java.time.DayOfWeek.MONDAY to 40,
                java.time.DayOfWeek.WEDNESDAY to 50,
            ),
        ),
        goal = com.meucoach.core.model.TrainingGoal(
            type = com.meucoach.core.model.GoalType.GENERAL_FITNESS,
        ),
    )

    private fun activity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-11-02T10:00:00Z"),
        durationSec = 1_800,
        distanceM = 5_000.0,
        averageHeartRate = 150,
        averagePace = Pace.fromSecondsPerKm(360),
    )

    private fun prescription() = com.meucoach.core.domain.prescription.DailyPrescription(
        date = monday,
        plannedSessionId = "2026-11-02-2026-11-02",
        plannedType = WorkoutType.EASY,
        plannedTitle = "Fácil e conversável",
        plannedDurationMin = 40,
        prescribedType = WorkoutType.EASY,
        title = "Fácil e conversável",
        prescribedDurationMin = 40,
        targets = com.meucoach.core.domain.prescription.PrescriptionTargets(zone = "Z2", rpe = 4),
        adjustment = com.meucoach.core.domain.prescription.PrescriptionAdjustment.KEEP,
        rationale = "Plano mantido.",
        ruleIds = listOf("R-ZONE-01", "R-PRESC-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v1",
        inputsHash = "hash-1",
    )

    private fun plan() = WeeklyPlan(
        weekStart = monday,
        sessions = listOf(
            PlannedSession(
                date = monday,
                type = WorkoutType.EASY,
                title = "Fácil e conversável",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
            PlannedSession(
                date = monday.plusDays(5),
                type = WorkoutType.LONG,
                title = "Longão",
                durationMin = 60,
                rationale = "Longo.",
                ruleIds = listOf("R-PLAN-03"),
                evidenceIds = listOf("EV-PFITZINGER-2008"),
            ),
        ),
        weeklyMinutes = 100,
        longRunMinutes = 60,
        qualityCount = 0,
        rationale = "Sem progressão até existir histórico.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008"),
    )
}
