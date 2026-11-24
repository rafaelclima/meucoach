package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtLayout
import com.meucoach.core.domain.story.ArtPhotoRef
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.ArtTemplate
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
    private lateinit var store: ArtPhotoStore
    private lateinit var loader: ArtPhotoLoader

    @Before
    fun setUp() {
        context.cacheDir.resolve("art").deleteRecursively()
        File(context.filesDir, ArtPhotoStore.DIRECTORY).deleteRecursively()
        publisher = MediaStoreGalleryImagePublisher(context)
        store = ArtPhotoStore(context)
        loader = ArtPhotoLoader(store)
        exporter = CanvasArtPngExporter(context, publisher, store, loader)
    }

    @Test
    fun `publishes a png into pictures meucoach`() {
        val png = pngFile("meucoach-story-test-arte-4x5.png")

        val result = publisher.publish(png)

        assertThat(result.savedToGallery).isTrue()
        assertThat(mediaMime(result.uri)).isEqualTo("image/png")
        assertThat(queryByName("meucoach-story-test-arte-4x5.png")).isEqualTo(1)
    }

    @Test
    fun `publishes a jpg into pictures meucoach`() {
        val jpg = jpgFile("meucoach-story-test-arte-foto-4x5.jpg")

        val result = publisher.publishJpg(jpg)

        assertThat(result.savedToGallery).isTrue()
        assertThat(mediaMime(result.uri)).isEqualTo("image/jpeg")
        assertThat(queryByName("meucoach-story-test-arte-foto-4x5.jpg")).isEqualTo(1)
    }

    @Test
    fun `a failed png publish leaves no pending row behind`() {
        assertFailedPublishLeavesNoRow("nao-existe.png") { publisher.publish(it) }
    }

    @Test
    fun `a failed jpg publish leaves no pending row behind`() {
        assertFailedPublishLeavesNoRow("nao-existe.jpg") { publisher.publishJpg(it) }
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
    fun `photo export writes a 1080 jpg and publishes it as jpeg`() {
        val export = runBlockingExport(plan(), photoRecipe(photoRef()), forShare = false)

        val published = export as ArtPngExport.Published
        assertThat(published.reused).isFalse()
        assertThat(mediaMime(published.uri)).isEqualTo("image/jpeg")
        val file = cacheFiles("jpg").single()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        assertThat(options.outWidth).isEqualTo(1080)
        assertThat(options.outHeight).isEqualTo(1350)
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
    fun `sharing the same photo recipe again reuses the last jpg`() {
        val plan = plan()
        val recipe = photoRecipe(photoRef())

        val first = runBlockingExport(plan, recipe, forShare = true) as ArtPngExport.Published
        val second = runBlockingExport(plan, recipe, forShare = true) as ArtPngExport.Published

        assertThat(second.uri).isEqualTo(first.uri)
        assertThat(second.reused).isTrue()
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
    fun `a changed photo layout or scale regenerates the jpg`() {
        val plan = plan()
        val firstPhoto = photoRef(PHOTO_ID)
        val secondPhoto = photoRef(PHOTO_ID_2)

        val first = runBlockingExport(plan, photoRecipe(firstPhoto)) as ArtPngExport.Published
        val changedPhoto = runBlockingExport(plan, photoRecipe(secondPhoto)) as ArtPngExport.Published
        val changedLayout = runBlockingExport(
            plan,
            photoRecipe(secondPhoto).copy(layout = ArtLayout(template = ArtTemplate.CENTER)),
        ) as ArtPngExport.Published
        val changedScale = runBlockingExport(
            plan,
            photoRecipe(secondPhoto).copy(
                layout = ArtLayout(template = ArtTemplate.CENTER, textScalePercent = 130),
            ),
        ) as ArtPngExport.Published

        assertThat(changedPhoto.uri).isNotEqualTo(first.uri)
        assertThat(changedPhoto.reused).isFalse()
        assertThat(changedLayout.uri).isNotEqualTo(changedPhoto.uri)
        assertThat(changedLayout.reused).isFalse()
        assertThat(changedScale.uri).isNotEqualTo(changedLayout.uri)
        assertThat(changedScale.reused).isFalse()
    }

    @Test
    fun `a changed photo format regenerates the jpg`() {
        val plan = plan()
        val recipe = photoRecipe(photoRef())

        val first = runBlockingExport(plan, recipe) as ArtPngExport.Published
        val second = runBlockingExport(
            plan,
            recipe.copy(format = ArtFormat.SQUARE),
        ) as ArtPngExport.Published

        assertThat(second.uri).isNotEqualTo(first.uri)
        assertThat(second.reused).isFalse()
        val square = cacheFiles("jpg").maxBy { it.lastModified() }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(square.absolutePath, options)
        assertThat(options.outWidth).isEqualTo(1080)
        assertThat(options.outHeight).isEqualTo(1080)
    }

    @Test
    fun `a changed plan regenerates instead of reusing`() {
        val firstPlan = plan()
        val secondPlan = firstPlan.copy(localDate = LocalDate.parse("2026-10-25"))

        val first = runBlockingExport(firstPlan, ArtRecipe()) as ArtPngExport.Published
        val second = runBlockingExport(secondPlan, ArtRecipe()) as ArtPngExport.Published

        assertThat(second.uri).isNotEqualTo(first.uri)
        assertThat(second.reused).isFalse()
    }

    @Test
    fun `a missing cache file regenerates the jpg`() {
        val plan = plan()
        val recipe = photoRecipe(photoRef())

        val first = runBlockingExport(plan, recipe) as ArtPngExport.Published
        cacheFiles("jpg").forEach { it.delete() }
        val second = runBlockingExport(plan, recipe) as ArtPngExport.Published

        assertThat(second.uri).isNotEqualTo(first.uri)
        assertThat(second.reused).isFalse()
        assertThat(cacheFiles("jpg")).hasSize(1)
    }

    @Test
    fun `a dead mediastore uri regenerates the jpg`() {
        val plan = plan()
        val recipe = photoRecipe(photoRef())

        val first = runBlockingExport(plan, recipe) as ArtPngExport.Published
        context.contentResolver.delete(first.uri, null, null)
        val second = runBlockingExport(plan, recipe) as ArtPngExport.Published

        assertThat(second.uri).isNotEqualTo(first.uri)
        assertThat(second.reused).isFalse()
    }

    @Test
    fun `a missing photo returns the neutral error and publishes nothing`() {
        val photo = photoRef()
        store.photoFile(photo.photoId).delete()
        val before = countArtRows()

        val export = runBlockingExport(plan(), photoRecipe(photo), forShare = true)

        assertThat(export).isEqualTo(ArtPngExport.Failure(StoryMessages.ART_EXPORT_FAILED))
        assertThat(countArtRows()).isEqualTo(before)
        assertThat(cacheFiles("jpg")).isEmpty()
    }

    @Test
    fun `a photo deleted after sharing is not reused and returns the neutral error`() {
        val plan = plan()
        val photo = photoRef()
        val first = runBlockingExport(plan, photoRecipe(photo), forShare = true)
        assertThat(first).isInstanceOf(ArtPngExport.Published::class.java)

        store.photoFile(photo.photoId).delete()
        val second = runBlockingExport(plan, photoRecipe(photo), forShare = true)

        assertThat(second).isEqualTo(ArtPngExport.Failure(StoryMessages.ART_EXPORT_FAILED))
    }

    @Test
    fun `a current recipe that did not change still publishes`() {
        val recipe = photoRecipe(photoRef())

        val export = runBlockingExport(
            plan = plan(),
            recipe = recipe,
            forShare = true,
            currentRecipe = { recipe },
        )

        assertThat(export).isInstanceOf(ArtPngExport.Published::class.java)
    }

    @Test
    fun `a recipe changed during the render is discarded before publishing`() {
        val recipe = photoRecipe(photoRef())
        val before = countArtRows()

        val export = runBlockingExport(
            plan = plan(),
            recipe = recipe,
            forShare = true,
            currentRecipe = { recipe.copy(textColor = ArtColor.CYAN) },
        )

        assertThat(export).isEqualTo(ArtPngExport.Stale)
        assertThat(countArtRows()).isEqualTo(before)
        assertThat(cacheFiles("jpg")).isEmpty()
    }

    @Test
    fun `a waiting photo does not invalidate the transparent png`() {
        val plan = plan()
        val waiting = ArtRecipe(
            background = ArtBackgroundMode.TRANSPARENT,
            photo = photoRef(PHOTO_ID),
        )

        val first = runBlockingExport(plan, waiting) as ArtPngExport.Published
        val second = runBlockingExport(
            plan,
            waiting.copy(photo = photoRef(PHOTO_ID_2)),
        ) as ArtPngExport.Published

        assertThat(second.uri).isEqualTo(first.uri)
        assertThat(second.reused).isTrue()
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
    fun `saving a photo always publishes a new jpg copy`() {
        val plan = plan()
        val recipe = photoRecipe(photoRef())

        val first = runBlockingExport(plan, recipe, forShare = false) as ArtPngExport.Published
        val second = runBlockingExport(plan, recipe, forShare = false) as ArtPngExport.Published

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
        val failing = CanvasArtPngExporter(context, FailingPublisher(), store, loader)
        var result: ArtPngExport? = null
        kotlinx.coroutines.runBlocking {
            result = failing.export(
                plan = plan(),
                localDate = plan().localDate,
                recipe = ArtRecipe(),
                activityId = ACTIVITY,
                forShare = false,
            )
        }

        assertThat(result).isEqualTo(ArtPngExport.Failure(StoryMessages.ART_EXPORT_FAILED))
    }

    @Test
    fun `the cache keeps at most eight png and jpg files and preserves the reusable one`() {
        val plan = plan()
        val recipe = photoRecipe(photoRef())
        val dir = File(context.cacheDir, "art").apply { mkdirs() }
        repeat(10) { index ->
            val extension = if (index % 2 == 0) "png" else "jpg"
            val stale = File(dir, "old-$index.$extension")
            stale.writeBytes(byteArrayOf(1))
            stale.setLastModified(1_000_000L + index * 1_000L)
        }

        val first = runBlockingExport(plan, recipe, forShare = true) as ArtPngExport.Published
        val remaining = dir.listFiles { file ->
            file.isFile && file.extension.lowercase() in setOf("png", "jpg")
        }.orEmpty().size

        assertThat(remaining).isLessThanOrEqualTo(8)
        val second = runBlockingExport(plan, recipe, forShare = true) as ArtPngExport.Published
        assertThat(second.reused).isTrue()
        assertThat(second.uri).isEqualTo(first.uri)
        assertThat(cacheFiles("jpg")).isNotEmpty()
    }

    private fun assertFailedPublishLeavesNoRow(name: String, publish: (File) -> ImagePublishResult) {
        val missing = File(context.cacheDir, "art/$name")

        val result = runCatching { publish(missing) }

        assertThat(result.isFailure).isTrue()
        assertThat(queryByName(name)).isZero()
    }

    private fun runBlockingExport(
        plan: StoryPlan,
        recipe: ArtRecipe,
        forShare: Boolean = true,
        currentRecipe: (() -> ArtRecipe)? = null,
    ): ArtPngExport {
        var result: ArtPngExport? = null
        kotlinx.coroutines.runBlocking {
            result = if (currentRecipe == null) {
                exporter.export(
                    plan = plan,
                    localDate = plan.localDate,
                    recipe = recipe,
                    activityId = ACTIVITY,
                    forShare = forShare,
                )
            } else {
                exporter.export(
                    plan = plan,
                    localDate = plan.localDate,
                    recipe = recipe,
                    activityId = ACTIVITY,
                    forShare = forShare,
                    currentRecipe = currentRecipe,
                )
            }
        }
        return checkNotNull(result)
    }

    private fun photoRecipe(photo: ArtPhotoRef): ArtRecipe =
        ArtRecipe(background = ArtBackgroundMode.PHOTO, photo = photo)

    private fun photoRef(photoId: String = PHOTO_ID): ArtPhotoRef {
        val bitmap = SyntheticArtPhoto.landscape(400, 300)
        val file = store.photoFile(photoId)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }
        bitmap.recycle()
        return ArtPhotoRef(photoId = photoId, activityId = ACTIVITY)
    }

    private fun cacheFiles(extension: String): List<File> = File(context.cacheDir, "art")
        .listFiles { file -> file.isFile && file.extension.equals(extension, ignoreCase = true) }
        .orEmpty()
        .toList()

    private fun mediaMime(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.MIME_TYPE), null, null, null)
            ?.use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0)
            }

    private fun queryByName(name: String): Int = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
        "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
        arrayOf(name),
        null,
    )!!.use { cursor -> cursor.count }

    private fun countArtRows(): Int = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
        "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
        arrayOf("meucoach-story-%-arte-foto-%"),
        null,
    )!!.use { cursor -> cursor.count }

    private fun pngFile(name: String): File {
        val dir = File(context.cacheDir, "art").apply { mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { output ->
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return file
    }

    private fun jpgFile(name: String): File {
        val dir = File(context.cacheDir, "art").apply { mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { output ->
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.JPEG, 90, output)
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

    private class FailingPublisher : GalleryImagePublisher {
        override fun publish(file: File): ImagePublishResult = error("Permission denied")

        override fun publishJpg(file: File): ImagePublishResult = error("Permission denied")
    }

    private companion object {
        const val ACTIVITY = "activity-1"
        const val PHOTO_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
        const val PHOTO_ID_2 = "7b1d6c2a-6d1f-4e5a-9a2b-6f4c8d0e1a3b"
    }
}
