package com.meucoach.app.di

import android.content.Context
import androidx.work.WorkManager
import com.meucoach.app.health.HealthSyncScheduler
import com.meucoach.app.health.WorkManagerHealthSyncScheduler
import com.meucoach.app.importer.ImportWorkScheduler
import com.meucoach.app.reminder.ReminderScheduler
import com.meucoach.app.reminder.WorkManagerReminderScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideImportWorkScheduler(workManager: WorkManager): ImportWorkScheduler =
        ImportWorkScheduler { request -> workManager.enqueue(request) }

    @Provides
    @Singleton
    fun provideReminderScheduler(workManager: WorkManager): ReminderScheduler =
        WorkManagerReminderScheduler(workManager)

    @Provides
    @Singleton
    fun provideHealthSyncScheduler(workManager: WorkManager): HealthSyncScheduler =
        WorkManagerHealthSyncScheduler(workManager)
}
