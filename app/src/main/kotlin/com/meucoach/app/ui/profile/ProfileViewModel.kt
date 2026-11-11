package com.meucoach.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.application.UndoRaceAutoCompletion
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.health.HealthSyncStore
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.NewRaceEvent
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventOutcome
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import com.meucoach.core.model.Sport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ProfileGateUiState(val isLoading: Boolean = true, val profile: AthleteProfile? = null)

@HiltViewModel
class ProfileGateViewModel @Inject constructor(repository: ProfileRepository) : ViewModel() {
    val uiState: StateFlow<ProfileGateUiState> = repository
        .observeProfile()
        .map { profile -> ProfileGateUiState(isLoading = false, profile = profile) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ProfileGateUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Snapshot of what the coach already knows about sleep and recovery. The
 * profile card uses it to prove that Health Connect feeds the engine instead of
 * only describing the feature.
 */
data class ProfileHealthSummary(
    val status: HealthConnectStatus = HealthConnectStatus.UNAVAILABLE,
    val hasPermissions: Boolean = false,
    val storedDays: Int = 0,
    val latestMetricDate: LocalDate? = null,
    val lastSyncedAt: Instant? = null,
)

/** A run that may be explicitly linked when the user completes a race. */
data class RaceActivityOption(val id: String, val title: String, val subtitle: String)

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: AthleteProfile? = null,
    val health: ProfileHealthSummary = ProfileHealthSummary(),
    val today: LocalDate? = null,
    val now: Instant? = null,
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    val raceEvents: List<RaceEvent> = emptyList(),
    val raceActivities: List<RaceActivityOption> = emptyList(),
    /** Arithmetic reference of the planned goal; null when there is no target time (ADR 0032). */
    val raceProjection: com.meucoach.core.domain.race.RaceTargetProjection? = null,
    val raceMessage: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val metricsRepository: DailyMetricsRepository,
    private val healthSource: HealthDailySource,
    private val syncStore: HealthSyncStore,
    private val raceEventRepository: RaceEventRepository,
    private val activities: ActivityRepository,
    private val undoRaceAutoCompletion: UndoRaceAutoCompletion,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private data class HealthAccess(
        val status: HealthConnectStatus = HealthConnectStatus.UNAVAILABLE,
        val hasPermissions: Boolean = false,
        val lastSyncedAt: Instant? = null,
    )

    private val access = MutableStateFlow(HealthAccess())
    private val raceMessage = MutableStateFlow<String?>(null)
    private val raceInputs = combine(
        raceEventRepository.observeAll(),
        activities.observeActivitySummaries(),
    ) { events, summaries -> events to summaries }

    val uiState: StateFlow<ProfileUiState> = combine(
        repository.observeProfile(),
        metricsRepository.observeAll(),
        access,
        timeProvider.today,
        combine(raceInputs, raceMessage) { inputs, message -> inputs to message },
    ) { profile, metrics, access, today, race ->
        val (events, summaries) = race.first
        val zone = profile?.timezone
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: timeProvider.zoneId.value
        val planned = events.firstOrNull { it.status == RaceStatus.PLANNED }
        val raceActivities = if (planned == null) {
            emptyList()
        } else {
            summaries
                .filter { summary ->
                    (summary.sport == Sport.RUNNING || summary.sport == Sport.TREADMILL) &&
                        summary.startTime.atZone(zone).toLocalDate() == planned.targetDate
                }
                .map { summary ->
                    val sport = if (summary.sport == Sport.TREADMILL) "Esteira" else "Corrida"
                    RaceActivityOption(
                        id = summary.id,
                        title = "$sport · ${String.format(
                            java.util.Locale.forLanguageTag("pt-BR"),
                            "%.1f",
                            summary.distance.kilometers,
                        )} km",
                        subtitle = summary.startTime.atZone(zone).toLocalTime().toString(),
                    )
                }
        }
        ProfileUiState(
            isLoading = false,
            profile = profile,
            health = ProfileHealthSummary(
                status = access.status,
                hasPermissions = access.hasPermissions,
                storedDays = metrics.size,
                latestMetricDate = metrics.lastOrNull()?.date,
                lastSyncedAt = access.lastSyncedAt,
            ),
            today = today,
            now = timeProvider.now(),
            zone = zone,
            raceEvents = events,
            raceActivities = raceActivities,
            raceProjection = planned?.let {
                com.meucoach.core.domain.race.RaceTargetProjector.project(it.distance, it.targetTimeSec)
            },
            raceMessage = race.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ProfileUiState(),
    )

    init {
        refreshHealth()
    }

    /** Creates the active event or edits it when one is already planned. */
    fun saveRaceEvent(distance: RaceDistance, targetDate: LocalDate, targetTimeSec: Int?) {
        viewModelScope.launch {
            val planned = uiState.value.raceEvents.firstOrNull { it.status == RaceStatus.PLANNED }
            val request = NewRaceEvent(distance, targetDate, targetTimeSec)
            val outcome = if (planned == null) {
                raceEventRepository.create(request)
            } else {
                raceEventRepository.update(planned.id, request)
            }
            raceMessage.value = raceOutcomeMessage(outcome, saved = "Prova salva.")
        }
    }

    /** Completion is always explicit; the activity is optional and never inferred. */
    fun completeRaceEvent(activityId: String?) {
        viewModelScope.launch {
            val planned = uiState.value.raceEvents.firstOrNull { it.status == RaceStatus.PLANNED } ?: return@launch
            raceMessage.value = raceOutcomeMessage(
                raceEventRepository.complete(planned.id, activityId),
                saved = "Prova concluída.",
            )
        }
    }

    fun cancelRaceEvent() {
        viewModelScope.launch {
            val planned = uiState.value.raceEvents.firstOrNull { it.status == RaceStatus.PLANNED } ?: return@launch
            raceMessage.value = raceOutcomeMessage(raceEventRepository.cancel(planned.id), saved = "Prova cancelada.")
        }
    }

    fun noShowRaceEvent() {
        viewModelScope.launch {
            val planned = uiState.value.raceEvents.firstOrNull { it.status == RaceStatus.PLANNED } ?: return@launch
            raceMessage.value = raceOutcomeMessage(
                raceEventRepository.noShow(planned.id),
                saved = "Registrado que você não compareceu.",
            )
        }
    }

    /** Reverts only an inferred completion and records the refusal (ADR 0032/D3). */
    fun undoAutoCompletedRace(eventId: String) {
        viewModelScope.launch {
            raceMessage.value = raceOutcomeMessage(
                undoRaceAutoCompletion(eventId),
                saved = "Conclusão automática desfeita. A prova voltou para planejada.",
            )
        }
    }

    fun consumeRaceMessage() {
        raceMessage.value = null
    }

    private fun raceOutcomeMessage(outcome: RaceEventOutcome, saved: String): String = when (outcome) {
        is RaceEventOutcome.Saved -> saved
        is RaceEventOutcome.Rejected -> outcome.message
        is RaceEventOutcome.ActiveEventExists -> "Já existe uma prova planejada ativa."
        is RaceEventOutcome.ActivityAlreadyLinked -> outcome.message
    }

    /** Re-reads the platform state when the screen resumes, after a sync or permission change. */
    fun refreshHealth() {
        viewModelScope.launch {
            val status = healthSource.status()
            val hasPermissions = status == HealthConnectStatus.AVAILABLE &&
                (healthSource.requiredPermissions - healthSource.grantedPermissions()).isEmpty()
            access.value = HealthAccess(
                status = status,
                hasPermissions = hasPermissions,
                lastSyncedAt = syncStore.lastSyncedAt(),
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
