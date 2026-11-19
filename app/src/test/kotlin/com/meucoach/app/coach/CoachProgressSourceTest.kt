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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachProgressSourceTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var activities: ActivityRepository
    private lateinit var source: CoachProgressSource

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
            zoneId = ZoneOffset.UTC,
            idGenerator = { "activity-${System.nanoTime()}" },
        )
        source = RoomCoachProgressSource(
            profiles = profiles,
            plans = plans,
            activities = activities,
            raceEvents = raceEvents,
            assembler = WeeklyReviewAssembler(
                plans,
                prescriptions,
                TrainingSignalSource(checkIns, metrics, activities, executions),
            ),
            time = FakeTimeProvider(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `sem perfil nao ha semanas`(): Unit = runBlocking {
        val context = source.observeProgress().first()

        assertThat(context.weeks).isEmpty()
        assertThat(context.profile).isNull()
        assertThat(context.today).isEqualTo(LocalDate.of(2026, 11, 18))
    }

    @Test
    fun `quatro semanas fechadas com a ultima tendo plano e corrida`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        plans.saveWeek(plan())
        activities.saveImportedActivity(activity())

        val context = source.observeProgress().first { it.weeks.size == 4 }

        assertThat(context.weeks.map { it.weekStart }).containsExactly(
            LocalDate.of(2026, 10, 19),
            LocalDate.of(2026, 10, 26),
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 9),
        )
        val last = context.weeks.last()
        assertThat(last.status).isEqualTo(WeeklyReviewStatus.AVAILABLE)
        assertThat(last.adherence?.planned).isEqualTo(2)
        assertThat(last.adherence?.done).isEqualTo(1)
        assertThat(context.weeks.take(3).map { it.status }).containsOnly(WeeklyReviewStatus.EMPTY_NO_DATA)
        assertThat(context.profile?.id).isEqualTo("athlete-1")
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
        thresholdPace = Pace.fromSecondsPerKm(360),
        timezone = "UTC",
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
