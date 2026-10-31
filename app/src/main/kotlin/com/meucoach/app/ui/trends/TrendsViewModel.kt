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
import com.meucoach.core.domain.trends.IntensityDistribution
import com.meucoach.core.domain.trends.IntensityDistributionCalculator
import com.meucoach.core.domain.trends.IntensitySession
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Sport
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
    val intensity: IntensityDistribution? = null,
)

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val activityRepository: ActivityRepository,
) : ViewModel() {
    private val engine = TrainingLoadEngine()
    private val trendsCalculator = ActivityTrendsCalculator()
    private val intensityCalculator = IntensityDistributionCalculator()
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
            val today = LocalDate.now()
            val summaries = activityRepository.listActivitySummaries()
            val activities = summaries.map { summary ->
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
                    today = today,
                ),
                activityTrends = trendsCalculator.calculate(
                    activities = summaries,
                    today = today,
                    zoneId = zone,
                ),
                intensity = profile.lactateThresholdHeartRate?.let { lthr ->
                    distribution(summaries, today, zone, lthr)
                },
            )
        }
    }

    private suspend fun distribution(
        summaries: List<ActivitySummary>,
        today: LocalDate,
        zone: ZoneId,
        lthr: Int,
    ): IntensityDistribution? {
        val start = today.minusDays(INTENSITY_WINDOW_DAYS - 1L)
        val sessions = summaries
            .filter { it.sport == Sport.RUNNING || it.sport == Sport.TREADMILL }
            .mapNotNull { summary ->
                val date = summary.startTime.atZone(zone).toLocalDate()
                if (date.isBefore(start) || date.isAfter(today)) return@mapNotNull null
                val samples = activityRepository.openActivity(summary.id)?.imported?.samples.orEmpty()
                if (samples.isEmpty()) null else IntensitySession(date = date, samples = samples)
            }
        return intensityCalculator.calculate(
            sessions = sessions,
            today = today,
            lthrBpm = lthr,
            windowDays = INTENSITY_WINDOW_DAYS,
        )
    }

    private companion object {
        const val INTENSITY_WINDOW_DAYS = 28
    }
}
