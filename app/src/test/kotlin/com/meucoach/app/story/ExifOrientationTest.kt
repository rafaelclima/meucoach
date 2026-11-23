package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExifOrientationTest {
    @Test
    fun `normal orientation keeps the bitmap untouched`() {
        val source = source()

        val result = ExifOrientation.apply(source, ExifInterface.ORIENTATION_NORMAL)

        assertThat(result).isSameAs(source)
        assertQuadrants(result, RED, GREEN, BLUE, YELLOW)
        result.recycle()
    }

    @Test
    fun `undefined orientation keeps the bitmap untouched`() {
        val source = source()

        val result = ExifOrientation.apply(source, 0)

        assertThat(result).isSameAs(source)
        assertQuadrants(result, RED, GREEN, BLUE, YELLOW)
        result.recycle()
    }

    @Test
    fun `mirror horizontal swaps left and right`() {
        assertOrientation(
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            GREEN,
            RED,
            YELLOW,
            BLUE,
        )
    }

    @Test
    fun `rotate 180 turns the bitmap upside down`() {
        assertOrientation(
            ExifInterface.ORIENTATION_ROTATE_180,
            YELLOW,
            BLUE,
            GREEN,
            RED,
        )
    }

    @Test
    fun `mirror vertical swaps top and bottom`() {
        assertOrientation(
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            BLUE,
            YELLOW,
            RED,
            GREEN,
        )
    }

    @Test
    fun `transpose reflects across the main diagonal`() {
        assertOrientation(
            ExifInterface.ORIENTATION_TRANSPOSE,
            RED,
            BLUE,
            GREEN,
            YELLOW,
        )
    }

    @Test
    fun `rotate 90 turns clockwise`() {
        assertOrientation(
            ExifInterface.ORIENTATION_ROTATE_90,
            BLUE,
            RED,
            YELLOW,
            GREEN,
        )
    }

    @Test
    fun `transverse reflects across the anti diagonal`() {
        assertOrientation(
            ExifInterface.ORIENTATION_TRANSVERSE,
            YELLOW,
            GREEN,
            BLUE,
            RED,
        )
    }

    @Test
    fun `rotate 270 turns counterclockwise`() {
        assertOrientation(
            ExifInterface.ORIENTATION_ROTATE_270,
            GREEN,
            YELLOW,
            RED,
            BLUE,
        )
    }

    private fun assertOrientation(orientation: Int, topLeft: Int, topRight: Int, bottomLeft: Int, bottomRight: Int) {
        val result = ExifOrientation.apply(source(), orientation)

        assertThat(result.width).isEqualTo(2)
        assertThat(result.height).isEqualTo(2)
        assertQuadrants(result, topLeft, topRight, bottomLeft, bottomRight)
        result.recycle()
    }

    private fun assertQuadrants(bitmap: Bitmap, topLeft: Int, topRight: Int, bottomLeft: Int, bottomRight: Int) {
        assertThat(bitmap.getPixel(0, 0)).isEqualTo(topLeft)
        assertThat(bitmap.getPixel(1, 0)).isEqualTo(topRight)
        assertThat(bitmap.getPixel(0, 1)).isEqualTo(bottomLeft)
        assertThat(bitmap.getPixel(1, 1)).isEqualTo(bottomRight)
    }

    private fun source(): Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
        setPixel(0, 0, RED)
        setPixel(1, 0, GREEN)
        setPixel(0, 1, BLUE)
        setPixel(1, 1, YELLOW)
    }

    private companion object {
        val RED = Color.rgb(255, 0, 0)
        val GREEN = Color.rgb(0, 255, 0)
        val BLUE = Color.rgb(0, 0, 255)
        val YELLOW = Color.rgb(255, 255, 0)
    }
}
