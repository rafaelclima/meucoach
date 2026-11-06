package com.meucoach.app.story

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.meucoach.core.data.epoch.DataEpochStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * WorkManager entry point for the story export (ADR 0018). The plan and the
 * map are always resolved from inside the worker, so the export survives the
 * detail screen being closed and the process being killed.
 *
 * The worker runs as a foreground service: without it, Android/Samsung stops
 * the job a few seconds after the app leaves the foreground and the export
 * never finishes.
 */
@HiltWorker
class StoryExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processor: StoryExportProcessor,
    private val notifier: StoryNotifier,
    private val failures: StoryExportFailureStore,
    private val epochStore: DataEpochStore,
) : CoroutineWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(
        activityId = inputData.getString(KEY_ACTIVITY_ID).orEmpty(),
        progress = 0f,
        etaSec = null,
    )

    override suspend fun doWork(): Result {
        val activityId = inputData.getString(KEY_ACTIVITY_ID) ?: return Result.failure()
        val palette = StoryPaletteCodec.decode(inputData.getIntArray(StoryPaletteCodec.KEY))
            ?: return Result.failure()
        val mapPath = inputData.getString(KEY_MAP_PATH)
        val epoch = epochStore.current()
        failures.clear(activityId)
        enterForeground(activityId)

        val outcome = try {
            processor.export(activityId, palette, mapPath, onProgress = ::publishProgress)
        } catch (error: CancellationException) {
            // Cancellation is not a failure: the user cancelled the export.
            throw error
        } catch (error: Throwable) {
            StoryExportOutcome.Failed(error.message ?: StoryMessages.EXPORT_FAILED)
        }

        // A reset happened while the story was rendering: do not notify or
        // publish a result that belongs to the previous data generation.
        if (epochStore.current() != epoch) return Result.success()

        return when (outcome) {
            is StoryExportOutcome.Completed -> {
                notifier.notifyCompleted(activityId)
                Result.success(outputData(outcome.result))
            }

            is StoryExportOutcome.Retry ->
                if (runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    fail(activityId, outcome.message)
                }

            is StoryExportOutcome.Failed -> fail(activityId, outcome.message)
        }
    }

    private fun fail(activityId: String, message: String): Result {
        // FAILED work does not carry outputData to WorkInfo; persist the
        // message so the detail screen can explain what happened.
        failures.save(activityId, message)
        notifier.notifyFailed(activityId, message)
        return Result.failure()
    }

    private suspend fun enterForeground(activityId: String) {
        // Android 12+ may refuse a background foreground-service start; the
        // export then behaves like before instead of crashing the worker.
        runCatching { setForeground(foregroundInfo(activityId, progress = 0f, etaSec = null)) }
            .onFailure { error -> Log.w(TAG, "Foreground service indisponível", error) }
    }

    private fun foregroundInfo(activityId: String, progress: Float, etaSec: Int?): ForegroundInfo {
        val notification = notifier.buildProgress(activityId, progress, etaSec)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                StoryNotification.PROGRESS_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(StoryNotification.PROGRESS_ID, notification)
        }
    }

    /** Throttled coarse progress + notification; WorkManager persists each write. */
    private suspend fun publishProgress(fraction: Float, etaSec: Int?) {
        val activityId = inputData.getString(KEY_ACTIVITY_ID).orEmpty()
        val now = SystemClock.elapsedRealtime()
        // Start at 0, not Long.MIN_VALUE: `now - Long.MIN_VALUE` overflows and
        // would compare as negative forever.
        if (now - lastProgressMs >= PROGRESS_INTERVAL_MS || fraction >= 1f) {
            lastProgressMs = now
            setProgress(
                workDataOf(
                    KEY_PROGRESS to fraction,
                    KEY_ETA_SEC to (etaSec ?: -1),
                ),
            )
        }
        if (now - lastNotificationMs >= NOTIFICATION_INTERVAL_MS || fraction <= 0f || fraction >= 1f) {
            lastNotificationMs = now
            // WorkManager owns the foreground notification: update it through
            // setForeground. Posting our own notification with the same id
            // replaces the FGS notification and Android stops the worker.
            runCatching { setForeground(foregroundInfo(activityId, fraction, etaSec)) }
        }
    }

    private var lastProgressMs = 0L
    private var lastNotificationMs = 0L

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

        private const val PROGRESS_INTERVAL_MS = 600L
        private const val NOTIFICATION_INTERVAL_MS = 1_500L
        private const val MAX_ATTEMPTS = 2
    }
}
