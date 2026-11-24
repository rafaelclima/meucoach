package com.meucoach.app.ui.activitydetail

import android.graphics.Bitmap
import android.net.Uri
import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.domain.calibration.CalibrationCandidate
import com.meucoach.core.domain.feedback.SessionComparison
import com.meucoach.core.domain.insights.ActivityInsight
import com.meucoach.core.domain.story.ArtBlock
import com.meucoach.core.domain.story.ArtPhotoCrop
import com.meucoach.core.domain.story.ArtRecipe
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
 * Sync export of the transparent art card (ADR 0058 §2.7): the Studio stays
 * open, the CTA shows busy and the failure comes back inline with a retry.
 */
sealed interface ArtExportUiState {
    data object Idle : ArtExportUiState

    /** One render+publish in flight; the CTAs disable themselves. */
    data class Exporting(val forShare: Boolean) : ArtExportUiState

    data class Done(
        val uri: Uri,
        val savedToGallery: Boolean,
        /** True when this export was requested by Compartilhar. */
        val forShare: Boolean,
        val reused: Boolean,
    ) : ArtExportUiState

    /**
     * The recipe changed while the render was in flight. The PNG no longer
     * matches what the screen shows, so it was discarded instead of shared.
     */
    data object Stale : ArtExportUiState

    data class Error(val message: String) : ArtExportUiState
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
 *
 * The art recipe is the effective combination of the shared style
 * (`meucoach_art`) and the per-activity photo record (`files/art_photo/`).
 * The style store never persists the photo; the photo store owns
 * `background` and `photo` (ADR 0061 §2.3).
 */
data class StoryStudioUiState(
    val isOpen: Boolean = false,
    val recipe: StoryRecipe = StoryRecipe(),
    val plan: StoryPlan? = null,
    val extras: List<StoryExtraOption> = emptyList(),
    /** Whether the recipe is written to `meucoach_story` when the video is generated. */
    val saveAsDefault: Boolean = true,
    /** Effective art recipe: shared style plus the activity photo (background/photo). */
    val artRecipe: ArtRecipe = ArtRecipe(),
    /** Whether the art recipe is written to `meucoach_art` when saved or shared. */
    val artSaveAsDefault: Boolean = true,
    /** State of the last Compartilhar/Salvar of the art tab. */
    val artExport: ArtExportUiState = ArtExportUiState.Idle,
    /** Block selected for the accessible nudge controls. */
    val artSelectedBlock: ArtBlock = ArtBlock.DISTANCE,
    /** True while an import/crop/remove/toggle touches the photo store. */
    val artPhotoBusy: Boolean = false,
    /** User-facing photo failure with retry; never carries URIs or paths. */
    val artPhotoError: String? = null,
    /** One-time warning when the stored photo file is gone (fallback to transparent). */
    val artMissingPhotoWarning: Boolean = false,
    /** True while the full-screen crop step is open inside the Studio. */
    val artCropEditing: Boolean = false,
    /** Draft focus/zoom edited in the crop step; applied on Confirm. */
    val artCropDraft: ArtPhotoCrop = ArtPhotoCrop(),
    /** Source dimensions of the stored photo, when known (for zoom limits/warnings). */
    val artPhotoSourceWidth: Int? = null,
    val artPhotoSourceHeight: Int? = null,
    /** Low-resolution warning for the current crop (<1080 soft, <540 strong). */
    val artPhotoQualityWarning: String? = null,
    /** True while the remove-photo confirmation is shown. */
    val artShowRemoveConfirm: Boolean = false,
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
