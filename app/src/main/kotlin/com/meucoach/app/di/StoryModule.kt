package com.meucoach.app.di

import com.meucoach.app.story.MapBackgroundRenderer
import com.meucoach.app.story.MapLibreBackgroundRenderer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StoryModule {
    @Binds
    @Singleton
    abstract fun bindMapBackgroundRenderer(renderer: MapLibreBackgroundRenderer): MapBackgroundRenderer
}
