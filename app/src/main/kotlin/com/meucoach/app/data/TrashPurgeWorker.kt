package com.meucoach.app.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meucoach.app.importer.ImportStaging
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Daily purge of activities whose seven-day trash window has expired. */
@HiltWorker
class TrashPurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val profiles: ProfileRepository,
    private val activities: ActivityRepository,
    private val staging: ImportStaging,
    private val timeProvider: TimeProvider,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // After a reset there is no profile; purge is a no-op and must not retry.
        if (profiles.getProfile() == null) return Result.success()
        val purged = runCatching { activities.purgeTrashBefore(timeProvider.now()) }
            .getOrElse { return Result.retry() }
        purged.forEach { item -> item.rawFilePath?.let { runCatching { staging.delete(it) } } }
        return Result.success()
    }

    companion object {
        const val TAG = "meucoach-trash-purge"
        const val UNIQUE_NAME = "meucoach-trash-purge"
    }
}
