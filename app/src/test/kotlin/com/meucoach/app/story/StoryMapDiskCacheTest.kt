package com.meucoach.app.story

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.StoryMapStyle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryMapDiskCacheTest {
    private val cacheDir: File = run {
        val base = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "disk-cache-test")
        base.deleteRecursively()
        base.mkdirs()
        base
    }

    @Test
    fun `style json round-trips from disk`() {
        StoryMapDiskCache.writeStyle(cacheDir, StoryMapStyle.MEUCOACH_DARK, """{"version":8}""")

        assertThat(StoryMapDiskCache.readStyle(cacheDir, StoryMapStyle.MEUCOACH_DARK))
            .isEqualTo("""{"version":8}""")
    }

    @Test
    fun `missing style returns null`() {
        assertThat(StoryMapDiskCache.readStyle(cacheDir, StoryMapStyle.MEUCOACH_LIGHT)).isNull()
    }

    @Test
    fun `blank style is never written`() {
        StoryMapDiskCache.writeStyle(cacheDir, StoryMapStyle.MEUCOACH_LIGHT, "   ")

        assertThat(StoryMapDiskCache.readStyle(cacheDir, StoryMapStyle.MEUCOACH_LIGHT)).isNull()
    }

    @Test
    fun `trim evicts the oldest files first`() {
        val stories = File(cacheDir, "stories").apply { mkdirs() }
        val old = File(stories, "old.jpg").apply { writeBytes(ByteArray(60)) }
        val recent = File(stories, "recent.jpg").apply { writeBytes(ByteArray(60)) }
        old.setLastModified(1_000L)
        recent.setLastModified(2_000L)

        StoryMapDiskCache.trim(stories, maxBytes = 100L)

        assertThat(old.exists()).isFalse()
        assertThat(recent.exists()).isTrue()
        assertThat(StoryMapDiskCache.storiesSize(stories)).isLessThanOrEqualTo(100L)
    }

    @Test
    fun `trim removes the sidecar with the bitmap`() {
        val stories = File(cacheDir, "stories-sidecar").apply { mkdirs() }
        val bitmap = File(stories, "map.jpg").apply { writeBytes(ByteArray(80)) }
        val sidecar = File(stories, "map.route").apply { writeBytes(ByteArray(40)) }
        bitmap.setLastModified(1_000L)
        sidecar.setLastModified(1_000L)
        File(stories, "keep.jpg").apply { writeBytes(ByteArray(10)) }.setLastModified(2_000L)

        StoryMapDiskCache.trim(stories, maxBytes = 50L)

        assertThat(bitmap.exists()).isFalse()
        assertThat(sidecar.exists()).isFalse()
    }
}
