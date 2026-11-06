package com.meucoach.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.meucoach.app.di.ApplicationScope
import com.meucoach.app.health.HealthSyncScheduler
import com.meucoach.app.reminder.ReminderScheduler
import com.meucoach.app.time.TimeProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var timeProvider: TimeProvider

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Background work is best-effort; a WorkManager init failure must not block the app.
        runCatching { reminderScheduler.schedule() }
        runCatching { healthSyncScheduler.schedule() }
        // Profile creation/edit and a fallback system-zone change reschedule reminders.
        applicationScope.launch {
            timeProvider.zoneId
                .drop(1)
                .collect { runCatching { reminderScheduler.schedule() } }
        }
    }
}
