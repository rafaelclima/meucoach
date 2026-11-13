package com.meucoach.app.ui.tutorial

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.TrainingSignalSource
import com.meucoach.app.firstrun.FirstRunMetricsStore
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.health.HealthConnectReadResult
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.Sex
import com.meucoach.core.model.TrainingGoal
import com.meucoach.core.model.WeeklyAvailability
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TutorialViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    /** Fixed Monday so the weekly plan always has the Mon/Wed/Sat sessions. */
    private val today = LocalDate.of(2026, 11, 2)
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val timeProvider = FakeTimeProvider(
        instant = today.atTime(12, 0).atZone(zone).toInstant(),
        zone = zone,
    )
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var store: TutorialStore
    private lateinit var metrics: FirstRunMetricsStore
    private lateinit var profileRepository: ProfileRepository
    private lateinit var checkInRepository: CheckInRepository
    private lateinit var metricsRepository: DailyMetricsRepository
    private lateinit var activityRepository: ActivityRepository
    private lateinit var planRepository: TrainingPlanRepository
    private lateinit var executionRepository: SessionExecutionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(TutorialStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = TutorialStore(context)
        metrics = FirstRunMetricsStore(context, prefsName = "tutorial-metrics-${System.nanoTime()}")
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profileRepository = ProfileRepository(database)
        checkInRepository = CheckInRepository(database)
        metricsRepository = DailyMetricsRepository(database)
        activityRepository = ActivityRepository(database)
        planRepository = TrainingPlanRepository(database)
        executionRepository = SessionExecutionRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `starts unseen when the intro was never completed`() {
        val viewModel = viewModel()
        awaitLoaded(viewModel)

        assertThat(viewModel.uiState.value.seen).isFalse()
    }

    @Test
    fun `starts seen when the intro was already completed`() {
        store.markIntroDone()
        val viewModel = viewModel()
        awaitLoaded(viewModel)

        assertThat(viewModel.uiState.value.seen).isTrue()
    }

    @Test
    fun `marking seen persists the flag`() {
        val viewModel = viewModel()
        awaitLoaded(viewModel)

        viewModel.markSeen()

        assertThat(viewModel.uiState.value.seen).isTrue()
        assertThat(store.isIntroDone()).isTrue()
    }

    @Test
    fun `loads the profile and a real weekly plan`() {
        runBlocking { profileRepository.saveProfile(profile()) }
        val viewModel = viewModel()

        val state = runBlocking {
            withTimeout(5_000) {
                viewModel.uiState.first { !it.isLoading }
            }
        }

        assertThat(state.name).isEqualTo("Rafael")
        assertThat(state.goalLabel).isEqualTo("5 km")
        assertThat(state.planPreview).isNotNull
        assertThat(state.planPreview?.sessions).isNotEmpty
        assertThat(state.workoutPreview).isNotNull
        assertThat(state.readinessPreview).isNotNull
        assertThat(
            runBlocking {
                planRepository.getWeek(
                    today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                )
            },
        ).isNotNull
    }

    @Test
    fun `loads the health connection state`() {
        runBlocking { profileRepository.saveProfile(profile()) }
        val viewModel = viewModel(healthConnected = true)

        val state = runBlocking {
            withTimeout(5_000) {
                viewModel.uiState.first { !it.isLoading && it.healthConnected }
            }
        }

        assertThat(state.healthConnected).isTrue()
    }

    private fun awaitLoaded(viewModel: TutorialViewModel) {
        runBlocking {
            withTimeout(5_000) {
                viewModel.uiState.first { !it.isLoading }
            }
        }
    }

    private fun viewModel(healthConnected: Boolean = false): TutorialViewModel = TutorialViewModel(
        store = store,
        metrics = metrics,
        profileRepository = profileRepository,
        planRepository = planRepository,
        signalSource = TrainingSignalSource(
            checkIns = checkInRepository,
            metrics = metricsRepository,
            activities = activityRepository,
            executions = executionRepository,
        ),
        healthSource = FakeHealthSource(healthConnected),
        timeProvider = timeProvider,
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        heightCm = 174.0,
        weightKg = 97.0,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 50, DayOfWeek.SATURDAY to 90),
        ),
        goal = TrainingGoal(type = GoalType.FIVE_K),
        name = "Rafael",
    )
}

private class FakeHealthSource(private val connected: Boolean) : HealthDailySource {
    override val requiredPermissions: Set<String> = setOf("sleep")

    override suspend fun status(): HealthConnectStatus = HealthConnectStatus.AVAILABLE

    override suspend fun grantedPermissions(): Set<String> = if (connected) requiredPermissions else emptySet()

    override suspend fun read(from: LocalDate, to: LocalDate, zoneId: ZoneId): HealthConnectReadResult =
        HealthConnectReadResult(days = emptyList(), missingPermissions = emptySet())
}
