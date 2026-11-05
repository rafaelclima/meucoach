package com.meucoach.app.health

import com.meucoach.app.di.ApplicationScope
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.health.HealthSyncResult
import com.meucoach.core.data.health.HealthSyncService
import com.meucoach.core.data.health.HealthSyncStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the automatic Health Connect sync. The app resume path calls
 * [syncOnResume]; the health screen calls [syncNow] for the explicit button.
 * The service keeps the freshness/backoff policy and serializes reads, so this
 * class only coordinates platform access and exposes state to the UI.
 */
data class HealthSyncState(
    val isSyncing: Boolean = false,
    val lastSyncedAt: Instant? = null,
    val lastAttemptAt: Instant? = null,
    val needsPermission: Boolean = false,
    val unavailable: Boolean = false,
    val lastError: String? = null,
)

@Singleton
class HealthSyncCoordinator @Inject constructor(
    private val source: HealthDailySource,
    private val service: HealthSyncService,
    private val store: HealthSyncStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val autoMutex = Mutex()
    private val _state = MutableStateFlow(
        HealthSyncState(
            lastSyncedAt = store.lastSyncedAt(),
            lastAttemptAt = store.lastAttemptAt(),
        ),
    )
    val state: StateFlow<HealthSyncState> = _state.asStateFlow()

    fun syncOnResume() {
        scope.launch {
            if (!autoMutex.tryLock()) return@launch
            try {
                _state.update {
                    it.copy(
                        isSyncing = true,
                        needsPermission = false,
                        unavailable = false,
                        lastError = null,
                    )
                }
                val status = source.status()
                if (status != HealthConnectStatus.AVAILABLE) {
                    _state.update {
                        it.copy(
                            isSyncing = false,
                            unavailable = status == HealthConnectStatus.UNAVAILABLE,
                            lastAttemptAt = store.lastAttemptAt(),
                        )
                    }
                    return@launch
                }
                val missing = source.requiredPermissions - source.grantedPermissions()
                if (missing.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            isSyncing = false,
                            needsPermission = true,
                            lastAttemptAt = store.lastAttemptAt(),
                        )
                    }
                    return@launch
                }
                val result = service.syncIfStale()
                _state.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncedAt = store.lastSyncedAt(),
                        lastAttemptAt = store.lastAttemptAt(),
                        lastError = result?.error,
                        needsPermission = result?.missingPermissions?.isNotEmpty() == true,
                    )
                }
            } catch (cancellation: CancellationException) {
                _state.update { it.copy(isSyncing = false) }
                throw cancellation
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isSyncing = false,
                        lastError = error.message ?: "Não foi possível atualizar o Health Connect.",
                        lastAttemptAt = store.lastAttemptAt(),
                    )
                }
            } finally {
                autoMutex.unlock()
            }
        }
    }

    suspend fun syncNow(): HealthSyncResult {
        _state.update { it.copy(isSyncing = true, lastError = null) }
        return try {
            service.sync().also { result ->
                _state.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncedAt = store.lastSyncedAt(),
                        lastAttemptAt = store.lastAttemptAt(),
                        lastError = result.error,
                        needsPermission = result.missingPermissions.isNotEmpty(),
                        unavailable = result.error?.contains("indisponível") == true,
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            _state.update { it.copy(isSyncing = false) }
            throw cancellation
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isSyncing = false,
                    lastError = error.message ?: "Não foi possível atualizar o Health Connect.",
                    lastAttemptAt = store.lastAttemptAt(),
                )
            }
            throw error
        }
    }
}
