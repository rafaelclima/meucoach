package com.meucoach.app.story

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtPngExportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var publisher: GalleryImagePublisher
    private lateinit var exporter: CanvasArtPngExporter

    @Before
    fun setUp() {
        context.cacheDir.resolve("art").deleteRecursively()
        publisher = MediaStoreGalleryImagePublisher(context)
        exporter = CanvasArtPngExporter(context, publisher)
    }

    @Test
    fun `publishes a png into pictures meucoach`() {
        val png = pngFile("meucoach-story-test-arte-4x5.png")

        val result = publisher.publish(png)

        assertThat(result.savedToGallery).isTrue()
        val resolver = context.contentResolver
        resolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(android.provider.MediaStore.Images.Media.DISPLAY_NAME),
            "${android.provider.MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf("meucoach-story-test-arte-4x5.png"),
            null,
        )!!.use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo("meucoach-story-test-arte-4x5.png")
        }
    }

    @Test
    fun `a failed publish leaves no pending row behind`() {
        val missing = File(context.cacheDir, "art/nao-existe.png")

        val result = runCatching { publisher.publish(missing) }

        assertThat(result.isFailure).isTrue()
        val resolver = context.contentResolver
        resolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(android.provider.MediaStore.Images.Media.DISPLAY_NAME),
            "${android.provider.MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf("nao-existe.png"),
            null,
        )!!.use { cursor ->
            assertThat(cursor.count).isZero()
        }
    }

    @Test
    fun `export renders the full resolution png and publishes it`() {
        val plan = plan()

        val export = runBlockingExport(plan, ArtRecipe())

        assertThat(export).isInstanceOf(ArtPngExport.Published::class.java)
        val published = export as ArtPngExport.Published
        assertThat(published.reused).isFalse()
        assertThat(published.savedToGallery).isTrue()
    }

    @Test
    fun `sharing the same recipe again reuses the last png`() {
        val plan = plan()

        val first = runBlockingExport(plan, ArtRecipe(), forShare = true)
        val second = runBlockingExport(plan, ArtRecipe(), forShare = true)

        val firstUri = (first as ArtPngExport.Published).uri
        val secondResult = second as ArtPngExport.Published
        assertThat(secondResult.uri).isEqualTo(firstUri)
        assertThat(secondResult.reused).isTrue()
    }

    @Test
    fun `a changed recipe regenerates instead of reusing`() {
        val plan = plan()

        val first = runBlockingExport(plan, ArtRecipe()) as ArtPngExport.Published
        val second = runBlockingExport(
            plan,
            ArtRecipe(font = com.meucoach.core.domain.story.ArtFont.ANTON),
        ) as ArtPngExport.Published

        assertThat(second.uri).isNotEqualTo(first.uri)
        assertThat(second.reused).isFalse()
    }

    @Test
    fun `a changed format regenerates instead of reusing`() {
        val plan = plan()

        val first = runBlockingExport(plan, ArtRecipe()) as ArtPngExport.Published
        val second = runBlockingExport(
            plan,
            ArtRecipe(format = ArtFormat.PORTRAIT_9_16),
        ) as ArtPngExport.Published

        assertThat(second.uri).isNotEqualTo(first.uri)
        assertThat(second.reused).isFalse()
    }

    @Test
    fun `saving always publishes a new copy instead of reusing`() {
        val plan = plan()

        val first = runBlockingExport(plan, ArtRecipe(), forShare = false) as ArtPngExport.Published
        val second = runBlockingExport(plan, ArtRecipe(), forShare = false) as ArtPngExport.Published

        assertThat(second.uri).isNotEqualTo(first.uri)
        assertThat(second.reused).isFalse()
    }

    @Test
    fun `returns no route without gps points`() {
        val export =
            runBlockingExport(plan().copy(route = com.meucoach.core.domain.story.RouteData(emptyList())), ArtRecipe())

        assertThat(export).isEqualTo(ArtPngExport.NoRoute)
    }

    @Test
    fun `a publish failure returns the friendly copy`() {
        val failing = CanvasArtPngExporter(
            context,
            object : GalleryImagePublisher {
                override fun publish(file: File): ImagePublishResult = error("Permission denied")
            },
        )
        var result: ArtPngExport? = null
        kotlinx.coroutines.runBlocking {
            result = failing.export(
                plan = plan(),
                localDate = plan().localDate,
                recipe = ArtRecipe(),
                activityId = "activity-1",
                forShare = false,
            )
        }

        assertThat(result).isEqualTo(ArtPngExport.Failure(StoryMessages.ART_EXPORT_FAILED))
    }

    @Test
    fun `the cache keeps at most eight pngs and preserves the reusable one`() {
        val plan = plan()
        val dir = File(context.cacheDir, "art").apply { mkdirs() }
        repeat(10) { index ->
            val stale = File(dir, "old-$index.png")
            stale.writeBytes(byteArrayOf(1))
            stale.setLastModified(1_000_000L + index * 1_000L)
        }

        val first = runBlockingExport(plan, ArtRecipe(), forShare = true) as ArtPngExport.Published
        val remaining = dir.listFiles { file -> file.extension.equals("png", ignoreCase = true) }
            .orEmpty()
            .size

        assertThat(remaining).isLessThanOrEqualTo(8)
        val second = runBlockingExport(plan, ArtRecipe(), forShare = true) as ArtPngExport.Published
        assertThat(second.reused).isTrue()
        assertThat(second.uri).isEqualTo(first.uri)
    }

    private fun runBlockingExport(plan: StoryPlan, recipe: ArtRecipe, forShare: Boolean = true): ArtPngExport {
        var result: ArtPngExport? = null
        kotlinx.coroutines.runBlocking {
            result = exporter.export(
                plan = plan,
                localDate = plan.localDate,
                recipe = recipe,
                activityId = "activity-1",
                forShare = forShare,
            )
        }
        return checkNotNull(result)
    }

    private fun pngFile(name: String): File {
        val dir = File(context.cacheDir, "art").apply { mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { output ->
            android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
        return file
    }

    private fun plan(): StoryPlan {
        val samples = (0..5).map { km ->
            ActivitySample(
                offsetSec = km * 360,
                latitude = -5.790 + km * 0.009,
                longitude = -35.210 + if (km % 2 == 0) 0.0 else 0.002,
                heartRate = 150,
                cadenceSpm = 170,
            )
        }
        val activity = ImportedActivity(
            source = ActivitySource.GPX,
            sport = Sport.RUNNING,
            startTime = Instant.parse("2026-10-24T12:00:00Z"),
            durationSec = 1_800,
            distanceM = 5_000.0,
            calories = 310,
            averageHeartRate = 150,
            averagePace = com.meucoach.core.model.Pace.fromSecondsPerKm(360),
            samples = samples,
        )
        return checkNotNull(StoryPlanCalculator.calculate(activity))
            .copy(localDate = LocalDate.parse("2026-10-24"))
    }
}
