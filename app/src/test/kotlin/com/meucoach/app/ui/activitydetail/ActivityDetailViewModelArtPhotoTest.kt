package com.meucoach.app.ui.activitydetail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.GetSessionComparison
import com.meucoach.app.story.ArtPhotoImporter
import com.meucoach.app.story.ArtPhotoLoader
import com.meucoach.app.story.ArtPhotoRecord
import com.meucoach.app.story.ArtPhotoStore
import com.meucoach.app.story.ArtPngExport
import com.meucoach.app.story.ArtPngExporter
import com.meucoach.app.story.ArtRecipeStore
import com.meucoach.app.story.MapBackgroundRenderer
import com.meucoach.app.story.StoryExportCoordinator
import com.meucoach.app.story.StoryExportFailureStore
import com.meucoach.app.story.StoryIntroStore
import com.meucoach.app.story.StoryMapSnapshot
import com.meucoach.app.story.StoryPalette
import com.meucoach.app.story.StoryRecipeStore
import com.meucoach.app.story.StorySessionResolver
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtBlock
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtLayout
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.ArtTemplate
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * F5 ViewModel coverage: style × photo combination, import/toggle/remove,
 * missing-photo fallback and the effective-offset clamp (ADR 0061 §2.3/§5.3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivityDetailViewModelArtPhotoTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ActivityRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var executions: SessionExecutionRepository
    private lateinit var prescriptions: DailyPrescriptionRepository
    private var uriCounter = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(StoryRecipeStore.PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(ArtRecipeStore.PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
        File(context.filesDir, ArtPhotoStore.DIRECTORY).deleteRecursively()
        database = Room.inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ActivityRepository(
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
    fun `a photo import lands in photo mode with the waiting style intact`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val viewModel = viewModel("activity-1")
        viewModel.uiState.first { !it.isLoading }

        viewModel.pickPhoto(uriFor(jpegBytes(64, 48)))
        val studio = viewModel.uiState.first {
            it.storyStudio.artRecipe.photo != null && !it.storyStudio.artPhotoBusy
        }.storyStudio

        assertThat(studio.artRecipe.background).isEqualTo(ArtBackgroundMode.PHOTO)
        assertThat(studio.artRecipe.photo?.activityId).isEqualTo("activity-1")
        assertThat(studio.artRecipe.format).isEqualTo(ArtFormat.PORTRAIT_4_5)
        assertThat(studio.artPhotoError).isNull()
    }

    @Test
    fun `a broken import keeps the old photo and reports retryable error`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val viewModel = viewModel("activity-1")
        viewModel.uiState.first { !it.isLoading }
        viewModel.pickPhoto(uriFor(jpegBytes(64, 48)))
        val first = viewModel.uiState.first {
            it.storyStudio.artRecipe.photo != null && !it.storyStudio.artPhotoBusy
        }.storyStudio.artRecipe.photo

        viewModel.pickPhoto(brokenUri())
        val studio = viewModel.uiState.first { it.storyStudio.artPhotoError != null }.storyStudio

        assertThat(studio.artRecipe.photo).isEqualTo(first)
        assertThat(studio.artRecipe.background).isEqualTo(ArtBackgroundMode.PHOTO)
        viewModel.dismissPhotoError()
        assertThat(viewModel.uiState.value.storyStudio.artPhotoError).isNull()
    }

    @Test
    fun `a picker cancel is a no-op`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val viewModel = viewModel("activity-1")
        viewModel.uiState.first { !it.isLoading }

        viewModel.pickPhoto(null)

        val studio = viewModel.uiState.value.storyStudio
        assertThat(studio.artRecipe.background).isEqualTo(ArtBackgroundMode.TRANSPARENT)
        assertThat(studio.artRecipe.photo).isNull()
        assertThat(studio.artPhotoBusy).isFalse()
    }

    @Test
    fun `transparent toggle keeps the photo waiting and photo toggle reuses it`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val viewModel = viewModel("activity-1")
        viewModel.uiState.first { !it.isLoading }
        viewModel.pickPhoto(uriFor(jpegBytes(64, 48)))
        val photo = viewModel.uiState.first {
            it.storyStudio.artRecipe.photo != null && !it.storyStudio.artPhotoBusy
        }.storyStudio.artRecipe.photo

        viewModel.selectBackground(ArtBackgroundMode.TRANSPARENT)
        val transparent = viewModel.uiState.first {
            it.storyStudio.artRecipe.background == ArtBackgroundMode.TRANSPARENT && !it.storyStudio.artPhotoBusy
        }.storyStudio
        assertThat(transparent.artRecipe.photo).isEqualTo(photo)

        viewModel.selectBackground(ArtBackgroundMode.PHOTO)
        val back = viewModel.uiState.first {
            it.storyStudio.artRecipe.background == ArtBackgroundMode.PHOTO && !it.storyStudio.artPhotoBusy
        }.storyStudio
        assertThat(back.artRecipe.photo).isEqualTo(photo)
    }

    @Test
    fun `a photo-less photo toggle stays transient and does not persist`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = viewModel("activity-1")
        viewModel.uiState.first { !it.isLoading }

        viewModel.selectBackground(ArtBackgroundMode.PHOTO)

        val studio = viewModel.uiState.value.storyStudio
        assertThat(studio.artRecipe.background).isEqualTo(ArtBackgroundMode.PHOTO)
        assertThat(studio.artRecipe.photo).isNull()
        assertThat(ArtPhotoStore(context).load("activity-1").photo).isNull()
    }

    @Test
    fun `style edits keep the photo while the style store stays photo-free`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val artStore = ArtRecipeStore(context)
        val viewModel = viewModel("activity-1", artStore = artStore)
        viewModel.uiState.first { !it.isLoading }
        viewModel.pickPhoto(uriFor(jpegBytes(64, 48)))
        viewModel.uiState.first {
            it.storyStudio.artRecipe.photo != null && !it.storyStudio.artPhotoBusy
        }

        viewModel.selectArtFormat(ArtFormat.SQUARE)

        val studio = viewModel.uiState.value.storyStudio
        assertThat(studio.artRecipe.format).isEqualTo(ArtFormat.SQUARE)
        assertThat(studio.artRecipe.photo).isNotNull()
        assertThat(artStore.load().format).isEqualTo(ArtFormat.PORTRAIT_4_5)
    }

    @Test
    fun `a missing photo file falls back to transparent with a one-time warning`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ArtPhotoStore(context)
        val viewModel = viewModel("activity-1", photoStore = store)
        viewModel.uiState.first { !it.isLoading }
        viewModel.pickPhoto(uriFor(jpegBytes(64, 48)))
        val photo = viewModel.uiState.first {
            it.storyStudio.artRecipe.photo != null && !it.storyStudio.artPhotoBusy
        }.storyStudio.artRecipe.photo
        checkNotNull(photo)
        // Simulate the file vanishing outside the store (disk sweep, user data).
        // The metadata still references the photo, so the next load must fall
        // back to transparent with the one-time warning.
        File(context.filesDir, "${ArtPhotoStore.DIRECTORY}/${photo.photoId}.jpg").delete()

        val reloaded = viewModel("activity-1", photoStore = ArtPhotoStore(context))
        val studio = reloaded.uiState.first { !it.isLoading }.storyStudio

        assertThat(studio.artRecipe.background).isEqualTo(ArtBackgroundMode.TRANSPARENT)
        assertThat(studio.artRecipe.photo).isNull()
        assertThat(studio.artMissingPhotoWarning).isTrue()
        reloaded.dismissMissingPhotoWarning()
        assertThat(reloaded.uiState.value.storyStudio.artMissingPhotoWarning).isFalse()
    }

    @Test
    fun `remove asks first and then returns to transparent`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val viewModel = viewModel("activity-1")
        viewModel.uiState.first { !it.isLoading }
        viewModel.pickPhoto(uriFor(jpegBytes(64, 48)))
        viewModel.uiState.first {
            it.storyStudio.artRecipe.photo != null && !it.storyStudio.artPhotoBusy
        }

        viewModel.requestRemovePhoto()
        assertThat(viewModel.uiState.value.storyStudio.artShowRemoveConfirm).isTrue()
        viewModel.cancelRemovePhoto()
        assertThat(viewModel.uiState.value.storyStudio.artShowRemoveConfirm).isFalse()

        viewModel.requestRemovePhoto()
        viewModel.removePhoto()
        val studio = viewModel.uiState.first {
            it.storyStudio.artRecipe.photo == null && !it.storyStudio.artPhotoBusy
        }.storyStudio
        assertThat(studio.artRecipe.background).isEqualTo(ArtBackgroundMode.TRANSPARENT)
        assertThat(studio.artShowRemoveConfirm).isFalse()
    }

    @Test
    fun `template scale and block selection persist in the effective recipe`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val viewModel = viewModel("activity-1")
        viewModel.uiState.first { !it.isLoading }

        viewModel.selectTemplate(ArtTemplate.BOTTOM)
        viewModel.selectBlock(ArtBlock.ROUTE)
        viewModel.setTextScale(130)

        val studio = viewModel.uiState.value.storyStudio
        assertThat(studio.artRecipe.layout.template).isEqualTo(ArtTemplate.BOTTOM)
        assertThat(studio.artSelectedBlock).isEqualTo(ArtBlock.ROUTE)
        assertThat(studio.artRecipe.layout.textScalePercent).isEqualTo(130)
    }

    @Test
    fun `text scale rejects out-of-range values`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val viewModel = viewModel("activity-1")
        viewModel.uiState.first { !it.isLoading }

        viewModel.setTextScale(75)
        viewModel.setTextScale(135)

        assertThat(viewModel.uiState.value.storyStudio.artRecipe.layout.textScalePercent).isEqualTo(100)
    }

    @Test
    fun `a far nudge is clamped to the effective offset`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val viewModel = viewModel("activity-1")
        viewModel.uiState.first { !it.isLoading }

        viewModel.selectBlock(ArtBlock.PACE_TIME)
        repeat(30) { viewModel.nudgeBlock(ArtBlock.PACE_TIME, 0.02f, 0f) }

        val offset = viewModel.uiState.value.storyStudio.artRecipe.layout.paceTimeOffset
        assertThat(offset.dx).isLessThan(0.5f)
        // The stored value is already effective: clamping it again is stable.
        val second = viewModel.effectiveOffsetFor(ArtBlock.PACE_TIME, offset)
        if (second != null) {
            assertThat(second.dx).isCloseTo(offset.dx, org.assertj.core.api.Assertions.within(0.02f))
        }
    }

    @Test
    fun `photo save publishes a jpg and keeps the style store photo-free`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val artStore = ArtRecipeStore(context)
        val exporter = FakeArtExporter()
        val viewModel = viewModel("activity-1", artStore = artStore, exporter = exporter)
        viewModel.uiState.first { !it.isLoading }
        viewModel.pickPhoto(uriFor(jpegBytes(64, 48)))
        viewModel.uiState.first {
            it.storyStudio.artRecipe.photo != null && !it.storyStudio.artPhotoBusy
        }

        viewModel.saveArtPng()

        val export = viewModel.uiState.value.storyStudio.artExport
        assertThat(export).isInstanceOf(ArtExportUiState.Done::class.java)
        val done = export as ArtExportUiState.Done
        assertThat(done.forShare).isFalse()
        assertThat(done.savedToGallery).isTrue()
        val exported = exporter.exports.single().second
        assertThat(exported.background).isEqualTo(ArtBackgroundMode.PHOTO)
        assertThat(exported.photo).isNotNull()
        assertThat(artStore.load().background).isEqualTo(ArtBackgroundMode.TRANSPARENT)
        assertThat(artStore.load().photo).isNull()
    }

    @Test
    fun `photo share is consumed once and retry keeps the share target`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val exporter = FakeArtExporter()
        val viewModel = viewModel("activity-1", exporter = exporter)
        viewModel.uiState.first { !it.isLoading }
        viewModel.pickPhoto(uriFor(jpegBytes(64, 48)))
        viewModel.uiState.first {
            it.storyStudio.artRecipe.photo != null && !it.storyStudio.artPhotoBusy
        }

        exporter.result = ArtPngExport.Failure("Permission denied")
        viewModel.shareArt()
        assertThat(viewModel.uiState.value.storyStudio.artExport)
            .isEqualTo(ArtExportUiState.Error("Não foi possível gerar a imagem da arte. Tente novamente."))

        exporter.result = ArtPngExport.Published(
            uri = Uri.parse("content://media/external/images/9"),
            savedToGallery = true,
            reused = false,
        )
        viewModel.retryArtExport()

        val done = viewModel.uiState.value.storyStudio.artExport as ArtExportUiState.Done
        assertThat(done.forShare).isTrue()
        assertThat(exporter.exports).hasSize(2)
        assertThat(exporter.exports.map { it.first }).containsExactly(true, true)

        viewModel.consumeArtShare()
        assertThat(viewModel.uiState.value.storyStudio.artExport).isEqualTo(ArtExportUiState.Idle)
    }

    @Test
    fun `photo export changed during rendering is stale`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val exporter = FakeArtExporter()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        exporter.gate = gate
        val viewModel = viewModel("activity-1", exporter = exporter)
        viewModel.uiState.first { !it.isLoading }
        viewModel.pickPhoto(uriFor(jpegBytes(64, 48)))
        viewModel.uiState.first {
            it.storyStudio.artRecipe.photo != null && !it.storyStudio.artPhotoBusy
        }

        viewModel.shareArt()
        viewModel.selectArtTextColor(ArtColor.CYAN)
        gate.complete(Unit)

        assertThat(viewModel.uiState.value.storyStudio.artExport).isEqualTo(ArtExportUiState.Stale)
    }

    @Test
    fun `an exporter stale result maps to the stale state`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val exporter = FakeArtExporter()
        exporter.result = ArtPngExport.Stale
        val viewModel = viewModel("activity-1", exporter = exporter)
        viewModel.uiState.first { !it.isLoading }

        viewModel.saveArtPng()

        assertThat(viewModel.uiState.value.storyStudio.artExport).isEqualTo(ArtExportUiState.Stale)
    }

    @Test
    fun `crop open update and apply persist the draft`() = runBlocking<Unit> {
        repository.saveImportedActivity(gpsActivity(), "sha")
        val viewModel = viewModel("activity-1")
        viewModel.uiState.first { !it.isLoading }
        viewModel.pickPhoto(uriFor(jpegBytes(200, 200)))
        viewModel.uiState.first {
            it.storyStudio.artRecipe.photo != null && !it.storyStudio.artPhotoBusy
        }

        viewModel.openCrop()
        assertThat(viewModel.uiState.value.storyStudio.artCropEditing).isTrue()
        viewModel.updateCrop(0.6f, 0.4f, 2f)
        assertThat(viewModel.uiState.value.storyStudio.artCropDraft.focusX).isEqualTo(0.6f)
        viewModel.applyCrop()
        val studio = viewModel.uiState.first {
            !it.storyStudio.artCropEditing && !it.storyStudio.artPhotoBusy
        }.storyStudio
        assertThat(studio.artRecipe.photo?.crop?.focusX).isEqualTo(0.6f)
        assertThat(studio.artRecipe.photo?.crop?.zoom).isEqualTo(2f)
        viewModel.openCrop()
        viewModel.cancelCrop()
        assertThat(viewModel.uiState.value.storyStudio.artCropEditing).isFalse()
    }

    private fun viewModel(
        activityId: String,
        artStore: ArtRecipeStore = ArtRecipeStore(ApplicationProvider.getApplicationContext()),
        photoStore: ArtPhotoStore? = null,
        exporter: ArtPngExporter = FakeArtExporter(),
    ): ActivityDetailViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolvedPhotoStore = photoStore ?: ArtPhotoStore(context)
        return ActivityDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(ActivityDetailViewModel.ARG_ACTIVITY_ID to activityId),
            ),
            repository = repository,
            profileRepository = profiles,
            sessions = StorySessionResolver(plans),
            mapRenderer = FakeMapRenderer(),
            storyCoordinator = FakeCoordinator(),
            storyFailures = StoryExportFailureStore(context),
            storyIntro = StoryIntroStore(context),
            storyRecipe = StoryRecipeStore(context),
            artRecipeStore = artStore,
            artPhotoStore = resolvedPhotoStore,
            artPhotoImporter = ArtPhotoImporter(context, resolvedPhotoStore),
            artPhotoLoader = ArtPhotoLoader(resolvedPhotoStore),
            appContext = context,
            artPngExporter = exporter,
            profilePhotos = com.meucoach.app.profile.ProfilePhotoStore(context),
            timeProvider = FakeTimeProvider(),
            comparisons = GetSessionComparison(repository, executions, prescriptions, plans),
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

    private fun jpegBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val file = File(
            ApplicationProvider.getApplicationContext<Context>().cacheDir,
            "art-vm-source-${uriCounter++}.jpg",
        )
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        bitmap.recycle()
        return file.readBytes().also { file.delete() }
    }

    private fun uriFor(bytes: ByteArray): Uri {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://test/art-vm/${uriCounter++}")
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            ByteArrayInputStream(bytes)
        }
        return uri
    }

    private fun brokenUri(): Uri {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://test/art-vm/broken-${uriCounter++}")
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            throw java.io.FileNotFoundException("revoked")
        }
        return uri
    }

    private class FakeMapRenderer : MapBackgroundRenderer {
        override suspend fun render(
            boundingBox: com.meucoach.core.domain.story.BoundingBox,
            route: List<com.meucoach.core.domain.story.StoryGeoPoint>,
            width: Int,
            height: Int,
            style: com.meucoach.core.domain.story.StoryMapStyle,
            pitchDegrees: Float,
            bearingDegrees: Float,
        ): StoryMapSnapshot = StoryMapSnapshot(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), emptyList())
    }

    private class FakeCoordinator : StoryExportCoordinator {
        private val infos = kotlinx.coroutines.flow.MutableStateFlow<List<androidx.work.WorkInfo>>(emptyList())
        override fun observe(activityId: String) = infos
        override suspend fun start(
            activityId: String,
            recipe: com.meucoach.core.domain.story.StoryRecipe,
            mapBackground: StoryMapSnapshot?,
            palette: StoryPalette,
            isCancelled: () -> Boolean,
        ) = Unit

        override fun cancel(activityId: String) = Unit
    }

    private class FakeArtExporter : ArtPngExporter {
        var result: ArtPngExport = ArtPngExport.Published(
            uri = Uri.parse("content://media/external/images/1"),
            savedToGallery = true,
            reused = false,
        )
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        val exports = mutableListOf<Pair<Boolean, ArtRecipe>>()

        override suspend fun export(
            plan: com.meucoach.core.domain.story.StoryPlan,
            localDate: java.time.LocalDate?,
            recipe: ArtRecipe,
            activityId: String,
            forShare: Boolean,
        ): ArtPngExport {
            exports += forShare to recipe
            gate?.await()
            return result
        }
    }
}
