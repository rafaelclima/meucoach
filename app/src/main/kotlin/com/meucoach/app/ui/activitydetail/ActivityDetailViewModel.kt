package com.meucoach.app.ui.activitydetail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.meucoach.app.story.MapBackgroundRenderer
import com.meucoach.app.story.StoryExportCoordinator
import com.meucoach.app.story.StoryExportFailureStore
import com.meucoach.app.story.StoryExportWorker
import com.meucoach.app.story.StoryIntroStore
import com.meucoach.app.story.StoryMapStyle
import com.meucoach.app.story.StoryMessages
import com.meucoach.app.story.StoryPalette
import com.meucoach.app.story.StoryPlanCalculator
import com.meucoach.app.story.StoryPlanLoader
import com.meucoach.app.story.StorySessionResolver
import com.meucoach.app.story.StoryVideoExporter
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.domain.calibration.CalibrationCandidateDetector
import com.meucoach.core.domain.insights.ActivityInsightsCalculator
import com.meucoach.core.domain.zones.HrZoneDistributionCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ActivityRepository,
    private val profileRepository: ProfileRepository,
    private val sessions: StorySessionResolver,
    private val mapRenderer: MapBackgroundRenderer,
    private val storyCoordinator: StoryExportCoordinator,
    private val storyFailures: StoryExportFailureStore,
    private val storyIntro: StoryIntroStore,
    private val profilePhotos: com.meucoach.app.profile.ProfilePhotoStore,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val activityId: String = checkNotNull(savedStateHandle[ARG_ACTIVITY_ID]) {
        "Activity detail requires an activityId"
    }
    private val _uiState = MutableStateFlow(ActivityDetailUiState())
    val uiState: StateFlow<ActivityDetailUiState> = _uiState.asStateFlow()
    private val insightsCalculator = ActivityInsightsCalculator()

    /** Identifies the export currently allowed to enqueue; null after a cancel. */
    private var activeExport: Any? = null

    /** After a cancel, stale work emissions must not resurrect the card. */
    private var blockWorkAdoption = false

    init {
        viewModelScope.launch {
            val activity = repository.openActivity(activityId)
            val profile = profileRepository.getProfile()
            val lthr = profile?.lactateThresholdHeartRate
            val avatar = withContext(Dispatchers.IO) {
                profile?.avatarPath?.let { profilePhotos.load(it, maxSize = 256) }
            }
            val coachVerdict = activity?.let { stored ->
                val planned = sessions.resolve(stored.localDate)
                withContext(Dispatchers.Default) {
                    StoryPlanCalculator.calculate(
                        activity = stored.imported,
                        targetDurationSec = StoryPlanLoader.TARGET_DURATION_SEC,
                        lthrBpm = lthr,
                        session = planned.current,
                        nextSession = planned.next,
                    )?.verdict
                }
            }
            // update(), not a fresh state: the WorkManager observer may have
            // restored a Generating/Done story while this load was running.
            _uiState.update {
                it.copy(
                    isLoading = false,
                    activity = activity,
                    notFound = activity == null,
                    insights = activity
                        ?.let { stored -> insightsCalculator.calculate(stored.imported.laps) }
                        .orEmpty(),
                    hrZones = activity?.let { stored ->
                        lthr?.let { value ->
                            HrZoneDistributionCalculator.calculate(stored.imported.samples, value)
                        }
                    },
                    lthr = lthr,
                    calibrationCandidate = activity?.let {
                        CalibrationCandidateDetector.detect(it.imported, it.localDate, timeProvider.currentDate())
                    },
                    storyIntroSeen = storyIntro.seen(),
                    avatar = avatar,
                    coachVerdict = coachVerdict,
                )
            }
        }
        observeStoryExport()
    }

    companion object {
        const val ARG_ACTIVITY_ID = "activityId"
    }

    fun createStory(style: StoryMapStyle = StoryMapStyle.CLASSIC) {
        val state = _uiState.value
        val stored = state.activity ?: return
        val current = state.storyExport
        if (current is StoryExportUiState.Generating || current is StoryExportUiState.PreparingMap) return
        _uiState.update { it.copy(storyExport = StoryExportUiState.PreparingMap) }
        viewModelScope.launch {
            val planned = sessions.resolve(stored.localDate)
            val plan = withContext(Dispatchers.Default) {
                StoryPlanCalculator.calculate(
                    activity = stored.imported,
                    targetDurationSec = StoryPlanLoader.TARGET_DURATION_SEC,
                    lthrBpm = state.lthr,
                    session = planned.current,
                    nextSession = planned.next,
                )
            }
            if (plan == null) {
                _uiState.update {
                    it.copy(
                        storyExport = StoryExportUiState.Error(StoryMessages.MISSING_GPS),
                    )
                }
                return@launch
            }
            // The map needs network; the route-only fallback no longer exists (ADR 0015 §4).
            val map = plan.boundingBox?.let { bounds ->
                val rendered = runCatching {
                    withTimeoutOrNull(StoryMessages.MAP_TIMEOUT_MS) {
                        mapRenderer.render(
                            bounds,
                            StoryVideoExporter.mapWidth(),
                            StoryVideoExporter.mapHeight(),
                            style,
                        )
                    }
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { state ->
                        state.copy(storyExport = StoryExportUiState.Error(StoryMessages.MAP_UNAVAILABLE))
                    }
                    return@launch
                }
                if (rendered == null) {
                    _uiState.update { state ->
                        state.copy(storyExport = StoryExportUiState.Error(StoryMessages.MAP_UNAVAILABLE))
                    }
                    return@launch
                }
                rendered
            }
            _uiState.update { it.copy(storyExport = StoryExportUiState.Preview(plan, map)) }
        }
    }

    /**
     * Hands the export to WorkManager (ADR 0018): the user may close the screen
     * or the app; the worker rebuilds the plan, encodes the video, saves it in
     * the gallery and posts a notification.
     */
    fun exportStory(palette: StoryPalette) {
        val preview = _uiState.value.storyExport as? StoryExportUiState.Preview ?: return
        val token = Any()
        activeExport = token
        blockWorkAdoption = false
        storyFailures.clear(activityId)
        _uiState.update { it.copy(storyExport = StoryExportUiState.Generating(progress = 0f)) }
        viewModelScope.launch {
            runCatching {
                storyCoordinator.start(
                    activityId = activityId,
                    mapBackground = preview.mapBackground,
                    palette = palette,
                    isCancelled = { activeExport !== token },
                )
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(storyExport = StoryExportUiState.Error(error.message ?: StoryMessages.MAP_STAGE_FAILED))
                }
            }
        }
    }

    /** First-run explanation of the map/relief download, dismissed by the user. */
    fun dismissStoryIntro() {
        storyIntro.markSeen()
        _uiState.update { it.copy(storyIntroSeen = true) }
    }

    /** Real cancellation (ADR 0018): the WorkManager job stops the codec between frames. */
    fun cancelStory() {
        val current = _uiState.value.storyExport
        if (current is StoryExportUiState.Generating || current is StoryExportUiState.PreparingMap) {
            // Closing the staging window first keeps a cancel during the map
            // snapshot from enqueueing the export right afterwards.
            activeExport = null
            blockWorkAdoption = true
            storyCoordinator.cancel(activityId)
        }
        _uiState.update { it.copy(storyExport = StoryExportUiState.Idle) }
    }

    /** Keeps the card in sync with the background WorkManager job (progress, result, failure). */
    private fun observeStoryExport() {
        viewModelScope.launch {
            storyCoordinator.observe(activityId).collect { infos ->
                // REPLACE leaves cancelled predecessors in the list; only the
                // live work (and its finished successors) may drive the card.
                applyWorkInfo(infos.lastOrNull { it.state != WorkInfo.State.CANCELLED })
            }
        }
    }

    /** Keeps the card in sync with the background WorkManager job (progress, result, failure).
     *  Internal so the state machine can be exercised directly from the tests. */
    internal fun applyWorkInfo(workInfo: WorkInfo?) {
        if (workInfo == null) return
        val current = _uiState.value.storyExport
        val adoptable = current is StoryExportUiState.Generating ||
            (current is StoryExportUiState.Idle && !blockWorkAdoption)
        if (!adoptable) return
        when (workInfo.state) {
            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                val etaSec = workInfo.progress.getInt(StoryExportWorker.KEY_ETA_SEC, -1).takeIf { it >= 0 }
                _uiState.update {
                    it.copy(
                        storyExport = StoryExportUiState.Generating(
                            progress = workInfo.progress.getFloat(StoryExportWorker.KEY_PROGRESS, 0f),
                            etaSec = etaSec,
                        ),
                    )
                }
            }

            WorkInfo.State.SUCCEEDED -> {
                val videoUri = workInfo.outputData.getString(StoryExportWorker.KEY_VIDEO_URI) ?: return
                storyFailures.clear(activityId)
                _uiState.update {
                    it.copy(
                        storyExport = StoryExportUiState.Done(
                            uri = Uri.parse(videoUri),
                            savedToGallery = workInfo.outputData.getBoolean(
                                StoryExportWorker.KEY_SAVED_TO_GALLERY,
                                false,
                            ),
                            coverUri = workInfo.outputData
                                .getString(StoryExportWorker.KEY_COVER_URI)
                                ?.let { cover -> Uri.parse(cover) },
                        ),
                    )
                }
            }

            WorkInfo.State.FAILED -> {
                // WorkManager drops the failure outputData; the worker stores
                // the message separately so this state is never silent.
                val message = workInfo.outputData.getString(StoryExportWorker.KEY_ERROR)
                    ?: storyFailures.messageFor(activityId)
                    ?: StoryMessages.EXPORT_FAILED
                _uiState.update { it.copy(storyExport = StoryExportUiState.Error(message)) }
            }

            WorkInfo.State.CANCELLED -> {
                if (current is StoryExportUiState.Generating) {
                    _uiState.update { it.copy(storyExport = StoryExportUiState.Idle) }
                }
            }

            else -> Unit
        }
    }

    fun deleteActivity() {
        val current = _uiState.value
        if (current.isDeleting || current.activity == null) return
        _uiState.update { it.copy(isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            val deleted = runCatching { repository.deleteActivity(activityId) }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    false
                }
            _uiState.update {
                if (deleted) {
                    it.copy(isDeleting = false, deleted = true)
                } else {
                    it.copy(
                        isDeleting = false,
                        deleteError = "Não foi possível apagar a atividade. Tente de novo.",
                    )
                }
            }
        }
    }

    fun clearDeleteError() {
        _uiState.update { it.copy(deleteError = null) }
    }
}
