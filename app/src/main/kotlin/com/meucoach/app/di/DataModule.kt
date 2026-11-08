package com.meucoach.app.di

import android.content.Context
import com.meucoach.app.firstrun.FirstRunMetricsStore
import com.meucoach.app.firstrun.FirstRunTipStore
import com.meucoach.app.importer.ImportedRunPromptStore
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.ui.tutorial.TutorialStore
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.epoch.DataEpochStore
import com.meucoach.core.data.epoch.DataWriteMutex
import com.meucoach.core.data.health.HealthConnectDailySource
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.health.HealthSyncService
import com.meucoach.core.data.health.HealthSyncStore
import com.meucoach.core.data.importer.ImportService
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.data.repository.UserDataRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeuCoachDatabase = MeuCoachDatabase.create(context)

    @Provides
    @Singleton
    fun provideActivityRepository(
        database: MeuCoachDatabase,
        clock: Clock,
        timeProvider: TimeProvider,
    ): ActivityRepository = ActivityRepository(
        database = database,
        clock = clock,
        currentZone = { timeProvider.zoneId.value },
    )

    @Provides
    @Singleton
    fun provideProfileRepository(database: MeuCoachDatabase, clock: Clock): ProfileRepository =
        ProfileRepository(database, clock)

    @Provides
    @Singleton
    fun provideCheckInRepository(database: MeuCoachDatabase, clock: Clock): CheckInRepository =
        CheckInRepository(database, clock)

    @Provides
    @Singleton
    fun provideDailyMetricsRepository(database: MeuCoachDatabase, clock: Clock): DailyMetricsRepository =
        DailyMetricsRepository(database, clock)

    @Provides
    @Singleton
    fun provideHealthDailySource(@ApplicationContext context: Context): HealthDailySource =
        HealthConnectDailySource(context)

    @Provides
    @Singleton
    fun provideHealthSyncStore(@ApplicationContext context: Context): HealthSyncStore = HealthSyncStore(context)

    @Provides
    @Singleton
    fun provideDataEpochStore(@ApplicationContext context: Context, clock: Clock): DataEpochStore =
        DataEpochStore(context, clock)

    @Provides
    @Singleton
    fun provideDataWriteMutex(): DataWriteMutex = DataWriteMutex()

    @Provides
    @Singleton
    fun provideHealthSyncService(
        source: HealthDailySource,
        metricsRepository: DailyMetricsRepository,
        profileRepository: ProfileRepository,
        syncStore: HealthSyncStore,
        clock: Clock,
        epochStore: DataEpochStore,
        writeMutex: DataWriteMutex,
    ): HealthSyncService = HealthSyncService(
        source = source,
        metricsRepository = metricsRepository,
        profileRepository = profileRepository,
        syncStore = syncStore,
        clock = clock,
        epochStore = epochStore,
        writeMutex = writeMutex,
    )

    @Provides
    @Singleton
    fun provideTrainingPlanRepository(database: MeuCoachDatabase, clock: Clock): TrainingPlanRepository =
        TrainingPlanRepository(database, clock)

    @Provides
    @Singleton
    fun provideDailyPrescriptionRepository(database: MeuCoachDatabase, clock: Clock): DailyPrescriptionRepository =
        DailyPrescriptionRepository(database, clock)

    @Provides
    @Singleton
    fun provideSessionExecutionRepository(database: MeuCoachDatabase, clock: Clock): SessionExecutionRepository =
        SessionExecutionRepository(database, clock)

    @Provides
    @Singleton
    fun provideUserDataRepository(
        database: MeuCoachDatabase,
        healthSyncStore: HealthSyncStore,
        clock: Clock,
    ): UserDataRepository = UserDataRepository(database, healthSyncStore, clock)

    @Provides
    @Singleton
    fun provideTutorialStore(@ApplicationContext context: Context, clock: Clock): TutorialStore =
        TutorialStore(context, clock = clock)

    @Provides
    @Singleton
    fun provideFirstRunMetricsStore(@ApplicationContext context: Context, clock: Clock): FirstRunMetricsStore =
        FirstRunMetricsStore(context, clock)

    @Provides
    @Singleton
    fun provideFirstRunTipStore(@ApplicationContext context: Context): FirstRunTipStore = FirstRunTipStore(context)

    @Provides
    @Singleton
    fun provideImportedRunPromptStore(@ApplicationContext context: Context): ImportedRunPromptStore =
        ImportedRunPromptStore(context)

    @Provides
    @Singleton
    fun provideImportService(
        database: MeuCoachDatabase,
        repository: ActivityRepository,
        clock: Clock,
        profileRepository: ProfileRepository,
        epochStore: DataEpochStore,
        writeMutex: DataWriteMutex,
    ): ImportService = ImportService(
        database = database,
        repository = repository,
        clock = clock,
        profileRepository = profileRepository,
        epochStore = epochStore,
        writeMutex = writeMutex,
    )
}
