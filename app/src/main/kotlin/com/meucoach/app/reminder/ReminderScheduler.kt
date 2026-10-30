package com.meucoach.app.reminder

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface ReminderScheduler {
    fun schedule()
}

class WorkManagerReminderScheduler @Inject constructor(private val workManager: WorkManager) : ReminderScheduler {
    override fun schedule() {
        schedule(kind = ReminderKind.CHECK_IN, hour = CHECK_IN_HOUR, minute = 0)
        schedule(kind = ReminderKind.WORKOUT, hour = WORKOUT_HOUR, minute = 0)
    }

    private fun schedule(kind: ReminderKind, hour: Int, minute: Int) {
        val delay = ReminderContent.nextDelay(ZonedDateTime.now(), hour, minute)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay)
            .setInputData(workDataOf(ReminderWorker.KEY_KIND to kind.name))
            .addTag(ReminderWorker.TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            workerName(kind),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun workerName(kind: ReminderKind): String = "meucoach-reminder-${kind.name.lowercase()}"

    companion object {
        const val CHECK_IN_HOUR = 18
        const val WORKOUT_HOUR = 7
    }
}
