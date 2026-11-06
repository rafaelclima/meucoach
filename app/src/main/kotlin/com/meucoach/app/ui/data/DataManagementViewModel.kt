package com.meucoach.app.ui.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.data.DataResetResult
import com.meucoach.app.data.DataResetter
import com.meucoach.app.firstrun.FirstRunMetricsStore
import com.meucoach.app.importer.ImportStaging
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.ui.activities.formatDateTime
import com.meucoach.app.ui.activities.formatDistance
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.app.ui.activities.sportLabel
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.TrashedActivity
import com.meucoach.core.data.repository.UserDataDeletion
import com.meucoach.core.data.repository.UserDataRepository
import com.meucoach.core.domain.retention.TrashPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TrashItemUi(val id: String, val title: String, val detail: String, val daysRemaining: Int)

data class DataManagementUiState(
    val isExporting: Boolean = false,
    val isDeleting: Boolean = false,
    val includeTrash: Boolean = false,
    val trash: List<TrashItemUi> = emptyList(),
    val message: String? = null,
    val error: String? = null,
) {
    val isBusy: Boolean get() = isExporting || isDeleting
}

@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val userData: UserDataRepository,
    private val activities: ActivityRepository,
    private val staging: ImportStaging,
    private val resetCoordinator: DataResetter,
    private val firstRunMetrics: FirstRunMetricsStore,
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(activities.observeTrash(), timeProvider.today) { items, _ -> items }
                .collect { items ->
                    _uiState.update { state ->
                        state.copy(trash = items.map { it.toUi() })
                    }
                }
        }
    }

    fun currentDate(): java.time.LocalDate = timeProvider.currentDate()

    fun toggleIncludeTrash() {
        _uiState.update { it.copy(includeTrash = !it.includeTrash) }
    }

    fun export(uri: Uri) {
        val state = _uiState.value
        if (state.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, message = null, error = null) }
            val result = runCatching {
                val json = userData.exportJson(
                    firstRun = firstRunMetrics.toJson(),
                    includeTrash = state.includeTrash,
                )
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error("Não foi possível abrir o arquivo escolhido.")
                    stream.use { output -> output.write(json.toByteArray(Charsets.UTF_8)) }
                }
            }
            _uiState.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(
                            isExporting = false,
                            message = if (state.includeTrash) {
                                "Backup exportado, incluindo os itens da lixeira."
                            } else {
                                "Backup exportado. Guarde o arquivo em um lugar seguro."
                            },
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isExporting = false,
                            error = error.message ?: "Não foi possível exportar agora.",
                        )
                    },
                )
            }
        }
    }

    fun restoreActivity(id: String) {
        viewModelScope.launch {
            val restored = runCatching { activities.restoreActivity(id) }.getOrDefault(false)
            _uiState.update {
                if (restored) {
                    it.copy(message = "Atividade restaurada.", error = null)
                } else {
                    it.copy(error = "Não foi possível restaurar a atividade.")
                }
            }
        }
    }

    fun deletePermanently(id: String) {
        viewModelScope.launch {
            val purged = runCatching { activities.purgeActivity(id) }.getOrNull()
            if (purged != null) {
                purged.rawFilePath?.let { path -> runCatching { staging.delete(path) } }
                _uiState.update { it.copy(message = "Atividade apagada definitivamente.", error = null) }
            } else {
                _uiState.update { it.copy(error = "Não foi possível apagar a atividade.") }
            }
        }
    }

    fun deleteAllData() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, message = null, error = null) }
            val result = try {
                Result.success(resetCoordinator.reset())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { reset -> state.copy(isDeleting = false, message = reset.message()) },
                    onFailure = { error ->
                        state.copy(
                            isDeleting = false,
                            error = error.message ?: "Não foi possível apagar os dados agora.",
                        )
                    },
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    private fun TrashedActivity.toUi(): TrashItemUi {
        val zone = timeProvider.zoneId.value
        return TrashItemUi(
            id = id,
            title = sportLabel(sport),
            detail = "${formatDistance(distance)} km · ${formatDuration(durationSec)} · " +
                "apagada em ${formatDateTime(deletedAt, zone)}",
            daysRemaining = TrashPolicy.daysRemaining(deletedAt, timeProvider.now()),
        )
    }

    private fun DataResetResult.message(): String {
        val base = deletion.message()
        if (failedCleanups.isEmpty()) return base
        return "$base Alguns arquivos não puderam ser removidos: ${failedCleanups.joinToString("; ")}."
    }

    private fun UserDataDeletion.message(): String = if (removedAnything) {
        "Dados apagados deste aparelho: " +
            "$activities corrida(s), $checkIns check-in(s), $metrics dia(s) de métricas e $plans plano(s). " +
            "O app volta para a configuração inicial."
    } else {
        "Não havia dados para apagar."
    }
}
