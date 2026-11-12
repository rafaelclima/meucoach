package com.meucoach.app.ui.calibration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.EvaluateReplan
import com.meucoach.app.application.EvaluateReplanResult
import com.meucoach.app.application.ReplanContextBuilder
import com.meucoach.app.application.easySession
import com.meucoach.app.application.qualitySession
import com.meucoach.app.application.replanPlan
import com.meucoach.app.application.replanProfile
import com.meucoach.app.replan.ProfilePlanEvaluationStore
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.ui.common.DateMask
import com.meucoach.app.ui.common.TargetTimeParts
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.calibration.CalibrationTier
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.AthleteProfile
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
class CalibrationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var activities: ActivityRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var metrics: DailyMetricsRepository
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
        profiles = ProfileRepository(database, clock)
        activities =
            ActivityRepository(
                database = database,
                clock = clock,
                zoneId = ZoneId.of("America/Sao_Paulo"),
                idGenerator = { "activity-${System.nanoTime()}" },
            )
        plans = TrainingPlanRepository(database, clock)
        checkIns = CheckInRepository(database, clock)
        metrics = DailyMetricsRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the profile and counts recent efforts`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        activities.saveImportedActivity(activity(daysAgo = 10), "sha-1")
        activities.saveImportedActivity(activity(daysAgo = 20).copy(averageHeartRate = null), "sha-2")

        val state = viewModel().uiState.first { !it.isLoading }

        assertThat(state.profile?.id).isEqualTo("athlete-1")
        assertThat(state.recentEfforts).isEqualTo(1)
        assertThat(state.trainingEfforts).hasSize(1)
        assertThat(state.trainingEfforts.single().averageHeartRate).isEqualTo(150)
    }

    @Test
    fun `estimates tier B from the recent efforts loaded into the state`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        activities.saveImportedActivity(
            sustainedEffort(daysAgo = 2, distanceM = 5_600.0, durationSec = 1_680, heartRate = 174),
            "sha-fast",
        )
        activities.saveImportedActivity(
            sustainedEffort(daysAgo = 5, distanceM = 6_000.0, durationSec = 1_920, heartRate = 168),
            "sha-steady",
        )
        activities.saveImportedActivity(
            sustainedEffort(daysAgo = 9, distanceM = 9_000.0, durationSec = 3_600, heartRate = 150),
            "sha-long",
        )

        val viewModel = viewModel()
        val loaded = viewModel.uiState.first { !it.isLoading }
        assertThat(loaded.trainingEfforts).hasSize(3)

        viewModel.estimateFromTraining()

        val result = viewModel.uiState.value.result
        assertThat(result?.tier).isEqualTo(CalibrationTier.B)
        assertThat(result?.thresholdPace?.secondsPerKm).isEqualTo(300)
        assertThat(result?.lthr).isEqualTo(174)
        assertThat(result?.vdot).isNotNull
        assertThat(result?.recommendation).contains("teste de 30 min")
    }

    @Test
    fun `estimation without enough efforts stays tier C without numbers`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        activities.saveImportedActivity(
            sustainedEffort(daysAgo = 2, distanceM = 5_600.0, durationSec = 1_680, heartRate = 174),
            "sha-fast",
        )
        activities.saveImportedActivity(
            sustainedEffort(daysAgo = 5, distanceM = 6_000.0, durationSec = 1_920, heartRate = 168),
            "sha-steady",
        )

        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }
        viewModel.estimateFromTraining()

        val result = viewModel.uiState.value.result
        assertThat(result?.tier).isEqualTo(CalibrationTier.C)
        assertThat(result?.vdot).isNull()
        assertThat(result?.thresholdPace).isNull()
        assertThat(result?.lthr).isNull()
    }

    @Test
    fun `saving a tier B estimate writes the estimated values into the profile`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        activities.saveImportedActivity(
            sustainedEffort(daysAgo = 2, distanceM = 5_600.0, durationSec = 1_680, heartRate = 174),
            "sha-fast",
        )
        activities.saveImportedActivity(
            sustainedEffort(daysAgo = 5, distanceM = 6_000.0, durationSec = 1_920, heartRate = 168),
            "sha-steady",
        )
        activities.saveImportedActivity(
            sustainedEffort(daysAgo = 9, distanceM = 9_000.0, durationSec = 3_600, heartRate = 150),
            "sha-long",
        )

        val timeProvider = FakeTimeProvider()
        val viewModel = viewModel(timeProvider = timeProvider)
        viewModel.uiState.first { !it.isLoading }
        viewModel.estimateFromTraining()
        assertThat(viewModel.uiState.value.result?.tier).isEqualTo(CalibrationTier.B)

        viewModel.save()
        withTimeout(5_000) { viewModel.uiState.first { it.isSaved } }

        val saved = profiles.getProfile()
        assertThat(saved?.vdot).isNotNull
        assertThat(saved?.thresholdPace?.secondsPerKm).isEqualTo(300)
        assertThat(saved?.lactateThresholdHeartRate).isEqualTo(174)
        assertThat(saved?.vdotSource).isEqualTo(com.meucoach.core.model.VdotSource.TRAINING_ESTIMATE)
        assertThat(saved?.vdotCalibratedAt).isEqualTo(timeProvider.currentDate())
        assertThat(saved?.vdotReferenceDistanceM).isNull()
        assertThat(saved?.vdotReferenceDurationSec).isNull()
    }

    @Test
    fun `calculates a tier A result from a recent race`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }
        fillRace(viewModel)

        viewModel.calculate()

        val result = viewModel.uiState.value.result
        assertThat(result).isNotNull
        assertThat(result?.tier).isEqualTo(CalibrationTier.A)
        assertThat(result?.vdot).isNotNull
        assertThat(result?.lthr).isEqualTo(181)
    }

    @Test
    fun `rejects invalid input and clears the previous result`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }
        fillRace(viewModel)
        viewModel.calculate()
        assertThat(viewModel.uiState.value.result).isNotNull

        viewModel.update { it.copy(timeParts = it.timeParts.copy(minutes = "abc")) }
        assertThat(viewModel.uiState.value.result).isNull()
        viewModel.calculate()

        assertThat(viewModel.uiState.value.result).isNull()
        assertThat(viewModel.uiState.value.errors.time).isNotNull()
    }

    @Test
    fun `saves calibration values into the profile`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }
        fillRace(viewModel)
        viewModel.calculate()

        viewModel.save()
        withTimeout(5_000) { viewModel.uiState.first { it.isSaved } }

        val saved = profiles.getProfile()
        assertThat(saved?.vdot).isNotNull
        assertThat(saved?.lactateThresholdHeartRate).isEqualTo(181)
        assertThat(saved?.thresholdPace).isNotNull
        assertThat(saved?.maxHeartRateObserved).isEqualTo(187)
        assertThat(saved?.vdotSource).isEqualTo(com.meucoach.core.model.VdotSource.PERFORMANCE)
        assertThat(saved?.vdotCalibratedAt).isNotNull
        assertThat(saved?.vdotReferenceDistanceM).isEqualTo(5_000.0)
        assertThat(saved?.vdotReferenceDurationSec).isEqualTo(1_859)
    }

    @Test
    fun `saving calibration evaluates the plan and publishes the result`(): Unit = runBlocking {
        val timeProvider = FakeTimeProvider(
            Instant.parse("2026-11-04T12:00:00Z"),
            ZoneOffset.UTC,
        )
        val today = timeProvider.currentDate()
        val weekStart = LocalDate.of(2026, 11, 2)
        profiles.saveProfile(replanProfile())
        plans.saveWeek(
            replanPlan(
                weekStart = weekStart,
                createdOn = today,
                sessions = listOf(
                    easySession(today),
                    qualitySession(weekStart.plusDays(4)),
                ),
            ),
        )
        val store = ProfilePlanEvaluationStore()
        val viewModel = viewModel(timeProvider = timeProvider, planEvaluationStore = store)
        viewModel.uiState.first { !it.isLoading }
        viewModel.update {
            it.copy(
                distanceKm = "5",
                timeParts = TargetTimeParts(minutes = "30", seconds = "59"),
                date = DateMask.toDigits(today),
                averageHeartRate = "181",
            )
        }
        viewModel.calculate()

        viewModel.save()
        withTimeout(5_000) { viewModel.uiState.first { it.isSaved } }

        assertThat(store.result.value).isInstanceOf(EvaluateReplanResult.NoChange::class.java)
        assertThat(store.result.value).isNotNull()
    }

    @Test
    fun `prefills the form from an eligible run without calculating or saving`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        activities.saveImportedActivity(effortActivity(), "sha-effort")
        val activityId = activities.listActivitySummaries().first().id

        val state = viewModel(activityId).uiState.first { !it.isLoading }

        assertThat(state.prefilledFrom).isNotNull
        assertThat(state.form.distanceKm).isEqualTo("7,00")
        assertThat(state.form.timeParts).isEqualTo(TargetTimeParts(minutes = "30", seconds = "00"))
        assertThat(state.form.averageHeartRate).isEqualTo("176")
        assertThat(state.result).isNull()
        assertThat(profiles.getProfile()?.lactateThresholdHeartRate).isNull()
    }

    @Test
    fun `does not prefill from an easy run outside the effort window`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        activities.saveImportedActivity(effortActivity(durationSec = 20 * 60), "sha-easy")
        val activityId = activities.listActivitySummaries().first().id

        val state = viewModel(activityId).uiState.first { !it.isLoading }

        assertThat(state.prefilledFrom).isNull()
        assertThat(state.form.distanceKm).isEqualTo("5")
        assertThat(state.form.timeParts.isBlank).isTrue()
    }

    private fun viewModel(
        activityId: String? = null,
        timeProvider: TimeProvider = FakeTimeProvider(),
        planEvaluationStore: ProfilePlanEvaluationStore = ProfilePlanEvaluationStore(),
    ): CalibrationViewModel {
        val contexts = ReplanContextBuilder(
            plans = plans,
            profiles = profiles,
            checkIns = checkIns,
            metrics = metrics,
            activities = activities,
            raceEvents = RaceEventRepository(database, clock),
            timeProvider = timeProvider,
        )
        return CalibrationViewModel(
            profileRepository = profiles,
            activityRepository = activities,
            timeProvider = timeProvider,
            evaluateReplan = EvaluateReplan(contexts, plans),
            planEvaluationStore = planEvaluationStore,
            savedStateHandle = androidx.lifecycle.SavedStateHandle(
                activityId?.let { mapOf(CalibrationViewModel.ARG_ACTIVITY_ID to it) }.orEmpty(),
            ),
        )
    }

    private fun fillRace(viewModel: CalibrationViewModel) {
        val date = DateMask.toDigits(LocalDate.now())
        viewModel.update {
            it.copy(
                distanceKm = "5",
                timeParts = TargetTimeParts(minutes = "30", seconds = "59"),
                date = date,
                averageHeartRate = "181",
            )
        }
    }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        timezone = "America/Sao_Paulo",
    )

    private fun activity(daysAgo: Long) = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.now().minusSeconds(daysAgo * 24 * 3_600),
        durationSec = 1_800,
        distanceM = 5_000.0,
        averageHeartRate = 150,
        averagePace = Pace.fromSecondsPerKm(360),
    )

    private fun sustainedEffort(daysAgo: Long, distanceM: Double, durationSec: Int, heartRate: Int) = ImportedActivity(
        source = ActivitySource.TCX,
        sport = Sport.RUNNING,
        startTime = Instant.now().minusSeconds(daysAgo * 24 * 3_600),
        durationSec = durationSec,
        distanceM = distanceM,
        averageHeartRate = heartRate,
        averagePace = Pace.fromSecondsPerKm(300),
    )

    private fun effortActivity(durationSec: Int = 30 * 60) = ImportedActivity(
        source = ActivitySource.TCX,
        sport = Sport.RUNNING,
        startTime = Instant.now().minusSeconds(24 * 3_600),
        durationSec = durationSec,
        distanceM = 7_000.0,
        averageHeartRate = 176,
        averagePace = Pace.fromSecondsPerKm(257),
        samples = (0..60).map { index ->
            ActivitySample(
                offsetSec = index * 30,
                heartRate = 176 + (if (index % 3 == 0) 1 else 0),
            )
        },
    )
}
