package com.meucoach.app.ui.activitydetail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.meucoach.app.application.GetSessionComparison
import com.meucoach.app.story.MapBackgroundRenderer
import com.meucoach.app.story.StoryExportCoordinator
import com.meucoach.app.story.StoryExportFailureStore
import com.meucoach.app.story.StoryExportWorker
import com.meucoach.app.story.StoryIntroStore
import com.meucoach.app.story.StoryMessages
import com.meucoach.app.story.StoryPalette
import com.meucoach.app.story.StoryRecipeStore
import com.meucoach.app.story.StorySessionResolver
import com.meucoach.app.story.StoryVideoExporter
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.domain.calibration.CalibrationCandidateDetector
import com.meucoach.core.domain.insights.ActivityInsightsCalculator
import com.meucoach.core.domain.story.StoryDuration
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.domain.story.StoryMetricCatalog
import com.meucoach.core.domain.story.StoryMetricValues
import com.meucoach.core.domain.story.StoryMetricValuesResolver
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.domain.story.StoryPreset
import com.meucoach.core.domain.story.StoryRecipe
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
    private val storyRecipe: StoryRecipeStore,
    private val profilePhotos: com.meucoach.app.profile.ProfilePhotoStore,
    private val timeProvider: TimeProvider,
    private val comparisons: GetSessionComparison,
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

    /** Guards the studio plan/availability against out-of-order duration changes. */
    private var studioRevision = 0

    init {
        viewModelScope.launch {
            val activity = repository.openActivity(activityId)
            val profile = profileRepository.getProfile()
            val lthr = profile?.lactateThresholdHeartRate
            val avatar = withContext(Dispatchers.IO) {
                profile?.avatarPath?.let { profilePhotos.load(it, maxSize = 256) }
            }
            val recipe = storyRecipe.load()
            val plan = activity?.let { stored -> calculateStoryPlan(stored, recipe, lthr) }
            val values = activity?.let { stored ->
                plan?.let { StoryMetricValuesResolver.from(it, stored.localDate) }
            }
            val comparison = activity?.let { comparisons.forActivity(it) }
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
                    coachVerdict = plan?.verdict,
                    comparison = comparison,
                    storyStudio = it.storyStudio.copy(
                        recipe = recipe,
                        plan = plan,
                        extras = storyExtraOptions(values),
                    ),
                )
            }
        }
        observeStoryExport()
    }

    companion object {
        const val ARG_ACTIVITY_ID = "activityId"
    }

    // ---------------------------------------------------------------------
    // Story Studio (recipe, preview and generation)
    // ---------------------------------------------------------------------

    fun openStoryStudio() {
        _uiState.update { state ->
            val studio = state.storyStudio
            state.copy(
                storyStudio = studio.copy(
                    isOpen = true,
                    // A default saved on another run may carry extras this
                    // activity does not have; the Studio only edits what it
                    // can show, and the recipe is the source of truth.
                    recipe = studio.recipe.copy(
                        extras = studio.recipe.extras.filter { extra ->
                            studio.extras.firstOrNull { it.extra == extra }?.isAvailable == true
                        },
                    ),
                ),
            )
        }
    }

    fun closeStoryStudio() {
        _uiState.update { it.copy(storyStudio = it.storyStudio.copy(isOpen = false)) }
    }

    fun setStorySaveAsDefault(save: Boolean) {
        _uiState.update { it.copy(storyStudio = it.storyStudio.copy(saveAsDefault = save)) }
    }

    fun selectStoryPreset(preset: StoryPreset) {
        val state = _uiState.value
        val studio = state.storyStudio
        val values = storyValues(state) ?: return
        val recipe = StoryRecipe.fromPreset(
            preset = preset,
            values = values,
            duration = studio.recipe.duration,
            mapStyle = studio.recipe.mapStyle,
            quality = studio.recipe.quality,
        )
        updateStoryRecipe(recipe)
    }

    fun toggleStoryExtra(extra: StoryExtra) {
        val studio = _uiState.value.storyStudio
        val option = studio.extras.firstOrNull { it.extra == extra } ?: return
        val selected = extra in studio.recipe.extras
        if (!option.isAvailable && !selected) return
        if (!selected && studio.recipe.extras.size >= StoryRecipe.MAX_EXTRAS) return
        _uiState.update {
            it.copy(storyStudio = it.storyStudio.copy(recipe = it.storyStudio.recipe.toggleExtra(extra)))
        }
    }

    fun selectStoryDuration(duration: StoryDuration) {
        val studio = _uiState.value.storyStudio
        if (studio.recipe.duration == duration) return
        updateStoryRecipe(studio.recipe.copy(duration = duration))
    }

    fun selectStoryMapStyle(style: StoryMapStyle) {
        // Satellite is out of the MVP by license (ADR 0040 §2.4); the enum
        // value survives only so old WorkManager jobs still decode.
        if (style == StoryMapStyle.SATELLITE) return
        val studio = _uiState.value.storyStudio
        if (studio.recipe.mapStyle == style) return
        _uiState.update {
            it.copy(storyStudio = it.storyStudio.copy(recipe = it.storyStudio.recipe.copy(mapStyle = style)))
        }
    }

    /** Clears the saved default and returns the Studio to the documented recipe. */
    fun resetStoryRecipe() {
        storyRecipe.reset()
        updateStoryRecipe(StoryRecipe())
    }

    private fun updateStoryRecipe(recipe: StoryRecipe) {
        val state = _uiState.value
        if (state.storyStudio.recipe == recipe) return
        val durationChanged = state.storyStudio.recipe.duration != recipe.duration
        _uiState.update { it.copy(storyStudio = it.storyStudio.copy(recipe = recipe)) }
        if (durationChanged) recomputeStoryPlan(recipe)
    }

    /**
     * Duration is the only recipe control that changes the timeline; extras and
     * map style leave the plan untouched. Recomputing is cheap and keeps the
     * three preview frames honest, but the result must match the last tap.
     */
    private fun recomputeStoryPlan(recipe: StoryRecipe) {
        val state = _uiState.value
        val stored = state.activity ?: return
        val revision = ++studioRevision
        viewModelScope.launch {
            val plan = calculateStoryPlan(stored, recipe, state.lthr)
            if (revision != studioRevision) return@launch
            val values = plan?.let { StoryMetricValuesResolver.from(it, stored.localDate) }
            _uiState.update {
                it.copy(
                    storyStudio = it.storyStudio.copy(
                        plan = plan,
                        extras = storyExtraOptions(values),
                    ),
                )
            }
        }
    }

    private suspend fun calculateStoryPlan(stored: StoredActivity, recipe: StoryRecipe, lthr: Int?): StoryPlan? {
        val planned = sessions.resolve(stored.localDate)
        return withContext(Dispatchers.Default) {
            StoryPlanCalculator.calculate(
                activity = stored.imported,
                targetDurationSec = recipe.targetDurationSec(stored.imported.distanceM / 1_000.0),
                lthrBpm = lthr,
                session = planned.current,
                nextSession = planned.next,
            )
        }
    }

    private fun storyValues(state: ActivityDetailUiState): StoryMetricValues? = state.storyStudio.plan?.let { plan ->
        StoryMetricValuesResolver.from(plan, state.activity?.localDate)
    }

    private fun storyExtraOptions(values: StoryMetricValues?): List<StoryExtraOption> {
        // Without a plan (no GPS) no per-km value exists either: resolving
        // against empty values keeps every extra disabled with its own reason.
        val resolved = values ?: StoryMetricValues()
        return StoryExtra.entries.map { extra ->
            val availability = StoryMetricCatalog.availability(extra, resolved)
            StoryExtraOption(
                extra = extra,
                label = StoryMetricCatalog.label(extra),
                isAvailable = availability.isAvailable,
                reason = availability.reason?.label,
            )
        }
    }

    /**
     * Renders the map and hands the export to WorkManager (ADR 0018): the user
     * may close the screen or the app; the worker rebuilds the plan, encodes
     * the video, saves it in the gallery and posts a notification.
     */
    fun generateStory(palette: StoryPalette) {
        val state = _uiState.value
        val stored = state.activity ?: return
        val current = state.storyExport
        if (current is StoryExportUiState.Generating || current is StoryExportUiState.PreparingMap) return
        val studio = state.storyStudio
        if (studio.saveAsDefault) storyRecipe.save(studio.recipe)
        val token = Any()
        activeExport = token
        blockWorkAdoption = false
        storyFailures.clear(activityId)
        _uiState.update {
            it.copy(
                storyStudio = it.storyStudio.copy(isOpen = false),
                storyExport = StoryExportUiState.PreparingMap,
            )
        }
        viewModelScope.launch {
            val plan = studio.plan ?: calculateStoryPlan(stored, studio.recipe, state.lthr)
            if (plan == null) {
                if (activeExport === token) {
                    _uiState.update {
                        it.copy(storyExport = StoryExportUiState.Error(StoryMessages.MISSING_GPS))
                    }
                }
                return@launch
            }
            // The map needs network; the route-only fallback no longer exists (ADR 0015 §4).
            val map = plan.boundingBox?.let { bounds ->
                runCatching {
                    withTimeoutOrNull(StoryMessages.MAP_TIMEOUT_MS) {
                        mapRenderer.render(
                            bounds,
                            StoryVideoExporter.mapWidth(),
                            StoryVideoExporter.mapHeight(),
                            studio.recipe.mapStyle,
                        )
                    }
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    null
                }
            }
            if (activeExport !== token) return@launch
            if (map == null) {
                _uiState.update {
                    it.copy(storyExport = StoryExportUiState.Error(StoryMessages.MAP_UNAVAILABLE))
                }
                return@launch
            }
            val startFailure = runCatching {
                storyCoordinator.start(
                    activityId = activityId,
                    recipe = studio.recipe,
                    mapBackground = map,
                    palette = palette,
                    isCancelled = { activeExport !== token },
                )
            }.exceptionOrNull()
            if (startFailure != null) {
                if (startFailure is CancellationException) throw startFailure
                _uiState.update {
                    it.copy(
                        storyExport = StoryExportUiState.Error(
                            startFailure.message ?: StoryMessages.MAP_STAGE_FAILED,
                        ),
                    )
                }
                return@launch
            }
            if (activeExport === token) {
                _uiState.update { it.copy(storyExport = StoryExportUiState.Generating(progress = 0f)) }
            }
        }
    }

    /** Retry from the error card, without asking the user to reopen the Studio. */
    fun retryStory(palette: StoryPalette) = generateStory(palette)

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
                            posterUri = workInfo.outputData
                                .getString(StoryExportWorker.KEY_POSTER_URI)
                                ?.let { poster -> Uri.parse(poster) },
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
            if (deleted) {
                storyCoordinator.cancel(activityId)
                storyFailures.clear(activityId)
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
