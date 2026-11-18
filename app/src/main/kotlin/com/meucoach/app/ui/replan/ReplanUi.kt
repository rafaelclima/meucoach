package com.meucoach.app.ui.replan

import com.meucoach.app.application.ReplanChange
import com.meucoach.app.application.ReplanChangeKind
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.replan.ReplanCause
import com.meucoach.core.domain.replan.ReplanSuggestion
import com.meucoach.core.domain.replan.ReplanTrigger
import com.meucoach.core.model.DataConfidence
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Everything the Today screen needs to offer and explain a replanning revision. */
data class ReplanUiState(
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    /** True while an undo request is in flight. */
    val isUndoing: Boolean = false,
    val planVersion: Int = 1,
    val suggestion: ReplanSuggestionUi? = null,
    /** Diff of the latest accepted revision, shown by "Ver o que mudou". */
    val lastChange: ReplanVersionChangeUi? = null,
    /** The latest revision can be reverted as a new version (ADR 0028). */
    val canUndo: Boolean = false,
    val message: String? = null,
    /** Eligible lost sessions for the Coach picker; `null` keeps the picker closed. */
    val missedSessions: List<MissedSessionUi>? = null,
    /** Future days that still accept a temporary reduction; `null` keeps it closed. */
    val lessTimeDays: List<LessTimeDayUi>? = null,
)

/** One selectable lost session; the date is the identity the domain validates. */
data class MissedSessionUi(val date: LocalDate, val title: String, val durationMin: Int?)

/** A future day with its persisted availability; any smaller value (including 0) is offered. */
data class LessTimeDayUi(val date: LocalDate, val currentMinutes: Int)

/** One temporary change requested in the picker; `minutes = 0` removes the day. */
data class LessTimeChange(val date: LocalDate, val minutes: Int) {
    val isRemoved: Boolean get() = minutes == 0
}

data class ReplanSuggestionUi(
    val versionFrom: Int,
    val versionTo: Int,
    val trigger: ReplanTrigger,
    val causes: List<ReplanCause>,
    val headline: String,
    val body: String,
    val changes: List<ReplanChangeUi>,
    val confidence: DataConfidence,
    /** What the user asked for, when this proposal answers a specific intent. */
    val requestNote: String? = null,
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
    causes = causes,
    headline = headline(),
    body = body(),
    changes = changes.map { it.toUi() },
    confidence = confidence,
    requestNote = requestNote(),
)

private fun ReplanSuggestion.requestNote(): String? {
    if (intent.temporaryAvailability.isNotEmpty()) {
        val entries = intent.temporaryAvailability.sortedBy { it.date }
        if (entries.size == 1) {
            val entry = entries.single()
            return if (entry.isRemoved) {
                "Você indicou que não pode treinar em " +
                    "${formatWeekday(entry.date)} (${formatDate(entry.date)})."
            } else {
                "Você indicou ${entry.minutes} min para " +
                    "${formatWeekday(entry.date)} (${formatDate(entry.date)})."
            }
        }
        val detail = entries.joinToString(" · ") { entry ->
            val amount = if (entry.isRemoved) "sem treino" else "${entry.minutes} min"
            "${formatWeekday(entry.date)} (${formatDate(entry.date)}): $amount"
        }
        return "Você indicou: $detail."
    }
    intent.requestedMissedDate?.let { date ->
        return "Você indicou o treino de ${formatWeekday(date)} (${formatDate(date)}) como perdido."
    }
    return null
}

private fun ReplanSuggestion.headline(): String = when {
    intent.temporaryAvailability.isNotEmpty() -> "Cabe no tempo que você tem?"
    intent.requestedMissedDate != null -> "Ajustar o resto da semana?"
    causes.size > 1 -> "Vale ajustar o resto da semana?"
    causes == listOf(ReplanCause.TIMEZONE_CONTEXT_CHANGED) -> "Seu fuso mudou"
    trigger == ReplanTrigger.ENGINE_UPGRADE -> "Nova versão do treino disponível"
    trigger == ReplanTrigger.DATA_READY -> "Chegaram dados novos do seu relógio"
    trigger == ReplanTrigger.PROFILE_CHANGED -> "Seu perfil mudou"
    trigger == ReplanTrigger.USER_REQUEST -> "Reorganizar o resto da semana?"
    trigger == ReplanTrigger.RACE_COMPLETED -> "Ajustar o resto da semana após a prova?"
    trigger == ReplanTrigger.MISSED_SESSIONS -> "Dá para ajustar o resto da semana"
    else -> "Sua disponibilidade mudou"
}

