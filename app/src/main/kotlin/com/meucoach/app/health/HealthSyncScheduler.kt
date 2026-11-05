package com.meucoach.app.health

import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface HealthSyncScheduler {
    fun schedule()
}

class WorkManagerHealthSyncScheduler @Inject constructor(private val workManager: WorkManager) : HealthSyncScheduler {
    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .addTag(HealthSyncWorker.TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val UNIQUE_NAME = "meucoach-health-sync"
        const val INITIAL_DELAY_MINUTES = 30L
    }
}
