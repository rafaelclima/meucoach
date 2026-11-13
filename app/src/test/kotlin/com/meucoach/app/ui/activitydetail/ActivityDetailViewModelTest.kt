package com.meucoach.app.ui.activitydetail

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.meucoach.app.application.GetSessionComparison
import com.meucoach.app.story.MapBackgroundRenderer
import com.meucoach.app.story.StoryExportCoordinator
import com.meucoach.app.story.StoryExportFailureStore
import com.meucoach.app.story.StoryExportWorker
import com.meucoach.app.story.StoryIntroStore
import com.meucoach.app.story.StoryPalette
import com.meucoach.app.story.StoryRecipeStore
import com.meucoach.app.story.StorySessionResolver
import com.meucoach.app.story.StoryVideoExporter
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.feedback.ComparisonVerdict
import com.meucoach.core.domain.feedback.SessionComparisonKind
import com.meucoach.core.domain.insights.ActivityInsightRules
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.story.BoundingBox
import com.meucoach.core.domain.story.StoryDuration
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.domain.story.StoryPreset
import com.meucoach.core.domain.story.StoryRecipe
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivityDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ActivityRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var executions: SessionExecutionRepository
    private lateinit var prescriptions: DailyPrescriptionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(StoryRecipeStore.PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
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
        executions = SessionExecutionRepository(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
            idGenerator = { "execution-1" },
        )
        prescriptions = DailyPrescriptionRepository(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
            idGenerator = { "prescription-1" },
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
    fun `compares a linked run with the plan of the day`() {
        runBlocking {
            repository.saveImportedActivity(importedActivity(), "sha")
            saveEasyPlan()

            val state = viewModel("activity-1").uiState.first { !it.isLoading }

            assertThat(state.comparison?.kind).isEqualTo(SessionComparisonKind.LINKED)
            assertThat(state.comparison?.plan?.title).isEqualTo("Fácil 40 min")
            assertThat(state.comparison?.durationPercentOfPlan).isEqualTo(75)
            assertThat(state.comparison?.durationVerdict).isEqualTo(ComparisonVerdict.BELOW)
        }
    }

    @Test
    fun `labels an unlinked run as free run`() {
        runBlocking {
            repository.saveImportedActivity(importedActivity(), "sha")

            val state = viewModel("activity-1").uiState.first { !it.isLoading }

            assertThat(state.comparison?.kind).isEqualTo(SessionComparisonKind.FREE_RUN)
            assertThat(state.comparison?.plan).isNull()
            assertThat(state.comparison?.durationVerdict).isNull()
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
            val coordinator = FakeStoryExportCoordinator()
            val viewModel = viewModel("activity-1", coordinator = coordinator)
            viewModel.uiState.first { !it.isLoading }

            viewModel.deleteActivity()
            val state = viewModel.uiState.first { it.deleted }

            assertThat(state.isDeleting).isFalse()
            assertThat(state.deleteError).isNull()
            assertThat(repository.openActivity("activity-1")).isNull()
            assertThat(coordinator.cancelledActivityId).isEqualTo("activity-1")
        }
    }

    @Test
    fun `requires an activity id`() {
        assertThatThrownBy {
            ActivityDetailViewModel(
                SavedStateHandle(),
                repository,
                profiles,
                StorySessionResolver(plans),
                FakeMapBackgroundRenderer(),
                FakeStoryExportCoordinator(),
                StoryExportFailureStore(ApplicationProvider.getApplicationContext()),
                StoryIntroStore(ApplicationProvider.getApplicationContext()),
                StoryRecipeStore(ApplicationProvider.getApplicationContext()),
                com.meucoach.app.profile.ProfilePhotoStore(
                    ApplicationProvider.getApplicationContext(),
                ),
                FakeTimeProvider(),
                GetSessionComparison(repository, executions, prescriptions, plans),
            )
        }
            .isInstanceOf(IllegalStateException::class.java)
    }

    private fun viewModel(
        activityId: String,
        mapRenderer: MapBackgroundRenderer = FakeMapBackgroundRenderer(),
        coordinator: StoryExportCoordinator = FakeStoryExportCoordinator(),
        failures: StoryExportFailureStore = StoryExportFailureStore(
            ApplicationProvider.getApplicationContext(),
        ),
        intro: StoryIntroStore = StoryIntroStore(ApplicationProvider.getApplicationContext()),
    ) = ActivityDetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(ActivityDetailViewModel.ARG_ACTIVITY_ID to activityId),
        ),
        repository = repository,
        profileRepository = profiles,
        sessions = StorySessionResolver(plans),
        mapRenderer = mapRenderer,
        storyCoordinator = coordinator,
        storyFailures = failures,
        storyIntro = intro,
        storyRecipe = StoryRecipeStore(ApplicationProvider.getApplicationContext()),
        profilePhotos = com.meucoach.app.profile.ProfilePhotoStore(
            ApplicationProvider.getApplicationContext(),
        ),
        timeProvider = FakeTimeProvider(),
        comparisons = GetSessionComparison(repository, executions, prescriptions, plans),
    )

    @Test
    fun `forwards the selected map style to the renderer`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val renderer = FakeMapBackgroundRenderer()
            val viewModel = viewModel("activity-1", renderer)
            viewModel.uiState.first { !it.isLoading }

            viewModel.selectStoryMapStyle(StoryMapStyle.TERRAIN)
            viewModel.generateStory(testPalette())
            viewModel.uiState.first { it.storyExport is StoryExportUiState.Generating }

            assertThat(renderer.style).isEqualTo(StoryMapStyle.TERRAIN)
        }
    }

    @Test
    fun `dismisses the one-time map notice`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("meucoach_story_intro", Context.MODE_PRIVATE)
                .edit().clear().commit()
            repository.saveImportedActivity(gpsActivity(), "sha")
            val intro = StoryIntroStore(context)
            val viewModel = viewModel("activity-1", intro = intro)
            val initial = viewModel.uiState.first { !it.isLoading }
            assertThat(initial.storyIntroSeen).isFalse()

            viewModel.dismissStoryIntro()

            assertThat(viewModel.uiState.value.storyIntroSeen).isTrue()
            assertThat(intro.seen()).isTrue()
        }
    }

    @Test
    fun `exposes the private coach reading on the detail screen`() {
        runBlocking {
            profiles.saveProfile(
                AthleteProfile(
                    id = "athlete-1",
                    birthDate = java.time.LocalDate.of(1988, 1, 1),
                    sex = Sex.MALE,
                    lactateThresholdHeartRate = 180,
                    timezone = "America/Sao_Paulo",
                    name = "Rafael",
                ),
            )
            repository.saveImportedActivity(gpsActivity(), "sha")
            saveEasyPlan()
            val state = viewModel("activity-1").uiState.first { !it.isLoading }

            assertThat(state.coachVerdict).isNotNull
            assertThat(state.coachVerdict?.headline).isNotBlank()
        }
    }

    @Test
    fun `reports missing gps when generating a story`() {
        runBlocking {
            repository.saveImportedActivity(importedActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.generateStory(testPalette())
            val state = viewModel.uiState.first { it.storyExport is StoryExportUiState.Error }

            assertThat((state.storyExport as StoryExportUiState.Error).message)
                .contains("rota GPS")
        }
    }

    @Test
    fun `builds the studio plan when the activity loads`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val state = viewModel("activity-1").uiState.first { !it.isLoading }

            assertThat(state.storyStudio.plan?.splits).isNotEmpty()
            assertThat(state.storyStudio.plan?.distanceKm).isGreaterThan(0.0)
            assertThat(state.storyStudio.recipe).isEqualTo(StoryRecipe())
        }
    }

    @Test
    fun `disables extras that the activity does not measure`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val studio = viewModel("activity-1").uiState.first { !it.isLoading }.storyStudio

            val heartRate = studio.extras.first { it.extra == StoryExtra.AVG_HR }
            val cadence = studio.extras.first { it.extra == StoryExtra.CADENCE }

            assertThat(heartRate.isAvailable).isTrue()
            assertThat(cadence.isAvailable).isFalse()
            assertThat(cadence.reason).isEqualTo("sem cadência")
        }
    }

    @Test
    fun `opens and closes the studio without losing the recipe`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.openStoryStudio()
            viewModel.toggleStoryExtra(StoryExtra.AVG_HR)
            assertThat(viewModel.uiState.value.storyStudio.isOpen).isTrue()

            viewModel.closeStoryStudio()

            assertThat(viewModel.uiState.value.storyStudio.isOpen).isFalse()
            assertThat(viewModel.uiState.value.storyStudio.recipe.extras).containsExactly(StoryExtra.AVG_HR)
        }
    }

    @Test
    fun `opening the studio drops extras this activity cannot show`() {
        runBlocking {
            StoryRecipeStore(ApplicationProvider.getApplicationContext()).save(
                StoryRecipe(extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE)),
            )
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.openStoryStudio()

            assertThat(viewModel.uiState.value.storyStudio.recipe.extras)
                .containsExactly(StoryExtra.AVG_HR)
        }
    }

    @Test
    fun `save as default can be turned back on`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.setStorySaveAsDefault(false)
            viewModel.setStorySaveAsDefault(true)

            assertThat(viewModel.uiState.value.storyStudio.saveAsDefault).isTrue()
        }
    }

    @Test
    fun `toggles an available extra and marks the recipe custom`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.toggleStoryExtra(StoryExtra.AVG_HR)

            val recipe = viewModel.uiState.value.storyStudio.recipe
            assertThat(recipe.extras).containsExactly(StoryExtra.AVG_HR)
            assertThat(recipe.preset).isEqualTo(StoryPreset.CUSTOM)
        }
    }

    @Test
    fun `blocks a third extra and unavailable extras`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.toggleStoryExtra(StoryExtra.AVG_HR)
            viewModel.toggleStoryExtra(StoryExtra.DATE)
            viewModel.toggleStoryExtra(StoryExtra.MAX_HR)
            viewModel.toggleStoryExtra(StoryExtra.CADENCE)

            val recipe = viewModel.uiState.value.storyStudio.recipe
            assertThat(recipe.extras).containsExactly(StoryExtra.AVG_HR, StoryExtra.DATE)
        }
    }

    @Test
    fun `a preset only turns on the extras this activity has`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.selectStoryPreset(StoryPreset.PHYSIOLOGICAL)

            val recipe = viewModel.uiState.value.storyStudio.recipe
            assertThat(recipe.preset).isEqualTo(StoryPreset.PHYSIOLOGICAL)
            assertThat(recipe.extras).containsExactly(StoryExtra.AVG_HR)
        }
    }

    @Test
    fun `changing the duration rebuilds the timeline`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.selectStoryDuration(StoryDuration.SEC_45)
            val plan = viewModel.uiState.first { it.storyStudio.plan?.durationSec == 45f }.storyStudio.plan

            assertThat(plan?.durationSec).isEqualTo(45f)
        }
    }

    @Test
    fun `ignores the satellite style outside the mvp`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.selectStoryMapStyle(StoryMapStyle.SATELLITE)

            assertThat(viewModel.uiState.value.storyStudio.recipe.mapStyle)
                .isEqualTo(StoryMapStyle.MEUCOACH_DARK)
        }
    }

    @Test
    fun `loads the saved recipe into the studio`() {
        runBlocking {
            val saved = StoryRecipe(
                preset = StoryPreset.COMPLETE,
                extras = listOf(StoryExtra.DATE),
                duration = StoryDuration.SEC_30,
                mapStyle = StoryMapStyle.MEUCOACH_LIGHT,
            )
            StoryRecipeStore(ApplicationProvider.getApplicationContext()).save(saved)
            repository.saveImportedActivity(gpsActivity(), "sha")

            val state = viewModel("activity-1").uiState.first { !it.isLoading }

            assertThat(state.storyStudio.recipe).isEqualTo(saved)
            assertThat(state.storyStudio.plan?.durationSec).isEqualTo(30f)
        }
    }

    @Test
    fun `resetting the recipe clears the saved default`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = StoryRecipeStore(context)
            store.save(StoryRecipe(duration = StoryDuration.SEC_30, mapStyle = StoryMapStyle.MEUCOACH_LIGHT))
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.resetStoryRecipe()

            assertThat(viewModel.uiState.value.storyStudio.recipe).isEqualTo(StoryRecipe())
            assertThat(store.load()).isEqualTo(StoryRecipe())
        }
    }

    @Test
    fun `generating saves the recipe as the new default`() {
        runBlocking {
            val store = StoryRecipeStore(ApplicationProvider.getApplicationContext())
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.toggleStoryExtra(StoryExtra.AVG_HR)
            viewModel.generateStory(testPalette())
            viewModel.uiState.first { it.storyExport is StoryExportUiState.Generating }

            assertThat(store.load().extras).containsExactly(StoryExtra.AVG_HR)
        }
    }

    @Test
    fun `generating does not save when the user declines`() {
        runBlocking {
            val store = StoryRecipeStore(ApplicationProvider.getApplicationContext())
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.setStorySaveAsDefault(false)
            viewModel.toggleStoryExtra(StoryExtra.AVG_HR)
            viewModel.generateStory(testPalette())
            viewModel.uiState.first { it.storyExport is StoryExportUiState.Generating }

            assertThat(store.load()).isEqualTo(StoryRecipe())
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
            val plan = viewModel("activity-1").uiState.first { !it.isLoading }.storyStudio.plan

            assertThat(plan?.verdict).isNotNull()
            assertThat(plan?.verdict?.tone).isNotNull()
            assertThat(plan?.verdict?.coachLine).isNotBlank()
            assertThat(plan?.splits?.map { it.verdict }).doesNotContainNull()
            assertThat(plan?.zoneRuns).isNotEmpty()
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
            val plan = viewModel("activity-1").uiState.first { !it.isLoading }.storyStudio.plan

            assertThat(plan?.verdict?.tone).isNull()
            assertThat(plan?.verdict?.coachLine).isEqualTo("Cada corrida conta.")
            assertThat(plan?.splits?.map { it.verdict }).containsOnlyNulls()
        }
    }

    @Test
    fun `renders the map for the story frame size`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val renderer = FakeMapBackgroundRenderer()
            val viewModel = viewModel("activity-1", renderer)
            viewModel.uiState.first { !it.isLoading }

            viewModel.generateStory(testPalette())
            viewModel.uiState.first { it.storyExport is StoryExportUiState.Generating }

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

            viewModel.generateStory(testPalette())
            val state = viewModel.uiState.first { it.storyExport is StoryExportUiState.Error }

            assertThat((state.storyExport as StoryExportUiState.Error).message)
                .contains("mapa")
        }
    }

    @Test
    fun `generating hands the studio recipe and palette to the coordinator`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val coordinator = FakeStoryExportCoordinator()
            val viewModel = viewModel("activity-1", coordinator = coordinator)
            viewModel.uiState.first { !it.isLoading }

            viewModel.selectStoryDuration(StoryDuration.SEC_30)
            viewModel.selectStoryMapStyle(StoryMapStyle.TERRAIN)
            viewModel.toggleStoryExtra(StoryExtra.AVG_HR)
            viewModel.generateStory(testPalette())
            viewModel.uiState.first { it.storyExport is StoryExportUiState.Generating }

            assertThat(viewModel.uiState.value.storyExport)
                .isEqualTo(StoryExportUiState.Generating(progress = 0f))
            assertThat(coordinator.startedActivityId).isEqualTo("activity-1")
            assertThat(coordinator.startedPalette).isEqualTo(testPalette())
            assertThat(coordinator.startedMap).isNotNull()
            assertThat(coordinator.startedRecipe).isEqualTo(
                StoryRecipe(
                    preset = StoryPreset.CUSTOM,
                    extras = listOf(StoryExtra.AVG_HR),
                    duration = StoryDuration.SEC_30,
                    mapStyle = StoryMapStyle.TERRAIN,
                ),
            )
        }
    }

    @Test
    fun `reports a failure when the export cannot be staged`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val coordinator = FakeStoryExportCoordinator(
                startError = IllegalStateException("cache cheio"),
            )
            val viewModel = viewModel("activity-1", coordinator = coordinator)
            viewModel.uiState.first { !it.isLoading }

            viewModel.generateStory(testPalette())
            val state = viewModel.uiState.first { it.storyExport is StoryExportUiState.Error }

            assertThat((state.storyExport as StoryExportUiState.Error).message)
                .isEqualTo("cache cheio")
        }
    }

    @Test
    fun `cancels the queued generation for real`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val coordinator = FakeStoryExportCoordinator()
            val viewModel = viewModel("activity-1", coordinator = coordinator)
            viewModel.uiState.first { !it.isLoading }

            viewModel.generateStory(testPalette())
            viewModel.uiState.first { it.storyExport is StoryExportUiState.Generating }
            viewModel.cancelStory()

            assertThat(coordinator.cancelledActivityId).isEqualTo("activity-1")
            assertThat(viewModel.uiState.value.storyExport).isEqualTo(StoryExportUiState.Idle)
        }
    }

    @Test
    fun `adopts running background progress into the generating state`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.applyWorkInfo(
                workInfo(
                    state = WorkInfo.State.RUNNING,
                    progress = workDataOf(
                        StoryExportWorker.KEY_PROGRESS to 0.42f,
                        StoryExportWorker.KEY_ETA_SEC to 12,
                    ),
                ),
            )

            val generating = viewModel.uiState.value.storyExport as StoryExportUiState.Generating
            assertThat(generating.progress).isEqualTo(0.42f)
            assertThat(generating.etaSec).isEqualTo(12)
        }
    }

    @Test
    fun `adopts a finished background export as done`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.applyWorkInfo(
                workInfo(
                    state = WorkInfo.State.SUCCEEDED,
                    outputData = workDataOf(
                        StoryExportWorker.KEY_VIDEO_URI to "content://media/external/video/media/42",
                        StoryExportWorker.KEY_SAVED_TO_GALLERY to true,
                        StoryExportWorker.KEY_COVER_URI to "content://media/external/images/media/43",
                        StoryExportWorker.KEY_POSTER_URI to "content://media/external/images/media/44",
                    ),
                ),
            )

            val done = viewModel.uiState.value.storyExport as StoryExportUiState.Done
            assertThat(done.uri.toString()).isEqualTo("content://media/external/video/media/42")
            assertThat(done.savedToGallery).isTrue()
            assertThat(done.coverUri.toString()).isEqualTo("content://media/external/images/media/43")
            assertThat(done.posterUri.toString()).isEqualTo("content://media/external/images/media/44")
        }
    }

    @Test
    fun `keeps the video when the background export has no poster`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.applyWorkInfo(
                workInfo(
                    state = WorkInfo.State.SUCCEEDED,
                    outputData = workDataOf(
                        StoryExportWorker.KEY_VIDEO_URI to "content://media/external/video/media/42",
                        StoryExportWorker.KEY_SAVED_TO_GALLERY to true,
                        StoryExportWorker.KEY_COVER_URI to "content://media/external/images/media/43",
                    ),
                ),
            )

            val done = viewModel.uiState.value.storyExport as StoryExportUiState.Done
            assertThat(done.posterUri).isNull()
        }
    }

    @Test
    fun `maps a background failure into the error state`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.applyWorkInfo(
                workInfo(
                    state = WorkInfo.State.RUNNING,
                    progress = workDataOf(StoryExportWorker.KEY_PROGRESS to 0.1f),
                ),
            )
            viewModel.applyWorkInfo(
                workInfo(
                    state = WorkInfo.State.FAILED,
                    outputData = workDataOf(
                        StoryExportWorker.KEY_ERROR to "Encoder sem buffer de entrada.",
                    ),
                ),
            )

            assertThat((viewModel.uiState.value.storyExport as StoryExportUiState.Error).message)
                .isEqualTo("Encoder sem buffer de entrada.")
        }
    }

    @Test
    fun `surfaces a failure stored by the worker when the work data is empty`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val failures = StoryExportFailureStore(ApplicationProvider.getApplicationContext())
            failures.save("activity-1", "Encoder sem buffer de entrada.")
            val viewModel = viewModel("activity-1", failures = failures)
            viewModel.uiState.first { !it.isLoading }

            viewModel.applyWorkInfo(workInfo(state = WorkInfo.State.FAILED))

            assertThat((viewModel.uiState.value.storyExport as StoryExportUiState.Error).message)
                .isEqualTo("Encoder sem buffer de entrada.")
        }
    }

    @Test
    fun `falls back to a generic message when no failure was stored`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.applyWorkInfo(workInfo(state = WorkInfo.State.FAILED))

            assertThat((viewModel.uiState.value.storyExport as StoryExportUiState.Error).message)
                .isEqualTo(com.meucoach.app.story.StoryMessages.EXPORT_FAILED)
        }
    }

    @Test
    fun `does not resurrect the card with a stale emission after cancel`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val viewModel = viewModel("activity-1")
            viewModel.uiState.first { !it.isLoading }

            viewModel.applyWorkInfo(
                workInfo(
                    state = WorkInfo.State.RUNNING,
                    progress = workDataOf(StoryExportWorker.KEY_PROGRESS to 0.3f),
                ),
            )
            viewModel.cancelStory()
            viewModel.applyWorkInfo(
                workInfo(
                    state = WorkInfo.State.RUNNING,
                    progress = workDataOf(StoryExportWorker.KEY_PROGRESS to 0.4f),
                ),
            )

            assertThat(viewModel.uiState.value.storyExport).isEqualTo(StoryExportUiState.Idle)
        }
    }

    private fun workInfo(state: WorkInfo.State, progress: Data = Data.EMPTY, outputData: Data = Data.EMPTY) = WorkInfo(
        id = UUID.randomUUID(),
        state = state,
        tags = setOf(StoryExportWorker.TAG),
        progress = progress,
        outputData = outputData,
        runAttemptCount = 0,
        generation = 0,
    )

    private fun testPalette() = StoryPalette(
        background = 1,
        surface = 2,
        surfaceInset = 3,
        border = 4,
        textPrimary = 5,
        textSecondary = 6,
        textTertiary = 7,
        accent = 8,
        coral = 9,
        onAccent = 10,
        mapCasing = 11,
        mapBaseRoute = 12,
        zone1 = 13,
        zone2 = 14,
        zone3 = 15,
        zone4 = 16,
        zone5 = 17,
    )

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
                        completionEvidence = com.meucoach.core.model.CompletionEvidence.ACTIVITY,
                        activityId = "activity-1",
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
        var calls: Int = 0
        var bounds: BoundingBox? = null
        var width: Int = 0
        var height: Int = 0
        var style: StoryMapStyle? = null

        override suspend fun render(
            boundingBox: BoundingBox,
            width: Int,
            height: Int,
            style: StoryMapStyle,
            pitchDegrees: Float,
            bearingDegrees: Float,
        ): Bitmap {
            this.calls += 1
            this.bounds = boundingBox
            this.width = width
            this.height = height
            this.style = style
            error?.let { throw it }
            return Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        }
    }

    private class FakeStoryExportCoordinator(private val startError: Throwable? = null) : StoryExportCoordinator {
        val infos = MutableStateFlow<List<WorkInfo>>(emptyList())
        var startedActivityId: String? = null
        var startedRecipe: com.meucoach.core.domain.story.StoryRecipe? = null
        var startedPalette: StoryPalette? = null
        var startedMap: Bitmap? = null
        var cancelledActivityId: String? = null

        override suspend fun start(
            activityId: String,
            recipe: com.meucoach.core.domain.story.StoryRecipe,
            mapBackground: Bitmap?,
            palette: StoryPalette,
            isCancelled: () -> Boolean,
        ) {
            startError?.let { throw it }
            startedActivityId = activityId
            startedRecipe = recipe
            startedMap = mapBackground
            startedPalette = palette
        }

        override fun observe(activityId: String): Flow<List<WorkInfo>> = infos

        override fun cancel(activityId: String) {
            cancelledActivityId = activityId
        }
    }
}
