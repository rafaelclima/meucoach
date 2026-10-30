package com.meucoach.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.model.AthleteProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProfileGateUiState(val isLoading: Boolean = true, val profile: AthleteProfile? = null)

@HiltViewModel
class ProfileGateViewModel @Inject constructor(repository: ProfileRepository) : ViewModel() {
    val uiState: StateFlow<ProfileGateUiState> = repository
        .observeProfile()
        .map { profile -> ProfileGateUiState(isLoading = false, profile = profile) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ProfileGateUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

data class ProfileUiState(val isLoading: Boolean = true, val profile: AthleteProfile? = null)

@HiltViewModel
class ProfileViewModel @Inject constructor(repository: ProfileRepository) : ViewModel() {
    val uiState: StateFlow<ProfileUiState> = repository
        .observeProfile()
        .map { profile -> ProfileUiState(isLoading = false, profile = profile) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ProfileUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
