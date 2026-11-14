package com.meucoach.app.story

import android.graphics.PointF
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
class StoryMapVariantTest {
    private val cacheDir: File = run {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir
        val dir = File(base, "variant-test")
        dir.deleteRecursively()
        dir.mkdirs()
        dir
    }

    @Test
    fun `hash is deterministic for the same inputs`() {
        val pixels = listOf(PointF(1.25f, 2.5f), PointF(11.75f, 19.5f))

        val first = StoryMapVariant.variantHash(StoryMapStyle.MEUCOACH_DARK, 2430, 4320, pixels)
        val second = StoryMapVariant.variantHash(StoryMapStyle.MEUCOACH_DARK, 2430, 4320, pixels)

        assertThat(first).isEqualTo(second)
        assertThat(first).matches("[0-9a-f]+")
    }

    @Test
    fun `hash changes with style pixels or resolution`() {
        val pixels = listOf(PointF(1f, 2f))
        val base = StoryMapVariant.variantHash(StoryMapStyle.MEUCOACH_DARK, 2430, 4320, pixels)

        assertThat(StoryMapVariant.variantHash(StoryMapStyle.MEUCOACH_LIGHT, 2430, 4320, pixels))
            .isNotEqualTo(base)
        assertThat(StoryMapVariant.variantHash(StoryMapStyle.MEUCOACH_DARK, 1080, 1920, pixels))
            .isNotEqualTo(base)
        assertThat(StoryMapVariant.variantHash(StoryMapStyle.MEUCOACH_DARK, 2430, 4320, listOf(PointF(3f, 4f))))
            .isNotEqualTo(base)
    }

    @Test
    fun `manifest round-trips with bookends`() {
        val dir = StoryMapVariant.variantDir(cacheDir, "activity-1", "abc123")
        val manifest = StoryMapVariant.Manifest(
            activityId = "activity-1",
            variantHash = "abc123",
            style = StoryMapStyle.MEUCOACH_DARK,
            follow = StoryMapVariant.Entry("follow.jpg", 2430, 4320),
            opening = StoryMapVariant.Entry("opening.jpg", 1890, 3360),
            final = StoryMapVariant.Entry("final.jpg", 1890, 3360),
        )

        StoryMapVariant.writeManifest(dir, manifest)
        val loaded = StoryMapVariant.readManifest(dir)

        assertThat(loaded).isEqualTo(manifest)
        assertThat(StoryMapVariant.manifestFile(dir).isFile).isTrue()
    }

    @Test
    fun `corrupt manifest returns null instead of guessing`() {
        val dir = StoryMapVariant.variantDir(cacheDir, "activity-1", "corrupt")
        dir.mkdirs()
        StoryMapVariant.manifestFile(dir).writeText("not json")

        assertThat(StoryMapVariant.readManifest(dir)).isNull()
    }

    @Test
    fun `manifest with version mismatch returns null`() {
        val dir = StoryMapVariant.variantDir(cacheDir, "activity-1", "v-mismatch")
        dir.mkdirs()
        StoryMapVariant.manifestFile(dir).writeText(
            """{"v":999,"activityId":"activity-1","variant":"v-mismatch","style":"MEUCOACH_DARK",""" +
                """"follow":{"file":"follow.jpg","w":4,"h":4}}""",
        )

        assertThat(StoryMapVariant.readManifest(dir)).isNull()
    }

    @Test
    fun `sanitizes activity id for the variant dir`() {
        val dir = StoryMapVariant.variantDir(cacheDir, "a/b\\c", "abc123")

        assertThat(dir.absolutePath).doesNotContain("a/b")
        assertThat(dir.parentFile?.name).isEqualTo("a_b_c")
    }
}
