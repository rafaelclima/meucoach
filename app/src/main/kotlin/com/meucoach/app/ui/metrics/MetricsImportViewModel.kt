package com.meucoach.app.ui.metrics

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.domain.health.DailyMetricsTrends
import com.meucoach.core.domain.health.DailyMetricsTrendsCalculator
import com.meucoach.core.domain.importer.CsvRowError
import com.meucoach.core.domain.importer.DailyMetricsCsvParser
import com.meucoach.core.model.DailyMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

data class MetricsImportUiState(
    val isImporting: Boolean = false,
    val importedDays: Int = 0,
    val duplicateDates: Int = 0,
    val errors: List<CsvRowError> = emptyList(),
    val message: String? = null,
    val storedDays: Int = 0,
    val latestMetricDate: java.time.LocalDate? = null,
    val windowDays: Int = DailyMetricsTrendsCalculator.DEFAULT_WINDOW_DAYS,
    val today: LocalDate? = null,
    val trends: DailyMetricsTrends = emptyTrends(),
)

@HiltViewModel
class MetricsImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DailyMetricsRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val parser = DailyMetricsCsvParser()
    private val trendsCalculator = DailyMetricsTrendsCalculator()

    @VisibleForTesting
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val _uiState = MutableStateFlow(
        MetricsImportUiState(
            today = timeProvider.currentDate(),
            trends = emptyTrends(timeProvider.currentDate()),
        ),
    )
    val uiState: StateFlow<MetricsImportUiState> = _uiState.asStateFlow()
    private var storedMetrics: List<DailyMetrics> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { rows ->
                storedMetrics = rows
                refresh(rows)
            }
        }
    }

    /** Re-evaluates the local day when the health screen resumes. */
    fun refreshToday() {
        refresh(storedMetrics)
    }

    fun selectWindow(days: Int) {
        require(days > 0) { "Window must be positive" }
        refresh(storedMetrics, windowDays = days)
    }

    private fun refresh(rows: List<DailyMetrics>, windowDays: Int = _uiState.value.windowDays) {
        val today = timeProvider.currentDate()
        _uiState.value = _uiState.value.copy(
            storedDays = rows.size,
            latestMetricDate = rows.lastOrNull()?.date,
            windowDays = windowDays,
            today = today,
            trends = trendsCalculator.calculate(
                metrics = rows,
                today = today,
                windowDays = windowDays,
            ),
        )
    }

    fun import(uri: Uri) {
        if (_uiState.value.isImporting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImporting = true,
                message = null,
                errors = emptyList(),
            )
            val parsed = runCatching {
                val text = withContext(ioDispatcher) { read(uri) }
                parser.parse(text)
            }
            parsed.fold(
                onSuccess = { result ->
                    repository.saveAll(result.metrics)
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        importedDays = result.metrics.size,
                        duplicateDates = result.duplicateDates,
                        errors = result.errors,
                        message = if (result.metrics.isEmpty()) {
                            "Nenhuma linha válida encontrada."
                        } else {
                            "${result.metrics.size} dias importados."
                        },
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        message = error.message ?: "Não foi possível ler o CSV.",
                    )
                },
            )
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

private fun emptyTrends(today: LocalDate = LocalDate.of(1970, 1, 1)): DailyMetricsTrends = DailyMetricsTrends(
    windowDays = DailyMetricsTrendsCalculator.DEFAULT_WINDOW_DAYS,
    startDate = today.minusDays(DailyMetricsTrendsCalculator.DEFAULT_WINDOW_DAYS - 1L),
    endDate = today,
    days = emptyList(),
    trends = emptyList(),
)
