package com.meucoach.app.di

import com.meucoach.app.story.CanvasStoryVideoExporter
import com.meucoach.app.story.MapBackgroundRenderer
import com.meucoach.app.story.MapLibreBackgroundRenderer
import com.meucoach.app.story.OpenFreeMapTileSource
import com.meucoach.app.story.StoryExportCoordinator
import com.meucoach.app.story.StoryTileSource
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

    /** MAP-09 seam: swapping to Protomaps means rebinding this interface. */
    @Binds
    @Singleton
    abstract fun bindStoryTileSource(source: OpenFreeMapTileSource): StoryTileSource

    @Binds
    @Singleton
    abstract fun bindStoryVideoExporter(exporter: CanvasStoryVideoExporter): StoryVideoExporter

    @Binds
    @Singleton
    abstract fun bindStoryExportCoordinator(coordinator: WorkManagerStoryExportCoordinator): StoryExportCoordinator
}
