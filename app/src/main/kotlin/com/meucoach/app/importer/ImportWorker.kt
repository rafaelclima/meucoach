package com.meucoach.app.importer

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

/** WorkManager entry point for one staged import file. */
@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processor: StagedImportProcessor,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val relativePath = inputData.getString(KEY_RELATIVE_PATH) ?: return Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: return Result.failure()
        val mime = inputData.getString(KEY_MIME)

        return try {
            processor.process(relativePath, displayName, mime)
            Result.success()
        } catch (error: IOException) {
            // The staged file is still there; WorkManager will retry with backoff.
            Result.retry()
        }
    }

    companion object {
        const val TAG = "meucoach-import"
        const val KEY_RELATIVE_PATH = "relativePath"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_MIME = "mime"
    }
}
