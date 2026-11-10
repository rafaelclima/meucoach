package com.meucoach.app.ui.onboarding

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.EvaluateReplan
import com.meucoach.app.application.EvaluateReplanResult
import com.meucoach.app.application.REPLAN_TODAY
import com.meucoach.app.application.REPLAN_WEEK_START
import com.meucoach.app.application.ReplanContextBuilder
import com.meucoach.app.application.easySession
import com.meucoach.app.application.qualitySession
import com.meucoach.app.application.replanPlan
import com.meucoach.app.application.replanProfile
import com.meucoach.app.replan.ProfilePlanEvaluationStore
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.replan.ReplanCause
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Experience
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.Pace
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ProfileRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var metrics: DailyMetricsRepository
    private lateinit var activities: ActivityRepository
    private val clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = ProfileRepository(database = database, clock = clock)
        plans = TrainingPlanRepository(database, clock)
        checkIns = CheckInRepository(database, clock)
        metrics = DailyMetricsRepository(database, clock)
        activities = ActivityRepository(database = database, clock = clock, zoneId = ZoneOffset.UTC)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `starts empty at the first step`(): Unit = runBlocking {
        val state = viewModel().uiState.first { !it.isLoading }

        assertThat(state.step).isZero()
        assertThat(state.form.birthDate).isEmpty()
        assertThat(state.isEditing).isFalse()
    }

    @Test
    fun `blocks the next step until the current one is valid`(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.next()

        val state = viewModel.uiState.value
        assertThat(state.step).isZero()
        assertThat(state.errors).containsKey(OnboardingField.BIRTH_DATE)
    }

    @Test
    fun `advances through valid steps and saves the profile`(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }
        fillValidForm(viewModel)

        viewModel.next()
        assertThat(viewModel.uiState.value.step).isEqualTo(1)
        viewModel.next()
        assertThat(viewModel.uiState.value.step).isEqualTo(2)
        viewModel.save()
        withTimeout(TIMEOUT_MILLIS) { viewModel.uiState.first { it.isSaved } }

        val state = viewModel.uiState.value
        assertThat(state.isSaved).isTrue()
        val saved = repository.getProfile()
        assertThat(saved).isNotNull()
        assertThat(saved?.id).isNotBlank()
        assertThat(saved?.goal?.type).isEqualTo(GoalType.FIVE_K)
        assertThat(saved?.weeklyAvailability?.totalMinutes).isEqualTo(180)
        assertThat(saved?.name).isEqualTo("Rafael")
    }

    @Test
    fun `requires a name on the first step`(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }
        fillValidForm(viewModel)
        viewModel.update { it.copy(name = "  ") }

        viewModel.next()

        assertThat(viewModel.uiState.value.step).isZero()
        assertThat(viewModel.uiState.value.errors).containsKey(OnboardingField.NAME)
    }

    @Test
    fun `jumps to the invalid step when saving`(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }
        fillValidForm(viewModel)
        viewModel.update { it.copy(restingHeartRate = "20") }

        viewModel.save()

        val state = viewModel.uiState.value
        assertThat(state.step).isEqualTo(2)
        assertThat(state.errors).containsKey(OnboardingField.RESTING_HEART_RATE)
        assertThat(repository.getProfile()).isNull()
    }

    @Test
    fun `loads an existing profile for editing and preserves calibration`(): Unit = runBlocking {
        repository.saveProfile(existingProfile())
        val viewModel = viewModel()
        val loaded = viewModel.uiState.first { !it.isLoading }

        assertThat(loaded.isEditing).isTrue()
        assertThat(loaded.form.city).isEqualTo("Natal")
        viewModel.update { it.copy(city = "Recife") }
        viewModel.save()
        withTimeout(TIMEOUT_MILLIS) { viewModel.uiState.first { it.isSaved } }

        val saved = repository.getProfile()
        assertThat(saved?.id).isEqualTo("athlete-1")
        assertThat(saved?.city).isEqualTo("Recife")
        assertThat(saved?.lactateThresholdHeartRate).isEqualTo(180)
        assertThat(saved?.vdot).isEqualTo(31.0)
    }

    @Test
    fun `toggles availability days`(): Unit = runBlocking {
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }

        viewModel.toggleDay(java.time.DayOfWeek.MONDAY)
        assertThat(viewModel.uiState.value.form.availabilityMinutes)
            .containsEntry(java.time.DayOfWeek.MONDAY, "40")

        viewModel.updateDayMinutes(java.time.DayOfWeek.MONDAY, "5x")
        assertThat(viewModel.uiState.value.form.availabilityMinutes)
            .containsEntry(java.time.DayOfWeek.MONDAY, "5")

        viewModel.toggleDay(java.time.DayOfWeek.MONDAY)
        assertThat(viewModel.uiState.value.form.availabilityMinutes).isEmpty()
    }

    @Test
    fun `saving an edited profile evaluates T4 and publishes the suggestion`(): Unit = runBlocking {
        val timeProvider = FakeTimeProvider(
            Instant.parse("2026-11-04T12:00:00Z"),
            ZoneOffset.UTC,
        )
        repository.saveProfile(replanProfile())
        plans.saveWeek(
            replanPlan(
                createdOn = REPLAN_TODAY,
                sessions = listOf(
                    easySession(REPLAN_TODAY),
                    qualitySession(REPLAN_WEEK_START.plusDays(4)),
                ),
            ),
        )
        val store = ProfilePlanEvaluationStore()
        val viewModel = viewModel(timeProvider, store)
        withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        fillValidForm(viewModel)

        // First save establishes the evaluated checkpoint for the original profile.
        viewModel.save()
        withTimeout(5_000) { viewModel.uiState.first { it.isSaved } }
        store.consume()
        viewModel.consumeSaved()

        viewModel.update { it.copy(experience = Experience.BEGINNER) }
        viewModel.save()
        withTimeout(5_000) { viewModel.uiState.first { it.isSaved } }

        val result = store.result.value
        assertThat(result).isInstanceOf(EvaluateReplanResult.Suggestion::class.java)
        assertThat((result as EvaluateReplanResult.Suggestion).suggestion.causes)
            .contains(ReplanCause.PROFILE_CHANGED)
        assertThat(repository.getProfile()?.experience).isEqualTo(Experience.BEGINNER)
    }

    private fun viewModel(
        timeProvider: TimeProvider = FakeTimeProvider(),
        planEvaluationStore: ProfilePlanEvaluationStore = ProfilePlanEvaluationStore(),
    ): OnboardingViewModel {
        val contexts = ReplanContextBuilder(
            plans = plans,
            profiles = repository,
            checkIns = checkIns,
            metrics = metrics,
            activities = activities,
            timeProvider = timeProvider,
        )
        return OnboardingViewModel(
            repository,
            com.meucoach.app.profile.ProfilePhotoStore(
                ApplicationProvider.getApplicationContext(),
            ),
            timeProvider,
            EvaluateReplan(contexts, plans),
            planEvaluationStore,
        )
    }

    private fun fillValidForm(viewModel: OnboardingViewModel) {
        viewModel.update {
            it.copy(
                name = "Rafael",
                birthDate = "01/01/1988",
                sex = Sex.MALE,
                heightCm = "174",
                weightKg = "97",
                goalType = GoalType.FIVE_K,
                targetDate = "14/03/2027",
                targetTime = "30:00",
                availabilityMinutes = mapOf(
                    java.time.DayOfWeek.MONDAY to "40",
                    java.time.DayOfWeek.WEDNESDAY to "50",
                    java.time.DayOfWeek.SATURDAY to "90",
                ),
                restingHeartRate = "58",
                maxHeartRateObserved = "187",
                timezone = "America/Sao_Paulo",
                city = "Natal",
            )
        }
    }

    private fun existingProfile() = AthleteProfile(
        id = "athlete-1",
        name = "Rafael",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        heightCm = 174.0,
        weightKg = 97.0,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(364),
        vdot = 31.0,
        experience = Experience.BEGINNER,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(mapOf(java.time.DayOfWeek.MONDAY to 40)),
        goal = TrainingGoal(type = GoalType.GENERAL_FITNESS),
        city = "Natal",
    )

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
