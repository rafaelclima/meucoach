package com.meucoach.app.story

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.epoch.DataEpochStore
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.story.BoundingBox
import com.meucoach.core.domain.story.StoryGeoPoint
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRecipe
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Sport
import kotlinx.coroutines.CancellationException
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
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The worker is the only WorkManager seam of the story export: it maps the
 * processor outcome to a terminal result, a failure message and a
 * notification (QA-03, ADR 0018). The codec loop itself stays in
 * [StoryExportProcessorTest]; here the exporter is a controllable fake and the
 * failure/retry paths run through the real processor gates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryExportWorkerTest {
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ActivityRepository
    private lateinit var failures: StoryExportFailureStore
    private lateinit var epochStore: DataEpochStore
    private lateinit var notifier: StoryNotifier
    private var exported = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
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
        failures = StoryExportFailureStore(context)
        failures.clearAll()
        epochStore = DataEpochStore(context)
        notifier = StoryNotifier(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `fails without an activity id`() {
        runBlocking {
            val result = worker(Data.EMPTY).doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        }
    }

    @Test
    fun `fails when the palette cannot be decoded`() {
        runBlocking {
            val result = worker(inputData(palette = null)).doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        }
    }

    @Test
    fun `completes the export and publishes the uris`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")

            val result = worker(inputData()).doWork()

            assertThat(result).isEqualTo(
                ListenableWorker.Result.success(
                    workDataOf(
                        StoryExportWorker.KEY_VIDEO_URI to "content://video/42",
                        StoryExportWorker.KEY_SAVED_TO_GALLERY to true,
                        StoryExportWorker.KEY_COVER_URI to "content://image/43",
                        StoryExportWorker.KEY_POSTER_URI to "content://image/44",
                    ),
                ),
            )
            assertThat(exported).isTrue()
            assertThat(failures.messageFor("activity-1")).isNull()
            val manager = context.getSystemService(NotificationManager::class.java)
            assertThat(Shadows.shadowOf(manager).allNotifications).isNotEmpty()
        }
    }

    @Test
    fun `reports a missing gps plan as a definitive failure`() {
        runBlocking {
            val result = worker(inputData()).doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
            assertThat(failures.messageFor("activity-1")).isEqualTo(StoryMessages.MISSING_GPS)
            val manager = context.getSystemService(NotificationManager::class.java)
            assertThat(Shadows.shadowOf(manager).allNotifications).isNotEmpty()
        }
    }

    @Test
    fun `retries a transient map failure before the last attempt`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")

            val result = worker(
                inputData(),
                mapRenderer = FakeMapRenderer(error = IllegalStateException("offline")),
            ).doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Retry::class.java)
            assertThat(failures.messageFor("activity-1")).isNull()
        }
    }

    @Test
    fun `gives up after the last attempt`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")

            val result = worker(
                inputData(),
                attempts = 2,
                mapRenderer = FakeMapRenderer(error = IllegalStateException("offline")),
            ).doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
            assertThat(failures.messageFor("activity-1")).isEqualTo(StoryMessages.MAP_UNAVAILABLE)
        }
    }

    @Test
    fun `ignores a completed export after a data reset`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val manager = context.getSystemService(NotificationManager::class.java)
            val before = Shadows.shadowOf(manager).allNotifications.size

            val result = worker(inputData(), onExport = { epochStore.rotate() }).doWork()

            // A stale generation must not notify: success, but silent.
            assertThat(result).isEqualTo(ListenableWorker.Result.success())
            assertThat(Shadows.shadowOf(manager).allNotifications.size).isEqualTo(before)
        }
    }

    @Test
    fun `propagates cancellation instead of reporting a failure`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")

            val thrown = runCatching {
                worker(
                    inputData(),
                    onExport = { throw CancellationException("Geração do story cancelada.") },
                ).doWork()
            }

            assertThat(thrown.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
            assertThat(failures.messageFor("activity-1")).isNull()
        }
    }

    private fun inputData(palette: StoryPalette? = testPalette()): Data {
        val builder = Data.Builder()
            .putString(StoryExportWorker.KEY_ACTIVITY_ID, "activity-1")
            .putString(StoryExportWorker.KEY_RECIPE_JSON, StoryRecipeCodec.encode(StoryRecipe()))
        if (palette != null) {
            builder.putIntArray(StoryPaletteCodec.KEY, StoryPaletteCodec.encode(palette))
        }
        return builder.build()
    }

    private fun worker(
        input: Data,
        attempts: Int = 0,
        mapRenderer: MapBackgroundRenderer = FakeMapRenderer(),
        onExport: () -> Unit = {},
    ): StoryExportWorker {
        val processor = StoryExportProcessor(
            planLoader = StoryPlanLoader(
                repository = repository,
                profiles = ProfileRepository(
                    database = database,
                    clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
                ),
                sessions = StorySessionResolver(
                    TrainingPlanRepository(
                        database = database,
                        clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
                    ),
                ),
            ),
            mapRenderer = mapRenderer,
            exporter = FakeExporter(onExport),
        )
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = if (workerClassName == StoryExportWorker::class.java.name) {
                StoryExportWorker(appContext, workerParameters, processor, notifier, failures, epochStore)
            } else {
                null
            }
        }
        return TestListenableWorkerBuilder<StoryExportWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(input)
            .setRunAttemptCount(attempts)
            .build()
    }

    private inner class FakeExporter(private val onExport: () -> Unit = {}) : StoryVideoExporter {
        override suspend fun export(
            plan: StoryPlan,
            palette: StoryPalette,
            recipe: StoryRecipe,
            mapBackground: StoryMapSnapshot?,
            openingMap: StoryMapSnapshot?,
            finalMap: StoryMapSnapshot?,
            isCancelled: () -> Boolean,
            onProgress: suspend (progress: Float) -> Unit,
        ): StoryExportResult {
            exported = true
            onExport()
            return StoryExportResult(
                uri = Uri.parse("content://video/42"),
                savedToGallery = true,
                coverUri = Uri.parse("content://image/43"),
                posterUri = Uri.parse("content://image/44"),
            )
        }
    }

    private class FakeMapRenderer(private val error: Throwable? = null) : MapBackgroundRenderer {
        override suspend fun render(
            boundingBox: BoundingBox,
            route: List<StoryGeoPoint>,
            width: Int,
            height: Int,
            style: StoryMapStyle,
            pitchDegrees: Float,
            bearingDegrees: Float,
        ): StoryMapSnapshot {
            error?.let { throw it }
            return StoryMapSnapshot(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), emptyList())
        }
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
}
