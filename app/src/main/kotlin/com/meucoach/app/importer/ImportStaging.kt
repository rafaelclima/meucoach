package com.meucoach.app.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.meucoach.core.data.id.UuidV7
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Copies a picked or shared document into app-private storage before it is queued.
 *
 * The URI permission from the picker/share sheet is temporary, so the worker
 * cannot rely on it later. Files are stored under `files/imports` with a
 * generated name; the original display name is kept only in the import job.
 */
class ImportStaging internal constructor(
    context: Context,
    private val maxBytes: Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, MAX_IMPORT_BYTES, Dispatchers.IO)

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val importsDir = File(appContext.filesDir, RELATIVE_DIR)

    suspend fun stage(uri: Uri): StagedImport = withContext(dispatcher) {
        val displayName = displayName(uri)
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        val extension = displayName
            .substringAfterLast('.', "")
            .lowercase()
            .filter { it.isLetterOrDigit() }
            .take(MAX_EXTENSION_LENGTH)
        val fileName = if (extension.isEmpty()) UuidV7.generate() else "${UuidV7.generate()}.$extension"
        val relativePath = "$RELATIVE_DIR/$fileName"
        val target = File(appContext.filesDir, relativePath)
        val part = File(appContext.filesDir, "$relativePath.part")
        target.parentFile?.mkdirs()

        try {
            val input = resolver.openInputStream(uri)
                ?: throw ImportStagingException("Não foi possível abrir o arquivo.")
            val size = input.use { source ->
                part.outputStream().use { output -> copyLimited(source, output) }
            }
            if (!part.renameTo(target)) {
                throw ImportStagingException("Não foi possível preparar o arquivo.")
            }
            StagedImport(
                relativePath = relativePath,
                displayName = displayName,
                mime = mime,
                sizeBytes = size,
            )
        } catch (error: ImportStagingException) {
            part.delete()
            throw error
        } catch (error: IOException) {
            part.delete()
            throw ImportStagingException("Não foi possível copiar o arquivo.", error)
        }
    }

    suspend fun read(relativePath: String): ByteArray = withContext(dispatcher) {
        resolve(relativePath).readBytes()
    }

    suspend fun delete(relativePath: String): Boolean = withContext(dispatcher) {
        resolve(relativePath).delete()
    }

    private fun copyLimited(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw ImportStagingException("Arquivo maior que ${limitLabel()}.")
            }
            output.write(buffer, 0, read)
        }
        if (total == 0L) throw ImportStagingException("O arquivo está vazio.")
        return total
    }

    private fun displayName(uri: Uri): String {
        val queried = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull()
        val raw = queried
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: FALLBACK_NAME
        return raw
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .take(MAX_NAME_LENGTH)
            .ifBlank { FALLBACK_NAME }
    }

    private fun resolve(relativePath: String): File {
        val file = File(appContext.filesDir, relativePath).canonicalFile
        if (!file.toPath().startsWith(importsDir.canonicalFile.toPath())) {
            throw ImportStagingException("Caminho de importação inválido.")
        }
        return file
    }

    private fun limitLabel(): String = if (maxBytes >= MEBIBYTE) "${maxBytes / MEBIBYTE} MB" else "$maxBytes bytes"

    companion object {
        const val RELATIVE_DIR = "imports"
        const val MAX_IMPORT_BYTES = 64L * 1024 * 1024
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val MAX_EXTENSION_LENGTH = 8
        private const val MAX_NAME_LENGTH = 200
        private const val FALLBACK_NAME = "arquivo"
        private const val MEBIBYTE = 1024L * 1024L
    }
}

data class StagedImport(val relativePath: String, val displayName: String, val mime: String?, val sizeBytes: Long)

class ImportStagingException(message: String, cause: Throwable? = null) : IOException(message, cause)
