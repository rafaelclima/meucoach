package com.meucoach.app.ui.tutorial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.application.buildLoadSnapshot
import com.meucoach.app.application.readinessResult
import com.meucoach.app.firstrun.FirstActionType
import com.meucoach.app.firstrun.FirstRunMetricsStore
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.app.ui.common.readinessLabel
import com.meucoach.app.ui.profile.availabilityLabel
import com.meucoach.app.ui.profile.goalLabel
import com.meucoach.app.ui.today.TodayUiState
import com.meucoach.app.ui.today.WorkoutUi
import com.meucoach.app.ui.today.todayState
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.load.ActivitySourceStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WeeklyPlanInput
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

data class TutorialSessionPreview(
    val dayLabel: String,
    val title: String,
    val durationMin: Int?,
    val type: WorkoutType,
)

data class TutorialPlanPreview(
    val sessions: List<TutorialSessionPreview>,
    val weeklyMinutes: Int,
    val qualityCount: Int,
)

data class TutorialWorkoutPreview(val title: String, val zone: String, val durationMin: Int?, val rationale: String)

data class TutorialReadinessPreview(
    val score: Int,
    val level: ReadinessLevel,
    val label: String,
    val confidence: DataConfidence,
)

data class TutorialUiState(
    val seen: Boolean = false,
    val isLoading: Boolean = true,
    val name: String? = null,
    val goalLabel: String? = null,
    val availabilityLabel: String? = null,
    val planPreview: TutorialPlanPreview? = null,
    val workoutPreview: TutorialWorkoutPreview? = null,
    val readinessPreview: TutorialReadinessPreview? = null,
    val hasCheckedInToday: Boolean = false,
    val healthConnected: Boolean = false,
    val hasActivities: Boolean = false,
    val error: String? = null,
)

/**
 * Loads the same real data the Today screen uses, so the first scene can reveal an actual
 * plan instead of a decorative example. The tutorial never invents a number: when a signal
 * is missing, the UI shows an honest fallback.
 */
@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val store: TutorialStore,
    private val metrics: FirstRunMetricsStore,
    private val profileRepository: ProfileRepository,
    private val checkInRepository: CheckInRepository,
    private val metricsRepository: DailyMetricsRepository,
    private val activityRepository: ActivityRepository,
    private val planRepository: TrainingPlanRepository,
    private val healthSource: HealthDailySource,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val planGenerator = WeeklyPlanGenerator()
    private val _uiState = MutableStateFlow(TutorialUiState(seen = store.isIntroDone()))
    val uiState: StateFlow<TutorialUiState> = _uiState.asStateFlow()

    init {
        if (!store.isIntroDone()) metrics.markTutorialStarted()
        load()
    }

    fun markSeen() {
        store.markIntroDone()
        metrics.markTutorialCompleted()
        _uiState.update { it.copy(seen = true) }
    }

    fun markSkipped() {
        store.markIntroDone()
        metrics.markTutorialSkipped()
        _uiState.update { it.copy(seen = true) }
    }

    fun onStepChanged(step: Int) {
        metrics.markTutorialStep(step)
    }

    fun markFirstAction(type: FirstActionType) {
        metrics.markFirstAction(type)
    }

    fun retry() {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { buildState() }.fold(
                onSuccess = { loaded ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = loaded.profile.name,
                            goalLabel = goalLabel(loaded.profile),
                            availabilityLabel = availabilityLabel(loaded.profile),
                            planPreview = loaded.plan.toPreview(),
                            workoutPreview = loaded.today.workout.toPreview(),
                            readinessPreview = TutorialReadinessPreview(
                                score = loaded.today.readinessScore,
                                level = loaded.today.readinessLevel,
                                label = readinessLabel(loaded.today.readinessLevel),
                                confidence = loaded.today.readinessConfidence,
                            ),
                            hasCheckedInToday = loaded.checkIn != null,
                            hasActivities = loaded.activities.isNotEmpty(),
                            error = null,
                        )
                    }
                    val connected = runCatching {
                        healthSource.status() == HealthConnectStatus.AVAILABLE &&
                            healthSource.grantedPermissions().containsAll(healthSource.requiredPermissions)
                    }.getOrDefault(false)
                    _uiState.update { it.copy(healthConnected = connected) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Não consegui carregar seu plano agora.",
                        )
                    }
                },
            )
        }
    }

    private suspend fun buildState(): LoadedState {
        val profile = profileRepository.getProfile() ?: error("Perfil não encontrado.")
        val today = timeProvider.currentDate()
        val checkIn = checkInRepository.get(today)
        val metrics = metricsRepository.getAll()
        val activities = activityRepository.listActivitySummaries()
        val readiness = readinessResult(checkIn, metrics, today)
        val load = buildLoadSnapshot(
            activities = activities,
            profile = profile,
            today = today,
            sourceStatus = ActivitySourceStatus(
                lastImportedAt = activityRepository.lastImportedAt(),
                checkedAt = timeProvider.now(),
            ),
        )
        val weekStart = today.weekStart()
        val previousWeek = planRepository.getWeek(weekStart.minusWeeks(1))
        val weekNumber = planRepository.consecutiveWeeksBefore(weekStart) + 1
        val plan = planRepository.getWeek(weekStart) ?: planGenerator.generate(
            WeeklyPlanInput(
                profile = profile,
                readiness = readiness,
                load = load,
                weekStart = weekStart,
                today = today,
                previousWeek = previousWeek,
                weekNumberInBlock = weekNumber,
            ),
        ).also { planRepository.saveWeek(it) }
        val todayUi = todayState(
            checkIn = checkIn,
            metrics = metrics,
            profile = profile,
            activities = activities,
            load = load,
            today = today,
            now = timeProvider.now().atZone(timeProvider.zoneId.value).toLocalTime(),
            plan = plan,
        )
        return LoadedState(
            profile = profile,
            checkIn = checkIn,
            activities = activities,
            plan = plan,
            today = todayUi,
        )
    }

    private data class LoadedState(
        val profile: AthleteProfile,
        val checkIn: DailyCheckIn?,
        val activities: List<ActivitySummary>,
        val plan: WeeklyPlan,
        val today: TodayUiState,
    )
}

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

private fun WeeklyPlan.toPreview(): TutorialPlanPreview = TutorialPlanPreview(
    sessions = sessions
        .sortedBy { it.date }
        .map { session ->
            TutorialSessionPreview(
                dayLabel = session.date.dayOfWeek
                    .getDisplayName(TextStyle.SHORT, PT_BR)
                    .trimEnd('.'),
                title = session.title,
                durationMin = session.durationMin,
                type = session.type,
            )
        },
    weeklyMinutes = weeklyMinutes,
    qualityCount = qualityCount,
)

private fun WorkoutUi.toPreview(): TutorialWorkoutPreview = TutorialWorkoutPreview(
    title = title,
    zone = zone,
    durationMin = duration.toIntOrNull(),
    rationale = rationale,
)
