package com.meucoach.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.id.UuidV7
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.model.AthleteProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class OnboardingUiState(
    val form: OnboardingForm = OnboardingForm(),
    val step: Int = 0,
    val errors: Map<OnboardingField, String> = emptyMap(),
    val existingProfile: AthleteProfile? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    val isEditing: Boolean get() = existingProfile != null
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val photoStore: com.meucoach.app.profile.ProfilePhotoStore,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = repository.getProfile()
            _uiState.value = OnboardingUiState(
                form = OnboardingValidator.fromProfile(profile),
                existingProfile = profile,
                isLoading = false,
            )
        }
    }

    fun update(transform: (OnboardingForm) -> OnboardingForm) {
        _uiState.value = _uiState.value.copy(
            form = transform(_uiState.value.form),
            errors = emptyMap(),
        )
    }

    fun toggleDay(day: DayOfWeek) {
        update { form ->
            val minutes = form.availabilityMinutes.toMutableMap()
            if (minutes.remove(day) == null) minutes[day] = DEFAULT_DAY_MINUTES
            form.copy(availabilityMinutes = minutes)
        }
    }

    fun updateDayMinutes(day: DayOfWeek, minutes: String) {
        update { form ->
            if (day !in form.availabilityMinutes) {
                form
            } else {
                form.copy(availabilityMinutes = form.availabilityMinutes + (day to minutes.filter { it.isDigit() }))
            }
        }
    }

    fun setAvatar(uri: android.net.Uri) {
        viewModelScope.launch {
            val path = photoStore.save(uri) ?: return@launch
            val previous = _uiState.value.form.avatarPath
            if (previous.isNotBlank() && previous != path) photoStore.delete(previous)
            update { it.copy(avatarPath = path) }
        }
    }

    fun clearAvatar() {
        val previous = _uiState.value.form.avatarPath
        photoStore.delete(previous)
        update { it.copy(avatarPath = "") }
    }

    fun next() {
        val state = _uiState.value
        if (state.step >= LAST_STEP) return
        val validation = OnboardingValidator.validateStep(state.step, state.form, timeProvider.currentDate())
        if (!validation.isValid) {
            _uiState.value = state.copy(errors = validation.errors)
            return
        }
        _uiState.value = state.copy(step = state.step + 1, errors = emptyMap())
    }

    fun back() {
        val state = _uiState.value
        if (state.step == 0) return
        _uiState.value = state.copy(step = state.step - 1, errors = emptyMap())
    }

    fun save() {
        val state = _uiState.value
        val today = timeProvider.currentDate()
        val validation = OnboardingValidator.validateAll(state.form, today)
        if (!validation.isValid) {
            _uiState.value = state.copy(
                step = stepFor(validation.errors.keys),
                errors = validation.errors,
            )
            return
        }
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            val profile = OnboardingValidator.toProfile(
                form = state.form,
                id = state.existingProfile?.id ?: UuidV7.generate(),
                existing = state.existingProfile,
                today = today,
            )
            repository.saveProfile(profile)
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                isSaved = true,
                existingProfile = profile,
            )
        }
    }

    fun consumeSaved() {
        _uiState.value = _uiState.value.copy(isSaved = false)
    }

    private fun stepFor(fields: Set<OnboardingField>): Int = when {
        fields.any { it in ABOUT_FIELDS } -> 0
        fields.any { it in GOAL_FIELDS } -> 1
        else -> 2
    }

    companion object {
        const val LAST_STEP = 2
        private const val DEFAULT_DAY_MINUTES = "40"

        private val ABOUT_FIELDS = setOf(
            OnboardingField.NAME,
            OnboardingField.BIRTH_DATE,
            OnboardingField.HEIGHT,
            OnboardingField.WEIGHT,
        )
        private val GOAL_FIELDS = setOf(
            OnboardingField.TARGET_DATE,
            OnboardingField.TARGET_TIME,
            OnboardingField.AVAILABILITY,
        )
    }
}
