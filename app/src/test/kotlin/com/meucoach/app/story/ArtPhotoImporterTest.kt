package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtPhotoCrop
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtPhotoImporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: ArtPhotoStore
    private lateinit var importer: ArtPhotoImporter
    private var uriCounter = 0

    @Before
    fun setUp() {
        File(context.filesDir, ArtPhotoStore.DIRECTORY).deleteRecursively()
        store = ArtPhotoStore(context)
        importer = ArtPhotoImporter(context, store)
    }

    @Test
    fun `imports a jpeg into the private store`() = runBlocking<Unit> {
        val bytes = jpegBytes(width = 8, height = 6)

        val result = importer.import(ACTIVITY_A, uriFor(bytes))

        val photo = (result as ArtPhotoImportResult.Success).photo
        assertThat(photo.activityId).isEqualTo(ACTIVITY_A)
        assertThat(photo.crop).isEqualTo(ArtPhotoCrop())
        assertThat(store.load(ACTIVITY_A))
            .isEqualTo(ArtPhotoRecord(ArtBackgroundMode.PHOTO, photo))
        val file = store.photoFile(photo.photoId)
        assertThat(file).exists()
        assertThat(boundsOf(file)).isEqualTo(8 to 6)
    }

    @Test
    fun `applies all eight exif orientations to the stored copy`() = runBlocking<Unit> {
        val orientations = listOf(
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )

        for (orientation in orientations) {
            val result = importer.import(
                ACTIVITY_A,
                uriFor(jpegBytes(width = 40, height = 20, orientation = orientation)),
            )
            val photo = (result as ArtPhotoImportResult.Success).photo
            val expected = if (orientation in 5..8) 20 to 40 else 40 to 20
            assertThat(boundsOf(store.photoFile(photo.photoId)))
                .describedAs("orientation $orientation")
                .isEqualTo(expected)
        }
    }

    @Test
    fun `replacing a photo deletes the previous file`() = runBlocking<Unit> {
        val first = (importer.import(ACTIVITY_A, uriFor(jpegBytes(8, 6))) as ArtPhotoImportResult.Success).photo
        val second = (importer.import(ACTIVITY_A, uriFor(jpegBytes(8, 6))) as ArtPhotoImportResult.Success).photo

        assertThat(store.photoFile(first.photoId)).doesNotExist()
        assertThat(store.photoFile(second.photoId)).exists()
        assertThat(store.load(ACTIVITY_A).photo).isEqualTo(second)
    }

    @Test
    fun `a failed import keeps the previous photo`() = runBlocking<Unit> {
        val first = (importer.import(ACTIVITY_A, uriFor(jpegBytes(8, 6))) as ArtPhotoImportResult.Success).photo

        val result = importer.import(ACTIVITY_A, brokenUri())

        assertThat(result).isEqualTo(ArtPhotoImportResult.Failure(ArtPhotoImportError.SOURCE_UNREADABLE))
        assertThat(store.photoFile(first.photoId)).exists()
        assertThat(store.load(ACTIVITY_A).photo).isEqualTo(first)
    }

    @Test
    fun `invalid image bytes fail without touching the store`() = runBlocking<Unit> {
        val result = importer.import(ACTIVITY_A, uriFor("not an image".toByteArray()))

        assertThat(result).isEqualTo(ArtPhotoImportResult.Failure(ArtPhotoImportError.INVALID_IMAGE))
        assertThat(store.load(ACTIVITY_A))
            .isEqualTo(ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null))
    }

    @Test
    fun `downsamples a large source to at most 3072 pixels`() = runBlocking<Unit> {
        val result = importer.import(ACTIVITY_A, uriFor(jpegBytes(width = 3200, height = 100)))
        val photo = (result as ArtPhotoImportResult.Success).photo
        val (width, height) = boundsOf(store.photoFile(photo.photoId))

        assertThat(maxOf(width, height)).isLessThanOrEqualTo(ArtPhotoImporter.MAX_SOURCE_SIDE)
        assertThat(width).isGreaterThan(0)
        assertThat(height).isGreaterThan(0)
    }

    @Test
    fun `stores the requested crop`() = runBlocking<Unit> {
        val crop = ArtPhotoCrop(focusX = 0.25f, focusY = 0.75f, zoom = 2f)

        val result = importer.import(ACTIVITY_A, uriFor(jpegBytes(8, 6)), crop = crop)

        val photo = (result as ArtPhotoImportResult.Success).photo
        assertThat(photo.crop).isEqualTo(crop)
        assertThat(store.load(ACTIVITY_A).photo!!.crop).isEqualTo(crop)
    }

    @Test
    fun `can store a waiting photo when the requested background is transparent`() = runBlocking<Unit> {
        val result = importer.import(
            ACTIVITY_A,
            uriFor(jpegBytes(8, 6)),
            background = ArtBackgroundMode.TRANSPARENT,
        )

        val photo = (result as ArtPhotoImportResult.Success).photo
        assertThat(store.load(ACTIVITY_A))
            .isEqualTo(ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, photo))
        assertThat(store.photoFile(photo.photoId)).exists()
    }

    @Test
    fun `the stored copy is upright and has no exif orientation`() = runBlocking<Unit> {
        val result = importer.import(
            ACTIVITY_A,
            uriFor(jpegBytes(width = 40, height = 20, orientation = ExifInterface.ORIENTATION_ROTATE_90)),
        )
        val photo = (result as ArtPhotoImportResult.Success).photo
        val file = store.photoFile(photo.photoId)

        val orientation = ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        assertThat(orientation).isIn(
            ExifInterface.ORIENTATION_UNDEFINED,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }

    @Test
    fun `one photo per activity and isolation between activities`() = runBlocking<Unit> {
        val first = (importer.import(ACTIVITY_A, uriFor(jpegBytes(8, 6))) as ArtPhotoImportResult.Success).photo
        val second = (importer.import(ACTIVITY_B, uriFor(jpegBytes(6, 8))) as ArtPhotoImportResult.Success).photo

        assertThat(first.photoId).isNotEqualTo(second.photoId)
        assertThat(store.photoFile(first.photoId)).exists()
        assertThat(store.photoFile(second.photoId)).exists()
        assertThat(store.load(ACTIVITY_A).photo).isEqualTo(first)
        assertThat(store.load(ACTIVITY_B).photo).isEqualTo(second)
    }

    @Test
    fun `a successful import leaves no temporary files`() = runBlocking<Unit> {
        importer.import(ACTIVITY_A, uriFor(jpegBytes(8, 6)))

        val directory = File(context.filesDir, ArtPhotoStore.DIRECTORY)
        assertThat(directory.listFiles { file -> file.name.endsWith(".tmp") }.orEmpty()).isEmpty()
    }

    private fun jpegBytes(width: Int, height: Int, orientation: Int? = null): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val file = File(context.cacheDir, "art-source-${uriCounter++}.jpg")
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        bitmap.recycle()
        if (orientation != null) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return file.readBytes().also { file.delete() }
    }

    private fun uriFor(bytes: ByteArray): Uri {
        val uri = Uri.parse("content://test/art-photo/${uriCounter++}")
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            ByteArrayInputStream(bytes)
        }
        return uri
    }

    private fun brokenUri(): Uri {
        val uri = Uri.parse("content://test/art-photo/broken-${uriCounter++}")
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            throw FileNotFoundException("revoked")
        }
        return uri
    }

    private fun boundsOf(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth to options.outHeight
    }

    private companion object {
        const val ACTIVITY_A = "activity-a"
        const val ACTIVITY_B = "activity-b"
    }
}