private fun ReplanSuggestion.body(): String = when {
    intent.temporaryAvailability.isNotEmpty() ->
        "Só os próximos dias mudam para caber na sua disponibilidade temporária, " +
            "sem repor carga e sem mexer no que já passou."

    intent.requestedMissedDate != null ->
        "Posso reorganizar só os próximos dias, sem repor o treino perdido e sem mexer no que já passou."

    causes.size > 1 -> {
        "Mais de um motivo motivou a revisão: ${causes.joinToString(" e ") { it.label() }}. " +
            "Posso reorganizar só os próximos dias, sem repor carga e sem mexer no que já passou."
    }

    causes == listOf(ReplanCause.TIMEZONE_CONTEXT_CHANGED) ->
        "O fuso que define seus dias mudou. Posso reorganizar só os próximos dias, " +
            "sem aumentar a carga e sem mexer no que já passou."

    trigger == ReplanTrigger.ENGINE_UPGRADE ->
        "O motor do plano evoluiu. Posso regenerar só os próximos dias, " +
            "sem aumentar a carga e sem mexer no que já passou."

    trigger == ReplanTrigger.DATA_READY ->
        "Os dados da sincronização chegaram. Posso reavaliar só os próximos dias " +
            "com os dados disponíveis, sem aumentar a carga e sem mexer no que já passou."

    trigger == ReplanTrigger.PROFILE_CHANGED ->
        "Seu perfil de planejamento mudou. Posso reorganizar só os próximos dias, " +
            "sem aumentar a carga e sem mexer no que já passou."

    trigger == ReplanTrigger.USER_REQUEST ->
        "O que já passou e o treino de hoje ficam como estão. A mudança vale só para os próximos dias."

    trigger == ReplanTrigger.MISSED_SESSIONS ->
        "$missedSentence Posso reorganizar só os próximos dias, " +
            "sem repor carga e sem mexer no que já passou."

    trigger == ReplanTrigger.RACE_COMPLETED ->
        "Você concluiu a prova. Posso encurtar os próximos dias e tirar o treino forte " +
            "durante a recuperação, sem repor carga e sem mexer no que já passou."

    else ->
        "Posso reorganizar só os próximos dias com os horários que você tem agora, " +
            "sem aumentar a carga e sem mexer no que já passou."
}

private fun ReplanCause.label(): String = when (this) {
    ReplanCause.PROFILE_CHANGED -> "perfil"
    ReplanCause.TIMEZONE_CONTEXT_CHANGED -> "fuso"
    ReplanCause.MISSED_SESSIONS -> "treinos não concluídos"
    ReplanCause.AVAILABILITY_CHANGED -> "disponibilidade"
    ReplanCause.USER_REQUEST -> "pedido seu"
    ReplanCause.RACE_COMPLETED -> "recuperação após a prova"
    ReplanCause.DATA_READY -> "dados da sincronização"
    ReplanCause.ENGINE_UPGRADE -> "nova versão do motor"
    ReplanCause.TEMPORARY_AVAILABILITY -> "tempo reduzido"
}

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

/** Short weekday used by change rows and pickers: "Seg", "Qua". */
internal fun formatDay(date: LocalDate): String {
    val locale = Locale.forLanguageTag("pt-BR")
    val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    return label.replaceFirstChar { it.uppercaseChar() }.trimEnd('.')
}

/** Full weekday for sentences: "segunda-feira", "sábado". */
internal fun formatWeekday(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR"))

/** Day and month without the year: "02/11". */
internal fun formatDate(date: LocalDate): String =
    String.format(Locale.ROOT, "%02d/%02d", date.dayOfMonth, date.monthValue)

/** Picker row label: "Sex 06/11". */
internal fun formatDayDate(date: LocalDate): String = "${formatDay(date)} ${formatDate(date)}"
