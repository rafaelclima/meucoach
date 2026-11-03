package com.meucoach.app.ui.activitydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.story.MapBackgroundRenderer
import com.meucoach.app.story.StoryPalette
import com.meucoach.app.story.StoryPlanCalculator
import com.meucoach.app.story.StoryVideoExporter
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.insights.ActivityInsightsCalculator
import com.meucoach.core.domain.prescription.PlannedSession
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
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ActivityRepository,
    private val profileRepository: ProfileRepository,
    private val planRepository: TrainingPlanRepository,
    private val storyExporter: StoryVideoExporter,
    private val mapRenderer: MapBackgroundRenderer,
) : ViewModel() {
    private val activityId: String = checkNotNull(savedStateHandle[ARG_ACTIVITY_ID]) {
        "Activity detail requires an activityId"
    }
    private val _uiState = MutableStateFlow(ActivityDetailUiState())
    val uiState: StateFlow<ActivityDetailUiState> = _uiState.asStateFlow()
    private val insightsCalculator = ActivityInsightsCalculator()

    init {
        viewModelScope.launch {
            val activity = repository.openActivity(activityId)
            val lthr = profileRepository.getProfile()?.lactateThresholdHeartRate
            _uiState.value = ActivityDetailUiState(
                isLoading = false,
                activity = activity,
                notFound = activity == null,
                insights = activity
                    ?.let { insightsCalculator.calculate(it.imported.laps) }
                    .orEmpty(),
                hrZones = activity?.let { stored ->
                    lthr?.let { value ->
                        HrZoneDistributionCalculator.calculate(stored.imported.samples, value)
                    }
                },
                lthr = lthr,
            )
        }
    }

    companion object {
        const val ARG_ACTIVITY_ID = "activityId"
        private const val MAP_TIMEOUT_MS = 45_000L
        private const val MAP_ERROR_MESSAGE =
            "Não foi possível baixar o mapa. Verifique a conexão e tente novamente."
    }

    fun createStory() {
        val state = _uiState.value
        val stored = state.activity ?: return
        val current = state.storyExport
        if (current is StoryExportUiState.Generating || current is StoryExportUiState.PreparingMap) return
        _uiState.update { it.copy(storyExport = StoryExportUiState.PreparingMap) }
        viewModelScope.launch {
            val planned = plannedSessions(stored)
            val plan = withContext(Dispatchers.Default) {
                StoryPlanCalculator.calculate(
                    activity = stored.imported,
                    targetDurationSec = 30,
                    lthrBpm = state.lthr,
                    session = planned.current,
                    nextSession = planned.next,
                )
            }
            if (plan == null) {
                _uiState.update {
                    it.copy(
                        storyExport = StoryExportUiState.Error(
                            "Esta atividade não tem rota GPS suficiente para um story.",
                        ),
                    )
                }
                return@launch
            }
            // The map needs network; the route-only fallback no longer exists (ADR 0015 §4).
            val map = plan.boundingBox?.let { bounds ->
                val rendered = runCatching {
                    withTimeoutOrNull(MAP_TIMEOUT_MS) {
                        mapRenderer.render(
                            bounds,
                            StoryVideoExporter.mapWidth(),
                            StoryVideoExporter.mapHeight(),
                        )
                    }
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { state ->
                        state.copy(storyExport = StoryExportUiState.Error(MAP_ERROR_MESSAGE))
                    }
                    return@launch
                }
                if (rendered == null) {
                    _uiState.update { state ->
                        state.copy(storyExport = StoryExportUiState.Error(MAP_ERROR_MESSAGE))
                    }
                    return@launch
                }
                rendered
            }
            _uiState.update { it.copy(storyExport = StoryExportUiState.Preview(plan, map)) }
        }
    }

    fun exportStory(palette: StoryPalette) {
        val preview = _uiState.value.storyExport as? StoryExportUiState.Preview ?: return
        _uiState.update { it.copy(storyExport = StoryExportUiState.Generating(0f)) }
        viewModelScope.launch {
            runCatching {
                storyExporter.export(preview.plan, palette, preview.mapBackground) { progress ->
                    _uiState.update { state ->
                        when (state.storyExport) {
                            is StoryExportUiState.Generating ->
                                state.copy(storyExport = StoryExportUiState.Generating(progress))

                            else -> state
                        }
                    }
                }
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        storyExport = StoryExportUiState.Done(
                            uri = result.uri,
                            savedToGallery = result.savedToGallery,
                            coverUri = result.coverUri,
                        ),
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        storyExport = StoryExportUiState.Error(
                            error.message ?: "Não foi possível gerar o vídeo.",
                        ),
                    )
                }
            }
        }
    }

    fun cancelStory() {
        _uiState.update { it.copy(storyExport = StoryExportUiState.Idle) }
    }

    /**
     * Planned session on the activity date and the next one in the same week.
     * Missing plans are not an error: the story then reports effort only.
     */
    private suspend fun plannedSessions(activity: StoredActivity): PlannedStorySessions {
        val weekStart = activity.localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = runCatching { planRepository.getWeek(weekStart) }.getOrNull()
            ?: return PlannedStorySessions()
        return PlannedStorySessions(
            current = week.sessions.firstOrNull { it.date == activity.localDate },
            next = week.sessions
                .filter { it.date.isAfter(activity.localDate) }
                .minByOrNull { it.date },
        )
    }

    private data class PlannedStorySessions(val current: PlannedSession? = null, val next: PlannedSession? = null)

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
