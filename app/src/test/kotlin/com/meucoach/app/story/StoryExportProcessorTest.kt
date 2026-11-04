package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Sport
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The processor is the whole background export minus WorkManager plumbing:
 * every gate (GPS, staged map, fallback render, progress, ETA) is covered here
 * without running the codec pipeline (ADR 0018).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryExportProcessorTest {
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ActivityRepository
    private val profiles: ProfileRepository by lazy {
        ProfileRepository(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
        )
    }
    private val plans: TrainingPlanRepository by lazy {
        TrainingPlanRepository(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
        )
    }
    private val mapRenderer = FakeMapRenderer()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `renders the snapshot when nothing was staged`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val processor = processor(FakeExporter(listOf(0.5f, 1f)))

            val outcome = processor.export(
                activityId = "activity-1",
                palette = testPalette(),
                mapPath = null,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(mapRenderer.width).isEqualTo(StoryVideoExporter.mapWidth())
            assertThat(mapRenderer.height).isEqualTo(StoryVideoExporter.mapHeight())
        }
    }

    @Test
    fun `prefers the staged map over a tile download`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val staged = stagedMap()
            val processor = processor(FakeExporter(listOf(1f)))

            val outcome = processor.export(
                activityId = "activity-1",
                palette = testPalette(),
                mapPath = staged.absolutePath,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(mapRenderer.calls).isEqualTo(0)
            // Coordinates do not linger in the cache after the export.
            assertThat(staged.exists()).isFalse()
        }
    }

    @Test
    fun `falls back to rendering when the staged map is gone`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val processor = processor(FakeExporter(listOf(1f)))

            val outcome = processor.export(
                activityId = "activity-1",
                palette = testPalette(),
                mapPath = File(context.cacheDir, "stories/missing.png").absolutePath,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(mapRenderer.calls).isEqualTo(1)
        }
    }

    @Test
    fun `reports missing gps as a definitive failure`() {
        runBlocking {
            repository.saveImportedActivity(averageActivity(), "sha")
            val processor = processor(FakeExporter(emptyList()))

            val outcome = processor.export(
                activityId = "activity-1",
                palette = testPalette(),
                mapPath = null,
                onProgress = { _, _ -> },
            )

            val failed = outcome as StoryExportOutcome.Failed
            assertThat(failed.message).contains("rota GPS")
        }
    }

    @Test
    fun `asks for a retry when the snapshot fails`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val processor =
                processor(FakeExporter(emptyList()), FakeMapRenderer(error = IllegalStateException("offline")))

            val outcome = processor.export(
                activityId = "activity-1",
                palette = testPalette(),
                mapPath = null,
                onProgress = { _, _ -> },
            )

            val retry = outcome as StoryExportOutcome.Retry
            assertThat(retry.message).contains("mapa")
        }
    }

    @Test
    fun `forwards fraction and eta from the codec loop`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            var nowMs = 1_000L
            val processor = processor(FakeExporter(listOf(0.25f, 0.5f)))
            val updates = mutableListOf<Pair<Float, Int?>>()

            processor.export(
                activityId = "activity-1",
                palette = testPalette(),
                mapPath = stagedMap().absolutePath,
                onProgress = { fraction, etaSec -> updates += fraction to etaSec },
                atMs = { nowMs.also { nowMs += 500 } },
            )

            assertThat(updates.map { it.first }).containsExactly(0.25f, 0.5f)
            // First sample has no rate yet; second: rate 0.5/s → 1 s remaining.
            assertThat(updates).containsExactly(0.25f to null, 0.5f to 1)
        }
    }

    private fun processor(
        exporter: StoryVideoExporter,
        mapRenderer: FakeMapRenderer = this.mapRenderer,
    ): StoryExportProcessor = StoryExportProcessor(
        planLoader = StoryPlanLoader(
            repository = repository,
            profiles = profiles,
            sessions = StorySessionResolver(plans),
        ),
        mapRenderer = mapRenderer,
        exporter = exporter,
    )

    private fun stagedMap(): File {
        val directory = File(context.cacheDir, "stories").apply { mkdirs() }
        val file = File(directory, "staged.png")
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        return file
    }

    private class FakeExporter(private val fractions: List<Float>) : StoryVideoExporter {
        var renderedPlan: StoryPlan? = null
        var renderedBackground: Bitmap? = null

        override suspend fun export(
            plan: StoryPlan,
            palette: StoryPalette,
            mapBackground: Bitmap?,
            isCancelled: () -> Boolean,
            onProgress: suspend (progress: Float) -> Unit,
        ): StoryExportResult {
            renderedPlan = plan
            renderedBackground = mapBackground
            fractions.forEach { onProgress(it) }
            return StoryExportResult(uri = android.net.Uri.parse("content://video/42"), savedToGallery = false)
        }
    }

    private class FakeMapRenderer(private val error: Throwable? = null) : MapBackgroundRenderer {
        var calls: Int = 0
        var width: Int = 0
        var height: Int = 0
        var style: StoryMapStyle? = null

        override suspend fun render(boundingBox: BoundingBox, width: Int, height: Int, style: StoryMapStyle): Bitmap {
            calls += 1
            this.width = width
            this.height = height
            this.style = style
            error?.let { throw it }
            return Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        }
    }

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

    private fun averageActivity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-10-24T08:17:10Z"),
        durationSec = 1_800,
        distanceM = 5_000.0,
        samples = listOf(
            ActivitySample(offsetSec = 0, heartRate = 140, speedMps = 2.7),
            ActivitySample(offsetSec = 900, heartRate = 150, speedMps = 2.8),
            ActivitySample(offsetSec = 1_800, heartRate = 160, speedMps = 2.9),
        ),
    )
}
