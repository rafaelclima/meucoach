package com.meucoach.app.ui.activitydetail

import android.graphics.Bitmap
import android.net.Uri
import com.meucoach.app.story.StoryPlan
import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.domain.calibration.CalibrationCandidate
import com.meucoach.core.domain.insights.ActivityInsight
import com.meucoach.core.domain.story.StoryVerdict
import com.meucoach.core.domain.zones.HrZoneDistribution

sealed interface StoryExportUiState {
    data object Idle : StoryExportUiState
    data object PreparingMap : StoryExportUiState
    data class Preview(val plan: StoryPlan, val mapBackground: Bitmap? = null) : StoryExportUiState

    /** Background export in progress. [etaSec] is null until the estimate is trustworthy (ADR 0018).*/
    data class Generating(val progress: Float, val etaSec: Int? = null) : StoryExportUiState

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
    val calibrationCandidate: CalibrationCandidate? = null,
    val storyExport: StoryExportUiState = StoryExportUiState.Idle,
    val storyIntroSeen: Boolean = true,
    /** Profile photo used by the story pin and the closing badge. */
    val avatar: Bitmap? = null,
    /** Private coach reading, shown on this screen and never in the video. */
    val coachVerdict: StoryVerdict? = null,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val deleteError: String? = null,
)
