package com.meucoach.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.health.HealthSyncStore
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.model.AthleteProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
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

/**
 * Snapshot of what the coach already knows about sleep and recovery. The
 * profile card uses it to prove that Health Connect feeds the engine instead of
 * only describing the feature.
 */
data class ProfileHealthSummary(
    val status: HealthConnectStatus = HealthConnectStatus.UNAVAILABLE,
    val hasPermissions: Boolean = false,
    val storedDays: Int = 0,
    val latestMetricDate: LocalDate? = null,
    val lastSyncedAt: Instant? = null,
)

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: AthleteProfile? = null,
    val health: ProfileHealthSummary = ProfileHealthSummary(),
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val metricsRepository: DailyMetricsRepository,
    private val healthSource: HealthDailySource,
    private val syncStore: HealthSyncStore,
) : ViewModel() {
    private data class HealthAccess(
        val status: HealthConnectStatus = HealthConnectStatus.UNAVAILABLE,
        val hasPermissions: Boolean = false,
        val lastSyncedAt: Instant? = null,
    )

    private val access = MutableStateFlow(HealthAccess())

    val uiState: StateFlow<ProfileUiState> = combine(
        repository.observeProfile(),
        metricsRepository.observeAll(),
        access,
    ) { profile, metrics, access ->
        ProfileUiState(
            isLoading = false,
            profile = profile,
            health = ProfileHealthSummary(
                status = access.status,
                hasPermissions = access.hasPermissions,
                storedDays = metrics.size,
                latestMetricDate = metrics.lastOrNull()?.date,
                lastSyncedAt = access.lastSyncedAt,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ProfileUiState(),
    )

    init {
        refreshHealth()
    }

    /** Re-reads the platform state when the screen resumes, after a sync or permission change. */
    fun refreshHealth() {
        viewModelScope.launch {
            val status = healthSource.status()
            val hasPermissions = status == HealthConnectStatus.AVAILABLE &&
                (healthSource.requiredPermissions - healthSource.grantedPermissions()).isEmpty()
            access.value = HealthAccess(
                status = status,
                hasPermissions = hasPermissions,
                lastSyncedAt = syncStore.lastSyncedAt(),
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
