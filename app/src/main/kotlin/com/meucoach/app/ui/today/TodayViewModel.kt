package com.meucoach.app.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meucoach.app.application.GetTodayPrescription
import com.meucoach.app.application.TodayPrescriptionRequest
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
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
import com.meucoach.core.domain.load.ActivitySourceStatus
import com.meucoach.core.domain.load.LoadActivity
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.load.TrainingLoadEngine
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.DailyPrescriptionEngine
import com.meucoach.core.domain.prescription.DailyPrescriptionInput
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.SessionSwapper
import com.meucoach.core.domain.prescription.SwapResult
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WeeklyPlanInput
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
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
    private val timeProvider: TimeProvider,
    private val getTodayPrescription: GetTodayPrescription,
) : ViewModel() {
    private val planGenerator = WeeklyPlanGenerator()
    private data class PlanHistory(
        val previousWeek: WeeklyPlan?,
        val weekNumberInBlock: Int,
        val isReady: Boolean,
        val weekStart: LocalDate,
    )

    private data class ResolvedDay(val context: TodayContext, val plan: WeeklyPlan?)

    private data class TodayContext(
        val checkIn: DailyCheckIn?,
        val metrics: List<DailyMetrics>,
        val profile: AthleteProfile?,
        val load: LoadSnapshot?,
        val today: LocalDate,
        val weeklyLoadKm: Double,
        val lastActivityImportAt: java.time.Instant?,
        val kmByDay: Map<LocalDate, Double>,
        val recentActivity: RecentActivityUi?,
    )

    private val planHistory = timeProvider.today
        .map { it.weekStart() }
        .distinctUntilChanged()
        .flatMapLatest { weekStart ->
            flow {
                emit(
                    PlanHistory(
                        previousWeek = planRepository.getWeek(weekStart.minusWeeks(1)),
                        weekNumberInBlock = planRepository.consecutiveWeeksBefore(weekStart) + 1,
                        isReady = true,
                        weekStart = weekStart,
                    ),
                )
            }
        }

    private val context = timeProvider.today
        .flatMapLatest { today ->
            val checkedAt = timeProvider.now()
            combine(
                checkInRepository.observe(today),
                metricsRepository.observeAll(),
                profileRepository.observeProfile(),
                activityRepository.observeActivitySummaries(),
                activityRepository.observeLastImportedAt(),
            ) { checkIn, metrics, profile, activities, lastImportedAt ->
                buildContext(today, checkIn, metrics, profile, activities, lastImportedAt, checkedAt)
            }
        }

    private val storedPlan = timeProvider.today
        .map { it.weekStart() }
        .distinctUntilChanged()
        .flatMapLatest { weekStart -> planRepository.observeWeek(weekStart) }

    private val resolvedDay = combine(context, storedPlan, planHistory) { context, plan, history ->
        val weekStart = context.today.weekStart()
        val historyReady = history.isReady && history.weekStart == weekStart
        val planned = plan ?: if (historyReady) {
            context.profile?.let {
                planGenerator.generate(
                    WeeklyPlanInput(
                        profile = it,
                        readiness = readinessResult(context.checkIn, context.metrics, context.today),
                        load = context.load,
                        weekStart = weekStart,
                        today = context.today,
                        previousWeek = history.previousWeek,
                        weekNumberInBlock = history.weekNumberInBlock,
                    ),
                )
            }
        } else {
            null
        }
        ResolvedDay(context = context, plan = planned)
    }

    val uiState: StateFlow<TodayUiState> = resolvedDay
        .mapLatest { resolved ->
            val context = resolved.context
            val plan = resolved.plan?.let { generated ->
                planRepository.getWeek(generated.weekStart) ?: generated.also { planRepository.saveWeek(it) }
            }
            val readiness = readinessResult(context.checkIn, context.metrics, context.today)
            val prescription = context.profile?.let { profile ->
                getTodayPrescription(
                    TodayPrescriptionRequest(
                        date = context.today,
                        profile = profile,
                        readiness = readiness,
                        load = context.load,
                        heatLevel = context.checkIn?.heatLevel ?: HeatLevel.NONE,
                        plan = plan,
                    ),
                )
            }
            todayState(
                checkIn = context.checkIn,
                metrics = context.metrics,
                profile = context.profile,
                load = context.load,
                today = context.today,
                now = timeProvider.now().atZone(timeProvider.zoneId.value).toLocalTime(),
                plan = plan,
                prescription = prescription,
                kmByDay = context.kmByDay,
                weeklyLoadKm = context.weeklyLoadKm,
                recentActivity = context.recentActivity,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = todayState(
                checkIn = null,
                today = timeProvider.currentDate(),
                now = timeProvider.now().atZone(timeProvider.zoneId.value).toLocalTime(),
            ),
        )

    private fun buildContext(
        today: LocalDate,
        checkIn: DailyCheckIn?,
        metrics: List<DailyMetrics>,
        profile: AthleteProfile?,
        activities: List<ActivitySummary>,
        lastImportedAt: java.time.Instant?,
        checkedAt: java.time.Instant,
    ): TodayContext {
        val load = profile?.let {
            buildLoadSnapshot(
                activities = activities,
                profile = it,
                today = today,
                sourceStatus = ActivitySourceStatus(lastImportedAt = lastImportedAt, checkedAt = checkedAt),
            )
        }
        val zone = timeProvider.zoneId.value
        val weekStart = today.weekStart()
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
                id = summary.id,
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
            lastActivityImportAt = lastImportedAt,
            kmByDay = kmByDay,
            recentActivity = recent,
        )
    }

    fun completeSession(date: LocalDate) {
        viewModelScope.launch {
            planRepository.updateStatus(date.weekStart(), date, SessionStatus.DONE)
        }
    }

    fun skipSession(date: LocalDate, reason: String) {
        viewModelScope.launch {
            planRepository.updateStatus(
                weekStart = date.weekStart(),
                date = date,
                status = SessionStatus.SKIPPED,
                skipReason = reason.trim().ifBlank { null },
            )
        }
    }

    fun swapSession(firstDate: LocalDate, secondDate: LocalDate, today: LocalDate) {
        viewModelScope.launch {
            val plan = planRepository.getWeek(firstDate.weekStart()) ?: return@launch
            val result = SessionSwapper.swap(plan, firstDate, secondDate, today)
            if (result is SwapResult.Applied) {
                planRepository.updateSessions(result.plan.weekStart, result.plan.sessions)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun buildLoadSnapshot(
    activities: List<ActivitySummary>,
    profile: AthleteProfile,
    today: LocalDate,
    sourceStatus: ActivitySourceStatus,
): LoadSnapshot {
    val zone = runCatching { java.time.ZoneId.of(profile.timezone) }.getOrNull()
        ?: java.time.ZoneId.systemDefault()
    return TrainingLoadEngine().calculate(
        activities = activities.map { summary ->
            LoadActivity(
                date = summary.startTime.atZone(zone).toLocalDate(),
                durationSec = summary.durationSec,
                averageHeartRate = summary.averageHeartRate,
                averagePace = summary.averagePace,
                startTime = summary.startTime,
            )
        },
        profile = profile,
        today = today,
        sourceStatus = sourceStatus,
    )
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
    onTrendsClick: () -> Unit,
    onActivityClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onImportClick: () -> Unit,
    onHealthClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
    checklistViewModel: FirstRunChecklistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val checklistState by checklistViewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        checklistViewModel.refreshHealth()
        onPauseOrDispose { }
    }
    TodayScreen(
        onDemoAction = onDemoAction,
        modifier = modifier,
        state = state,
        onCheckInClick = onCheckInClick,
        onTrendsClick = onTrendsClick,
        onActivityClick = onActivityClick,
        onProfileClick = onProfileClick,
        onImportClick = onImportClick,
        onHealthClick = onHealthClick,
        checklistState = checklistState,
        onChecklistDismiss = checklistViewModel::dismiss,
        onCompleteSession = viewModel::completeSession,
        onSkipSession = viewModel::skipSession,
        onSwapSession = { first, second -> viewModel.swapSession(first, second, state.today) },
    )
}

/** Overlays the persisted day decision on the demo shell when there is no prescription yet. */
internal fun todayState(
    checkIn: DailyCheckIn?,
    metrics: List<DailyMetrics> = emptyList(),
    profile: AthleteProfile? = null,
    activities: List<ActivitySummary> = emptyList(),
    load: LoadSnapshot? = null,
    today: LocalDate,
    now: LocalTime = LocalTime.NOON,
    plan: WeeklyPlan? = null,
    prescription: DailyPrescription? = null,
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
    val base = demoTodayUiState(today, now)
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
    // Preview/test/tutorial fallback. Production always receives the persisted
    // prescription from GetTodayPrescription, which is the single authority.
    val dayPrescription =
        prescription ?: profile?.let { buildPreviewPrescription(it, checkIn, result, load, today, plan) }
    val explanation = if (profile != null && dayPrescription != null) {
        buildExplanation(
            checkIn = checkIn,
            inputs = inputs,
            result = result,
            prescription = dayPrescription,
            load = load,
        )
    } else {
        null
    }
    val readinessExplanation = buildReadinessExplanation(
        checkIn = checkIn,
        inputs = inputs,
        result = result,
    )
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
        workout = dayPrescription?.toWorkoutUi() ?: base.workout,
        prescription = dayPrescription,
        explanation = explanation,
        readinessExplanation = readinessExplanation,
        ctl = load?.ctl?.roundToInt(),
        atl = load?.atl?.roundToInt(),
        tsb = load?.tsb?.roundToInt(),
        acwr = load?.acwr,
        loadDataState = load?.dataState,
        hasCheckedIn = checkIn != null,
        plan = plan,
        days = days,
        weeklyLoadKm = weeklyLoadKm,
        recentActivity = recentActivity,
        athleteName = profile?.name.orEmpty(),
        avatarPath = profile?.avatarPath,
        weather = profile?.city ?: "",
    )
}

private fun buildPreviewPrescription(
    profile: AthleteProfile,
    checkIn: DailyCheckIn?,
    readiness: com.meucoach.core.domain.readiness.ReadinessResult,
    load: LoadSnapshot?,
    today: LocalDate,
    plan: WeeklyPlan?,
): DailyPrescription {
    val planned = plan?.sessions?.firstOrNull { it.date == today }
    return DailyPrescriptionEngine().prescribe(
        DailyPrescriptionInput(
            date = today,
            profile = profile,
            readiness = readiness,
            load = load,
            heatLevel = checkIn?.heatLevel ?: HeatLevel.NONE,
            plannedSession = planned,
            plannedSessionId = if (plan != null && planned != null) {
                "${plan.weekStart}-${planned.date}"
            } else {
                null
            },
            planConfidence = plan?.confidence,
        ),
    )
}

private fun DailyPrescription.toWorkoutUi() = WorkoutUi(
    title = title,
    zone = when (prescribedType) {
        com.meucoach.core.domain.prescription.WorkoutType.EASY,
        com.meucoach.core.domain.prescription.WorkoutType.LONG,
        -> "Z2 · leve"

        com.meucoach.core.domain.prescription.WorkoutType.QUALITY -> "Z3–Z4"

        com.meucoach.core.domain.prescription.WorkoutType.REST -> "—"
    },
    duration = prescribedDurationMin?.toString() ?: "—",
    distance = targets.distanceKm?.let { String.format(PT_BR, "%.1f", it) } ?: "—",
    pace = targets.pace?.let { "${it.fast.format()}–${it.slow.format()}" } ?: "—",
    heartRate = targets.heartRate?.let { target ->
        target.maxBpm?.let { max -> "${target.minBpm}–$max" } ?: "${target.minBpm}+"
    } ?: "—",
    rationale = rationale,
    rpe = targets.rpe,
    canTrain = !isRest,
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
                hint = if (sleep.durationMin < 360) "abaixo de 6 h" else null,
                tone = if (sleep.durationMin < 360) SignalTone.WARNING else SignalTone.NEUTRAL,
            ),
        )
    }
    inputs.hrv?.let { hrv ->
        val z = ln(hrv.rmssd7dMs / hrv.baseline60dMs) / hrv.sdLnMs
        add(
            ReadinessSignalUi(
                label = "Variabilidade",
                value = when {
                    z < -1.0 -> "abaixo"
                    z > 0.0 -> "acima"
                    else -> "média"
                },
                hint = "${hrv.validNights} noites",
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
        val rounded = delta.roundToInt()
        add(
            ReadinessSignalUi(
                label = "FC de repouso",
                value = if (rounded == 0) "estável" else String.format(PT_BR, "%+d bpm", rounded),
                hint = if (rounded == 0) null else "média mensal",
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
                    else -> "menor é melhor"
                },
                tone = if (warning) SignalTone.WARNING else SignalTone.NEUTRAL,
            ),
        )
    } else if (checkIn != null && illness) {
        add(
            ReadinessSignalUi(
                label = "Alerta",
                value = "Doença/lesão",
                hint = "sem treino forte",
                tone = SignalTone.WARNING,
            ),
        )
    }
}

private val PT_BR = Locale.forLanguageTag("pt-BR")
