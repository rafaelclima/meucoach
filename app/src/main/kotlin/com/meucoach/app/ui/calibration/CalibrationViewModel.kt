package com.meucoach.app.ui.calibration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.application.EvaluateReplan
import com.meucoach.app.application.EvaluateReplanResult
import com.meucoach.app.replan.ProfilePlanEvaluationStore
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.domain.calibration.CalibrationCalculator
import com.meucoach.core.domain.calibration.CalibrationCandidate
import com.meucoach.core.domain.calibration.CalibrationCandidateDetector
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
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class CalibrationUiState(
    val isLoading: Boolean = true,
    val profile: AthleteProfile? = null,
    val form: CalibrationForm = CalibrationForm(),
    val errors: CalibrationFormErrors = CalibrationFormErrors(),
    val result: CalibrationResult? = null,
    val recentEfforts: Int = 0,
    val prefilledFrom: CalibrationCandidate? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
)

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val activityRepository: ActivityRepository,
    private val timeProvider: TimeProvider,
    private val evaluateReplan: EvaluateReplan,
    private val planEvaluationStore: ProfilePlanEvaluationStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val activityId: String? = savedStateHandle[ARG_ACTIVITY_ID]
    private val _uiState = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            val summaries = activityRepository.listActivitySummaries()
            val zone = timeProvider.zoneId.value
            val today = timeProvider.currentDate()
            val recent = recentEffortCount(summaries, zone, today)
            val candidate = activityId
                ?.let { activityRepository.openActivity(it) }
                ?.let { CalibrationCandidateDetector.detect(it.imported, it.localDate, today) }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                profile = profile,
                form = candidate?.let { prefillForm(it) } ?: CalibrationForm(),
                prefilledFrom = candidate,
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
        val today = timeProvider.currentDate()
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
            // Calibration changes target inputs, so it participates in T4.
            runCatching { evaluateReplan(explicit = false) }
                .onSuccess { evaluation ->
                    when (evaluation) {
                        is EvaluateReplanResult.Unavailable -> planEvaluationStore.clear()
                        else -> planEvaluationStore.publish(evaluation)
                    }
                }
            _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
        }
    }

    fun consumeSaved() {
        _uiState.value = _uiState.value.copy(isSaved = false)
    }

    private fun prefillForm(candidate: CalibrationCandidate) = CalibrationForm(
        distanceKm = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", candidate.distanceM / 1_000.0),
        time = CalibrationFormValidator.formatTime(candidate.durationSec),
        date = candidate.date.format(DATE),
        averageHeartRate = candidate.averageHeartRate.toString(),
    )

    companion object {
        const val ARG_ACTIVITY_ID = "activityId"
        private val DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    }
}
