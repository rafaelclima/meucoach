package com.meucoach.app.ui.activitydetail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.meucoach.app.application.GetSessionComparison
import com.meucoach.app.story.ArtLayoutEngine
import com.meucoach.app.story.ArtLayoutTextFactory
import com.meucoach.app.story.ArtPhotoImportError
import com.meucoach.app.story.ArtPhotoImportResult
import com.meucoach.app.story.ArtPhotoImporter
import com.meucoach.app.story.ArtPhotoLoader
import com.meucoach.app.story.ArtPhotoRecord
import com.meucoach.app.story.ArtPhotoStore
import com.meucoach.app.story.ArtPngExport
import com.meucoach.app.story.ArtPngExporter
import com.meucoach.app.story.ArtRecipeStore
import com.meucoach.app.story.MapBackgroundRenderer
import com.meucoach.app.story.StoryBranding
import com.meucoach.app.story.StoryExportCoordinator
import com.meucoach.app.story.StoryExportFailureStore
import com.meucoach.app.story.StoryExportWorker
import com.meucoach.app.story.StoryIntroStore
import com.meucoach.app.story.StoryMessages
import com.meucoach.app.story.StoryPalette
import com.meucoach.app.story.StoryRecipeStore
import com.meucoach.app.story.StorySessionResolver
import com.meucoach.app.story.StoryVideoExporter
import com.meucoach.app.story.typeface
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.domain.calibration.CalibrationCandidateDetector
import com.meucoach.core.domain.insights.ActivityInsightsCalculator
import com.meucoach.core.domain.story.ArtBlock
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtLayout
import com.meucoach.core.domain.story.ArtOffset
import com.meucoach.core.domain.story.ArtPhotoCrop
import com.meucoach.core.domain.story.ArtPhotoRef
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.ArtTemplate
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
@Suppress("LongParameterList")
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
    internal val artRecipeStore: ArtRecipeStore,
    internal val artPhotoStore: ArtPhotoStore,
    internal val artPhotoImporter: ArtPhotoImporter,
    internal val artPhotoLoader: ArtPhotoLoader,
    @ApplicationContext internal val appContext: Context,
    private val artPngExporter: ArtPngExporter,
    private val profilePhotos: com.meucoach.app.profile.ProfilePhotoStore,
    private val timeProvider: TimeProvider,
    private val comparisons: GetSessionComparison,
) : ViewModel() {
    internal val activityId: String = checkNotNull(savedStateHandle[ARG_ACTIVITY_ID]) {
        "Activity detail requires an activityId"
    }
    private val _uiState = MutableStateFlow(ActivityDetailUiState())
    val uiState: StateFlow<ActivityDetailUiState> = _uiState.asStateFlow()

    internal fun currentActivityState(): ActivityDetailUiState = _uiState.value

    internal fun updateActivityState(transform: (ActivityDetailUiState) -> ActivityDetailUiState) {
        _uiState.update(transform)
    }
    private val insightsCalculator = ActivityInsightsCalculator()

    /** Identifies the export currently allowed to enqueue; null after a cancel. */
    private var activeExport: Any? = null

    /** After a cancel, stale work emissions must not resurrect the card. */
    private var blockWorkAdoption = false

    /** Guards the studio plan/availability against out-of-order duration changes. */
    private var studioRevision = 0

    /** Target of the last art export, so "Tentar novamente" re-runs it. */
    private var lastArtExport: Boolean? = null

    /** Last picker URI, so an import failure can retry without reopening the picker. */
    internal var lastPhotoUri: Uri? = null

    init {
        viewModelScope.launch {
            val activity = repository.openActivity(activityId)
            val profile = profileRepository.getProfile()
            val lthr = profile?.lactateThresholdHeartRate
            val avatar = withContext(Dispatchers.IO) {
                profile?.avatarPath?.let { profilePhotos.load(it, maxSize = 256) }
            }
            val recipe = sanitizeRecipe(storyRecipe.load())
            val plan = activity?.let { stored -> calculateStoryPlan(stored, recipe, lthr) }
            val values = activity?.let { stored ->
                plan?.let { StoryMetricValuesResolver.from(it, stored.localDate) }
            }
            val comparison = activity?.let { comparisons.forActivity(it) }
            val artStyle = artRecipeStore.load()
            val photoSnapshot = artPhotoStore.loadSnapshot(activityId)
            val artEffective = effectiveRecipe(artStyle, photoSnapshot.record)
            val photoSize = photoSnapshot.record.photo?.let { photoSourceSize(it.photoId) }
            val qualityWarning = qualityWarningFor(
                sourceWidth = photoSize?.first,
                sourceHeight = photoSize?.second,
                format = artEffective.format,
                crop = photoSnapshot.record.photo?.crop,
            )
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
                        artRecipe = artEffective,
                        artMissingPhotoWarning = photoSnapshot.missingPhoto,
                        artPhotoSourceWidth = photoSize?.first,
                        artPhotoSourceHeight = photoSize?.second,
                        artPhotoQualityWarning = qualityWarning,
                        artCropDraft = photoSnapshot.record.photo?.crop ?: ArtPhotoCrop(),
                    ),
                )
            }
        }
        observeStoryExport()
    }

    companion object {
        const val ARG_ACTIVITY_ID = "activityId"
        private const val TAG = "ActivityDetailVM"

        /** Accessible nudge step: 2% of the canvas per tap (PRD §5.3). */
        const val NUDGE_STEP = 0.02f

        /** Crop focus step for the accessible arrows: 2% of the source. */
        const val CROP_FOCUS_STEP = 0.02f

        /** Crop zoom step for the accessible −/+ controls. */
        const val CROP_ZOOM_STEP = 0.1f

        /** Crop side below this allows upscale with a strong warning. */
        const val MIN_CROP_SIDE = 540

        /** Crop side below this shows the soft low-resolution warning. */
        const val LOW_RES_SIDE = 1080

        const val PHOTO_LOW_SOFT = "Foto em baixa — o JPG pode ficar suave"
        const val PHOTO_LOW_STRONG = "Foto em baixa resolução — o JPG pode ficar bem suave"
        const val PHOTO_MISSING_WARNING = "A foto anterior não foi encontrada; a arte voltou ao fundo transparente."
    }

    // ---------------------------------------------------------------------
    // Story Studio (recipe, preview and generation)
    // ---------------------------------------------------------------------

    fun openStoryStudio() {
        _uiState.update { state ->
            val studio = state.storyStudio
            // A default saved on another run may carry extras this activity
            // does not have; the Studio only edits what it can show, and the
            // recipe is the source of truth.
            val available: (StoryExtra) -> Boolean = { extra ->
                studio.extras.firstOrNull { it.extra == extra }?.isAvailable == true
            }
            state.copy(
                storyStudio = studio.copy(
                    isOpen = true,
                    recipe = studio.recipe.copy(extras = studio.recipe.extras.filter(available)),
                    // The art card offers a smaller set: video-only extras
                    // never come back from a saved default (ADR 0058 §2.6).
                    artRecipe = studio.artRecipe.copy(
                        extras = studio.artRecipe.extras.filter { extra ->
                            extra in ArtRecipe.EXTRA_CHOICES && available(extra)
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

    // ---------------------------------------------------------------------
    // Story Studio — transparent route art (ADR 0058)
    // ---------------------------------------------------------------------

    fun selectArtFormat(format: ArtFormat) {
        updateArtRecipe { it.copy(format = format) }
        refreshPhotoQualityWarning()
    }

    fun toggleArtExtra(extra: StoryExtra) {
        val studio = _uiState.value.storyStudio
        val option = studio.extras.firstOrNull { it.extra == extra } ?: return
        val selected = extra in studio.artRecipe.extras
        if (extra !in ArtRecipe.EXTRA_CHOICES) return
        if (!option.isAvailable && !selected) return
        updateArtRecipe { it.toggleExtra(extra) }
    }

    fun selectArtTextColor(color: ArtColor) {
        updateArtRecipe { it.copy(textColor = color) }
    }

    fun selectArtRouteColor(color: ArtColor) {
        updateArtRecipe { it.copy(routeColor = color) }
    }

    fun selectArtFont(font: ArtFont) {
        updateArtRecipe { it.copy(font = font) }
    }

    fun setArtSaveAsDefault(save: Boolean) {
        _uiState.update { it.copy(storyStudio = it.storyStudio.copy(artSaveAsDefault = save)) }
    }

    /** Clears the saved default and returns the art to the documented recipe. */
    fun resetArtRecipe() {
        if (_uiState.value.storyStudio.artPhotoBusy) return
        artRecipeStore.reset()
        _uiState.update { state ->
            state.copy(
                storyStudio = state.storyStudio.copy(
                    artRecipe = ArtRecipe(),
                    artPhotoError = null,
                    artMissingPhotoWarning = false,
                    artCropEditing = false,
                    artCropDraft = ArtPhotoCrop(),
                    artPhotoSourceWidth = null,
                    artPhotoSourceHeight = null,
                    artPhotoQualityWarning = null,
                    artShowRemoveConfirm = false,
                    artSelectedBlock = ArtBlock.DISTANCE,
                    artExport = ArtExportUiState.Idle,
                ),
            )
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { artPhotoStore.remove(activityId) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w(TAG, "Art photo reset failed", error)
                _uiState.update {
                    it.copy(
                        storyStudio = it.storyStudio.copy(
                            artPhotoError = photoErrorMessage(ArtPhotoImportError.STORAGE),
                        ),
                    )
                }
            }
        }
    }

    /** Compartilhar: render + publish, then the share sheet opens on Done. */
    fun shareArt() {
        exportArt(forShare = true)
    }

    /** Salvar: render + publish without opening the share sheet (JPG in photo, PNG transparent). */
    fun saveArtPng() {
        exportArt(forShare = false)
    }

    /** Re-runs the last Compartilhar/Salvar after a failure. */
    fun retryArtExport() {
        lastArtExport?.let { exportArt(forShare = it) }
    }

    /**
     * Marks the share handoff as delivered (P0-2): the sheet opens once per
     * Done; rotating, switching tabs or reopening the Studio must not replay
     * the same Uri from the state that lives in the view model.
     */
    fun consumeArtShare() {
        _uiState.update { state ->
            val export = state.storyStudio.artExport
            if (export is ArtExportUiState.Done && export.forShare) {
                state.copy(storyStudio = state.storyStudio.copy(artExport = ArtExportUiState.Idle))
            } else {
                state
            }
        }
    }

    private fun exportArt(forShare: Boolean) {
        val state = _uiState.value
        val stored = state.activity ?: return
        val studio = state.storyStudio
        val plan = studio.plan ?: return
        val ready = plan.route.points.size >= 2 &&
            studio.artExport !is ArtExportUiState.Exporting && !studio.artPhotoBusy
        if (!ready) return
        val recipe = studio.artRecipe
        // The documented switch persists only the style (format, extras, font,
        // colors, template, offsets, scale); the photo lives per activity and
        // ArtRecipeStore normalizes it out.
        if (studio.artSaveAsDefault) artRecipeStore.save(recipe)
        lastArtExport = forShare
        _uiState.update {
            it.copy(storyStudio = it.storyStudio.copy(artExport = ArtExportUiState.Exporting(forShare)))
        }
        viewModelScope.launch {
            val export = artPngExporter.export(
                plan = plan,
                localDate = stored.localDate,
                recipe = recipe,
                activityId = activityId,
                forShare = forShare,
                currentRecipe = { currentActivityState().storyStudio.artRecipe },
            )
            _uiState.update { current ->
                val inFlight = current.storyStudio.artExport as? ArtExportUiState.Exporting ?: return@update current
                // Only the request that is still on screen may publish its
                // outcome; a stale response after leaving must not resurrect it.
                if (inFlight.forShare != forShare) return@update current
                // P0-3: the control changed while rendering, so the image on
                // disk no longer matches the recipe the user sees. Discard it.
                val next = if (current.storyStudio.artRecipe != recipe) {
                    ArtExportUiState.Stale
                } else {
                    when (export) {
                        is ArtPngExport.Published -> ArtExportUiState.Done(
                            uri = export.uri,
                            savedToGallery = export.savedToGallery,
                            forShare = forShare,
                            reused = export.reused,
                        )

                        ArtPngExport.NoRoute -> ArtExportUiState.Error(StoryMessages.ART_NO_ROUTE)

                        ArtPngExport.Stale -> ArtExportUiState.Stale

                        // The technical detail belongs in the log, never on screen.
                        is ArtPngExport.Failure -> ArtExportUiState.Error(StoryMessages.ART_EXPORT_FAILED)
                    }
                }
                current.copy(storyStudio = current.storyStudio.copy(artExport = next))
            }
        }
    }

    /**
     * Every real edit invalidates a previous confirmation or error (P1-4).
     * An export in flight is not cancelled: its completion compares the
     * captured recipe and discards itself if the art changed (P0-3).
     */
    internal fun updateArtRecipe(transform: (ArtRecipe) -> ArtRecipe) {
        _uiState.update { state ->
            val current = state.storyStudio.artRecipe
            val next = transform(current)
            if (next == current) return@update state
            val export = state.storyStudio.artExport
            state.copy(
                storyStudio = state.storyStudio.copy(
                    artRecipe = next,
                    artExport = if (export is ArtExportUiState.Exporting) export else ArtExportUiState.Idle,
                ),
            )
        }
    }

    /**
     * Satellite left the Studio (MAP-08, ADR 0040 §2.4): a default saved by
     * an older build renders the dark style now. The enum value survives only
     * so old WorkManager jobs still decode.
     */
    @Suppress("DEPRECATION")
    private fun sanitizeRecipe(recipe: StoryRecipe): StoryRecipe = if (recipe.mapStyle == StoryMapStyle.SATELLITE) {
        recipe.copy(mapStyle = StoryMapStyle.MEUCOACH_DARK)
    } else {
        recipe
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
            )?.copy(localDate = stored.localDate)
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
                            plan.geoPoints,
                            StoryVideoExporter.mapWidth(mapRenderer.lowRam),
                            StoryVideoExporter.mapHeight(mapRenderer.lowRam),
                            studio.recipe.mapStyle,
                        )
                    }
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    null
                }
            }
            if (activeExport !== token) {
                // The preview shot was rendered but never staged; release the
                // ~40 MB follow bitmap instead of waiting for the GC (ENC-08).
                map?.let { runCatching { it.bitmap.recycle() } }
                return@launch
            }
            if (map == null) {
                _uiState.update {
                    it.copy(storyExport = StoryExportUiState.Error(StoryMessages.MAP_UNAVAILABLE))
                }
                return@launch
            }
            try {
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
            } finally {
                // The worker re-reads the staged JPEG from disk; the in-memory
                // preview bitmap is no longer needed after staging (ENC-08).
                runCatching { map.bitmap.recycle() }
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
