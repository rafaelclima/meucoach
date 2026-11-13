package com.meucoach.app.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.application.TrainingSignalSource
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.PlanCompletion
import com.meucoach.core.domain.prescription.PlanCompletionCalculator
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewCalculator
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.math.roundToInt

enum class TrendsPeriod(val weeks: Int, val consistencyDays: Int, val label: String) {
    WEEKS_6(weeks = 6, consistencyDays = 42, label = "6 sem"),
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
    val weeklyReview: WeeklyReview? = null,
    val period: TrendsPeriod = TrendsPeriod.WEEKS_6,
)

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val activityRepository: ActivityRepository,
    private val planRepository: TrainingPlanRepository,
    private val raceEventRepository: RaceEventRepository,
    private val signalSource: TrainingSignalSource,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val trendsCalculator = ActivityTrendsCalculator()
    private val intensityCalculator = IntensityDistributionCalculator()
    private val planCompletionCalculator = PlanCompletionCalculator()
    private val weeklyReviewCalculator = WeeklyReviewCalculator()
    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            timeProvider.today.collect { today ->
                load(_uiState.value.period, today)
            }
        }
    }

    fun selectPeriod(period: TrendsPeriod) {
        if (period == _uiState.value.period) return
        _uiState.value = _uiState.value.copy(period = period)
        load(period, timeProvider.currentDate())
    }

    private fun load(period: TrendsPeriod, today: LocalDate) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val profile = profileRepository.getProfile()
            if (profile == null) {
                _uiState.value = TrendsUiState(isLoading = false, period = period)
                return@launch
            }
            val zone = timeProvider.zoneId.value
            val signals = signalSource.snapshot(profile, today, timeProvider.now())
            val summaries = signals.activities
            val completion = completion(today)
            val intensitySessions = intensitySessions(summaries, today, zone)
            val intensity = profile.lactateThresholdHeartRate?.let { lthr ->
                intensityCalculator.calculate(
                    sessions = intensitySessions,
                    today = today,
                    lthrBpm = lthr,
                    windowDays = INTENSITY_WINDOW_DAYS,
                )
            }
            // The review always covers the last closed week: a partial week would
            // turn a progress snapshot into an adherence judgement (no guilt).
            val reviewWeekStart = today.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY),
            ).minusWeeks(1)
            val weeklyReview = weeklyReview(
                weekStart = reviewWeekStart,
                today = today,
                summaries = summaries,
                zone = zone,
                completion = completion,
                intensity = intensity,
                intensitySessions = intensitySessions,
            )
            _uiState.value = TrendsUiState(
                isLoading = false,
                profile = profile,
                snapshot = signals.load,
                activityTrends = trendsCalculator.calculate(
                    activities = summaries,
                    today = today,
                    zoneId = zone,
                    weeks = period.weeks,
                    consistencyDays = period.consistencyDays,
                ),
                intensity = intensity,
                planCompletion = completion,
                weeklyReview = weeklyReview,
                period = period,
            )
        }
    }

    private suspend fun completion(today: LocalDate): PlanCompletion {
        val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStarts = (PLAN_COMPLETION_WEEKS - 1 downTo 0)
            .map { currentMonday.minusWeeks(it.toLong()) }
        // Event days never count as planned/missed sessions, in any state.
        val excludedDates = raceEventRepository.getDatesBetween(
            start = weekStarts.first(),
            end = weekStarts.last().plusDays(6),
        )
        return planCompletionCalculator.calculate(
            plans = planRepository.getWeeks(weekStarts),
            today = today,
            weeks = PLAN_COMPLETION_WEEKS,
            excludedDates = excludedDates,
        )
    }

    private suspend fun intensitySessions(
        summaries: List<ActivitySummary>,
        today: LocalDate,
        zone: ZoneId,
    ): List<IntensitySession> {
        val start = today.minusDays(INTENSITY_WINDOW_DAYS - 1L)
        return summaries
            .filter { it.sport == Sport.RUNNING || it.sport == Sport.TREADMILL }
            .mapNotNull { summary ->
                val date = summary.startTime.atZone(zone).toLocalDate()
                if (date.isBefore(start) || date.isAfter(today)) return@mapNotNull null
                val samples = activityRepository.openActivity(summary.id)?.imported?.samples.orEmpty()
                if (samples.isEmpty()) null else IntensitySession(date = date, samples = samples)
            }
    }

    /**
     * Consolidates the last closed week without a second training decision. The
     * calculator only receives values that Trends already computed; it never
     * re-reads a target or invents a section for missing input (FR-011).
     */
    private suspend fun weeklyReview(
        weekStart: LocalDate,
        today: LocalDate,
        summaries: List<ActivitySummary>,
        zone: ZoneId,
        completion: PlanCompletion,
        intensity: IntensityDistribution?,
        intensitySessions: List<IntensitySession>,
    ): WeeklyReview {
        val weekEnd = weekStart.plusDays(6)
        val plan = planRepository.getWeek(weekStart)
        val completionWeek = completion.weeks.firstOrNull { it.weekStart == weekStart }
        val checkIns = signalSource.allCheckIns()
        val metrics = signalSource.allMetrics()
        val readiness = signalSource.readinessHistory(today, REVIEW_HEALTH_WINDOW_DAYS)
        val executedMin = summaries
            .filter { it.sport == Sport.RUNNING || it.sport == Sport.TREADMILL }
            .filter { summary ->
                val date = summary.startTime.atZone(zone).toLocalDate()
                !date.isBefore(weekStart) && !date.isAfter(weekEnd)
            }
            .sumOf { it.durationSec }
            .let { seconds -> if (seconds <= 0) null else (seconds / 60.0).roundToInt().takeIf { it > 0 } }
        val plannedMin = plan
            ?.let { week ->
                week.sessions
                    .filter { it.type != WorkoutType.REST && !it.date.isBefore(week.createdOn) }
                    .mapNotNull { it.durationMin }
                    .sum()
                    .takeIf { it > 0 }
            }
        val hasPlan = plan?.sessions.orEmpty().any { it.type != WorkoutType.REST }
        val intensityWeek = intensity?.weeks?.firstOrNull { it.weekStart == weekStart }
        val weekIntensitySessions = if (intensityWeek == null) {
            0
        } else {
            intensitySessions.count { it.date in weekStart..weekEnd }.coerceAtLeast(1)
        }

        return weeklyReviewCalculator.calculate(
            weekStart = weekStart,
            today = today,
            hasPlan = hasPlan,
            completion = completionWeek,
            plannedDurationMin = if (hasPlan) plannedMin else null,
            executedDurationMin = executedMin,
            intensity = intensityWeek,
            intensitySessions = weekIntensitySessions,
            readinessDays = readiness,
            metrics = metrics,
            checkIns = checkIns,
        )
    }

    private companion object {
        const val INTENSITY_WINDOW_DAYS = 28
        const val PLAN_COMPLETION_WEEKS = 8

        /** Covers the last closed week (7–13 days back) with readiness/sleep. */
        const val REVIEW_HEALTH_WINDOW_DAYS = 14
    }
}
