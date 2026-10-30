package com.meucoach.app.ui.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HealthConnectUiState(
    val isLoading: Boolean = true,
    val status: HealthConnectStatus = HealthConnectStatus.UNAVAILABLE,
    val requiredPermissions: Set<String> = emptySet(),
    val missingPermissions: Set<String> = emptySet(),
    val isSyncing: Boolean = false,
    val importedDays: Int = 0,
    val message: String? = null,
) {
    val hasPermissions: Boolean get() = missingPermissions.isEmpty()
}

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val source: HealthDailySource,
    private val metricsRepository: DailyMetricsRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HealthConnectUiState())
    val uiState: StateFlow<HealthConnectUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val status = source.status()
            val granted = source.grantedPermissions()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                status = status,
                requiredPermissions = source.requiredPermissions,
                missingPermissions = if (status == HealthConnectStatus.AVAILABLE) {
                    source.requiredPermissions - granted
                } else {
                    source.requiredPermissions
                },
            )
        }
    }

    fun onPermissionsResult() {
        refresh()
        sync()
    }

    fun sync() {
        val state = _uiState.value
        if (state.isSyncing) return
        if (state.status != HealthConnectStatus.AVAILABLE) {
            _uiState.value = state.copy(message = "Health Connect indisponível neste aparelho.")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isSyncing = true, message = null)
            val profile = profileRepository.getProfile()
            val zone = profile
                ?.let { runCatching { ZoneId.of(it.timezone) }.getOrNull() }
                ?: ZoneId.systemDefault()
            val today = LocalDate.now()
            val result = source.read(from = today.minusDays(SYNC_DAYS - 1L), to = today, zoneId = zone)
            val message = when {
                result.error != null -> result.error

                result.missingPermissions.isNotEmpty() ->
                    "Autorize a leitura de sono, FC, HRV e SpO2."

                result.days.isEmpty() -> "Nenhum dado novo encontrado."

                else -> {
                    metricsRepository.saveAll(result.days)
                    "${result.days.size} dias importados do Health Connect."
                }
            }
            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                importedDays = result.days.size,
                missingPermissions = result.missingPermissions.ifEmpty { _uiState.value.missingPermissions },
                message = message,
            )
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private companion object {
        const val SYNC_DAYS = 30L
    }
}
