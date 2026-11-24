package com.meucoach.app.story

import android.content.Context
import android.graphics.BitmapFactory
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtPhotoRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of loading an activity's photo record. [missingPhoto] is true when
 * valid metadata still referenced a photo but the private file was missing or
 * corrupt; the record is already the transparent fallback and the caller may
 * show the one-time warning from the PRD without treating the load as an error.
 */
data class ArtPhotoSnapshot(val record: ArtPhotoRecord, val missingPhoto: Boolean = false)

/**
 * Private, per-activity photo store (ADR 0061 §2.2/§2.3).
 *
 * Files live in `files/art_photo/`: `<photoId>.jpg` for the upright private
 * copy and `<sha256(activityId)>.json` for the metadata. A single mutex
 * serializes reads, imports, toggles, removals and the orphan sweep; callers
 * that need a transaction (the importer and loader) use [withLock] so a photo
 * cannot be deleted while it is being written or decoded.
 */
@Singleton
class ArtPhotoStore @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY)
    private val mutex = Mutex()

    suspend fun load(activityId: String): ArtPhotoRecord = loadSnapshot(activityId).record

    suspend fun loadSnapshot(activityId: String): ArtPhotoSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock { loadSnapshotLocked(activityId) }
    }

    suspend fun save(activityId: String, record: ArtPhotoRecord) = withContext(Dispatchers.IO) {
        mutex.withLock { saveLocked(activityId, record) }
    }

    suspend fun remove(activityId: String) = withContext(Dispatchers.IO) {
        mutex.withLock { removeLocked(activityId) }
    }

    /**
     * Deletes the whole `files/art_photo/` directory (F7 reset). Runs under the
     * store mutex so no import, toggle or sweep interleaves. Returns true when
     * the directory is gone (including when it never existed), false otherwise,
     * so [com.meucoach.app.data.DataResetCoordinator] can report it in
     * `failedCleanups`.
     */
    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!directory.exists()) return@withLock true
            directory.deleteRecursively()
        }
    }

    /**
     * Deletes `.tmp` and unreferenced `.jpg` files older than 24 hours. A file
     * referenced by any valid metadata (including a waiting photo under
     * `TRANSPARENT`) is never touched. Returns the number of removed files.
     */
    suspend fun sweepOrphans(nowMillis: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        mutex.withLock { sweepOrphansLocked(nowMillis) }
    }

    internal suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    internal fun loadLocked(activityId: String): ArtPhotoRecord = loadSnapshotLocked(activityId).record

    private fun loadSnapshotLocked(activityId: String): ArtPhotoSnapshot {
        val metadata = readMetadataLocked(activityId) ?: return ArtPhotoSnapshot(EMPTY_RECORD)
        val photo = metadata.record.photo
        if (photo != null && !isUsablePhoto(photo.photoId)) {
            metadataFile(activityId).delete()
            return ArtPhotoSnapshot(EMPTY_RECORD, missingPhoto = true)
        }
        return ArtPhotoSnapshot(metadata.record)
    }

    internal fun saveLocked(activityId: String, record: ArtPhotoRecord) {
        require(record.photo == null || record.photo.activityId == activityId) {
            "The photo belongs to another activity."
        }
        val normalized = when {
            record.photo != null && !isUsablePhoto(record.photo.photoId) ->
                ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null)

            else -> record
        }
        writeMetadataLocked(activityId, normalized)
    }

    internal fun commitImportLocked(
        activityId: String,
        record: ArtPhotoRecord,
        tempPhoto: File,
        previousPhotoId: String?,
    ) {
        val photo = requireNotNull(record.photo) { "An imported photo requires a photo reference." }
        require(photo.activityId == activityId) { "The photo belongs to another activity." }
        directory.mkdirs()
        val target = photoFile(photo.photoId)
        if (!tempPhoto.renameTo(target)) {
            throw IOException("Could not commit the imported photo.")
        }
        try {
            writeMetadataLocked(activityId, record)
        } catch (error: Exception) {
            target.delete()
            throw error
        }
        if (previousPhotoId != null && previousPhotoId != photo.photoId) {
            deletePhotoLocked(previousPhotoId)
        }
    }

    internal fun photoFile(photoId: String): File = File(directory, "$photoId.jpg")

    internal fun photoTempFile(photoId: String): File = File(directory, "$photoId.jpg.tmp")

    internal fun metadataFile(activityId: String): File {
        require(activityId.isNotBlank()) { "activityId must not be blank." }
        return File(directory, "${sha256(activityId)}.json")
    }

    private fun removeLocked(activityId: String) {
        val metadata = readMetadataLocked(activityId)
        metadataFile(activityId).delete()
        metadata?.record?.photo?.let { photo -> deletePhotoLocked(photo.photoId) }
    }

    private fun readMetadataLocked(activityId: String): ArtPhotoMetadata? {
        val file = metadataFile(activityId)
        if (!file.isFile) return null
        val metadata = runCatching { ArtPhotoCodec.decode(file.readText()) }.getOrNull()
        if (metadata == null || metadata.activityId != activityId) {
            file.delete()
            return null
        }
        return metadata
    }

    private fun writeMetadataLocked(activityId: String, record: ArtPhotoRecord) {
        directory.mkdirs()
        val target = metadataFile(activityId)
        val temporary = File("${target.path}.tmp")
        temporary.writeText(ArtPhotoCodec.encode(activityId, record))
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Could not commit the art photo metadata.")
        }
    }

    private fun deletePhotoLocked(photoId: String) {
        photoFile(photoId).delete()
    }

    private fun isUsablePhoto(photoId: String): Boolean = isUsablePhotoFile(photoFile(photoId))

    internal fun isUsablePhotoFile(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val header = ByteArray(2)
        file.inputStream().use { input ->
            if (input.read(header) != header.size) return false
        }
        if (header[0] != JPEG_SOI_0 || header[1] != JPEG_SOI_1) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private fun sweepOrphansLocked(nowMillis: Long): Int {
        val files = directory.listFiles() ?: return 0
        val referenced = referencedPhotoIds(files)
        val cutoff = nowMillis - ORPHAN_MAX_AGE_MS
        var removed = 0
        files.forEach { file ->
            if (!file.isFile) return@forEach
            val isTemporary = file.name.endsWith(".tmp")
            val isPhoto = file.name.endsWith(".jpg")
            if (!isTemporary && !isPhoto) return@forEach
            if (isPhoto && file.nameWithoutExtension in referenced) return@forEach
            if (file.lastModified() <= cutoff && file.delete()) removed++
        }
        return removed
    }

    private fun referencedPhotoIds(files: Array<File>): Set<String> {
        val referenced = mutableSetOf<String>()
        files.filter { it.isFile && it.name.endsWith(".json") }.forEach { file ->
            val metadata = runCatching { ArtPhotoCodec.decode(file.readText()) }.getOrNull()
                ?: return@forEach
            val expected = runCatching { metadataFile(metadata.activityId).name }.getOrNull()
                ?: return@forEach
            if (file.name == expected) {
                metadata.record.photo?.let { referenced += it.photoId }
            }
        }
        return referenced
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val digits = "0123456789abcdef"
        val hex = CharArray(digest.size * 2)
        digest.forEachIndexed { index, byte ->
            val unsigned = byte.toInt() and 0xFF
            hex[index * 2] = digits[unsigned ushr 4]
            hex[index * 2 + 1] = digits[unsigned and 0x0F]
        }
        return String(hex)
    }

    companion object {
        internal const val DIRECTORY = "art_photo"
        internal const val ORPHAN_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private const val JPEG_SOI_0 = 0xFF.toByte()
        private const val JPEG_SOI_1 = 0xD8.toByte()

        private val EMPTY_RECORD = ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null)
    }
}
