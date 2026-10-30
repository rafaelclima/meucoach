package com.meucoach.app.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.domain.calibration.CalibrationCalculator
import com.meucoach.core.domain.calibration.CalibrationInput
import com.meucoach.core.domain.calibration.CalibrationResult
import com.meucoach.core.model.AthleteProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class CalibrationUiState(
    val isLoading: Boolean = true,
    val profile: AthleteProfile? = null,
    val form: CalibrationForm = CalibrationForm(),
    val errors: CalibrationFormErrors = CalibrationFormErrors(),
    val result: CalibrationResult? = null,
    val recentEfforts: Int = 0,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
)

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val activityRepository: ActivityRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            val summaries = activityRepository.listActivitySummaries()
            val zone = profile
                ?.let { runCatching { ZoneId.of(it.timezone) }.getOrNull() }
                ?: ZoneId.systemDefault()
            val recent = recentEffortCount(summaries, zone, LocalDate.now())
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                profile = profile,
                recentEfforts = recent,
            )
        }
    }

    fun update(transform: (CalibrationForm) -> CalibrationForm) {
        _uiState.value = _uiState.value.copy(
            form = transform(_uiState.value.form),
            errors = CalibrationFormErrors(),
            result = null,
        )
    }

    fun calculate() {
        val state = _uiState.value
        val today = LocalDate.now()
        val errors = CalibrationFormValidator.validate(state.form, today)
        if (!errors.isValid) {
            _uiState.value = state.copy(errors = errors, result = null)
            return
        }
        val result = CalibrationCalculator.calibrate(
            CalibrationInput(
                profile = state.profile ?: return,
                today = today,
                performance = CalibrationFormValidator.toPerformance(state.form, today),
                recentActivitiesWithHeartRateAndPace = state.recentEfforts,
            ),
        )
        _uiState.value = state.copy(errors = CalibrationFormErrors(), result = result)
    }

    fun save() {
        val state = _uiState.value
        val profile = state.profile ?: return
        val result = state.result ?: return
        if (result.vdot == null && result.thresholdPace == null && result.lthr == null) return
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            profileRepository.saveProfile(
                profile.copy(
                    lactateThresholdHeartRate = result.lthr ?: profile.lactateThresholdHeartRate,
                    thresholdPace = result.thresholdPace ?: profile.thresholdPace,
                    vdot = result.vdot ?: profile.vdot,
                ),
            )
            _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
        }
    }

    fun consumeSaved() {
        _uiState.value = _uiState.value.copy(isSaved = false)
    }
}
