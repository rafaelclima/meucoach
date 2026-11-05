package com.meucoach.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.ui.tutorial.TutorialStore
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class FirstRunChecklistState(
    val isLoading: Boolean = true,
    val hasCheckedIn: Boolean = false,
    val hasActivities: Boolean = false,
    val healthConnected: Boolean = false,
    val dismissed: Boolean = false,
) {
    val completed: Int get() = listOf(hasCheckedIn, hasActivities, healthConnected).count { it }
    val total: Int get() = 3
    val shouldShow: Boolean get() = !isLoading && !dismissed && completed < total
}

/** Tracks the three first-run setup steps shown on Today. It never blocks the app. */
@HiltViewModel
class FirstRunChecklistViewModel @Inject constructor(
    checkInRepository: CheckInRepository,
    activityRepository: ActivityRepository,
    private val healthSource: HealthDailySource,
    private val store: TutorialStore,
) : ViewModel() {
    private val today = LocalDate.now()
    private val _uiState = MutableStateFlow(
        FirstRunChecklistState(dismissed = store.isChecklistDismissed()),
    )
    val uiState: StateFlow<FirstRunChecklistState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                checkInRepository.observe(today),
                activityRepository.observeActivitySummaries(),
            ) { checkIn, activities ->
                Pair(checkIn != null, activities.isNotEmpty())
            }.collect { (hasCheckedIn, hasActivities) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasCheckedIn = hasCheckedIn,
                        hasActivities = hasActivities,
                    )
                }
            }
        }
        refreshHealth()
    }

    fun refreshHealth() {
        viewModelScope.launch {
            val connected = runCatching {
                healthSource.status() == HealthConnectStatus.AVAILABLE &&
                    healthSource.grantedPermissions().containsAll(healthSource.requiredPermissions)
            }.getOrDefault(false)
            _uiState.update { it.copy(healthConnected = connected) }
        }
    }

    fun dismiss() {
        store.dismissChecklist()
        _uiState.update { it.copy(dismissed = true) }
    }
}
