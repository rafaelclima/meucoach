package com.meucoach.app.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.application.CompleteSession
import com.meucoach.app.application.CompleteSessionRequest
import com.meucoach.app.application.CompleteSessionResult
import com.meucoach.app.application.ReplaceManualCompletion
import com.meucoach.app.application.ReplaceManualCompletionRequest
import com.meucoach.app.application.ReplaceManualCompletionResult
import com.meucoach.app.importer.ImportedRunPromptStore
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.app.ui.activities.formatDistance
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.app.ui.activities.paceLabel
import com.meucoach.app.ui.activities.sportLabel
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.Sport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** What the import overlay can ask (or tell) the user. */
sealed interface ImportedRunPromptUi {
    val activityId: String
    val activityTitle: String
    val activityDetail: String

    data class Offer(
        override val activityId: String,
        override val activityTitle: String,
        override val activityDetail: String,
        val sessionTitle: String,
        val sessionDurationMin: Int?,
    ) : ImportedRunPromptUi

    data class AlreadyDone(
        override val activityId: String,
        override val activityTitle: String,
        override val activityDetail: String,
        val evidenceLabel: String,
        /** True only for an explicit manual declaration that can be corrected. */
        val canReplace: Boolean,
    ) : ImportedRunPromptUi
}

data class ImportedRunPromptState(
    val prompt: ImportedRunPromptUi? = null,
    val isLinking: Boolean = false,
    val message: String? = null,
)

/**
 * Turns a finished import into an immediate decision: link the run to today's
 * planned session or keep it as a free run. It is intentionally global (hosted
 * by the main scaffold) so the prompt appears whether the user imported from
 * the Corridas tab or shared a file from another app.
 */
@HiltViewModel
class ImportedRunPromptViewModel @Inject constructor(
    private val activities: ActivityRepository,
    private val plans: TrainingPlanRepository,
    private val prescriptions: DailyPrescriptionRepository,
    private val checkIns: CheckInRepository,
    private val executions: SessionExecutionRepository,
    private val completeSession: CompleteSession,
    private val replaceManualCompletion: ReplaceManualCompletion,
    private val store: ImportedRunPromptStore,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val prompt = MutableStateFlow<ImportedRunPromptUi?>(null)
    private val linking = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ImportedRunPromptState> = combine(prompt, linking, message) { current, isLinking, feedback ->
        ImportedRunPromptState(prompt = current, isLinking = isLinking, message = feedback)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ImportedRunPromptState(),
    )

    init {
        viewModelScope.launch {
            combine(store.pending, timeProvider.today) { pending, today -> pending to today }
                .distinctUntilChanged()
                .collect { (pending, today) -> resolve(pending, today) }
        }
    }

    fun link() {
        val offer = prompt.value as? ImportedRunPromptUi.Offer ?: return
        if (linking.value) return
        viewModelScope.launch {
            linking.value = true
            val today = timeProvider.currentDate()
            val result = completeSession(
                CompleteSessionRequest(
                    date = today,
                    today = today,
                    evidence = CompletionEvidence.ACTIVITY,
                    activityId = offer.activityId,
                    prescription = prescriptions.getLatest(today),
                    illnessOrInjury = checkIns.get(today)?.illnessOrInjury == true,
                    zoneId = timeProvider.zoneId.value,
                ),
            )
            linking.value = false
            when (result) {
                is CompleteSessionResult.Completed -> {
                    store.clear()
                    prompt.value = null
                    message.value = "Corrida vinculada ao treino de hoje."
                }

                is CompleteSessionResult.Closed -> message.value = result.message

                is CompleteSessionResult.Rejected -> message.value = result.message
            }
        }
    }

    /** Keeps the run in the library without linking it to the planned session. */
    fun keepAsFreeRun() {
        store.clear()
        prompt.value = null
        message.value = null
    }

    /** Corrects an accidental "fiz sem registro" with the imported run. */
    fun replaceManualRecord() {
        val current = prompt.value as? ImportedRunPromptUi.AlreadyDone ?: return
        if (!current.canReplace || linking.value) return
        viewModelScope.launch {
            linking.value = true
            val today = timeProvider.currentDate()
            val result = replaceManualCompletion(
                ReplaceManualCompletionRequest(
                    date = today,
                    today = today,
                    activityId = current.activityId,
                    zoneId = timeProvider.zoneId.value,
                ),
            )
            linking.value = false
            when (result) {
                is ReplaceManualCompletionResult.Replaced -> {
                    store.clear()
                    prompt.value = null
                    message.value = "Registro manual substituído pela corrida. " +
                        "O treino agora entra em volume, carga e zonas."
                }

                is ReplaceManualCompletionResult.Rejected -> message.value = result.message
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    private suspend fun resolve(pending: Set<String>, today: LocalDate) {
        if (pending.isEmpty()) {
            prompt.value = null
            return
        }
        val zone = timeProvider.zoneId.value
        val stale = mutableSetOf<String>()
        var resolved: ImportedRunPromptUi? = null
        for (activityId in pending) {
            val candidate = candidate(activityId, today, zone)
            if (candidate == null) {
                stale += activityId
            } else if (resolved == null) {
                resolved = candidate
            }
        }
        if (stale.isNotEmpty()) store.remove(stale)
        prompt.value = resolved
    }

    private suspend fun candidate(activityId: String, today: LocalDate, zone: ZoneId): ImportedRunPromptUi? {
        val activity = activities.getActivitySummary(activityId) ?: return null
        if (activity.sport != Sport.RUNNING && activity.sport != Sport.TREADMILL) return null
        if (activity.startTime.atZone(zone).toLocalDate() != today) return null
        if (executions.getForActivity(activityId) != null) return null
        val plan = plans.getWeek(today.weekStart()) ?: return null
        val session = plan.sessions.firstOrNull { it.date == today } ?: return null
        if (session.status == SessionStatus.CLOSED) return null
        if (session.activityId == activityId) return null
        if (prescriptions.getLatest(today)?.isRest == true) return null

        val title = sportLabel(activity.sport)
        val detail = activityDetail(activity)
        return when (session.status) {
            SessionStatus.PLANNED -> ImportedRunPromptUi.Offer(
                activityId = activity.id,
                activityTitle = title,
                activityDetail = detail,
                sessionTitle = session.title,
                sessionDurationMin = session.durationMin,
            )

            SessionStatus.DONE -> ImportedRunPromptUi.AlreadyDone(
                activityId = activity.id,
                activityTitle = title,
                activityDetail = detail,
                evidenceLabel = evidenceLabel(session.completionEvidence),
                canReplace = session.completionEvidence == CompletionEvidence.MANUAL,
            )

            SessionStatus.CLOSED -> null
        }
    }

    private fun activityDetail(activity: ActivitySummary): String = listOfNotNull(
        "${formatDistance(activity.distance)} km",
        formatDuration(activity.durationSec),
        activity.averagePace?.let { "${paceLabel(it)} /km" },
    ).joinToString(" · ")

    private fun evidenceLabel(evidence: CompletionEvidence?): String = when (evidence) {
        CompletionEvidence.ACTIVITY -> "com corrida vinculada"
        CompletionEvidence.GUIDED -> "com sessão guiada"
        CompletionEvidence.MANUAL -> "sem registro de corrida"
        CompletionEvidence.LEGACY_UNVERIFIED, null -> "não verificado"
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
