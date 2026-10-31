package com.meucoach.app.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.load.LoadActivity
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.load.TrainingLoadEngine
import com.meucoach.core.domain.prescription.PlanCompletion
import com.meucoach.core.domain.prescription.PlanCompletionCalculator
import com.meucoach.core.domain.trends.ActivityTrends
import com.meucoach.core.domain.trends.ActivityTrendsCalculator
import com.meucoach.core.domain.trends.IntensityDistribution
import com.meucoach.core.domain.trends.IntensityDistributionCalculator
import com.meucoach.core.domain.trends.IntensitySession
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Sport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class TrendsPeriod(val weeks: Int, val consistencyDays: Int, val label: String) {
    WEEKS_6(weeks = 6, consistencyDays = 28, label = "6 sem"),
    WEEKS_12(weeks = 12, consistencyDays = 84, label = "12 sem"),
    WEEKS_26(weeks = 26, consistencyDays = 182, label = "26 sem"),
}

data class TrendsUiState(
    val isLoading: Boolean = true,
    val profile: AthleteProfile? = null,
    val snapshot: LoadSnapshot? = null,
    val activityTrends: ActivityTrends? = null,
    val intensity: IntensityDistribution? = null,
    val planCompletion: PlanCompletion? = null,
    val period: TrendsPeriod = TrendsPeriod.WEEKS_6,
)

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val activityRepository: ActivityRepository,
    private val planRepository: TrainingPlanRepository,
) : ViewModel() {
    private val engine = TrainingLoadEngine()
    private val trendsCalculator = ActivityTrendsCalculator()
    private val intensityCalculator = IntensityDistributionCalculator()
    private val planCompletionCalculator = PlanCompletionCalculator()
    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(TrendsPeriod.WEEKS_6)
    }

    fun selectPeriod(period: TrendsPeriod) {
        if (period == _uiState.value.period) return
        _uiState.value = _uiState.value.copy(period = period)
        load(period)
    }

    private fun load(period: TrendsPeriod) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val profile = profileRepository.getProfile()
            if (profile == null) {
                _uiState.value = TrendsUiState(isLoading = false, period = period)
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
                    weeks = period.weeks,
                    consistencyDays = period.consistencyDays,
                ),
                intensity = profile.lactateThresholdHeartRate?.let { lthr ->
                    distribution(summaries, today, zone, lthr)
                },
                planCompletion = completion(today),
                period = period,
            )
        }
    }

    private suspend fun completion(today: LocalDate): PlanCompletion {
        val currentMonday = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        val weekStarts = (PLAN_COMPLETION_WEEKS - 1 downTo 0)
            .map { currentMonday.minusWeeks(it.toLong()) }
        return planCompletionCalculator.calculate(
            plans = planRepository.getWeeks(weekStarts),
            today = today,
            weeks = PLAN_COMPLETION_WEEKS,
        )
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
        const val PLAN_COMPLETION_WEEKS = 8
    }
}
