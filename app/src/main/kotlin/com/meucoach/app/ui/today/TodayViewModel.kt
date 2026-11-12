package com.meucoach.app.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meucoach.app.application.CompleteSession
import com.meucoach.app.application.CompleteSessionRequest
import com.meucoach.app.application.CompleteSessionResult
import com.meucoach.app.application.GetTodayDecision
import com.meucoach.app.application.ReplaceManualCompletion
import com.meucoach.app.application.ReplaceManualCompletionRequest
import com.meucoach.app.application.ReplaceManualCompletionResult
import com.meucoach.app.application.TodayDecisionRequest
import com.meucoach.app.application.UndoRaceAutoCompletion
import com.meucoach.app.application.buildLoadSnapshot
import com.meucoach.app.application.readinessResult
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.app.ui.activities.formatDateTime
import com.meucoach.app.ui.activities.formatDistance
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.app.ui.activities.paceLabel
import com.meucoach.app.ui.activities.sportLabel
import com.meucoach.core.data.db.entity.plannedSessionId
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.briefing.SessionBriefingEngine
import com.meucoach.core.domain.load.ActivitySourceStatus
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.CompletedRace
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.DailyPrescriptionEngine
import com.meucoach.core.domain.prescription.DailyPrescriptionInput
import com.meucoach.core.domain.prescription.RacePlan
import com.meucoach.core.domain.prescription.RecoveryWindow
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionCompletionRules
import com.meucoach.core.domain.prescription.SessionMatcher
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.SessionSwapper
import com.meucoach.core.domain.prescription.SwapResult
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WeeklyPlanInput
import com.meucoach.core.domain.readiness.DailyMetricsReadinessBuilder
import com.meucoach.core.domain.readiness.ReadinessCalculator
import com.meucoach.core.domain.readiness.toSubjectiveCheckIn
import com.meucoach.core.domain.today.TodayDecision
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.HeatLevel
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import com.meucoach.core.model.Sport
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
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    checkInRepository: CheckInRepository,
    metricsRepository: DailyMetricsRepository,
    profileRepository: ProfileRepository,
    private val activityRepository: ActivityRepository,
    private val planRepository: TrainingPlanRepository,
    private val executionRepository: SessionExecutionRepository,
    private val raceEventRepository: RaceEventRepository,
    private val completeSession: CompleteSession,
    private val replaceManualCompletion: ReplaceManualCompletion,
    private val undoRaceAutoCompletion: UndoRaceAutoCompletion,
    private val timeProvider: TimeProvider,
    private val getTodayDecision: GetTodayDecision,
) : ViewModel() {
    private val planGenerator = WeeklyPlanGenerator()
    private val matcher = SessionMatcher()
    private val completionMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private data class PlanHistory(
        val previousWeek: WeeklyPlan?,
        val recentWeeks: List<WeeklyPlan>,
        val weekNumberInBlock: Int,
        val isReady: Boolean,
        val weekStart: LocalDate,
    )

    private data class ResolvedDay(
        val context: TodayContext,
        val plan: WeeklyPlan?,
        val events: List<RaceEvent>,
        val recovery: RecoveryWindow?,
    )

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
        val activities: List<ActivitySummary>,
        val linkedActivityIds: Set<String>,
    )

    private val planHistory = timeProvider.today
        .map { it.weekStart() }
        .distinctUntilChanged()
        .flatMapLatest { weekStart ->
            flow {
                val recentWeeks = planRepository.getWeeks((1L..4L).map { weekStart.minusWeeks(it) })
                emit(
                    PlanHistory(
                        previousWeek = recentWeeks.firstOrNull { it.weekStart == weekStart.minusWeeks(1) },
                        recentWeeks = recentWeeks,
                        weekNumberInBlock = planRepository.consecutiveWeeksBefore(weekStart) + 1,
                        isReady = true,
                        weekStart = weekStart,
                    ),
                )
            }
        }

    private data class ContextInputs(
        val checkIn: DailyCheckIn?,
        val metrics: List<DailyMetrics>,
        val profile: AthleteProfile?,
        val activities: List<ActivitySummary>,
        val lastImportedAt: java.time.Instant?,
    )

    private val context = timeProvider.today
        .flatMapLatest { today ->
            val checkedAt = timeProvider.now()
            val inputs = combine(
                checkInRepository.observe(today),
                metricsRepository.observeAll(),
                profileRepository.observeProfile(),
                activityRepository.observeActivitySummaries(),
                activityRepository.observeLastImportedAt(),
            ) { checkIn, metrics, profile, activities, lastImportedAt ->
                ContextInputs(checkIn, metrics, profile, activities, lastImportedAt)
            }
            combine(inputs, executionRepository.observeLinkedActivityIds()) { base, linkedActivityIds ->
                buildContext(
                    today = today,
                    checkIn = base.checkIn,
                    metrics = base.metrics,
                    profile = base.profile,
                    activities = base.activities,
                    lastImportedAt = base.lastImportedAt,
                    linkedActivityIds = linkedActivityIds,
                    checkedAt = checkedAt,
                )
            }
        }

    private val storedPlan = timeProvider.today
        .map { it.weekStart() }
        .distinctUntilChanged()
        .flatMapLatest { weekStart -> planRepository.observeWeek(weekStart) }

    private val events = raceEventRepository.observeAll()

    private val resolvedDay = combine(context, storedPlan, planHistory, events) { context, plan, history, events ->
        val weekStart = context.today.weekStart()
        val historyReady = history.isReady && history.weekStart == weekStart
        // ADR 0037: only completed races start a recovery window; the latest one
        // applies while it intersects the visible week.
        val recovery = RecoveryWindow.covering(
            races = events.mapNotNull(CompletedRace::from),
            start = weekStart,
            end = weekStart.plusDays(6),
        )
        val planned = plan ?: if (historyReady) {
            context.profile?.let {
                val eventDates = events.map { event -> event.targetDate }.toSet()
                val race = events
                    .filter { event -> event.status == RaceStatus.PLANNED }
                    .maxByOrNull { event -> event.updatedAt }
                    ?.let(RacePlan::from)
                planGenerator.generate(
                    WeeklyPlanInput(
                        profile = it,
                        readiness = readinessResult(context.checkIn, context.metrics, context.today),
                        load = context.load,
                        weekStart = weekStart,
                        today = context.today,
                        previousWeek = history.previousWeek,
                        recentWeeks = history.recentWeeks,
                        weekNumberInBlock = history.weekNumberInBlock,
                        race = race,
                        eventDates = eventDates,
                        recovery = recovery,
                    ),
                )
            }
        } else {
            null
        }
        ResolvedDay(context = context, plan = planned, events = events, recovery = recovery)
    }

    private val dayState = resolvedDay
        .mapLatest { resolved ->
            val context = resolved.context
            val plan = resolved.plan?.let { generated ->
                planRepository.getWeek(generated.weekStart) ?: generated.also { planRepository.saveWeek(it) }
            }
            val readiness = readinessResult(context.checkIn, context.metrics, context.today)
            // Competition wins and prevents any prescription calculation/persist
            // for the race day; the resolver owns the single branch.
            val decision = getTodayDecision(
                TodayDecisionRequest(
                    date = context.today,
                    profile = context.profile,
                    readiness = readiness,
                    load = context.load,
                    heatLevel = context.checkIn?.heatLevel ?: HeatLevel.NONE,
                    plan = plan,
                    recovery = resolved.recovery,
                ),
            )
            val prescription = (decision as? TodayDecision.Training)?.prescription
            val competition = decision is TodayDecision.Competition
            closeBlockedSessionIfNeeded(
                plan = plan,
                prescription = prescription,
                today = context.today,
                illnessOrInjury = context.checkIn?.illnessOrInjury == true ||
                    com.meucoach.core.domain.readiness.ReadinessFlag.ILLNESS_OR_INJURY in readiness.flags,
            )
            val suggestions = suggestionsFor(
                plan = plan,
                prescription = prescription,
                context = context,
                zone = timeProvider.zoneId.value,
                competition = competition,
            )
            val todayExecution = if (competition) {
                null
            } else {
                plan?.sessions
                    ?.firstOrNull { it.date == context.today }
                    ?.let { session -> executionUi(plan, session, context) }
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
                decision = decision,
                eventDates = resolved.events.map { event -> event.targetDate }.toSet(),
                recovery = resolved.recovery,
                kmByDay = context.kmByDay,
                weeklyLoadKm = context.weeklyLoadKm,
                recentActivity = context.recentActivity,
                activitySuggestions = suggestions,
                todayExecution = todayExecution,
                completionMessage = null,
            )
        }

    val uiState: StateFlow<TodayUiState> = combine(dayState, completionMessage) { state, message ->
        state.copy(completionMessage = message)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = todayState(
            checkIn = null,
            today = timeProvider.currentDate(),
            now = timeProvider.now().atZone(timeProvider.zoneId.value).toLocalTime(),
        ),
    )

    private suspend fun closeBlockedSessionIfNeeded(
        plan: WeeklyPlan?,
        prescription: DailyPrescription?,
        today: LocalDate,
        illnessOrInjury: Boolean,
    ) {
        if (plan == null || prescription?.isRest != true) return
        val session = plan.sessions.firstOrNull { it.date == today } ?: return
        if (session.status != SessionStatus.PLANNED) return
        executionRepository.closeSession(
            plannedSessionId = plannedSessionId(plan.weekStart, session.date),
            reason = SessionCompletionRules.closureForRest(illnessOrInjury),
        )
    }

    private suspend fun executionUi(
        plan: WeeklyPlan,
        session: com.meucoach.core.domain.prescription.PlannedSession,
        context: TodayContext,
    ): TodayExecutionUi? {
        if (session.status != SessionStatus.DONE) return null
        val execution = session.executionId?.let { executionRepository.getExecution(it) }
            ?: executionRepository.getForSession(plannedSessionId(plan.weekStart, session.date))
        val activity = session.activityId?.let { id -> context.activities.firstOrNull { it.id == id } }
        return TodayExecutionUi(
            duration = (execution?.durationSec ?: activity?.durationSec)?.let(::formatDuration),
            distance = activity?.distance?.let { "${formatDistance(it)} km" },
            pace = activity?.averagePace?.let { "${paceLabel(it)} /km" },
            rpe = execution?.rpe,
            activityId = session.activityId,
            withoutRunData = session.completionEvidence == CompletionEvidence.MANUAL,
        )
    }

    private fun suggestionsFor(
        plan: WeeklyPlan?,
        prescription: DailyPrescription?,
        context: TodayContext,
        zone: java.time.ZoneId,
        competition: Boolean = false,
    ): List<ActivitySuggestionUi> {
        if (competition) {
            // On a race day the imported run may be linked to the event, never
            // offered as a training completion.
            return context.activities
                .filter { activity ->
                    (activity.sport == Sport.RUNNING || activity.sport == Sport.TREADMILL) &&
                        activity.startTime.atZone(zone).toLocalDate() == context.today &&
                        activity.id !in context.linkedActivityIds
                }
                .map { activity ->
                    ActivitySuggestionUi(
                        id = activity.id,
                        title = sportLabel(activity.sport),
                        subtitle = formatDateTime(activity.startTime, zone),
                        distance = "${formatDistance(activity.distance)} km",
                        duration = formatDuration(activity.durationSec),
                    )
                }
        }
        val session = plan?.sessions?.firstOrNull {
            it.date == context.today && (
                it.status == SessionStatus.PLANNED ||
                    (it.status == SessionStatus.DONE && it.completionEvidence == CompletionEvidence.MANUAL)
                )
        } ?: return emptyList()
        // A manual completion has no matcher baseline to compare against; offer
        // every run of the day so the user can correct the mistake.
        val candidates = if (session.status == SessionStatus.DONE) {
            context.activities.filter { activity ->
                (activity.sport == Sport.RUNNING || activity.sport == Sport.TREADMILL) &&
                    activity.startTime.atZone(zone).toLocalDate() == context.today &&
                    activity.id !in context.linkedActivityIds
            }
        } else {
            val intended = session.copy(
                durationMin = prescription?.prescribedDurationMin ?: session.durationMin,
            )
            matcher.suggest(
                session = intended,
                activities = context.activities,
                zoneId = zone,
                excludedActivityIds = context.linkedActivityIds,
            )
        }
        return candidates.map { activity ->
            ActivitySuggestionUi(
                id = activity.id,
                title = sportLabel(activity.sport),
                subtitle = formatDateTime(activity.startTime, zone),
                distance = "${formatDistance(activity.distance)} km",
                duration = formatDuration(activity.durationSec),
            )
        }
    }

    private fun buildContext(
        today: LocalDate,
        checkIn: DailyCheckIn?,
        metrics: List<DailyMetrics>,
        profile: AthleteProfile?,
        activities: List<ActivitySummary>,
        lastImportedAt: java.time.Instant?,
        linkedActivityIds: Set<String>,
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
            activities = activities,
            linkedActivityIds = linkedActivityIds,
        )
    }

    fun completeWithActivity(date: LocalDate, activityId: String) {
        val state = uiState.value
        complete(
            date = date,
            evidence = CompletionEvidence.ACTIVITY,
            activityId = activityId,
            prescription = state.prescription,
            illnessOrInjury = state.illnessOrInjury,
        )
    }

    fun completeManually(date: LocalDate, durationMin: Int, rpe: Int) {
        val state = uiState.value
        complete(
            date = date,
            evidence = CompletionEvidence.MANUAL,
            durationMin = durationMin,
            rpe = rpe,
            prescription = state.prescription,
            illnessOrInjury = state.illnessOrInjury,
        )
    }

    fun replaceManualWithActivity(date: LocalDate, activityId: String) {
        viewModelScope.launch {
            val result = replaceManualCompletion(
                ReplaceManualCompletionRequest(
                    date = date,
                    today = timeProvider.currentDate(),
                    activityId = activityId,
                    zoneId = timeProvider.zoneId.value,
                ),
            )
            completionMessage.value = when (result) {
                is ReplaceManualCompletionResult.Replaced ->
                    "Registro manual substituído pela corrida. " +
                        "O treino agora entra em volume, carga e zonas."

                is ReplaceManualCompletionResult.Rejected -> result.message
            }
        }
    }

    private fun complete(
        date: LocalDate,
        evidence: CompletionEvidence,
        activityId: String? = null,
        durationMin: Int? = null,
        rpe: Int? = null,
        prescription: DailyPrescription?,
        illnessOrInjury: Boolean,
    ) {
        viewModelScope.launch {
            val today = timeProvider.currentDate()
            val result = completeSession(
                CompleteSessionRequest(
                    date = date,
                    today = today,
                    evidence = evidence,
                    activityId = activityId,
                    durationMin = durationMin,
                    rpe = rpe,
                    prescription = prescription,
                    illnessOrInjury = illnessOrInjury,
                    zoneId = timeProvider.zoneId.value,
                ),
            )
            completionMessage.value = when (result) {
                is CompleteSessionResult.Completed ->
                    result.warning ?: "Treino concluído com evidência."

                is CompleteSessionResult.Closed -> result.message

                is CompleteSessionResult.Rejected -> result.message
            }
        }
    }

    fun consumeCompletionMessage() {
        completionMessage.value = null
    }

    /** Explicit race completion; the activity link is optional and never inferred. */
    fun completeRaceEvent(activityId: String? = null) {
        viewModelScope.launch {
            val event = raceEventRepository.getPlanned() ?: return@launch
            completionMessage.value = when (val outcome = raceEventRepository.complete(event.id, activityId)) {
                is com.meucoach.core.data.repository.RaceEventOutcome.Saved ->
                    "Prova registrada como concluída."

                is com.meucoach.core.data.repository.RaceEventOutcome.Rejected -> outcome.message

                is com.meucoach.core.data.repository.RaceEventOutcome.ActiveEventExists ->
                    "Já existe uma prova planejada ativa."

                is com.meucoach.core.data.repository.RaceEventOutcome.ActivityAlreadyLinked -> outcome.message
            }
        }
    }

    fun cancelRaceEvent() = resolveRaceEvent(cancel = true)

    fun noShowRaceEvent() = resolveRaceEvent(cancel = false)

    /** Reverts only an inferred completion and records the refusal (ADR 0032/D3). */
    fun undoAutoCompletedRace(eventId: String) {
        viewModelScope.launch {
            completionMessage.value = when (val outcome = undoRaceAutoCompletion(eventId)) {
                is com.meucoach.core.data.repository.RaceEventOutcome.Saved ->
                    "Conclusão automática desfeita. A prova voltou para planejada."

                is com.meucoach.core.data.repository.RaceEventOutcome.Rejected -> outcome.message

                is com.meucoach.core.data.repository.RaceEventOutcome.ActiveEventExists ->
                    "Já existe outra prova planejada."

                is com.meucoach.core.data.repository.RaceEventOutcome.ActivityAlreadyLinked -> outcome.message
            }
        }
    }

    private fun resolveRaceEvent(cancel: Boolean) {
        viewModelScope.launch {
            val event = raceEventRepository.getPlanned() ?: return@launch
            val outcome = if (cancel) {
                raceEventRepository.cancel(event.id)
            } else {
                raceEventRepository.noShow(event.id)
            }
            completionMessage.value = when (outcome) {
                is com.meucoach.core.data.repository.RaceEventOutcome.Saved ->
                    if (cancel) "Prova cancelada." else "Registrado que você não compareceu."

                is com.meucoach.core.data.repository.RaceEventOutcome.Rejected -> outcome.message

                is com.meucoach.core.data.repository.RaceEventOutcome.ActiveEventExists ->
                    "Já existe uma prova planejada ativa."

                is com.meucoach.core.data.repository.RaceEventOutcome.ActivityAlreadyLinked -> outcome.message
            }
        }
    }

    fun skipSession(date: LocalDate, reason: String) {
        viewModelScope.launch {
            val weekStart = date.weekStart()
            executionRepository.closeSession(
                plannedSessionId = plannedSessionId(weekStart, date),
                reason = SessionClosureReason.USER_SKIPPED,
                note = reason,
            )
        }
    }

    fun swapSession(firstDate: LocalDate, secondDate: LocalDate, today: LocalDate) {
        viewModelScope.launch {
            val weekStart = firstDate.weekStart()
            val plan = planRepository.getWeek(weekStart) ?: return@launch
            val raceDates = raceEventRepository.getDatesBetween(weekStart, weekStart.plusDays(6))
            val result = SessionSwapper.swap(plan, firstDate, secondDate, today, raceDates)
            if (result is SwapResult.Applied) {
                planRepository.updateSessions(result.plan.weekStart, result.plan.sessions)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
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
    replanViewModel: com.meucoach.app.ui.replan.ReplanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val checklistState by checklistViewModel.uiState.collectAsStateWithLifecycle()
    val replanState by replanViewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        checklistViewModel.refreshHealth()
        replanViewModel.refresh()
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
        onCompleteWithActivity = viewModel::completeWithActivity,
        onCompleteManually = viewModel::completeManually,
        onReplaceWithActivity = viewModel::replaceManualWithActivity,
        onSkipSession = viewModel::skipSession,
        onSwapSession = { first, second -> viewModel.swapSession(first, second, state.today) },
        onCompleteRace = viewModel::completeRaceEvent,
        onCancelRace = viewModel::cancelRaceEvent,
        onNoShowRace = viewModel::noShowRaceEvent,
        onUndoAutoRaceCompletion = viewModel::undoAutoCompletedRace,
        onConsumeCompletionMessage = viewModel::consumeCompletionMessage,
        replan = replanState,
        onReplanRequest = replanViewModel::replanNow,
        onReplanAccept = replanViewModel::accept,
        onReplanDecline = replanViewModel::decline,
        onReplanSuppress = replanViewModel::suppress,
        onReplanConsumeMessage = replanViewModel::consumeMessage,
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
    decision: TodayDecision? = null,
    eventDates: Set<LocalDate> = emptySet(),
    kmByDay: Map<LocalDate, Double> = emptyMap(),
    weeklyLoadKm: Double? = null,
    recentActivity: RecentActivityUi? = null,
    activitySuggestions: List<ActivitySuggestionUi> = emptyList(),
    todayExecution: TodayExecutionUi? = null,
    completionMessage: String? = null,
    recovery: RecoveryWindow? = null,
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
    val competition = decision is TodayDecision.Competition
    // Preview/test/tutorial fallback. Production always receives the persisted
    // prescription from GetTodayDecision, which is the single authority.
    val dayPrescription = when (decision) {
        is TodayDecision.Competition -> null

        is TodayDecision.Training -> decision.prescription

        null -> prescription ?: profile?.let {
            buildPreviewPrescription(it, checkIn, result, load, today, plan, recovery)
        }
    }
    val todaySession = plan?.sessions?.firstOrNull { it.date == today }
    val briefing = if (competition) {
        null
    } else {
        dayPrescription?.let { SessionBriefingEngine().brief(it, todaySession?.status) }
    }
    val explanation = if (!competition && profile != null && dayPrescription != null) {
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
    val raceProjection = (decision as? TodayDecision.Competition)?.event?.let { event ->
        com.meucoach.core.domain.race.RaceTargetProjector.project(event.distance, event.targetTimeSec)
    }
    val racePrediction = (decision as? TodayDecision.Competition)?.event?.let { event ->
        profile?.let {
            com.meucoach.core.domain.race.RaceTimePredictor.predict(it, event.distance, today)
        }
    }
    val raceActivityAvailable = (decision as? TodayDecision.Competition)?.event?.activityId?.let { id ->
        activities.any { it.id == id }
    } ?: false
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
        briefing = briefing,
        decision = decision,
        raceProjection = raceProjection,
        racePrediction = racePrediction,
        raceActivityAvailable = raceActivityAvailable,
        eventDates = eventDates,
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
        activitySuggestions = if (competition) emptyList() else activitySuggestions,
        todayExecution = if (competition) null else todayExecution,
        completionMessage = completionMessage,
        illnessOrInjury = signals.illnessOrInjury,
        recovery = recovery?.takeIf { it.isActive(today) }?.let {
            RecoveryUi(
                dayOfWindow = it.dayOfWindow(today),
                totalDays = it.totalDays,
                until = it.race.raceDate.plusDays(it.totalDays.toLong()),
            )
        },
    )
}

private fun buildPreviewPrescription(
    profile: AthleteProfile,
    checkIn: DailyCheckIn?,
    readiness: com.meucoach.core.domain.readiness.ReadinessResult,
    load: LoadSnapshot?,
    today: LocalDate,
    plan: WeeklyPlan?,
    recovery: RecoveryWindow? = null,
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
            recovery = recovery,
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
        val z = hrv.zScore
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
