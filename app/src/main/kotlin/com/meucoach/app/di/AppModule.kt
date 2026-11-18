package com.meucoach.app.di

import com.meucoach.app.BuildConfig
import com.meucoach.app.data.DataResetCoordinator
import com.meucoach.app.data.DataResetter
import com.meucoach.app.time.ProfileTimeProvider
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.coach.CoachApi
import com.meucoach.core.data.coach.DisabledCoachApi
import com.meucoach.core.data.coach.HttpCoachApi
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

    @Provides
    @Singleton
    fun provideDataResetter(coordinator: DataResetCoordinator): DataResetter = coordinator

    @Provides
    @Singleton
    fun provideCoachApi(): CoachApi {
        val url = BuildConfig.COACH_PROXY_URL
        val token = BuildConfig.COACH_PROXY_TOKEN
        if (url.isBlank() || token.isBlank()) {
            return DisabledCoachApi()
        }
        return HttpCoachApi(baseUrl = url, token = token)
    }
}
