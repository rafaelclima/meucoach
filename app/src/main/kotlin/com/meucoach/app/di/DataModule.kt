package com.meucoach.app.di

import android.content.Context
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.importer.ImportService
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.ProfileRepository
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
    fun provideImportService(database: MeuCoachDatabase, repository: ActivityRepository): ImportService =
        ImportService(database, repository)
}
