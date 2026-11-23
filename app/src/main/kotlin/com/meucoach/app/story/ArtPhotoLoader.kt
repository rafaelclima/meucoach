package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtPhotoRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Decodes only the crop region of a stored photo into the requested preview or
 * export width (ADR 0061 §2.6). The source is already upright; this class never
 * reapplies EXIF and never returns a bitmap larger than the target. The caller
 * owns the returned bitmap; intermediates are recycled here.
 *
 * [BitmapRegionDecoder] is deprecated on recent SDKs, but it is still available
 * on the supported range and decodes only the crop region without
 * materializing the full 3072 px source.
 */
@Singleton
class ArtPhotoLoader @Inject constructor(private val store: ArtPhotoStore) {
    suspend fun load(photo: ArtPhotoRef, format: ArtFormat, targetWidth: Int): Bitmap? =
        withContext(Dispatchers.Default) {
            require(targetWidth > 0) { "targetWidth must be positive." }
            store.withLock {
                val file = store.photoFile(photo.photoId)
                if (!file.isFile) return@withLock null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withLock null
                val crop = photo.crop.resolve(format, bounds.outWidth, bounds.outHeight)
                    ?: return@withLock null
                val region = cropRegion(
                    left = crop.left,
                    top = crop.top,
                    right = crop.right,
                    bottom = crop.bottom,
                    sourceWidth = bounds.outWidth,
                    sourceHeight = bounds.outHeight,
                )
                val targetHeight = (targetWidth.toLong() * format.height / format.width)
                    .toInt()
                    .coerceAtLeast(1)
                val sample = sampleSize(region.width(), region.height(), targetWidth, targetHeight)
                val decoded = decodeRegion(file, region, sample) ?: return@withLock null
                if (decoded.width == targetWidth && decoded.height == targetHeight) {
                    decoded
                } else {
                    val scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
                    if (scaled !== decoded) decoded.recycle()
                    scaled
                }
            }
        }

    @Suppress("DEPRECATION")
    private fun decodeRegion(file: java.io.File, region: Rect, sample: Int): Bitmap? = runCatching {
        FileInputStream(file).use { input ->
            val decoder = BitmapRegionDecoder.newInstance(input.fd, false)
            try {
                decoder.decodeRegion(
                    region,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                )
            } finally {
                decoder.recycle()
            }
        }
    }.getOrNull()

    private fun cropRegion(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Rect {
        val x = floor(left * sourceWidth).toInt().coerceIn(0, sourceWidth - 1)
        val y = floor(top * sourceHeight).toInt().coerceIn(0, sourceHeight - 1)
        val rightPx = ceil(right * sourceWidth).toInt().coerceIn(x + 1, sourceWidth)
        val bottomPx = ceil(bottom * sourceHeight).toInt().coerceIn(y + 1, sourceHeight)
        return Rect(x, y, rightPx, bottomPx)
    }

    private fun sampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }
}
