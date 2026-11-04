package com.meucoach.app.di

import com.meucoach.app.story.CanvasStoryVideoExporter
import com.meucoach.app.story.MapBackgroundRenderer
import com.meucoach.app.story.MapLibreBackgroundRenderer
import com.meucoach.app.story.StoryExportCoordinator
import com.meucoach.app.story.StoryVideoExporter
import com.meucoach.app.story.WorkManagerStoryExportCoordinator
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

    @Binds
    @Singleton
    abstract fun bindStoryVideoExporter(exporter: CanvasStoryVideoExporter): StoryVideoExporter

    @Binds
    @Singleton
    abstract fun bindStoryExportCoordinator(coordinator: WorkManagerStoryExportCoordinator): StoryExportCoordinator
}
