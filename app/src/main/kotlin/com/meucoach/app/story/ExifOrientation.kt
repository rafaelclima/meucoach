package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import kotlin.math.ceil

/**
 * Applies the eight EXIF orientations to a decoded bitmap. The private copy
 * must be upright before it reaches the store/loader, so every later crop uses
 * the same coordinate system as [com.meucoach.core.domain.story.ArtPhotoCrop].
 *
 * Consumes [bitmap]: the same instance is returned for normal/undefined
 * orientation; otherwise the transformed copy is returned and the source is
 * recycled.
 */
internal object ExifOrientation {
    fun apply(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }

            ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { setScale(1f, -1f) }

            ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply {
                setRotate(90f)
                postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }

            ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply {
                setRotate(-90f)
                postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(-90f) }

            else -> return bitmap
        }

        val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        matrix.mapRect(bounds)
        matrix.postTranslate(-bounds.left, -bounds.top)
        val output = Bitmap.createBitmap(
            ceil(bounds.width()).toInt().coerceAtLeast(1),
            ceil(bounds.height()).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        Canvas(output).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        bitmap.recycle()
        return output
    }
}
