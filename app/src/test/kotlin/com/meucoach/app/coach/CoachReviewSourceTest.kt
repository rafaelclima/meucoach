package com.meucoach.app.coach

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.TrainingSignalSource
import com.meucoach.app.application.WeeklyReviewAssembler
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
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import com.meucoach.core.model.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
class CoachReviewSourceTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private lateinit var database: MeuCoachDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var activities: ActivityRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var source: CoachReviewSource

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC)
        profiles = ProfileRepository(database, clock)
        plans = TrainingPlanRepository(database, clock)
        val raceEvents = RaceEventRepository(database, clock)
        val prescriptions = DailyPrescriptionRepository(database, clock)
        val checkIns = CheckInRepository(database, clock)
        val metrics = DailyMetricsRepository(database, clock)
        val executions = SessionExecutionRepository(database, clock)
        activities = ActivityRepository(
            database = database,
            clock = clock,
            zoneId = zone,
            idGenerator = { "activity-${System.nanoTime()}" },
        )
        val signals = TrainingSignalSource(checkIns, metrics, activities, executions)
        source = RoomCoachReviewSource(
            profiles = profiles,
            activities = activities,
            plans = plans,
            raceEvents = raceEvents,
            assembler = WeeklyReviewAssembler(plans, prescriptions, signals),
            time = FakeTimeProvider(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `reconstroi a ultima semana fechada com plano e corrida fora do plano`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        plans.saveWeek(plan())
        activities.saveImportedActivity(activity())

        val review = source.observeLastClosedWeek().first { it != null }!!

        assertThat(review.weekStart).isEqualTo(LocalDate.of(2026, 11, 9))
        assertThat(review.weekEnd).isEqualTo(LocalDate.of(2026, 11, 15))
        assertThat(review.status).isEqualTo(WeeklyReviewStatus.AVAILABLE)
        assertThat(review.adherence?.planned).isEqualTo(2)
        assertThat(review.adherence?.done).isEqualTo(1)
        assertThat(review.volume?.plannedMin).isEqualTo(60)
        assertThat(review.volume?.executedMin).isEqualTo(30)
        assertThat(review.volume?.extraMin).isEqualTo(30)
    }

    @Test
    fun `sem perfil nao ha revisao`(): Unit = runBlocking {
        val review = source.observeLastClosedWeek().first()

        assertThat(review).isNull()
    }

    @Test
    fun `sem plano nem corridas a semana e vazia em vez de zero`(): Unit = runBlocking {
        profiles.saveProfile(profile())

        val review = source.observeLastClosedWeek().first { it != null }!!

        assertThat(review.status).isEqualTo(WeeklyReviewStatus.EMPTY_NO_DATA)
        assertThat(review.adherence).isNull()
        assertThat(review.volume).isNull()
    }

    private fun plan() = WeeklyPlan(
        weekStart = LocalDate.of(2026, 11, 9),
        sessions = listOf(
            PlannedSession(
                date = LocalDate.of(2026, 11, 10),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.LEGACY_UNVERIFIED,
            ),
            PlannedSession(
                date = LocalDate.of(2026, 11, 12),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.PLANNED,
            ),
        ),
        weeklyMinutes = 60,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana base",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = LocalDate.of(2026, 11, 9),
        engineVersion = WeeklyPlanGenerator.ENGINE_VERSION,
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        thresholdPace = Pace.fromSecondsPerKm(360),
        timezone = "America/Sao_Paulo",
    )

    private fun activity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-11-10T10:00:00Z"),
        durationSec = 1_800,
        distanceM = 5_000.0,
        averageHeartRate = 150,
        averagePace = Pace.fromSecondsPerKm(360),
    )
}
