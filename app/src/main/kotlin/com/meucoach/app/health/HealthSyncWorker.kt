package com.meucoach.app.health

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meucoach.core.data.health.HealthSyncService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class HealthSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncService: HealthSyncService,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching { syncService.sync() }.fold(
        onSuccess = { result ->
            if (result.error != null && result.missingPermissions.isEmpty()) {
                Result.retry()
            } else {
                Result.success()
            }
        },
        onFailure = { Result.retry() },
    )

    companion object {
        const val TAG = "meucoach-health-sync"
    }
}
