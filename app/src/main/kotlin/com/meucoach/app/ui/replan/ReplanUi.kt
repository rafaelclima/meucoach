package com.meucoach.app.ui.replan

import com.meucoach.app.application.ReplanChange
import com.meucoach.app.application.ReplanChangeKind
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.replan.ReplanSuggestion
import com.meucoach.core.domain.replan.ReplanTrigger
import com.meucoach.core.model.DataConfidence
import java.time.LocalDate

/** Everything the Today screen needs to offer and explain a replanning revision. */
data class ReplanUiState(
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val planVersion: Int = 1,
    val suggestion: ReplanSuggestionUi? = null,
    /** Diff of the latest accepted revision, shown by "Ver o que mudou". */
    val lastChange: ReplanVersionChangeUi? = null,
    val message: String? = null,
)

data class ReplanSuggestionUi(
    val versionFrom: Int,
    val versionTo: Int,
    val trigger: ReplanTrigger,
    val headline: String,
    val body: String,
    val changes: List<ReplanChangeUi>,
    val confidence: DataConfidence,
)

data class ReplanVersionChangeUi(
    val fromVersion: Int,
    val toVersion: Int,
    val reason: String,
    val changes: List<ReplanChangeUi>,
)

data class ReplanChangeUi(
    val date: LocalDate,
    val kind: ReplanChangeKind,
    val kindLabel: String,
    val previousLabel: String?,
    val nextLabel: String?,
)

internal fun ReplanSuggestion.toUi(changes: List<ReplanChange>): ReplanSuggestionUi = ReplanSuggestionUi(
    versionFrom = expectedVersion,
    versionTo = expectedVersion + 1,
    trigger = trigger,
    headline = when (trigger) {
        ReplanTrigger.USER_REQUEST -> "Reorganizar o resto da semana?"
        ReplanTrigger.MISSED_SESSIONS -> "Dá para ajustar o resto da semana"
        ReplanTrigger.AVAILABILITY_CHANGED -> "Sua disponibilidade mudou"
    },
    body = when (trigger) {
        ReplanTrigger.USER_REQUEST ->
            "O que já passou e o treino de hoje ficam como estão. A mudança vale só para os próximos dias."

        ReplanTrigger.MISSED_SESSIONS ->
            "$missedSentence Posso reorganizar só os próximos dias, " +
                "sem repor carga e sem mexer no que já passou."

        ReplanTrigger.AVAILABILITY_CHANGED ->
            "Posso reorganizar só os próximos dias com os horários que você tem agora, " +
                "sem aumentar a carga e sem mexer no que já passou."
    },
    changes = changes.map { it.toUi() },
    confidence = confidence,
)

internal fun ReplanChange.toUi(): ReplanChangeUi = ReplanChangeUi(
    date = date,
    kind = kind,
    kindLabel = when (kind) {
        ReplanChangeKind.ADDED -> "Novo"
        ReplanChangeKind.REMOVED -> "Sai"
        ReplanChangeKind.MODIFIED -> "Muda"
    },
    previousLabel = before?.label(),
    nextLabel = after?.label(),
)

internal fun PlannedSession.label(): String = durationMin?.let { "$title · $it min" } ?: title

private val ReplanSuggestion.missedSentence: String
    get() = when (missedTrainableCount) {
        1 -> "1 treino desta semana não aconteceu."
        else -> "$missedTrainableCount treinos desta semana não aconteceram."
    }

internal fun confidenceLabel(confidence: DataConfidence): String = when (confidence) {
    DataConfidence.HIGH -> "Confiança alta na sugestão"
    DataConfidence.MEDIUM -> "Confiança média na sugestão"
    DataConfidence.LOW -> "Confiança baixa na sugestão"
}
