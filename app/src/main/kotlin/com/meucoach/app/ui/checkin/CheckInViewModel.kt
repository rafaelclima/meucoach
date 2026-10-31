package com.meucoach.app.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.HeatLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CheckInForm(
    val sleepQuality: Int = 3,
    val fatigue: Int = 3,
    val soreness: Int = 3,
    val stress: Int = 3,
    val illnessOrInjury: Boolean = false,
    val heatLevel: HeatLevel = HeatLevel.NONE,
) {
    fun toCheckIn(date: LocalDate) = DailyCheckIn(
        date = date,
        sleepQuality = sleepQuality,
        fatigue = fatigue,
        soreness = soreness,
        stress = stress,
        illnessOrInjury = illnessOrInjury,
        heatLevel = heatLevel,
    )

    companion object {
        fun from(checkIn: DailyCheckIn) = CheckInForm(
            sleepQuality = checkIn.sleepQuality,
            fatigue = checkIn.fatigue,
            soreness = checkIn.soreness,
            stress = checkIn.stress,
            illnessOrInjury = checkIn.illnessOrInjury,
            heatLevel = checkIn.heatLevel,
        )
    }
}

data class CheckInUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val form: CheckInForm = CheckInForm(),
    val existing: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val history: List<DailyCheckIn> = emptyList(),
)

@HiltViewModel
class CheckInViewModel @Inject constructor(private val repository: CheckInRepository) : ViewModel() {
    private val today = LocalDate.now()
    private val _uiState = MutableStateFlow(CheckInUiState(date = today))
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { all ->
                _uiState.update { state ->
                    state.copy(history = all.sortedByDescending { it.date }.take(HISTORY_LIMIT))
                }
            }
        }
        viewModelScope.launch {
            val existing = repository.get(today)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                form = existing?.let(CheckInForm::from) ?: CheckInForm(),
                existing = existing != null,
            )
        }
    }

    fun update(transform: (CheckInForm) -> CheckInForm) {
        _uiState.value = _uiState.value.copy(form = transform(_uiState.value.form))
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            repository.save(state.form.toCheckIn(state.date))
            _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true, existing = true)
        }
    }

    fun consumeSaved() {
        _uiState.value = _uiState.value.copy(isSaved = false)
    }

    private companion object {
        const val HISTORY_LIMIT = 7
    }
}
