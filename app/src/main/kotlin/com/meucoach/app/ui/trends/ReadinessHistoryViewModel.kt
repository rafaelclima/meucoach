package com.meucoach.app.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.domain.readiness.ReadinessHistoryCalculator
import com.meucoach.core.domain.readiness.ReadinessHistoryDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class ReadinessHistoryUiState(val isLoading: Boolean = true, val days: List<ReadinessHistoryDay> = emptyList())

@HiltViewModel
class ReadinessHistoryViewModel @Inject constructor(
    checkInRepository: CheckInRepository,
    metricsRepository: DailyMetricsRepository,
    timeProvider: TimeProvider,
) : ViewModel() {
    private val calculator = ReadinessHistoryCalculator()

    val uiState: StateFlow<ReadinessHistoryUiState> = combine(
        checkInRepository.observeAll(),
        metricsRepository.observeAll(),
        timeProvider.today,
    ) { checkIns, metrics, today ->
        ReadinessHistoryUiState(
            isLoading = false,
            days = calculator.calculate(
                checkIns = checkIns,
                metrics = metrics,
                today = today,
                days = HISTORY_DAYS,
            ),
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ReadinessHistoryUiState(),
        )

    private companion object {
        const val HISTORY_DAYS = 14
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
