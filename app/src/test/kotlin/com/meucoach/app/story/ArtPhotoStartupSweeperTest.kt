package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtPhotoRef
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtPhotoStartupSweeperTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: ArtPhotoStore
    private lateinit var sweeper: ArtPhotoStartupSweeper

    @Before
    fun setUp() {
        File(context.filesDir, ArtPhotoStore.DIRECTORY).deleteRecursively()
        store = ArtPhotoStore(context)
        sweeper = ArtPhotoStartupSweeper(store)
    }

    @Test
    fun `sweep removes stale orphans and keeps the referenced photo`() = runBlocking<Unit> {
        val now = 1_800_000_000_000L
        val stale = now - 25 * 60 * 60 * 1000L
        val referenced = writePhoto(REFERENCED_ID)
        store.save(ACTIVITY, ArtPhotoRecord(ArtBackgroundMode.PHOTO, ArtPhotoRef(REFERENCED_ID, ACTIVITY)))
        store.photoFile(REFERENCED_ID).setLastModified(stale)

        writePhoto(ORPHAN_ID)
        store.photoFile(ORPHAN_ID).setLastModified(stale)
        val tmp = File(store.photoFile(TMP_ID).path + ".tmp").apply {
            parentFile?.mkdirs()
            writeText("temp")
            setLastModified(stale)
        }

        val removed = sweeper.sweep(now)

        assertThat(removed).isEqualTo(2)
        assertThat(store.photoFile(REFERENCED_ID)).exists()
        assertThat(store.photoFile(ORPHAN_ID)).doesNotExist()
        assertThat(tmp).doesNotExist()
        assertThat(referenced).isNotNull()
    }

    private fun writePhoto(photoId: String): ArtPhotoRef {
        val bitmap = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val file = store.photoFile(photoId)
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }
        bitmap.recycle()
        return ArtPhotoRef(photoId = photoId, activityId = ACTIVITY)
    }

    private companion object {
        const val ACTIVITY = "activity-1"
        const val REFERENCED_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
        const val ORPHAN_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3302"
        const val TMP_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3303"
    }
}
