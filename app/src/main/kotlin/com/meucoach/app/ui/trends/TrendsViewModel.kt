package com.meucoach.app.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.domain.load.LoadActivity
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.load.TrainingLoadEngine
import com.meucoach.core.domain.trends.ActivityTrends
import com.meucoach.core.domain.trends.ActivityTrendsCalculator
import com.meucoach.core.model.AthleteProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TrendsUiState(
    val isLoading: Boolean = true,
    val profile: AthleteProfile? = null,
    val snapshot: LoadSnapshot? = null,
    val activityTrends: ActivityTrends? = null,
)

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val activityRepository: ActivityRepository,
) : ViewModel() {
    private val engine = TrainingLoadEngine()
    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            if (profile == null) {
                _uiState.value = TrendsUiState(isLoading = false)
                return@launch
            }
            val zone = runCatching { ZoneId.of(profile.timezone) }.getOrNull() ?: ZoneId.systemDefault()
            val activities = activityRepository.listActivitySummaries().map { summary ->
                LoadActivity(
                    date = summary.startTime.atZone(zone).toLocalDate(),
                    durationSec = summary.durationSec,
                    averageHeartRate = summary.averageHeartRate,
                    averagePace = summary.averagePace,
                )
            }
            _uiState.value = TrendsUiState(
                isLoading = false,
                profile = profile,
                snapshot = engine.calculate(
                    activities = activities,
                    profile = profile,
                    today = LocalDate.now(),
                ),
                activityTrends = ActivityTrendsCalculator().calculate(
                    activities = activityRepository.listActivitySummaries(),
                    today = LocalDate.now(),
                    zoneId = zone,
                ),
            )
        }
    }
}
