package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.ArtBackgroundMode
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtPhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: ArtPhotoStore

    @Before
    fun setUp() {
        File(context.filesDir, ArtPhotoStore.DIRECTORY).deleteRecursively()
        store = ArtPhotoStore(context)
    }

    @Test
    fun `load defaults to transparent when the activity has no metadata`() = runBlocking<Unit> {
        assertThat(store.load(ACTIVITY_A))
            .isEqualTo(ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null))
    }

    @Test
    fun `save and load persist background photo and crop`() = runBlocking<Unit> {
        val photo = writePhoto(PHOTO_A)
        val record = ArtPhotoRecord(ArtBackgroundMode.PHOTO, photo)

        store.save(ACTIVITY_A, record)

        assertThat(store.load(ACTIVITY_A)).isEqualTo(record)
        assertThat(store.photoFile(PHOTO_A)).exists()
    }

    @Test
    fun `save keeps a waiting photo under transparent mode`() = runBlocking<Unit> {
        val photo = writePhoto(PHOTO_A)
        val record = ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, photo)

        store.save(ACTIVITY_A, record)

        assertThat(store.load(ACTIVITY_A)).isEqualTo(record)
        assertThat(store.photoFile(PHOTO_A)).exists()
    }

    @Test
    fun `save normalizes a missing photo file to transparent`() = runBlocking<Unit> {
        val record = ArtPhotoRecord(
            ArtBackgroundMode.PHOTO,
            ArtPhotoRef(PHOTO_A, ACTIVITY_A),
        )

        store.save(ACTIVITY_A, record)

        assertThat(store.load(ACTIVITY_A))
            .isEqualTo(ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null))
    }

    @Test
    fun `save rejects a photo owned by another activity`() = runBlocking<Unit> {
        writePhoto(PHOTO_A)
        val foreign = ArtPhotoRecord(
            ArtBackgroundMode.PHOTO,
            ArtPhotoRef(PHOTO_A, ACTIVITY_B),
        )

        assertThatThrownBy { runBlocking { store.save(ACTIVITY_A, foreign) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `load falls back and removes metadata when the photo file is missing`() = runBlocking<Unit> {
        writePhoto(PHOTO_A)
        store.save(ACTIVITY_A, ArtPhotoRecord(ArtBackgroundMode.PHOTO, ArtPhotoRef(PHOTO_A, ACTIVITY_A)))
        store.photoFile(PHOTO_A).delete()

        val loaded = store.load(ACTIVITY_A)

        assertThat(loaded).isEqualTo(ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null))
        assertThat(store.metadataFile(ACTIVITY_A)).doesNotExist()
    }

    @Test
    fun `snapshot flags a photo that disappeared and keeps normal loads unmarked`() = runBlocking<Unit> {
        assertThat(store.loadSnapshot(ACTIVITY_A).missingPhoto).isFalse()

        writePhoto(PHOTO_A)
        store.save(ACTIVITY_A, ArtPhotoRecord(ArtBackgroundMode.PHOTO, ArtPhotoRef(PHOTO_A, ACTIVITY_A)))
        assertThat(store.loadSnapshot(ACTIVITY_A).missingPhoto).isFalse()

        store.photoFile(PHOTO_A).delete()

        val snapshot = store.loadSnapshot(ACTIVITY_A)
        assertThat(snapshot.record)
            .isEqualTo(ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null))
        assertThat(snapshot.missingPhoto).isTrue()
    }

    @Test
    fun `load falls back and removes corrupt metadata`() = runBlocking<Unit> {
        store.metadataFile(ACTIVITY_A).apply {
            parentFile?.mkdirs()
            writeText("not json")
        }

        val loaded = store.load(ACTIVITY_A)

        assertThat(loaded).isEqualTo(ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null))
        assertThat(store.metadataFile(ACTIVITY_A)).doesNotExist()
    }

    @Test
    fun `load rejects metadata owned by another activity`() = runBlocking<Unit> {
        writePhoto(PHOTO_A)
        store.metadataFile(ACTIVITY_A).apply {
            parentFile?.mkdirs()
            writeText(
                ArtPhotoCodec.encode(
                    ACTIVITY_B,
                    ArtPhotoRecord(ArtBackgroundMode.PHOTO, ArtPhotoRef(PHOTO_A, ACTIVITY_B)),
                ),
            )
        }

        val loaded = store.load(ACTIVITY_A)

        assertThat(loaded).isEqualTo(ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null))
        assertThat(store.metadataFile(ACTIVITY_A)).doesNotExist()
    }

    @Test
    fun `load falls back when the stored bytes are not an image`() = runBlocking<Unit> {
        store.photoFile(PHOTO_A).apply {
            parentFile?.mkdirs()
            writeText("not an image")
        }
        store.metadataFile(ACTIVITY_A).apply {
            parentFile?.mkdirs()
            writeText(
                ArtPhotoCodec.encode(
                    ACTIVITY_A,
                    ArtPhotoRecord(ArtBackgroundMode.PHOTO, ArtPhotoRef(PHOTO_A, ACTIVITY_A)),
                ),
            )
        }

        val loaded = store.load(ACTIVITY_A)

        assertThat(loaded).isEqualTo(ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null))
        assertThat(store.metadataFile(ACTIVITY_A)).doesNotExist()
    }

    @Test
    fun `remove deletes only the activity metadata and photo`() = runBlocking<Unit> {
        writePhoto(PHOTO_A)
        writePhoto(PHOTO_B)
        store.save(ACTIVITY_A, ArtPhotoRecord(ArtBackgroundMode.PHOTO, ArtPhotoRef(PHOTO_A, ACTIVITY_A)))
        store.save(ACTIVITY_B, ArtPhotoRecord(ArtBackgroundMode.PHOTO, ArtPhotoRef(PHOTO_B, ACTIVITY_B)))

        store.remove(ACTIVITY_A)

        assertThat(store.load(ACTIVITY_A))
            .isEqualTo(ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null))
        assertThat(store.photoFile(PHOTO_A)).doesNotExist()
        assertThat(store.load(ACTIVITY_B).photo).isNotNull
        assertThat(store.photoFile(PHOTO_B)).exists()
    }

    @Test
    fun `sweep deletes stale unreferenced files and keeps referenced or fresh ones`() = runBlocking<Unit> {
        val now = 1_800_000_000_000L
        val stale = now - 25 * 60 * 60 * 1000L
        writePhoto(PHOTO_A)
        store.save(ACTIVITY_A, ArtPhotoRecord(ArtBackgroundMode.PHOTO, ArtPhotoRef(PHOTO_A, ACTIVITY_A)))
        store.photoFile(PHOTO_A).setLastModified(stale)

        writePhoto(PHOTO_ORPHAN_STALE)
        store.photoFile(PHOTO_ORPHAN_STALE).setLastModified(stale)
        writePhoto(PHOTO_ORPHAN_FRESH)
        store.photoFile(PHOTO_ORPHAN_FRESH).setLastModified(now)

        val staleTemp = File(store.photoFile(PHOTO_TEMP_STALE).path + ".tmp").apply {
            parentFile?.mkdirs()
            writeText("temp")
            setLastModified(stale)
        }
        val freshTemp = File(store.photoFile(PHOTO_TEMP_FRESH).path + ".tmp").apply {
            parentFile?.mkdirs()
            writeText("temp")
            setLastModified(now)
        }
        val staleMetadataTemp = File(store.metadataFile(ACTIVITY_B).path + ".tmp").apply {
            parentFile?.mkdirs()
            writeText("temp")
            setLastModified(stale)
        }

        val removed = store.sweepOrphans(now)

        assertThat(removed).isEqualTo(3)
        assertThat(store.photoFile(PHOTO_A)).exists()
        assertThat(store.photoFile(PHOTO_ORPHAN_STALE)).doesNotExist()
        assertThat(store.photoFile(PHOTO_ORPHAN_FRESH)).exists()
        assertThat(staleTemp).doesNotExist()
        assertThat(freshTemp).exists()
        assertThat(staleMetadataTemp).doesNotExist()
    }

    @Test
    fun `a waiting photo under transparent mode is never swept`() = runBlocking<Unit> {
        val now = 1_800_000_000_000L
        writePhoto(PHOTO_A)
        store.save(ACTIVITY_A, ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, ArtPhotoRef(PHOTO_A, ACTIVITY_A)))
        store.photoFile(PHOTO_A).setLastModified(now - 48 * 60 * 60 * 1000L)

        store.sweepOrphans(now)

        assertThat(store.photoFile(PHOTO_A)).exists()
    }

    @Test
    fun `save does not leave temporary files behind`() = runBlocking<Unit> {
        writePhoto(PHOTO_A)

        store.save(ACTIVITY_A, ArtPhotoRecord(ArtBackgroundMode.PHOTO, ArtPhotoRef(PHOTO_A, ACTIVITY_A)))

        val temporary = store.metadataFile(ACTIVITY_A).parentFile!!
            .listFiles { file -> file.name.endsWith(".tmp") }
            .orEmpty()
        assertThat(temporary).isEmpty()
    }

    @Test
    fun `clear deletes the directory metadata and photos`() = runBlocking<Unit> {
        writePhoto(PHOTO_A)
        writePhoto(PHOTO_B)
        store.save(ACTIVITY_A, ArtPhotoRecord(ArtBackgroundMode.PHOTO, ArtPhotoRef(PHOTO_A, ACTIVITY_A)))
        store.save(ACTIVITY_B, ArtPhotoRecord(ArtBackgroundMode.PHOTO, ArtPhotoRef(PHOTO_B, ACTIVITY_B)))

        assertThat(store.clear()).isTrue()

        assertThat(File(context.filesDir, ArtPhotoStore.DIRECTORY).exists()).isFalse()
        assertThat(store.load(ACTIVITY_A))
            .isEqualTo(ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null))
        assertThat(store.load(ACTIVITY_B))
            .isEqualTo(ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null))
    }

    @Test
    fun `clear on a missing directory succeeds`() = runBlocking<Unit> {
        assertThat(store.clear()).isTrue()
    }

    private fun writePhoto(
        photoId: String,
        width: Int = 8,
        height: Int = 6,
        crop: ArtPhotoCrop = ArtPhotoCrop(),
    ): ArtPhotoRef {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val file = store.photoFile(photoId)
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }
        bitmap.recycle()
        return ArtPhotoRef(photoId = photoId, activityId = ACTIVITY_A, crop = crop)
    }

    private companion object {
        const val ACTIVITY_A = "activity-a"
        const val ACTIVITY_B = "activity-b"
        const val PHOTO_A = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
        const val PHOTO_B = "3f2504e0-4f89-41d3-9a0c-0305e82c3302"
        const val PHOTO_ORPHAN_STALE = "3f2504e0-4f89-41d3-9a0c-0305e82c3303"
        const val PHOTO_ORPHAN_FRESH = "3f2504e0-4f89-41d3-9a0c-0305e82c3304"
        const val PHOTO_TEMP_STALE = "3f2504e0-4f89-41d3-9a0c-0305e82c3305"
        const val PHOTO_TEMP_FRESH = "3f2504e0-4f89-41d3-9a0c-0305e82c3306"
    }
}
