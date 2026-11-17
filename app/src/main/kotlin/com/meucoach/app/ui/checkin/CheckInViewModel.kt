package com.meucoach.app.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.time.TimeProvider
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
    val sleepQuality: Int? = null,
    val fatigue: Int? = null,
    val soreness: Int? = null,
    val stress: Int? = null,
    val illnessOrInjury: Boolean = false,
    val heatLevel: HeatLevel = HeatLevel.NONE,
) {
    /** ADR 0049 §2.2: no 3/3/3/3 default; save stays disabled until all four are answered. */
    val isComplete: Boolean
        get() = sleepQuality != null && fatigue != null && soreness != null && stress != null

    fun toCheckIn(date: LocalDate) = DailyCheckIn(
        date = date,
        sleepQuality = requireNotNull(sleepQuality) { "Check-in incompleto: responda ao sono" },
        fatigue = requireNotNull(fatigue) { "Check-in incompleto: responda à fadiga" },
        soreness = requireNotNull(soreness) { "Check-in incompleto: responda à dor" },
        stress = requireNotNull(stress) { "Check-in incompleto: responda ao estresse" },
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
    val date: LocalDate,
    val form: CheckInForm = CheckInForm(),
    val existing: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val history: List<DailyCheckIn> = emptyList(),
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val repository: CheckInRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CheckInUiState(date = timeProvider.currentDate()))
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    init {
        // Re-loads the form when the local day changes (including while the screen is open).
        viewModelScope.launch {
            timeProvider.today.collect { date ->
                val current = _uiState.value
                if (current.isLoading || current.date != date) {
                    load(date)
                }
            }
        }
        viewModelScope.launch {
            repository.observeAll().collect { all ->
                _uiState.update { state ->
                    state.copy(history = all.sortedByDescending { it.date }.take(HISTORY_LIMIT))
                }
            }
        }
    }

    private suspend fun load(date: LocalDate) {
        val existing = repository.get(date)
        // ADR 0049 §2.2 option B: without a check-in today, suggest the most
        // recent one within 7 days as a draft. Saving stays an explicit
        // confirmation; older history never pre-fills.
        val draft = existing ?: repository.getAll()
            .filter { it.date.isBefore(date) && !it.date.isBefore(date.minusDays(PREFILL_WINDOW_DAYS.toLong())) }
            .maxByOrNull { it.date }
        _uiState.update {
            it.copy(
                date = date,
                isLoading = false,
                form = draft?.let(CheckInForm::from) ?: CheckInForm(),
                existing = existing != null,
                isSaved = false,
            )
        }
    }

    fun update(transform: (CheckInForm) -> CheckInForm) {
        _uiState.value = _uiState.value.copy(form = transform(_uiState.value.form))
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || !state.form.isComplete) return
        val savedDate = state.date
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            // Uses the date captured by the screen, never the clock at save time:
            // a check-in confirmed at 23:59 belongs to the day it was confirmed.
            repository.save(state.form.toCheckIn(savedDate))
            _uiState.update { current ->
                current.copy(
                    isSaving = false,
                    isSaved = current.date == savedDate,
                    existing = current.existing || current.date == savedDate,
                )
            }
        }
    }

    fun consumeSaved() {
        _uiState.value = _uiState.value.copy(isSaved = false)
    }

    private companion object {
        const val HISTORY_LIMIT = 7

        /** ADR 0049 §2.2 option B: only a check-in from the last 7 days pre-fills the form. */
        const val PREFILL_WINDOW_DAYS = 7
    }
}
