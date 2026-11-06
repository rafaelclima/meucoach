package com.meucoach.app.ui.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.health.HealthSyncCoordinator
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.health.HealthSyncStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
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
    val lastSyncedAt: Instant? = null,
    val lastError: String? = null,
    val message: String? = null,
    val today: LocalDate? = null,
    val now: Instant? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val hasPermissions: Boolean get() = missingPermissions.isEmpty()
}

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val source: HealthDailySource,
    private val coordinator: HealthSyncCoordinator,
    private val syncStore: HealthSyncStore,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private data class Access(
        val isLoading: Boolean = true,
        val status: HealthConnectStatus = HealthConnectStatus.UNAVAILABLE,
        val missingPermissions: Set<String> = emptySet(),
    )

    private val access = MutableStateFlow(Access())
    private val manualMessage = MutableStateFlow<String?>(null)
    private val importedDays = MutableStateFlow(0)

    val uiState: StateFlow<HealthConnectUiState> = combine(
        access,
        coordinator.state,
        manualMessage,
        importedDays,
        timeProvider.today,
    ) { access, sync, message, days, today ->
        HealthConnectUiState(
            isLoading = access.isLoading,
            status = access.status,
            requiredPermissions = source.requiredPermissions,
            missingPermissions = access.missingPermissions,
            isSyncing = sync.isSyncing,
            importedDays = days,
            lastSyncedAt = syncStore.lastSyncedAt() ?: sync.lastSyncedAt,
            lastError = sync.lastError,
            message = message,
            today = today,
            now = timeProvider.now(),
            zone = timeProvider.zoneId.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HealthConnectUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val status = source.status()
            val granted = source.grantedPermissions()
            access.value = Access(
                isLoading = false,
                status = status,
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
        if (coordinator.state.value.isSyncing) return
        viewModelScope.launch {
            manualMessage.value = null
            runCatching { coordinator.syncNow() }.fold(
                onSuccess = { result ->
                    importedDays.value = result.importedDays
                    manualMessage.value = when {
                        result.error != null -> result.error

                        result.missingPermissions.isNotEmpty() ->
                            "Autorize a leitura de sono, FC de repouso, variabilidade cardíaca " +
                                "(HRV) e oxigenação (SpO₂)."

                        result.importedDays == 0 -> "Nenhum dado novo encontrado."

                        result.incremental ->
                            "${result.importedDays} dias atualizados do Health Connect."

                        else -> "${result.importedDays} dias importados do Health Connect."
                    }
                },
                onFailure = { error ->
                    manualMessage.value = error.message ?: "Não foi possível sincronizar agora."
                },
            )
        }
    }

    fun consumeMessage() {
        manualMessage.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
