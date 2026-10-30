package com.meucoach.app.ui.activitydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.domain.insights.ActivityInsightsCalculator
import com.meucoach.core.domain.zones.HrZoneDistributionCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ActivityRepository,
    private val profileRepository: ProfileRepository,
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
}
