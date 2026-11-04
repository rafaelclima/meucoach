package com.meucoach.app.di

import android.content.Context
import com.meucoach.app.ui.tutorial.TutorialStore
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.health.HealthConnectDailySource
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.health.HealthSyncService
import com.meucoach.core.data.health.HealthSyncStore
import com.meucoach.core.data.importer.ImportService
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.data.repository.UserDataRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeuCoachDatabase = MeuCoachDatabase.create(context)

    @Provides
    @Singleton
    fun provideActivityRepository(database: MeuCoachDatabase): ActivityRepository = ActivityRepository(database)

    @Provides
    @Singleton
    fun provideProfileRepository(database: MeuCoachDatabase): ProfileRepository = ProfileRepository(database)

    @Provides
    @Singleton
    fun provideCheckInRepository(database: MeuCoachDatabase): CheckInRepository = CheckInRepository(database)

    @Provides
    @Singleton
    fun provideDailyMetricsRepository(database: MeuCoachDatabase): DailyMetricsRepository =
        DailyMetricsRepository(database)

    @Provides
    @Singleton
    fun provideHealthDailySource(@ApplicationContext context: Context): HealthDailySource =
        HealthConnectDailySource(context)

    @Provides
    @Singleton
    fun provideHealthSyncStore(@ApplicationContext context: Context): HealthSyncStore = HealthSyncStore(context)

    @Provides
    @Singleton
    fun provideHealthSyncService(
        source: HealthDailySource,
        metricsRepository: DailyMetricsRepository,
        profileRepository: ProfileRepository,
        syncStore: HealthSyncStore,
    ): HealthSyncService = HealthSyncService(
        source = source,
        metricsRepository = metricsRepository,
        profileRepository = profileRepository,
        syncStore = syncStore,
    )

    @Provides
    @Singleton
    fun provideTrainingPlanRepository(database: MeuCoachDatabase): TrainingPlanRepository =
        TrainingPlanRepository(database)

    @Provides
    @Singleton
    fun provideUserDataRepository(database: MeuCoachDatabase, healthSyncStore: HealthSyncStore): UserDataRepository =
        UserDataRepository(database, healthSyncStore)

    @Provides
    @Singleton
    fun provideTutorialStore(@ApplicationContext context: Context): TutorialStore = TutorialStore(context)

    @Provides
    @Singleton
    fun provideImportService(database: MeuCoachDatabase, repository: ActivityRepository): ImportService =
        ImportService(database, repository)
}
