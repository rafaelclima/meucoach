package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtPhotoCrop
import com.meucoach.core.domain.story.ArtPhotoRef
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtPhotoLoaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: ArtPhotoStore
    private lateinit var loader: ArtPhotoLoader

    @Before
    fun setUp() {
        File(context.filesDir, ArtPhotoStore.DIRECTORY).deleteRecursively()
        store = ArtPhotoStore(context)
        loader = ArtPhotoLoader(store)
    }

    @Test
    fun `loads 4x5 at preview width`() = runBlocking<Unit> {
        val photo = writePhoto(PHOTO, width = 400, height = 300)

        val bitmap = loader.load(photo, ArtFormat.PORTRAIT_4_5, targetWidth = 540)!!

        assertThat(bitmap.width).isEqualTo(540)
        assertThat(bitmap.height).isEqualTo(675)
        bitmap.recycle()
    }

    @Test
    fun `loads square and 9x16 formats at preview width`() = runBlocking<Unit> {
        val photo = writePhoto(PHOTO, width = 400, height = 300)

        val square = loader.load(photo, ArtFormat.SQUARE, targetWidth = 540)!!
        val story = loader.load(photo, ArtFormat.PORTRAIT_9_16, targetWidth = 540)!!

        assertThat(square.width).isEqualTo(540)
        assertThat(square.height).isEqualTo(540)
        assertThat(story.width).isEqualTo(540)
        assertThat(story.height).isEqualTo(960)
        square.recycle()
        story.recycle()
    }

    @Test
    fun `loads export width`() = runBlocking<Unit> {
        val photo = writePhoto(PHOTO, width = 400, height = 300)

        val bitmap = loader.load(photo, ArtFormat.PORTRAIT_4_5, targetWidth = 1080)!!

        assertThat(bitmap.width).isEqualTo(1080)
        assertThat(bitmap.height).isEqualTo(1350)
        bitmap.recycle()
    }

    @Test
    fun `focus at the top and bottom changes the visible half`() = runBlocking<Unit> {
        val top = writePhoto(PHOTO, width = 100, height = 200, splitColors = true)
            .copy(crop = ArtPhotoCrop(focusX = 0.5f, focusY = 0f, zoom = 1f))
        val bottom = top.copy(crop = ArtPhotoCrop(focusX = 0.5f, focusY = 1f, zoom = 1f))

        val topBitmap = loader.load(top, ArtFormat.PORTRAIT_4_5, targetWidth = 540)!!
        val bottomBitmap = loader.load(bottom, ArtFormat.PORTRAIT_4_5, targetWidth = 540)!!
        val topPixel = topBitmap.getPixel(270, 337)
        val bottomPixel = bottomBitmap.getPixel(270, 337)

        assertThat(Color.red(topPixel)).isGreaterThan(Color.blue(topPixel))
        assertThat(Color.blue(bottomPixel)).isGreaterThan(Color.red(bottomPixel))
        topBitmap.recycle()
        bottomBitmap.recycle()
    }

    @Test
    fun `missing or corrupt files return null`() = runBlocking<Unit> {
        val missing = ArtPhotoRef(PHOTO, ACTIVITY)
        assertThat(loader.load(missing, ArtFormat.PORTRAIT_4_5, 540)).isNull()

        store.photoFile(PHOTO).apply {
            parentFile?.mkdirs()
            writeText("not an image")
        }
        assertThat(loader.load(missing, ArtFormat.PORTRAIT_4_5, 540)).isNull()
    }

    @Test
    fun `target width must be positive`() {
        val photo = ArtPhotoRef(PHOTO, ACTIVITY)

        assertThatThrownBy {
            runBlocking { loader.load(photo, ArtFormat.PORTRAIT_4_5, targetWidth = 0) }
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun writePhoto(photoId: String, width: Int, height: Int, splitColors: Boolean = false): ArtPhotoRef {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (splitColors) {
            val canvas = Canvas(bitmap)
            val paint = Paint().apply { color = Color.RED }
            canvas.drawRect(0f, 0f, width.toFloat(), height / 2f, paint)
            paint.color = Color.BLUE
            canvas.drawRect(0f, height / 2f, width.toFloat(), height.toFloat(), paint)
        } else {
            bitmap.eraseColor(Color.RED)
        }
        val file = store.photoFile(photoId)
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        bitmap.recycle()
        return ArtPhotoRef(photoId = photoId, activityId = ACTIVITY)
    }

    private companion object {
        const val ACTIVITY = "activity-1"
        const val PHOTO = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    }
}
