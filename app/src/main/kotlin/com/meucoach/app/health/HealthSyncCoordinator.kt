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
    /** Last sync's implausible nights; zero means no diagnostic to show. */
    val suspectSleepNights: Int = 0,
    /** Last sync's valid fragmented nights; the largest block was used. */
    val fragmentedSleepNights: Int = 0,
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

    /**
     * Data-gate outcome for plan generation (ADR 0048 §2.1). Starts [Idle];
     * every terminal branch of a resume/manual sync settles it as [Ready]
     * (fresh data in) or [Failed] (generate with the available data and say
     * so explicitly). Awaiting the check-in is never part of the gate.
     */
    private val _gate = MutableStateFlow<HealthSyncGate>(HealthSyncGate.Idle)
    val gate: StateFlow<HealthSyncGate> = _gate.asStateFlow()

    /** Clears in-memory freshness after a full local reset. */
    fun resetState() {
        _state.value = HealthSyncState()
        _gate.value = HealthSyncGate.Idle
    }

    fun syncOnResume() {
        scope.launch {
            if (!autoMutex.tryLock()) return@launch
            try {
                _gate.value = HealthSyncGate.Syncing
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
                    _gate.value = HealthSyncGate.Failed("Health Connect indisponível no aparelho.")
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
                    _gate.value = HealthSyncGate.Failed("Permissão do Health Connect pendente.")
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
                if (result == null) {
                    // Fresh: the store holds the last success by construction.
                    _gate.value = HealthSyncGate.Ready(store.lastSyncedAt() ?: Instant.now())
                } else {
                    settleGate(result.error, result.missingPermissions.isNotEmpty())
                }
                _state.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncedAt = store.lastSyncedAt(),
                        lastAttemptAt = store.lastAttemptAt(),
                        lastError = result?.error,
                        needsPermission = result?.missingPermissions?.isNotEmpty() == true,
                        suspectSleepNights = result?.suspectSleepNights ?: it.suspectSleepNights,
                        fragmentedSleepNights = result?.fragmentedSleepNights ?: it.fragmentedSleepNights,
                    )
                }
            } catch (cancellation: CancellationException) {
                _state.update { it.copy(isSyncing = false) }
                throw cancellation
            } catch (error: Exception) {
                _gate.value = HealthSyncGate.Failed(
                    error.message ?: "Não foi possível atualizar o Health Connect.",
                )
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
        _gate.value = HealthSyncGate.Syncing
        _state.update { it.copy(isSyncing = true, lastError = null) }
        return try {
            service.sync().also { result ->
                settleGate(result.error, result.missingPermissions.isNotEmpty())
                _state.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncedAt = store.lastSyncedAt(),
                        lastAttemptAt = store.lastAttemptAt(),
                        lastError = result.error,
                        needsPermission = result.missingPermissions.isNotEmpty(),
                        unavailable = result.error?.contains("indisponível") == true,
                        suspectSleepNights = result.suspectSleepNights,
                        fragmentedSleepNights = result.fragmentedSleepNights,
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            _state.update { it.copy(isSyncing = false) }
            throw cancellation
        } catch (error: Exception) {
            _gate.value = HealthSyncGate.Failed(
                error.message ?: "Não foi possível atualizar o Health Connect.",
            )
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

    private fun settleGate(error: String?, permissionMissing: Boolean) {
        _gate.value = if (error != null) {
            HealthSyncGate.Failed(error)
        } else if (permissionMissing) {
            HealthSyncGate.Failed("Permissão do Health Connect pendente.")
        } else {
            HealthSyncGate.Ready(store.lastSyncedAt() ?: Instant.now())
        }
    }
}
