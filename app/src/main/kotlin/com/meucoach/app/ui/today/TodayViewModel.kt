package com.meucoach.app.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.toReadinessResult
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(repository: CheckInRepository) : ViewModel() {
    val checkIn: StateFlow<DailyCheckIn?> = repository
        .observe(LocalDate.now())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun TodayRoute(
    onDemoAction: (String) -> Unit,
    onCheckInClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val checkIn by viewModel.checkIn.collectAsStateWithLifecycle()
    TodayScreen(
        onDemoAction = onDemoAction,
        modifier = modifier,
        state = todayState(checkIn),
        checkInDone = checkIn != null,
        onCheckInClick = onCheckInClick,
    )
}

/** Overlays today's real check-in on the demo shell until prescription exists. */
internal fun todayState(checkIn: DailyCheckIn?): TodayUiState {
    val base = demoTodayUiState()
    if (checkIn == null) {
        return base.copy(
            readinessScore = 50,
            readinessLevel = ReadinessLevel.AMBER,
            readinessConfidence = DataConfidence.LOW,
            readinessSignals = listOf(
                ReadinessSignalUi(
                    label = "Check-in",
                    value = "Pendente",
                    hint = "15 s para medir o dia",
                    tone = SignalTone.NEUTRAL,
                ),
            ),
        )
    }

    val result = checkIn.toReadinessResult()
    val warning = ReadinessFlag.ILLNESS_OR_INJURY in result.flags ||
        ReadinessFlag.NO_QUALITY in result.flags
    return base.copy(
        readinessScore = result.score,
        readinessLevel = result.level,
        readinessConfidence = result.confidence,
        readinessSignals = listOf(
            ReadinessSignalUi(
                label = "Check-in",
                value = "${checkIn.hooperSum}/20",
                hint = if (ReadinessFlag.ILLNESS_OR_INJURY in result.flags) {
                    "sinal de alerta"
                } else {
                    "Hooper · menor é melhor"
                },
                tone = if (warning) SignalTone.WARNING else SignalTone.POSITIVE,
            ),
        ),
    )
}
