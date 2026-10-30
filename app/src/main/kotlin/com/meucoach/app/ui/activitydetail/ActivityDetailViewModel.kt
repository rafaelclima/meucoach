package com.meucoach.app.ui.activitydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.core.data.repository.ActivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repository: ActivityRepository) :
    ViewModel() {
    private val activityId: String = checkNotNull(savedStateHandle[ARG_ACTIVITY_ID]) {
        "Activity detail requires an activityId"
    }
    private val _uiState = MutableStateFlow(ActivityDetailUiState())
    val uiState: StateFlow<ActivityDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val activity = repository.openActivity(activityId)
            _uiState.value = ActivityDetailUiState(
                isLoading = false,
                activity = activity,
                notFound = activity == null,
            )
        }
    }

    companion object {
        const val ARG_ACTIVITY_ID = "activityId"
    }
}
