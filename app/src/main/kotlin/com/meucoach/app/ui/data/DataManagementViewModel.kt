package com.meucoach.app.ui.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.core.data.repository.UserDataDeletion
import com.meucoach.core.data.repository.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DataManagementUiState(
    val isExporting: Boolean = false,
    val isDeleting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val isBusy: Boolean get() = isExporting || isDeleting
}

@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val userData: UserDataRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()

    fun export(uri: Uri) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, message = null, error = null) }
            val result = runCatching {
                val json = userData.exportJson()
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error("Não foi possível abrir o arquivo escolhido.")
                    stream.use { output -> output.write(json.toByteArray(Charsets.UTF_8)) }
                }
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = {
                        state.copy(
                            isExporting = false,
                            message = "Backup exportado. Guarde o arquivo em um lugar seguro.",
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            isExporting = false,
                            error = error.message ?: "Não foi possível exportar agora.",
                        )
                    },
                )
            }
        }
    }

    fun deleteAllData() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, message = null, error = null) }
            val result = runCatching { userData.deleteAllData() }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { deletion -> state.copy(isDeleting = false, message = deletion.message()) },
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

    private fun UserDataDeletion.message(): String = if (removedAnything) {
        "Dados apagados deste aparelho: " +
            "$activities corrida(s), $checkIns check-in(s), $metrics dia(s) de métricas e $plans plano(s). " +
            "O app volta para a configuração inicial."
    } else {
        "Não havia dados para apagar."
    }
}
