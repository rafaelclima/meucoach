package com.meucoach.app.ui.activitydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.story.StoryPalette
import com.meucoach.app.story.StoryPlanCalculator
import com.meucoach.app.story.StoryVideoExporter
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.domain.insights.ActivityInsightsCalculator
import com.meucoach.core.domain.zones.HrZoneDistributionCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ActivityRepository,
    private val profileRepository: ProfileRepository,
    private val storyExporter: StoryVideoExporter,
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
    }

    fun createStory(palette: StoryPalette) {
        val activity = _uiState.value.activity ?: return
        if (_uiState.value.storyExport is StoryExportUiState.Generating) return
        _uiState.update { it.copy(storyExport = StoryExportUiState.Generating(0f)) }
        viewModelScope.launch {
            val plan = withContext(Dispatchers.Default) {
                StoryPlanCalculator.calculate(activity.imported, targetDurationSec = 30)
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
            runCatching {
                storyExporter.export(plan, palette) { progress ->
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
}
