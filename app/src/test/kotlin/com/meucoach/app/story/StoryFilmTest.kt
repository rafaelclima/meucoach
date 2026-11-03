package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.Canvas
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryFilmTest {
    @Test
    fun `draws deterministic grain that shifts with time`() {
        val first = render(0f)
        val sameTime = render(0f)
        val later = render(1f)

        assertThat(first.any { it != 0 }).isTrue()
        assertThat(first contentEquals sameTime).isTrue()
        assertThat(later contentEquals first).isFalse()
    }

    private fun render(timeSec: Float): IntArray {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        StoryFilm.drawGrain(Canvas(bitmap), SIZE, SIZE, timeSec)
        val pixels = IntArray(SIZE * SIZE)
        bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        bitmap.recycle()
        return pixels
    }

    private companion object {
        const val SIZE = 64
    }
}
