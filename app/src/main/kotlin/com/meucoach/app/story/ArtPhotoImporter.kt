package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtPhotoCrop
import com.meucoach.core.domain.story.ArtPhotoRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface ArtPhotoImportResult {
    data class Success(val photo: ArtPhotoRef) : ArtPhotoImportResult

    data class Failure(val error: ArtPhotoImportError) : ArtPhotoImportResult
}

enum class ArtPhotoImportError {
    SOURCE_UNREADABLE,
    INVALID_IMAGE,
    STORAGE,
}

/**
 * Copies a PhotoPicker URI into the private per-activity store (ADR 0061 §2.2).
 *
 * The import is atomic: decode with bounds/sample, apply EXIF orientation,
 * downsample to a side of at most [MAX_SOURCE_SIDE], write `<photoId>.jpg.tmp`,
 * validate it, rename it, then write/rename the metadata. The previous photo is
 * deleted only after the metadata commit; any failure leaves the old photo and
 * metadata intact. The whole operation holds the store mutex so two imports
 * cannot interleave.
 */
@Singleton
class ArtPhotoImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ArtPhotoStore,
) {
    suspend fun import(
        activityId: String,
        source: Uri,
        crop: ArtPhotoCrop = ArtPhotoCrop(),
        background: ArtBackgroundMode = ArtBackgroundMode.PHOTO,
    ): ArtPhotoImportResult = withContext(Dispatchers.IO) {
        require(activityId.isNotBlank()) { "activityId must not be blank." }
        store.withLock {
            val previousPhotoId = store.loadLocked(activityId).photo?.photoId
            val photoId = UUID.randomUUID().toString()
            val temporary = store.photoTempFile(photoId)
            var decoded: Bitmap? = null
            var upright: Bitmap? = null
            var constrained: Bitmap? = null
            try {
                val orientation = readOrientation(source)
                val bounds = readBounds(source)
                val sample = sampleSize(bounds.first, bounds.second)
                decoded = decode(source, sample)
                    ?: throw ArtPhotoImportFailure(ArtPhotoImportError.INVALID_IMAGE)
                upright = ExifOrientation.apply(decoded, orientation)
                constrained = constrain(upright)
                writeJpeg(constrained, temporary)
                if (!store.isUsablePhotoFile(temporary)) {
                    throw ArtPhotoImportFailure(ArtPhotoImportError.STORAGE)
                }
                val photo = ArtPhotoRef(photoId = photoId, activityId = activityId, crop = crop)
                store.commitImportLocked(
                    activityId = activityId,
                    record = ArtPhotoRecord(background = background, photo = photo),
                    tempPhoto = temporary,
                    previousPhotoId = previousPhotoId,
                )
                ArtPhotoImportResult.Success(photo)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                ArtPhotoImportResult.Failure(error.toImportError())
            } finally {
                temporary.delete()
                recycleDistinct(constrained, upright, decoded)
            }
        }
    }

    private fun readOrientation(source: Uri): Int = runCatching {
        context.contentResolver.openInputStream(source)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun readBounds(source: Uri): Pair<Int, Int> = openSource(source) { input ->
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw ArtPhotoImportFailure(ArtPhotoImportError.INVALID_IMAGE)
        }
        options.outWidth to options.outHeight
    }

    private fun decode(source: Uri, sampleSize: Int): Bitmap? = openSource(source) { input ->
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        BitmapFactory.decodeStream(input, null, options)
    }

    private fun <T> openSource(source: Uri, block: (java.io.InputStream) -> T): T {
        val input = try {
            context.contentResolver.openInputStream(source)
        } catch (error: Exception) {
            sourceFailure(error)
        } ?: sourceFailure(null)
        return try {
            input.use(block)
        } catch (failure: ArtPhotoImportFailure) {
            throw failure
        } catch (error: Exception) {
            sourceFailure(error)
        }
    }

    private fun sourceFailure(cause: Throwable?): Nothing =
        throw ArtPhotoImportFailure(ArtPhotoImportError.SOURCE_UNREADABLE, cause)

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / sample > MAX_SOURCE_SIDE) {
            sample *= 2
        }
        return sample
    }

    private fun constrain(bitmap: Bitmap): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= MAX_SOURCE_SIDE) return bitmap
        val scale = MAX_SOURCE_SIDE.toFloat() / largest
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun writeJpeg(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                throw ArtPhotoImportFailure(ArtPhotoImportError.STORAGE)
            }
            output.fd.sync()
        }
    }

    private fun recycleDistinct(vararg bitmaps: Bitmap?) {
        val recycled = mutableListOf<Bitmap>()
        bitmaps.filterNotNull().forEach { bitmap ->
            if (recycled.none { it === bitmap }) {
                bitmap.recycle()
                recycled += bitmap
            }
        }
    }

    private fun Throwable.toImportError(): ArtPhotoImportError = when (this) {
        is ArtPhotoImportFailure -> error
        is SecurityException, is FileNotFoundException -> ArtPhotoImportError.SOURCE_UNREADABLE
        is IOException -> ArtPhotoImportError.STORAGE
        else -> ArtPhotoImportError.INVALID_IMAGE
    }

    private class ArtPhotoImportFailure(val error: ArtPhotoImportError, cause: Throwable? = null) : Exception(cause)

    companion object {
        internal const val MAX_SOURCE_SIDE = 3072
        internal const val JPEG_QUALITY = 90
    }
}
