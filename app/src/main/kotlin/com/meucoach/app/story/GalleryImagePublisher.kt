package com.meucoach.app.story

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** Result of publishing one image into the gallery (ADR 0058 §2.7). */
data class ImagePublishResult(val uri: Uri, val savedToGallery: Boolean)

/**
 * Publishes an image file into `Pictures/MeuCoach` via MediaStore. Android 12+
 * only: no `WRITE_EXTERNAL_STORAGE` and no legacy branch. Callers decide
 * whether a failed publish is fatal — the video keeps treating cover/poster
 * failures as skippable.
 */
interface GalleryImagePublisher {
    /** Publishes the transparent art PNG (`image/png`). */
    fun publish(file: File): ImagePublishResult

    /** Publishes the photo art JPEG (`image/jpeg`, ADR 0061 §2.6). */
    fun publishJpg(file: File): ImagePublishResult
}

class MediaStoreGalleryImagePublisher
@Inject
constructor(@ApplicationContext private val context: Context) :
    GalleryImagePublisher {
    override fun publish(file: File): ImagePublishResult = publish(file, MIME_PNG)

    override fun publishJpg(file: File): ImagePublishResult = publish(file, MIME_JPEG)

    private fun publish(file: File, mimeType: String): ImagePublishResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/MeuCoach",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível reservar a imagem na galeria.")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Não foi possível gravar a imagem na galeria.")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return ImagePublishResult(uri, savedToGallery = true)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private companion object {
        const val MIME_PNG = "image/png"
        const val MIME_JPEG = "image/jpeg"
    }
}
