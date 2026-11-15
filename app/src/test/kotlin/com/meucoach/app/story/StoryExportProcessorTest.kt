package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.story.BoundingBox
import com.meucoach.core.domain.story.StoryDuration
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryGeoPoint
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRecipe
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
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = null,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(mapRenderer.widths).contains(StoryVideoExporter.mapWidth())
            assertThat(mapRenderer.widths).contains(StoryMapScript.openingWidth())
            assertThat(mapRenderer.widths).contains(StoryMapScript.finalWidth())
            assertThat(mapRenderer.pitches).contains(
                StoryMapScript.FOLLOW_PITCH_DEGREES,
                StoryMapScript.OPENING_PITCH_DEGREES,
                StoryMapScript.FINAL_PITCH_DEGREES,
            )
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
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = staged.absolutePath,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            // Only the bookends are rendered; the follow shot is staged.
            assertThat(mapRenderer.widths).containsExactly(
                StoryMapScript.openingWidth(),
                StoryMapScript.finalWidth(),
            )
            // Coordinates do not linger in the cache after the export.
            assertThat(staged.exists()).isFalse()
            assertThat(StoryMapSnapshotStore.routeFile(staged).exists()).isFalse()
        }
    }

    @Test
    fun `staged map carries the exact route pixels into the export`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val staged = stagedMap()
            val exporter = FakeExporter(listOf(1f))

            processor(exporter).export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = staged.absolutePath,
                onProgress = { _, _ -> },
            )

            assertThat(exporter.renderedBackground?.routePixels)
                .containsExactly(android.graphics.PointF(1f, 2f))
        }
    }

    @Test
    fun `re-renders when the staged map has no route sidecar`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val staged = stagedMap(withRoute = false)
            val processor = processor(FakeExporter(listOf(1f)))

            val outcome = processor.export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = staged.absolutePath,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(mapRenderer.calls).isEqualTo(3)
        }
    }

    @Test
    fun `falls back to rendering when the staged map is gone`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val processor = processor(FakeExporter(listOf(1f)))

            val outcome = processor.export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = File(context.cacheDir, "stories/missing.png").absolutePath,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(mapRenderer.calls).isEqualTo(3)
        }
    }

    @Test
    fun `reports missing gps as a definitive failure`() {
        runBlocking {
            repository.saveImportedActivity(averageActivity(), "sha")
            val processor = processor(FakeExporter(emptyList()))

            val outcome = processor.export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
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
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = null,
                onProgress = { _, _ -> },
            )

            val retry = outcome as StoryExportOutcome.Retry
            assertThat(retry.message).contains("mapa")
        }
    }

    @Test
    fun `reports map progress before the encode starts`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val processor = processor(FakeExporter(listOf(0.5f)))
            val updates = mutableListOf<Pair<Float, Int?>>()

            processor.export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = null,
                onProgress = { fraction, etaSec -> updates += fraction to etaSec },
            )

            // 0–10% map (plan, follow, bookends), 10–100% encode (ENC-06).
            assertThat(updates.map { it.first }).containsExactly(0.02f, 0.05f, 0.10f, 0.55f)
            assertThat(updates.take(3).map { it.second }).containsOnly(null as Int?)
            val fractions = updates.map { it.first }
            assertThat(fractions).isSorted
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
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = stagedMap().absolutePath,
                onProgress = { fraction, etaSec -> updates += fraction to etaSec },
                atMs = { nowMs.also { nowMs += 500 } },
            )

            // The encode range maps to 10–100%: 0.25 → 0.325, 0.5 → 0.55.
            // Rescaling preserves the ETA math, so the expectations hold.
            assertThat(updates.map { it.first }).containsExactly(0.02f, 0.05f, 0.10f, 0.325f, 0.55f)
            // Map samples carry no ETA; first encode sample warms the rate;
            // second: rate 0.45/s → 1 s remaining.
            assertThat(updates.map { it.second }).containsExactly(null, null, null, null, 1)
        }
    }

    @Test
    fun `propagates cancellation instead of reporting a failure`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val processor = processor(
                FakeExporter(
                    fractions = emptyList(),
                    error = kotlinx.coroutines.CancellationException("Geração do story cancelada."),
                ),
            )

            val outcome = runCatching {
                processor.export(
                    activityId = "activity-1",
                    recipe = StoryRecipe(),
                    palette = testPalette(),
                    mapPath = null,
                    onProgress = { _, _ -> },
                )
            }

            // Cancellation travels as CancellationException (the worker turns it
            // into CANCELLED); it must never surface as a Failed outcome with a
            // notification, and the partial file stays the exporter's job.
            assertThat(outcome.exceptionOrNull())
                .isInstanceOf(kotlinx.coroutines.CancellationException::class.java)
        }
    }

    @Test
    fun `forwards the poster with the completed export`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val poster = Uri.parse("content://media/external/images/media/44")
            val processor = processor(FakeExporter(listOf(1f), poster))

            val outcome = processor.export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = null,
                onProgress = { _, _ -> },
            )

            val completed = outcome as StoryExportOutcome.Completed
            assertThat(completed.result.posterUri).isEqualTo(poster)
        }
    }

    @Test
    fun `exports with the recipe passed by the worker`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val exporter = FakeExporter(listOf(1f))
            val processor = processor(exporter)
            val recipe = StoryRecipe(
                extras = listOf(StoryExtra.DATE),
                duration = StoryDuration.SEC_45,
            )

            val outcome = processor.export(
                activityId = "activity-1",
                recipe = recipe,
                palette = testPalette(),
                mapPath = null,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(exporter.renderedRecipe).isEqualTo(recipe)
            assertThat(exporter.renderedPlan?.durationSec).isEqualTo(45f)
        }
    }

    @Test
    fun `uses the recipe map style instead of the palette`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val exporter = FakeExporter(listOf(1f))
            val palette = testPalette().copy(mapStyle = StoryMapStyle.MEUCOACH_DARK)
            val recipe = StoryRecipe(mapStyle = StoryMapStyle.MEUCOACH_LIGHT)

            val outcome = processor(exporter).export(
                activityId = "activity-1",
                recipe = recipe,
                palette = palette,
                mapPath = null,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(mapRenderer.styles).isNotEmpty()
            assertThat(mapRenderer.styles).allMatch { it == StoryMapStyle.MEUCOACH_LIGHT }
        }
    }

    @Test
    fun `recycles map bitmaps after the export`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val exporter = FakeExporter(listOf(1f))

            val outcome = processor(exporter).export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = null,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(exporter.renderedBackground?.bitmap?.isRecycled).isTrue()
            assertThat(exporter.renderedOpening?.bitmap?.isRecycled).isTrue()
            assertThat(exporter.renderedFinal?.bitmap?.isRecycled).isTrue()
        }
    }

    @Test
    fun `low ram renders two keyframes at the constrained budget`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val lowRam = FakeMapRenderer(lowRam = true)
            val processor = processor(FakeExporter(listOf(1f)), lowRam)

            val outcome = processor.export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = null,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            // 1.25× follow + final only: the opening bookend stays on the
            // follow snapshot instead of costing a third render.
            assertThat(lowRam.widths).containsExactly(
                StoryVideoExporter.mapWidth(lowRam = true),
                StoryMapScript.finalWidth(lowRam = true),
            )
            assertThat(lowRam.pitches).containsExactly(
                StoryMapScript.FOLLOW_PITCH_DEGREES,
                StoryMapScript.FINAL_PITCH_DEGREES,
            )
        }
    }

    @Test
    fun `renders three keyframes with their own pitches`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val exporter = FakeExporter(listOf(1f))
            processor(exporter).export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = null,
                onProgress = { _, _ -> },
            )

            assertThat(exporter.renderedBackground).isNotNull()
            assertThat(exporter.renderedOpening).isNotNull()
            assertThat(exporter.renderedFinal).isNotNull()
            // Follow is the full-res shot; bookends share the smaller size.
            assertThat(mapRenderer.widths).containsExactly(
                StoryVideoExporter.mapWidth(),
                StoryMapScript.openingWidth(),
                StoryMapScript.finalWidth(),
            )
            assertThat(mapRenderer.pitches).containsExactly(
                StoryMapScript.FOLLOW_PITCH_DEGREES,
                StoryMapScript.OPENING_PITCH_DEGREES,
                StoryMapScript.FINAL_PITCH_DEGREES,
            )
        }
    }

    @Test
    fun `keeps the export when a bookend render fails`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val flaky = FakeMapRenderer(failPitch = StoryMapScript.FINAL_PITCH_DEGREES)
            val exporter = FakeExporter(listOf(1f))

            val outcome = processor(exporter, flaky).export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = null,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(exporter.renderedBackground).isNotNull()
            assertThat(exporter.renderedOpening).isNotNull()
            assertThat(exporter.renderedFinal).isNull()
        }
    }

    @Test
    fun `stages bookends into the variant dir on a cache miss`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val variantDir = StoryMapVariant.variantDir(context.cacheDir, "activity-1", "bookend-miss-1")
            variantDir.deleteRecursively()
            val follow = StoryMapVariant.followFile(variantDir)
            variantDir.mkdirs()
            val followBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            StoryMapSnapshotStore.write(
                follow,
                StoryMapSnapshot(followBitmap, listOf(android.graphics.PointF(1f, 2f))),
            )
            followBitmap.recycle()
            StoryMapVariant.writeManifest(
                variantDir,
                StoryMapVariant.Manifest(
                    activityId = "activity-1",
                    variantHash = "bookend-miss-1",
                    style = StoryMapStyle.MEUCOACH_DARK,
                    follow = StoryMapVariant.Entry(StoryMapVariant.FOLLOW_NAME, 4, 4),
                ),
            )
            val sized = SizedFakeMapRenderer()
            val exporter = FakeExporter(listOf(1f))

            val outcome = processor(exporter, sized).export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = follow.absolutePath,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(sized.calls).isEqualTo(2)
            assertThat(exporter.renderedOpening).isNotNull()
            assertThat(exporter.renderedFinal).isNotNull()
            val opening = StoryMapVariant.openingFile(variantDir)
            val final = StoryMapVariant.finalFile(variantDir)
            assertThat(opening.isFile).isTrue()
            assertThat(final.isFile).isTrue()
            assertThat(StoryMapSnapshotStore.routeFile(opening).isFile).isTrue()
            assertThat(StoryMapSnapshotStore.routeFile(final).isFile).isTrue()
            assertThat(opening.inputStream().use { it.read() }).isEqualTo(0xFF)
            val manifest = StoryMapVariant.readManifest(variantDir)
            assertThat(manifest?.opening?.width).isEqualTo(StoryMapScript.openingWidth())
            assertThat(manifest?.final?.height).isEqualTo(StoryMapScript.finalHeight())
            // Variant cache stays for the reexport; only legacy flat staging is deleted.
            assertThat(follow.exists()).isTrue()
            exporter.renderedOpening?.bitmap?.recycle()
            exporter.renderedFinal?.bitmap?.recycle()
        }
    }

    @Test
    fun `reuses staged bookends without re-rendering`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val variantDir = StoryMapVariant.variantDir(context.cacheDir, "activity-1", "bookend-hit-1")
            variantDir.deleteRecursively()
            val follow = StoryMapVariant.followFile(variantDir)
            variantDir.mkdirs()
            val followBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            StoryMapSnapshotStore.write(
                follow,
                StoryMapSnapshot(followBitmap, listOf(android.graphics.PointF(1f, 2f))),
            )
            followBitmap.recycle()
            StoryMapVariant.writeManifest(
                variantDir,
                StoryMapVariant.Manifest(
                    activityId = "activity-1",
                    variantHash = "bookend-hit-1",
                    style = StoryMapStyle.MEUCOACH_DARK,
                    follow = StoryMapVariant.Entry(StoryMapVariant.FOLLOW_NAME, 4, 4),
                ),
            )
            val firstExporter = FakeExporter(listOf(1f))
            processor(firstExporter, SizedFakeMapRenderer()).export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = follow.absolutePath,
                onProgress = { _, _ -> },
            )
            firstExporter.renderedOpening?.bitmap?.recycle()
            firstExporter.renderedFinal?.bitmap?.recycle()

            val secondRenderer = SizedFakeMapRenderer()
            val secondExporter = FakeExporter(listOf(1f))
            val outcome = processor(secondExporter, secondRenderer).export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = follow.absolutePath,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(secondRenderer.calls).isEqualTo(0)
            assertThat(secondExporter.renderedOpening).isNotNull()
            assertThat(secondExporter.renderedFinal).isNotNull()
            assertThat(secondExporter.renderedOpening!!.bitmap.width)
                .isEqualTo(StoryMapScript.openingWidth())
            secondExporter.renderedOpening?.bitmap?.recycle()
            secondExporter.renderedFinal?.bitmap?.recycle()
        }
    }

    @Test
    fun `corrupt manifest falls back to rendering bookends`() {
        runBlocking {
            repository.saveImportedActivity(gpsActivity(), "sha")
            val variantDir = StoryMapVariant.variantDir(context.cacheDir, "activity-1", "bookend-corrupt-1")
            variantDir.deleteRecursively()
            val follow = StoryMapVariant.followFile(variantDir)
            variantDir.mkdirs()
            val followBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            StoryMapSnapshotStore.write(
                follow,
                StoryMapSnapshot(followBitmap, listOf(android.graphics.PointF(1f, 2f))),
            )
            followBitmap.recycle()
            StoryMapVariant.manifestFile(variantDir).writeText("not json")
            val exporter = FakeExporter(listOf(1f))

            val outcome = processor(exporter, FakeMapRenderer()).export(
                activityId = "activity-1",
                recipe = StoryRecipe(),
                palette = testPalette(),
                mapPath = follow.absolutePath,
                onProgress = { _, _ -> },
            )

            assertThat(outcome).isInstanceOf(StoryExportOutcome.Completed::class.java)
            assertThat(exporter.renderedOpening).isNotNull()
            assertThat(exporter.renderedFinal).isNotNull()
        }
    }

    private fun processor(
        exporter: StoryVideoExporter,
        mapRenderer: MapBackgroundRenderer = this.mapRenderer,
    ): StoryExportProcessor = StoryExportProcessor(
        planLoader = StoryPlanLoader(
            repository = repository,
            profiles = profiles,
            sessions = StorySessionResolver(plans),
        ),
        mapRenderer = mapRenderer,
        exporter = exporter,
    )

    private fun stagedMap(withRoute: Boolean = true): File {
        val directory = File(context.cacheDir, "stories").apply { mkdirs() }
        val file = File(directory, "staged.png")
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        if (withRoute) {
            StoryMapSnapshotStore.write(file, StoryMapSnapshot(bitmap, listOf(android.graphics.PointF(1f, 2f))))
        } else {
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
        bitmap.recycle()
        return file
    }

    private class FakeExporter(
        private val fractions: List<Float>,
        private val posterUri: Uri? = null,
        private val error: Throwable? = null,
    ) : StoryVideoExporter {
        var renderedPlan: StoryPlan? = null
        var renderedRecipe: StoryRecipe? = null
        var renderedBackground: StoryMapSnapshot? = null
        var renderedOpening: StoryMapSnapshot? = null
        var renderedFinal: StoryMapSnapshot? = null

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
            error?.let { throw it }
            renderedPlan = plan
            renderedRecipe = recipe
            renderedBackground = mapBackground
            renderedOpening = openingMap
            renderedFinal = finalMap
            fractions.forEach { onProgress(it) }
            return StoryExportResult(
                uri = Uri.parse("content://video/42"),
                savedToGallery = false,
                posterUri = posterUri,
            )
        }
    }

    private class FakeMapRenderer(
        private val error: Throwable? = null,
        private val failPitch: Float? = null,
        override val lowRam: Boolean = false,
    ) : MapBackgroundRenderer {
        var calls: Int = 0
        var width: Int = 0
        var height: Int = 0
        var style: StoryMapStyle? = null
        val widths = mutableListOf<Int>()
        val pitches = mutableListOf<Float>()
        val styles = mutableListOf<StoryMapStyle>()

        override suspend fun render(
            boundingBox: BoundingBox,
            route: List<StoryGeoPoint>,
            width: Int,
            height: Int,
            style: StoryMapStyle,
            pitchDegrees: Float,
            bearingDegrees: Float,
        ): StoryMapSnapshot {
            calls += 1
            this.width = width
            this.height = height
            this.style = style
            widths += width
            pitches += pitchDegrees
            styles += style
            error?.let { throw it }
            failPitch?.let { failing ->
                if (pitchDegrees == failing) throw IllegalStateException("tile offline")
            }
            return StoryMapSnapshot(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), emptyList())
        }
    }

    /** Renders bookends at their real staged size so the variant cache can validate them. */
    private class SizedFakeMapRenderer : MapBackgroundRenderer {
        var calls: Int = 0

        override suspend fun render(
            boundingBox: BoundingBox,
            route: List<StoryGeoPoint>,
            width: Int,
            height: Int,
            style: StoryMapStyle,
            pitchDegrees: Float,
            bearingDegrees: Float,
        ): StoryMapSnapshot {
            calls += 1
            return StoryMapSnapshot(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888), emptyList())
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
