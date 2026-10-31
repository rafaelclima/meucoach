package com.meucoach.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.meucoach.app.health.HealthSyncScheduler
import com.meucoach.app.reminder.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MeuCoachApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var healthSyncScheduler: HealthSyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Background work is best-effort; a WorkManager init failure must not block the app.
        runCatching { reminderScheduler.schedule() }
        runCatching { healthSyncScheduler.schedule() }
    }
}
