package com.meucoach.app.ui.activitydetail

import android.graphics.Bitmap
import android.net.Uri
import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.domain.calibration.CalibrationCandidate
import com.meucoach.core.domain.feedback.SessionComparison
import com.meucoach.core.domain.insights.ActivityInsight
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRecipe
import com.meucoach.core.domain.story.StoryVerdict
import com.meucoach.core.domain.zones.HrZoneDistribution

sealed interface StoryExportUiState {
    data object Idle : StoryExportUiState
    data object PreparingMap : StoryExportUiState

    /** Background export in progress. [etaSec] is null until the estimate is trustworthy (ADR 0018).*/
    data class Generating(val progress: Float, val etaSec: Int? = null) : StoryExportUiState

    data class Done(val uri: Uri, val savedToGallery: Boolean, val coverUri: Uri? = null, val posterUri: Uri? = null) :
        StoryExportUiState
    data class Error(val message: String) : StoryExportUiState
}

/**
 * One extra chip of the Story Studio. Availability is decided by
 * `StoryMetricCatalog` from the activity itself, never by the UI: an extra
 * without data is disabled with its reason and never becomes zero.
 */
data class StoryExtraOption(
    val extra: StoryExtra,
    val label: String,
    val isAvailable: Boolean,
    val reason: String? = null,
) {
    init {
        require(isAvailable || reason != null) { "An unavailable story extra needs a reason" }
    }
}

/**
 * Recipe being edited in the Story Studio. The plan is resolved in the view
 * model for the current activity, so the three-frame preview only draws.
 */
data class StoryStudioUiState(
    val isOpen: Boolean = false,
    val recipe: StoryRecipe = StoryRecipe(),
    val plan: StoryPlan? = null,
    val extras: List<StoryExtraOption> = emptyList(),
    /** Whether the recipe is written to `meucoach_story` when the video is generated. */
    val saveAsDefault: Boolean = true,
)

data class ActivityDetailUiState(
    val isLoading: Boolean = true,
    val activity: StoredActivity? = null,
    val notFound: Boolean = false,
    val insights: List<ActivityInsight> = emptyList(),
    val hrZones: HrZoneDistribution? = null,
    val lthr: Int? = null,
    val calibrationCandidate: CalibrationCandidate? = null,
    val storyExport: StoryExportUiState = StoryExportUiState.Idle,
    val storyStudio: StoryStudioUiState = StoryStudioUiState(),
    val storyIntroSeen: Boolean = true,
    /** Profile photo used by the story pin and the closing badge. */
    val avatar: Bitmap? = null,
    /** Private coach reading, shown on this screen and never in the video. */
    val coachVerdict: StoryVerdict? = null,
    /** Planned × executed reading (FR-010); null while loading or without an activity. */
    val comparison: SessionComparison? = null,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val deleteError: String? = null,
)
