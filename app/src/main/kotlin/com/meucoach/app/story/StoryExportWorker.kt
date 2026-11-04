package com.meucoach.app.story

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * WorkManager entry point for the story export (ADR 0018). The plan and the
 * map are always resolved from inside the worker, so the export survives the
 * detail screen being closed and the process being killed.
 */
@HiltWorker
class StoryExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processor: StoryExportProcessor,
    private val notifier: StoryNotifier,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val activityId = inputData.getString(KEY_ACTIVITY_ID) ?: return Result.failure()
        val palette = StoryPaletteCodec.decode(inputData.getIntArray(StoryPaletteCodec.KEY))
            ?: return Result.failure()
        val mapPath = inputData.getString(KEY_MAP_PATH)

        val outcome = try {
            processor.export(activityId, palette, mapPath, onProgress = ::emitProgress)
        } catch (error: CancellationException) {
            // Cancellation is not a failure: the user cancelled the export.
            throw error
        } catch (error: Throwable) {
            StoryExportOutcome.Failed(error.message ?: StoryMessages.EXPORT_FAILED)
        }

        return when (outcome) {
            is StoryExportOutcome.Completed -> {
                notifier.notifyCompleted(activityId)
                Result.success(outputData(outcome.result))
            }

            is StoryExportOutcome.Retry ->
                if (runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    notifier.notifyFailed(activityId, outcome.message)
                    Result.failure(workDataOf(KEY_ERROR to outcome.message))
                }

            is StoryExportOutcome.Failed -> {
                notifier.notifyFailed(activityId, outcome.message)
                Result.failure(workDataOf(KEY_ERROR to outcome.message))
            }
        }
    }

    /** Throttled coarse progress; WorkManager persists every write, so 30 fps is not viable. */
    private suspend fun emitProgress(fraction: Float, etaSec: Int?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEmitMs < EMIT_INTERVAL_MS && fraction < 1f) return
        lastEmitMs = now
        setProgress(
            workDataOf(
                KEY_PROGRESS to fraction,
                KEY_ETA_SEC to (etaSec ?: -1),
            ),
        )
    }

    private var lastEmitMs = Long.MIN_VALUE

    private fun outputData(result: StoryExportResult): Data = workDataOf(
        KEY_VIDEO_URI to result.uri.toString(),
        KEY_SAVED_TO_GALLERY to result.savedToGallery,
        KEY_COVER_URI to result.coverUri?.toString(),
    )

    companion object {
        const val TAG = "meucoach-story"

        const val KEY_ACTIVITY_ID = "activityId"
        const val KEY_MAP_PATH = "mapPath"

        const val KEY_PROGRESS = "progress"
        const val KEY_ETA_SEC = "etaSec"

        const val KEY_VIDEO_URI = "videoUri"
        const val KEY_SAVED_TO_GALLERY = "savedToGallery"
        const val KEY_COVER_URI = "coverUri"
        const val KEY_ERROR = "error"

        private const val EMIT_INTERVAL_MS = 600L
        private const val MAX_ATTEMPTS = 2
    }
}
