package com.meucoach.app.importer

import android.net.Uri
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import java.io.IOException
import javax.inject.Inject

/**
 * Stages picked/shared files and hands them to WorkManager.
 *
 * Staging happens before enqueueing because the URI permission granted by the
 * picker or share sheet is temporary and may be gone when the worker runs.
 */
class ImportCoordinator @Inject constructor(
    private val staging: ImportStaging,
    private val scheduler: ImportWorkScheduler,
) {
    suspend fun enqueue(uris: List<Uri>): ImportEnqueueResult {
        val staged = mutableListOf<StagedImport>()
        val failures = mutableListOf<ImportEnqueueFailure>()

        uris.distinctBy { it.toString() }.forEach { uri ->
            try {
                staged += staging.stage(uri)
            } catch (error: IOException) {
                failures += failure(uri, error)
            } catch (error: SecurityException) {
                failures += failure(uri, error)
            }
        }

        staged.forEach { scheduler.enqueue(workRequest(it)) }
        return ImportEnqueueResult(queued = staged.size, failures = failures)
    }

    private fun failure(uri: Uri, error: Exception): ImportEnqueueFailure = ImportEnqueueFailure(
        fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "arquivo",
        message = error.message ?: "Não foi possível preparar o arquivo.",
    )

    private fun workRequest(staged: StagedImport): OneTimeWorkRequest {
        val data = Data.Builder()
            .putString(ImportWorker.KEY_RELATIVE_PATH, staged.relativePath)
            .putString(ImportWorker.KEY_DISPLAY_NAME, staged.displayName)
            .putString(ImportWorker.KEY_MIME, staged.mime)
            .build()
        return OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(data)
            .addTag(ImportWorker.TAG)
            .build()
    }
}

fun interface ImportWorkScheduler {
    fun enqueue(request: OneTimeWorkRequest)
}

data class ImportEnqueueResult(val queued: Int, val failures: List<ImportEnqueueFailure> = emptyList())

data class ImportEnqueueFailure(val fileName: String, val message: String)
