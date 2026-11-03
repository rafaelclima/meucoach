package com.meucoach.app.ui.activitydetail

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.story.BoundingBox
import com.meucoach.app.story.MapBackgroundRenderer
import com.meucoach.app.story.StoryVideoExporter
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.insights.ActivityInsightRules
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.AthleteProfile
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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivityDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ActivityRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var plans: TrainingPlanRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            ActivityRepository(
                database = database,
                clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
                zoneId = ZoneId.of("America/Sao_Paulo"),
                idGenerator = { "activity-1" },
            )
        profiles = ProfileRepository(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
        )
        plans = TrainingPlanRepository(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `loads an activity by id`() {
        runBlocking {
            repository.saveImportedActivity(importedActivity(), "sha")

            val viewModel = viewModel("activity-1")
            val state = viewModel.uiState.first { !it.isLoading }

            assertThat(state.notFound).isFalse()
            assertThat(state.activity?.id).isEqualTo("activity-1")
            assertThat(state.activity?.imported?.distanceM).isEqualTo(5_000.0)
            assertThat(state.insights.map { it.ruleId }).containsExactly(ActivityInsightRules.PACING)
        }
    }

    @Test
    fun `marks a missing activity as not found`() {
        runBlocking {
            val state = viewModel("missing").uiState.first { !it.isLoading }

            assertThat(state.notFound).isTrue()
            assertThat(state.activity).isNull()
        }
    }

    @Test
    fun `computes heart-rate zones when the profile has an LTHR`() {
        runBlocking {
            profiles.saveProfile(
                AthleteProfile(
                    id = "athlete-1",
                    birthDate = java.time.LocalDate.of(1988, 1, 1),
                    sex = Sex.MALE,
                    lactateThresholdHeartRate = 180,
                    timezone = "America/Sao_Paulo",
                ),
            )
            repository.saveImportedActivity(importedActivity(), "sha")

            val state = viewModel("activity-1").uiState.first { !it.isLoading }

            assertThat(state.hrZones).isNotNull
            assertThat(state.hrZones?.totalSec).isEqualTo(1_800)
            assertThat(state.lthr).isEqualTo(180)
        }
    }

    @Test
    fun `omits zones without an LTHR`() {
        runBlocking {
            repository.saveImportedActivity(importedActivity(), "sha")

            val state = viewModel("activity-1").uiState.first { !it.isLoading }

            assertThat(state.hrZones).isNull()
            assertThat(state.lthr).isNull()
        }
    }

    @Test
    fun `offers calibration for a sustained thirty minute effort`() {
        runBlocking {
            repository.saveImportedActivity(sustainedEffortActivity(), "sha")
            val state = viewModel("activity-1").uiState.first { !it.isLoading }

            assertThat(state.calibrationCandidate).isNotNull
            assertThat(state.calibrationCandidate?.durationSec).isEqualTo(30 * 60)
            assertThat(state.calibrationCandidate?.averageHeartRate).isEqualTo(176)
        }
    }

    @Test
    fun `does not offer calibration for an activity without a sustained heart rate record`() {
        runBlocking {
            repository.saveImportedActivity(importedActivity(), "sha")
            val state = viewModel("activity-1").uiState.first { !it.isLoading }

            assertThat(state.calibrationCandidate).isNull()
        }
    }

    @Test
    fun `deletes the activity and hides it from the history`() {
        runBlocking {
            repository.saveImportedActivity(importedActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.deleteActivity()
            val state = viewModel.uiState.first { it.deleted }

            assertThat(state.isDeleting).isFalse()
            assertThat(state.deleteError).isNull()
            assertThat(repository.openActivity("activity-1")).isNull()
        }
    }

    @Test
    fun `requires an activity id`() {
        assertThatThrownBy {
            ActivityDetailViewModel(
                SavedStateHandle(),
                repository,
                profiles,
                plans,
                StoryVideoExporter(ApplicationProvider.getApplicationContext()),
                FakeMapBackgroundRenderer(),
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
    }

    private fun viewModel(activityId: String, mapRenderer: MapBackgroundRenderer = FakeMapBackgroundRenderer()) =
        ActivityDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(ActivityDetailViewModel.ARG_ACTIVITY_ID to activityId),
            ),
            repository = repository,
            profileRepository = profiles,
            planRepository = plans,
            storyExporter = StoryVideoExporter(ApplicationProvider.getApplicationContext()),
            mapRenderer = mapRenderer,
        )

    @Test
    fun `reports missing gps when creating a story`() {
        runBlocking {
            repository.saveImportedActivity(importedActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.createStory()
            val state = viewModel.uiState.first { it.storyExport is StoryExportUiState.Error }

            assertThat((state.storyExport as StoryExportUiState.Error).message)
                .contains("rota GPS")
        }
    }

    @Test
    fun `prepares a story preview before exporting`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.createStory()
            val preview = viewModel.uiState.first {
                it.storyExport is StoryExportUiState.Preview
            }.storyExport as StoryExportUiState.Preview

            assertThat(preview.plan.splits).isNotEmpty()
            assertThat(preview.plan.distanceKm).isGreaterThan(0.0)
            assertThat(preview.mapBackground).isNotNull

            viewModel.cancelStory()
            assertThat(viewModel.uiState.value.storyExport).isEqualTo(StoryExportUiState.Idle)
        }
    }

    @Test
    fun `stamps the story with the planned session verdict`() {
        runBlocking {
            profiles.saveProfile(
                AthleteProfile(
                    id = "athlete-1",
                    birthDate = java.time.LocalDate.of(1988, 1, 1),
                    sex = Sex.MALE,
                    lactateThresholdHeartRate = 180,
                    timezone = "America/Sao_Paulo",
                ),
            )
            repository.saveImportedActivity(gpsActivity(), "sha")
            saveEasyPlan()
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.createStory()
            val preview = viewModel.uiState.first {
                it.storyExport is StoryExportUiState.Preview
            }.storyExport as StoryExportUiState.Preview

            assertThat(preview.plan.verdict).isNotNull
            assertThat(preview.plan.verdict?.tone).isNotNull
            assertThat(preview.plan.verdict?.coachLine).isNotBlank()
            assertThat(preview.plan.splits.map { it.verdict }).doesNotContainNull()
            assertThat(preview.plan.zoneRuns).isNotEmpty()
        }
    }

    @Test
    fun `keeps the story positive when there is no plan`() {
        runBlocking {
            profiles.saveProfile(
                AthleteProfile(
                    id = "athlete-1",
                    birthDate = java.time.LocalDate.of(1988, 1, 1),
                    sex = Sex.MALE,
                    lactateThresholdHeartRate = 180,
                    timezone = "America/Sao_Paulo",
                ),
            )
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.createStory()
            val preview = viewModel.uiState.first {
                it.storyExport is StoryExportUiState.Preview
            }.storyExport as StoryExportUiState.Preview

            assertThat(preview.plan.verdict?.tone).isNull()
            assertThat(preview.plan.verdict?.coachLine).isEqualTo("Cada corrida conta.")
            assertThat(preview.plan.splits.map { it.verdict }).containsOnlyNulls()
        }
    }

    @Test
    fun `renders the map for the story frame size`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val renderer = FakeMapBackgroundRenderer()
            val viewModel = viewModel("activity-1", renderer)
            viewModel.uiState.first { !it.isLoading }

            viewModel.createStory()
            viewModel.uiState.first { it.storyExport is StoryExportUiState.Preview }

            assertThat(renderer.width).isEqualTo(StoryVideoExporter.mapWidth())
            assertThat(renderer.height).isEqualTo(StoryVideoExporter.mapHeight())
            assertThat(renderer.bounds).isEqualTo(
                BoundingBox(
                    minLat = -5.790,
                    maxLat = -5.772,
                    minLon = -35.210,
                    maxLon = -35.210,
                ),
            )
        }
    }

    @Test
    fun `reports a map failure instead of exporting without the map`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel(
                "activity-1",
                FakeMapBackgroundRenderer(error = IllegalStateException("offline")),
            )
            viewModel.uiState.first { !it.isLoading }

            viewModel.createStory()
            val state = viewModel.uiState.first { it.storyExport is StoryExportUiState.Error }

            assertThat((state.storyExport as StoryExportUiState.Error).message)
                .contains("mapa")
        }
    }

    private suspend fun saveEasyPlan() {
        val date = java.time.LocalDate.parse("2026-10-24")
        plans.saveWeek(
            WeeklyPlan(
                weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                sessions = listOf(
                    PlannedSession(
                        date = date,
                        type = WorkoutType.EASY,
                        title = "Fácil 40 min",
                        durationMin = 40,
                        rationale = "Teste determinístico.",
                        ruleIds = listOf("R-ZONE-01"),
                        evidenceIds = listOf("EV-FRIEL-2018"),
                        status = SessionStatus.DONE,
                    ),
                ),
                weeklyMinutes = 40,
                longRunMinutes = null,
                qualityCount = 0,
                rationale = "Semana de teste.",
                confidence = DataConfidence.HIGH,
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-FRIEL-2018"),
            ),
        )
    }

    private fun gpsActivity() = ImportedActivity(
        source = ActivitySource.GPX,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-10-24T08:17:10Z"),
        durationSec = 720,
        distanceM = 2_000.0,
        calories = 140,
        samples = listOf(
            ActivitySample(offsetSec = 0, latitude = -5.790, longitude = -35.210, heartRate = 145),
            ActivitySample(offsetSec = 360, latitude = -5.781, longitude = -35.210, heartRate = 150),
            ActivitySample(offsetSec = 720, latitude = -5.772, longitude = -35.210, heartRate = 155),
        ),
    )

    private fun sustainedEffortActivity() = ImportedActivity(
        source = ActivitySource.TCX,
        sport = Sport.RUNNING,
        startTime = Instant.now().minusSeconds(24 * 3_600),
        durationSec = 30 * 60,
        distanceM = 7_000.0,
        averageHeartRate = 176,
        averagePace = Pace.fromSecondsPerKm(257),
        samples = (0..60).map { index ->
            ActivitySample(
                offsetSec = index * 30,
                latitude = -5.790 + index * 0.00003,
                longitude = -35.210,
                heartRate = 176 + (if (index % 3 == 0) 1 else 0),
            )
        },
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
        averageCadenceSpm = 170,
        averagePowerW = 250,
        elevationGainM = 40.0,
        movingTimeSec = 1_750,
        parserVersion = "1.0.0",
        laps = listOf(
            ActivityLap(
                index = 0,
                durationSec = 1_000,
                distanceM = 2_500.0,
                averageHeartRate = 150,
            ),
            ActivityLap(
                index = 1,
                durationSec = 800,
                distanceM = 2_500.0,
                averageHeartRate = 155,
            ),
        ),
        samples = listOf(
            ActivitySample(offsetSec = 0, heartRate = 140, speedMps = 2.7),
            ActivitySample(offsetSec = 900, heartRate = 150, speedMps = 2.8),
            ActivitySample(offsetSec = 1_800, heartRate = 160, speedMps = 2.9),
        ),
    )

    private class FakeMapBackgroundRenderer(private val error: Throwable? = null) : MapBackgroundRenderer {
        var bounds: BoundingBox? = null
        var width: Int = 0
        var height: Int = 0

        override suspend fun render(boundingBox: BoundingBox, width: Int, height: Int): Bitmap {
            this.bounds = boundingBox
            this.width = width
            this.height = height
            error?.let { throw it }
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }
}
