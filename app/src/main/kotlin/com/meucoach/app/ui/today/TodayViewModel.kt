package com.meucoach.app.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meucoach.app.ui.activities.formatDateTime
import com.meucoach.app.ui.activities.formatDistance
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.app.ui.activities.paceLabel
import com.meucoach.app.ui.activities.sportLabel
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.load.LoadActivity
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.load.TrainingLoadEngine
import com.meucoach.core.domain.prescription.PrescriptionInput
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.SessionSwapper
import com.meucoach.core.domain.prescription.SwapResult
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WeeklyPlanInput
import com.meucoach.core.domain.prescription.WorkoutRecommender
import com.meucoach.core.domain.readiness.DailyMetricsReadinessBuilder
import com.meucoach.core.domain.readiness.ReadinessCalculator
import com.meucoach.core.domain.readiness.toSubjectiveCheckIn
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.HeatLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    checkInRepository: CheckInRepository,
    metricsRepository: DailyMetricsRepository,
    profileRepository: ProfileRepository,
    activityRepository: ActivityRepository,
    private val planRepository: TrainingPlanRepository,
) : ViewModel() {
    private val engine = TrainingLoadEngine()
    private val recommender = WorkoutRecommender()
    private val planGenerator = WeeklyPlanGenerator()
    private val currentWeekStart = LocalDate.now()
        .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    private data class PlanHistory(val previousWeek: WeeklyPlan?, val weekNumberInBlock: Int, val isReady: Boolean)
    private val planHistory = MutableStateFlow(PlanHistory(null, 1, isReady = false))

    init {
        viewModelScope.launch {
            planHistory.value = PlanHistory(
                previousWeek = planRepository.getWeek(currentWeekStart.minusWeeks(1)),
                weekNumberInBlock = planRepository.consecutiveWeeksBefore(currentWeekStart) + 1,
                isReady = true,
            )
        }
    }

    private data class TodayContext(
        val checkIn: DailyCheckIn?,
        val metrics: List<DailyMetrics>,
        val profile: AthleteProfile?,
        val load: LoadSnapshot?,
        val today: LocalDate,
        val weeklyLoadKm: Double,
        val kmByDay: Map<LocalDate, Double>,
        val recentActivity: RecentActivityUi?,
    )

    val uiState: StateFlow<TodayUiState> = combine(
        combine(
            checkInRepository.observe(LocalDate.now()),
            metricsRepository.observeAll(),
            profileRepository.observeProfile(),
            activityRepository.observeActivitySummaries(),
        ) { checkIn, metrics, profile, activities ->
            buildContext(checkIn, metrics, profile, activities)
        },
        planRepository.observeWeek(currentWeekStart),
        planHistory,
    ) { context, storedPlan, history ->
        todayState(
            checkIn = context.checkIn,
            metrics = context.metrics,
            profile = context.profile,
            load = context.load,
            today = context.today,
            plan = storedPlan ?: if (history.isReady) {
                context.profile?.let {
                    planGenerator.generate(
                        WeeklyPlanInput(
                            profile = it,
                            readiness = readinessResult(context.checkIn, context.metrics, context.today),
                            load = context.load,
                            weekStart = context.today,
                            previousWeek = history.previousWeek,
                            weekNumberInBlock = history.weekNumberInBlock,
                        ),
                    )
                }
            } else {
                null
            },
            kmByDay = context.kmByDay,
            weeklyLoadKm = context.weeklyLoadKm,
            recentActivity = context.recentActivity,
        )
    }
        .mapLatest { state ->
            val generated = state.plan ?: return@mapLatest state
            val stored = planRepository.getWeek(generated.weekStart)
            if (stored != null) {
                state.copy(plan = stored)
            } else {
                planRepository.saveWeek(generated)
                state
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = todayState(null, emptyList()),
        )

    private fun buildContext(
        checkIn: DailyCheckIn?,
        metrics: List<DailyMetrics>,
        profile: AthleteProfile?,
        activities: List<ActivitySummary>,
    ): TodayContext {
        val today = LocalDate.now()
        val load = profile?.let { buildLoad(activities, it, today) }
        val zone = profile?.let { runCatching { java.time.ZoneId.of(it.timezone) }.getOrNull() }
            ?: java.time.ZoneId.systemDefault()
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val weekKm = activities
            .filter {
                val date = it.startTime.atZone(zone).toLocalDate()
                !date.isBefore(weekStart) && !date.isAfter(weekStart.plusDays(6))
            }
            .sumOf { it.distance.kilometers }
        val kmByDay = activities.groupBy { it.startTime.atZone(zone).toLocalDate() }
            .mapValues { (_, rows) -> rows.sumOf { it.distance.kilometers } }
        val recent = activities.maxByOrNull { it.startTime }?.let { summary ->
            RecentActivityUi(
                title = sportLabel(summary.sport),
                subtitle = formatDateTime(summary.startTime, zone),
                distance = "${formatDistance(summary.distance)} km",
                duration = formatDuration(summary.durationSec),
                pace = summary.averagePace?.let { "${paceLabel(it)} /km" } ?: "—",
                zone = "—",
            )
        }
        return TodayContext(
            checkIn = checkIn,
            metrics = metrics,
            profile = profile,
            load = load,
            today = today,
            weeklyLoadKm = weekKm,
            kmByDay = kmByDay,
            recentActivity = recent,
        )
    }

    fun completeSession(date: LocalDate) {
        viewModelScope.launch {
            planRepository.updateStatus(currentWeekStart, date, SessionStatus.DONE)
        }
    }

    fun skipSession(date: LocalDate, reason: String) {
        viewModelScope.launch {
            planRepository.updateStatus(
                weekStart = currentWeekStart,
                date = date,
                status = SessionStatus.SKIPPED,
                skipReason = reason.trim().ifBlank { null },
            )
        }
    }

    fun swapSession(firstDate: LocalDate, secondDate: LocalDate, today: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            val plan = planRepository.getWeek(currentWeekStart) ?: return@launch
            val result = SessionSwapper.swap(plan, firstDate, secondDate, today)
            if (result is SwapResult.Applied) {
                planRepository.updateSessions(result.plan.weekStart, result.plan.sessions)
            }
        }
    }

    private fun buildLoad(activities: List<ActivitySummary>, profile: AthleteProfile, today: LocalDate): LoadSnapshot {
        val zone = runCatching { java.time.ZoneId.of(profile.timezone) }.getOrNull()
            ?: java.time.ZoneId.systemDefault()
        return engine.calculate(
            activities = activities.map { summary ->
                LoadActivity(
                    date = summary.startTime.atZone(zone).toLocalDate(),
                    durationSec = summary.durationSec,
                    averageHeartRate = summary.averageHeartRate,
                    averagePace = summary.averagePace,
                )
            },
            profile = profile,
            today = today,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun readinessResult(
    checkIn: DailyCheckIn?,
    metrics: List<DailyMetrics>,
    today: LocalDate,
): com.meucoach.core.domain.readiness.ReadinessResult {
    val inputs = DailyMetricsReadinessBuilder.build(metrics, today)
    val signals = inputs.signals.copy(
        illnessOrInjury = inputs.signals.illnessOrInjury || checkIn?.illnessOrInjury == true,
    )
    return ReadinessCalculator.calculate(
        checkIn = checkIn?.toSubjectiveCheckIn(),
        sleep = inputs.sleep,
        hrv = inputs.hrv,
        restingHeartRate = inputs.restingHeartRate,
        signals = signals,
    )
}

@Composable
fun TodayRoute(
    onDemoAction: (String) -> Unit,
    onCheckInClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(
        onDemoAction = onDemoAction,
        modifier = modifier,
        state = state,
        onCheckInClick = onCheckInClick,
        onCompleteSession = viewModel::completeSession,
        onSkipSession = viewModel::skipSession,
        onSwapSession = viewModel::swapSession,
    )
}

/** Overlays real readiness on the demo shell until prescription exists. */
internal fun todayState(
    checkIn: DailyCheckIn?,
    metrics: List<DailyMetrics> = emptyList(),
    profile: AthleteProfile? = null,
    activities: List<ActivitySummary> = emptyList(),
    load: LoadSnapshot? = null,
    today: LocalDate = LocalDate.now(),
    plan: WeeklyPlan? = null,
    kmByDay: Map<LocalDate, Double> = emptyMap(),
    weeklyLoadKm: Double? = null,
    recentActivity: RecentActivityUi? = null,
): TodayUiState {
    val inputs = DailyMetricsReadinessBuilder.build(metrics, today)
    val signals = inputs.signals.copy(
        illnessOrInjury = inputs.signals.illnessOrInjury || checkIn?.illnessOrInjury == true,
    )
    val result = ReadinessCalculator.calculate(
        checkIn = checkIn?.toSubjectiveCheckIn(),
        sleep = inputs.sleep,
        hrv = inputs.hrv,
        restingHeartRate = inputs.restingHeartRate,
        signals = signals,
    )
    val warning = result.level != com.meucoach.core.model.ReadinessLevel.GREEN
    val base = demoTodayUiState(today)
    val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val dayLabels = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    val days = (0..6).map { offset ->
        val date = weekStart.plusDays(offset.toLong())
        DayLoadUi(
            label = dayLabels[offset],
            loadKm = kmByDay[date] ?: 0.0,
            isToday = date == today,
        )
    }
    val recommendation = profile?.let {
        WorkoutRecommender().recommend(
            PrescriptionInput(
                profile = it,
                readiness = result,
                load = load,
                today = today,
                heatLevel = checkIn?.heatLevel ?: HeatLevel.NONE,
            ),
        )
    }
    val explanation = if (profile != null && recommendation != null) {
        buildExplanation(
            profile = profile,
            checkIn = checkIn,
            inputs = inputs,
            result = result,
            recommendation = recommendation,
            load = load,
        )
    } else {
        null
    }
    return base.copy(
        readinessScore = result.score,
        readinessLevel = result.level,
        readinessConfidence = result.confidence,
        readinessSignals = buildSignals(
            checkIn = checkIn,
            inputs = inputs,
            warning = warning,
            illness = signals.illnessOrInjury,
        ),
        workout = recommendation?.toWorkoutUi() ?: base.workout,
        explanation = explanation,
        ctl = load?.ctl?.roundToInt() ?: base.ctl,
        atl = load?.atl?.roundToInt() ?: base.atl,
        tsb = load?.tsb?.roundToInt() ?: base.tsb,
        hasCheckedIn = checkIn != null,
        plan = plan,
        days = if (kmByDay.isEmpty() && weeklyLoadKm == null) base.days else days,
        weeklyLoadKm = weeklyLoadKm ?: base.weeklyLoadKm,
        recentActivity = recentActivity ?: base.recentActivity,
        athleteName = if (profile == null) base.athleteName else "",
        weather = profile?.city ?: "",
    )
}

private fun com.meucoach.core.domain.prescription.WorkoutRecommendation.toWorkoutUi() = WorkoutUi(
    title = title,
    zone = if (type == com.meucoach.core.domain.prescription.WorkoutType.EASY) "Z2" else "—",
    duration = durationMin?.toString() ?: "—",
    distance = distanceKm?.let { String.format(PT_BR, "%.1f", it) } ?: "—",
    pace = pace?.let { "${it.fast.format()}–${it.slow.format()}" } ?: "—",
    heartRate = heartRate?.let { target ->
        target.maxBpm?.let { max -> "${target.minBpm}–$max" } ?: "${target.minBpm}+"
    } ?: "—",
    rationale = rationale,
)

private fun buildSignals(
    checkIn: DailyCheckIn?,
    inputs: com.meucoach.core.domain.readiness.ReadinessInputs,
    warning: Boolean,
    illness: Boolean,
): List<ReadinessSignalUi> = buildList {
    inputs.sleep?.let { sleep ->
        add(
            ReadinessSignalUi(
                label = "Sono",
                value = formatSleepDuration(sleep.durationMin),
                hint = if (sleep.durationMin < 360) "abaixo de 6 h" else "ok",
                tone = if (sleep.durationMin < 360) SignalTone.WARNING else SignalTone.NEUTRAL,
            ),
        )
    }
    inputs.hrv?.let { hrv ->
        val z = ln(hrv.rmssd7dMs / hrv.baseline60dMs) / hrv.sdLnMs
        add(
            ReadinessSignalUi(
                label = "HRV",
                value = String.format(PT_BR, "%+.1f SWC", z),
                hint = "${hrv.validNights} noites válidas",
                tone = when {
                    z < -1.0 -> SignalTone.WARNING
                    z > 0.0 -> SignalTone.POSITIVE
                    else -> SignalTone.NEUTRAL
                },
            ),
        )
    }
    inputs.restingHeartRate?.let { rhr ->
        val delta = rhr.average7dBpm - rhr.baseline30dBpm
        add(
            ReadinessSignalUi(
                label = "FC repouso",
                value = String.format(PT_BR, "%+.0f bpm", delta),
                hint = "vs baseline 30d",
                tone = when {
                    delta >= 7.0 -> SignalTone.WARNING
                    delta <= -3.0 -> SignalTone.POSITIVE
                    else -> SignalTone.NEUTRAL
                },
            ),
        )
    }
    if (isEmpty()) {
        add(
            ReadinessSignalUi(
                label = "Check-in",
                value = checkIn?.let { "${it.hooperSum}/20" } ?: "Pendente",
                hint = when {
                    checkIn == null -> "15 s para medir o dia"
                    illness -> "sinal de alerta"
                    warning -> "revisar o dia"
                    else -> "Hooper · menor é melhor"
                },
                tone = if (warning) SignalTone.WARNING else SignalTone.NEUTRAL,
            ),
        )
    } else if (checkIn != null && illness) {
        add(
            ReadinessSignalUi(
                label = "Alerta",
                value = "Doença/lesão",
                hint = "sem qualidade",
                tone = SignalTone.WARNING,
            ),
        )
    }
}

private fun formatSleepDuration(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) "${hours}h${rest.toString().padStart(2, '0')}" else "${rest}min"
}

private val PT_BR = Locale.forLanguageTag("pt-BR")
