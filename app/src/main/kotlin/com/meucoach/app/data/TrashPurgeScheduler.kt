package com.meucoach.app.data

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface TrashPurgeScheduler {
    fun schedule()
}

class WorkManagerTrashPurgeScheduler @Inject constructor(private val workManager: WorkManager) : TrashPurgeScheduler {
    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .addTag(TrashPurgeWorker.TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            TrashPurgeWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
