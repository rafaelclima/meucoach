package com.meucoach.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies the picked profile photo into app-private storage and downscales it.
 * Only a local path is persisted in the profile — no cloud, no broad storage
 * permission, and the original URI is not kept.
 */
@Singleton
class ProfilePhotoStore @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.filesDir, "profile")
    private val resolver = context.contentResolver

    suspend fun save(uri: Uri, maxSize: Int = 512): String? = withContext(Dispatchers.IO) {
        val bitmap = decodeUri(uri, maxSize) ?: return@withContext null
        directory.mkdirs()
        val file = File(directory, "avatar.jpg")
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }
        bitmap.recycle()
        file.absolutePath
    }

    /** Loads the avatar downscaled for UI/video use; null when the file is gone. */
    fun load(path: String?, maxSize: Int = 512): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeFile(path, this)
            }
            options.inSampleSize = sampleSize(options.outWidth, options.outHeight, maxSize)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        }.getOrNull()
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (file.parentFile == directory) runCatching { file.delete() }
    }

    private fun decodeUri(uri: Uri, maxSize: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSize)
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private fun sampleSize(width: Int, height: Int, maxSize: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= maxSize && height / (sample * 2) >= maxSize) {
            sample *= 2
        }
        return sample
    }
}
