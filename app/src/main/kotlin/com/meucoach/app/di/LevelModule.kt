package com.meucoach.app.di

import com.meucoach.app.application.EvaluateLevelGraduation
import com.meucoach.app.application.LevelGraduationEvaluator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LevelModule {
    @Binds
    @Singleton
    abstract fun bindLevelGraduationEvaluator(impl: EvaluateLevelGraduation): LevelGraduationEvaluator
}
