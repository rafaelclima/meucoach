package com.meucoach.app.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.meucoach.app.application.GetTodayDecision
import com.meucoach.app.application.GetTodayPrescription
import com.meucoach.app.application.RefreshTodayPrescription
import com.meucoach.app.application.TrainingSignalSource
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Sex
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderWorkerTest {
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var checkIns: CheckInRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var raceEvents: RaceEventRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var prescriptions: DailyPrescriptionRepository
    private lateinit var signals: TrainingSignalSource
    private lateinit var notifier: ReminderNotifier
    private val today = LocalDate.of(2026, 11, 4)
    private val weekStart = LocalDate.of(2026, 11, 2)
    private val clock = Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        checkIns = CheckInRepository(database, clock)
        plans = TrainingPlanRepository(database, clock)
        raceEvents = RaceEventRepository(database, clock)
        profiles = ProfileRepository(database, clock)
        prescriptions = DailyPrescriptionRepository(database, clock)
        signals = TrainingSignalSource(
            checkIns = checkIns,
            metrics = DailyMetricsRepository(database, clock),
            activities = ActivityRepository(database = database, clock = clock, zoneId = ZoneOffset.UTC),
            executions = SessionExecutionRepository(database, clock),
        )
        notifier = ReminderNotifier(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the 7am workout is the adjusted prescription, not the weekly plan`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        plans.saveWeek(plan())

        val result = worker().doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = Shadows.shadowOf(manager).allNotifications.single()
        val shadow = Shadows.shadowOf(notification)
        assertThat(shadow.contentTitle.toString()).isEqualTo("Treino de hoje: Base primeiro")
        assertThat(shadow.contentText.toString()).contains("Ajustado para hoje", "Hoje: fácil")
        assertThat(shadow.contentText.toString()).doesNotContain("Treino de hoje: Intervalado")
        assertThat(prescriptions.getLatest(today)?.adjustment?.name).isNotEqualTo("KEEP")
    }

    @Test
    fun `the weekly preview notifies only when next week is already persisted`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val nextMonday = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
        val nextPlan = plan().copy(
            weekStart = nextMonday,
            qualityCount = 1,
            rationale = "Prévia da próxima semana.",
            createdOn = today,
        )
        plans.saveWeek(nextPlan)

        val result = worker(ReminderKind.WEEK_PREVIEW).doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = Shadows.shadowOf(manager).allNotifications.single()
        assertThat(Shadows.shadowOf(notification).contentTitle.toString()).isEqualTo("Próxima semana pronta")
    }

    @Test
    fun `the check-in worker never cancels the workout reminder`(): Unit = runBlocking {
        notifier.notify(
            ReminderMessage(
                id = ReminderContent.WORKOUT_ID,
                kind = ReminderKind.WORKOUT,
                title = "Treino de hoje: Rodagem",
                body = "40 min",
                deepLink = DEEP_LINK_TODAY,
            ),
        )
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )

        val result = worker(ReminderKind.CHECK_IN).doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        val manager = context.getSystemService(NotificationManager::class.java)
        val titles = Shadows.shadowOf(manager).allNotifications.map {
            Shadows.shadowOf(it).contentTitle.toString()
        }
        assertThat(titles).contains("Treino de hoje: Rodagem")
    }

    private fun worker(kind: ReminderKind = ReminderKind.WORKOUT): ReminderWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = if (workerClassName == ReminderWorker::class.java.name) {
                val timeProvider = FakeTimeProvider(clock.instant(), ZoneOffset.UTC)
                val resolver = WorkoutReminderResolver(
                    refresh = RefreshTodayPrescription(
                        profiles = profiles,
                        plans = plans,
                        raceEvents = raceEvents,
                        signals = signals,
                        getTodayDecision = GetTodayDecision(raceEvents, GetTodayPrescription(prescriptions)),
                        time = timeProvider,
                    ),
                )
                ReminderWorker(
                    appContext = appContext,
                    params = workerParameters,
                    checkIns = checkIns,
                    plans = plans,
                    workoutResolver = resolver,
                    timeProvider = timeProvider,
                    notifier = notifier,
                )
            } else {
                null
            }
        }
        val data = Data.Builder()
            .putString(ReminderWorker.KEY_KIND, kind.name)
            .build()
        return TestListenableWorkerBuilder<ReminderWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(data)
            .build()
    }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1990, 1, 1),
        sex = Sex.MALE,
        experience = Experience.BEGINNER,
        timezone = "UTC",
    )

    private fun plan() = WeeklyPlan(
        weekStart = weekStart,
        sessions = listOf(
            PlannedSession(
                date = today,
                type = WorkoutType.QUALITY,
                title = "Intervalado",
                durationMin = 40,
                rationale = "Plano forte.",
                ruleIds = listOf("R-PLAN-02"),
                evidenceIds = listOf("EV-SEILER-2010"),
                targets = PrescriptionTargets(primary = TargetKind.RPE, rpe = 8),
                status = SessionStatus.PLANNED,
            ),
        ),
        weeklyMinutes = 40,
        longRunMinutes = null,
        qualityCount = 1,
        rationale = "Semana de qualidade.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = weekStart,
    )
}
