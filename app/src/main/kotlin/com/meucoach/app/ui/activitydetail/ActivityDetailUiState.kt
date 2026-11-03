package com.meucoach.app.ui.activitydetail

import android.graphics.Bitmap
import android.net.Uri
import com.meucoach.app.story.StoryPlan
import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.domain.insights.ActivityInsight
import com.meucoach.core.domain.zones.HrZoneDistribution

sealed interface StoryExportUiState {
    data object Idle : StoryExportUiState
    data object PreparingMap : StoryExportUiState
    data class Preview(val plan: StoryPlan, val mapBackground: Bitmap? = null) : StoryExportUiState
    data class Generating(val progress: Float) : StoryExportUiState
    data class Done(val uri: Uri, val savedToGallery: Boolean, val coverUri: Uri? = null) : StoryExportUiState
    data class Error(val message: String) : StoryExportUiState
}

data class ActivityDetailUiState(
    val isLoading: Boolean = true,
    val activity: StoredActivity? = null,
    val notFound: Boolean = false,
    val insights: List<ActivityInsight> = emptyList(),
    val hrZones: HrZoneDistribution? = null,
    val lthr: Int? = null,
    val storyExport: StoryExportUiState = StoryExportUiState.Idle,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val deleteError: String? = null,
)
