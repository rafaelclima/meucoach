package com.meucoach.app.importer

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** WorkManager entry point for one staged import file. */
@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processor: StagedImportProcessor,
    private val notifier: ImportNotifier,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val relativePath = inputData.getString(KEY_RELATIVE_PATH) ?: return Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: return Result.failure()
        val mime = inputData.getString(KEY_MIME)

        return try {
            val result = processor.process(relativePath, displayName, mime)
            // The import already happened: a notification error must never
            // turn it into a retry (the file is gone and the job is DONE).
            runCatching { notifier.notify(result, displayName) }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            handleFailure(displayName, error)
        }
    }

    private fun handleFailure(displayName: String, error: Throwable): Result =
        when (ImportFailurePolicy.decide(error, runAttemptCount)) {
            ImportFailurePolicy.Action.RETRY -> Result.retry()

            ImportFailurePolicy.Action.GIVE_UP -> {
                // No job row exists for staging failures, so the notification is
                // the only place where the runner learns the file was dropped.
                runCatching { notifier.notifyFailure(displayName, ImportFailurePolicy.message(error)) }
                Result.failure()
            }
        }

    companion object {
        const val TAG = "meucoach-import"
        const val KEY_RELATIVE_PATH = "relativePath"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_MIME = "mime"
    }
}
