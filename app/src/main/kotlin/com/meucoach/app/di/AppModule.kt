package com.meucoach.app.di

import com.meucoach.app.time.ProfileTimeProvider
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.ProfileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One clock for repositories, schedulers and the [TimeProvider]. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideTimeProvider(
        profileRepository: ProfileRepository,
        clock: Clock,
        @ApplicationScope scope: CoroutineScope,
    ): TimeProvider = ProfileTimeProvider(profileRepository, clock, scope)
}
