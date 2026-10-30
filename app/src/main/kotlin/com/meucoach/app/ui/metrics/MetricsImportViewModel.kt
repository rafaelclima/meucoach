package com.meucoach.app.ui.metrics

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.domain.importer.CsvRowError
import com.meucoach.core.domain.importer.DailyMetricsCsvParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

data class MetricsImportUiState(
    val isImporting: Boolean = false,
    val importedDays: Int = 0,
    val duplicateDates: Int = 0,
    val errors: List<CsvRowError> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class MetricsImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DailyMetricsRepository,
) : ViewModel() {
    private val parser = DailyMetricsCsvParser()
    private val _uiState = MutableStateFlow(MetricsImportUiState())
    val uiState: StateFlow<MetricsImportUiState> = _uiState.asStateFlow()

    fun import(uri: Uri) {
        if (_uiState.value.isImporting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, message = null, errors = emptyList())
            val result = runCatching {
                val text = withContext(Dispatchers.IO) { read(uri) }
                parser.parse(text)
            }
            val state = result.fold(
                onSuccess = { parsed ->
                    repository.saveAll(parsed.metrics)
                    MetricsImportUiState(
                        importedDays = parsed.metrics.size,
                        duplicateDates = parsed.duplicateDates,
                        errors = parsed.errors,
                        message = if (parsed.metrics.isEmpty()) {
                            "Nenhuma linha válida encontrada."
                        } else {
                            "${parsed.metrics.size} dias importados."
                        },
                    )
                },
                onFailure = { error ->
                    MetricsImportUiState(
                        message = error.message ?: "Não foi possível ler o CSV.",
                    )
                },
            )
            _uiState.value = state
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun read(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Não foi possível abrir o arquivo.")
        return stream.bufferedReader().use { reader ->
            val text = reader.readText()
            if (text.length > MAX_CHARS) throw IOException("CSV muito grande.")
            text
        }
    }

    private companion object {
        const val MAX_CHARS = 2_000_000
    }
}
