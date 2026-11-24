package com.meucoach.app.story

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtRecipe
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryArtPhotoRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val photoRecipe = ArtRecipe(
        background = ArtBackgroundMode.PHOTO,
    )

    @Test
    fun `photo mode returns null without a bitmap`() {
        val renderer = renderer(photoRecipe)

        assertThat(renderer.render(fiveKStoryPlan(), fiveKStoryPlan().localDate, 1080, 1350)).isNull()
    }

    @Test
    fun `transparent mode ignores a photo bitmap`() {
        val plan = fiveKStoryPlan()
        val photo = SyntheticArtPhoto.solid(1080, 1350, 0xFFFF0000.toInt())

        val bitmap = checkNotNull(renderer(ArtRecipe()).render(plan, plan.localDate, 1080, 1350, photo))

        assertThat(bitmap.getPixel(0, 0) and 0xFF000000.toInt()).isZero()
        assertThat(bitmap.getPixel(1_079, 1_349) and 0xFF000000.toInt()).isZero()
        assertThat(photo.isRecycled).isFalse()
        bitmap.recycle()
        photo.recycle()
    }

    @Test
    fun `photo mode draws the photo full bleed and the 58 percent scrim`() {
        val plan = fiveKStoryPlan()
        val white = SyntheticArtPhoto.solid(1080, 1350, 0xFFFFFFFF.toInt())

        val bitmap = checkNotNull(renderer(photoRecipe).render(plan, plan.localDate, 1080, 1350, white))

        // Outside every block the photo is untouched.
        assertThat(bitmap.getPixel(1_070, 10)).isEqualTo(0xFFFFFFFF.toInt())
        // Inside the rounded scrim over the brand (padding 36), black at 58%
        // over white is ~107 in every channel and fully opaque.
        val scrim = bitmap.getPixel(110, 60)
        assertThat(scrim ushr 24).isEqualTo(255)
        assertThat((scrim shr 16) and 0xFF).isBetween(100, 115)
        assertThat((scrim shr 8) and 0xFF).isBetween(100, 115)
        assertThat(scrim and 0xFF).isBetween(100, 115)
        assertThat(white.isRecycled).isFalse()
        bitmap.recycle()
        white.recycle()
    }

    @Test
    fun `photo mode draws a full bleed 9x16 with the story safe bands`() {
        val plan = fiveKStoryPlan()
        val photo = SyntheticArtPhoto.landscape(1080, 1920)

        val bitmap = checkNotNull(
            renderer(photoRecipe.copy(format = ArtFormat.PORTRAIT_9_16))
                .render(plan, plan.localDate, 1080, 1920, photo),
        )

        // Full bleed: even the top story band is photo, not transparency.
        listOf(10 to 10, 10 to 1_910, 1_070 to 1_910).forEach { (x, y) ->
            assertThat(bitmap.getPixel(x, y) ushr 24).isEqualTo(255)
            assertThat(bitmap.getPixel(x, y)).isEqualTo(photo.getPixel(x, y))
        }
        bitmap.recycle()
        photo.recycle()
    }

    @Test
    fun `write jpg produces a 1080 wide jpeg without alpha`() {
        val plan = fiveKStoryPlan()
        val photo = SyntheticArtPhoto.landscape(1080, 1350)
        val output = ByteArrayOutputStream()

        val written = renderer(photoRecipe).writeJpg(plan, plan.localDate, photo, output)

        assertThat(written).isTrue()
        val jpg = output.toByteArray()
        assertThat(jpg.size).isGreaterThan(1_000)
        assertThat(jpg.copyOfRange(0, JPEG_SIGNATURE.size)).isEqualTo(JPEG_SIGNATURE)
        val decoded = checkNotNull(BitmapFactory.decodeByteArray(jpg, 0, jpg.size))
        assertThat(decoded.width).isEqualTo(1080)
        assertThat(decoded.height).isEqualTo(1350)
        assertThat(decoded.hasAlpha()).isFalse()
        decoded.recycle()
        photo.recycle()
    }

    @Test
    fun `write jpg refuses the transparent mode`() {
        val plan = fiveKStoryPlan()
        val photo = SyntheticArtPhoto.solid(1080, 1350, 0xFFFFFFFF.toInt())

        val written = renderer(ArtRecipe()).writeJpg(plan, plan.localDate, photo, ByteArrayOutputStream())

        assertThat(written).isFalse()
        photo.recycle()
    }

    private fun renderer(recipe: ArtRecipe): StoryArtRenderer = StoryArtRenderer(
        recipe = recipe,
        typeface = ArtFont.INTER.typeface(context),
        logoBitmap = StoryBranding.logo(context),
    )

    private companion object {
        val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    }
}
