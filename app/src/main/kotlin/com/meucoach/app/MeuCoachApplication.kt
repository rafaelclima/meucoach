package com.meucoach.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.meucoach.app.data.TrashPurgeScheduler
import com.meucoach.app.di.ApplicationScope
import com.meucoach.app.health.HealthSyncScheduler
import com.meucoach.app.reminder.ReminderScheduler
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.ProfileRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
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
    lateinit var trashPurgeScheduler: TrashPurgeScheduler

    @Inject
    lateinit var timeProvider: TimeProvider

    @Inject
    lateinit var profiles: ProfileRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Schedule only while a profile exists. After a full reset the app must
        // stay silent until onboarding creates a new profile (ADR 0025).
        applicationScope.launch {
            profiles.observeProfile()
                .map { it != null }
                .distinctUntilChanged()
                .collect { hasProfile ->
                    if (hasProfile) scheduleBackgroundWork()
                }
        }
        // Profile creation/edit and a fallback system-zone change reschedule reminders.
        applicationScope.launch {
            timeProvider.zoneId
                .drop(1)
                .collect {
                    if (profiles.getProfile() != null) {
                        runCatching { reminderScheduler.schedule() }
                    }
                }
        }
    }

    private fun scheduleBackgroundWork() {
        runCatching { reminderScheduler.schedule() }
        runCatching { healthSyncScheduler.schedule() }
        runCatching { trashPurgeScheduler.schedule() }
    }
}
