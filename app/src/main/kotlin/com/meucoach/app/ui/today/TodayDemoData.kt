package com.meucoach.app.ui.today

import com.meucoach.core.domain.load.LoadDataState
import com.meucoach.core.domain.load.LoadMetric
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SignalTone {
    POSITIVE,
    NEUTRAL,
    WARNING,
}

data class ReadinessSignalUi(val label: String, val value: String, val hint: String?, val tone: SignalTone)

data class WorkoutUi(
    val title: String,
    val zone: String,
    val duration: String,
    val distance: String,
    val pace: String,
    val heartRate: String,
    val rationale: String,
    /** Perceived effort fallback when pace/HR are not calibrated. */
    val rpe: Int? = null,
    /** False for a rest prescription; start/complete actions must stay disabled. */
    val canTrain: Boolean = true,
)

data class DayLoadUi(val label: String, val loadKm: Double, val isToday: Boolean)

data class RecentActivityUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val distance: String,
    val duration: String,
    val pace: String,
    val zone: String,
)

/** An imported run that may correspond to today's planned session. */
data class ActivitySuggestionUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val distance: String,
    val duration: String,
)

/** What was actually executed today; null while the session is still planned. */
data class TodayExecutionUi(
    val duration: String?,
    val distance: String?,
    val pace: String?,
    val rpe: Int?,
    val activityId: String?,
    /** True when the completion has no linked run (manual evidence). */
    val withoutRunData: Boolean,
)

/** Post-race recovery shown in the day card (ADR 0037). */
data class RecoveryUi(
    val dayOfWindow: Int,
    val totalDays: Int,
    /** Last day of the window, inclusive. */
    val until: LocalDate,
)

data class TodayUiState(
    val today: LocalDate,
    val athleteName: String,
    val greeting: String,
    val dateLine: String,
    val weather: String,
    val readinessScore: Int,
    val readinessLevel: ReadinessLevel,
    val readinessConfidence: DataConfidence,
    val readinessSignals: List<ReadinessSignalUi>,
    val workout: WorkoutUi,
    /** Null until there is at least one activity with distance; the UI shows an empty state. */
    val weeklyLoadKm: Double? = null,
    val days: List<DayLoadUi> = emptyList(),
    /** Null when no load snapshot exists; the UI must not invent fitness, fatigue or form. */
    val ctl: Int? = null,
    val atl: Int? = null,
    val tsb: Int? = null,
    /** Null until at least one activity exists; the UI hides the card instead of inventing a run. */
    val recentActivity: RecentActivityUi? = null,
    val hasCheckedIn: Boolean = false,
    val plan: com.meucoach.core.domain.prescription.WeeklyPlan? = null,
    /** Single authority of the day; null only before a profile exists. */
    val prescription: com.meucoach.core.domain.prescription.DailyPrescription? = null,
    /** Briefing derived from the persisted prescription; null for done/closed days. */
    val briefing: com.meucoach.core.domain.briefing.SessionBriefing? = null,
    /** Single daily authority resolved before rendering (ADR 0030). */
    val decision: com.meucoach.core.domain.today.TodayDecision? = null,
    /** Arithmetic reference of the declared race goal; never a training target (ADR 0032). */
    val raceProjection: com.meucoach.core.domain.race.RaceTargetProjection? = null,
    /** VDOT/Riegel prediction of today's race; never a prescription (ADR 0036). */
    val racePrediction: com.meucoach.core.domain.race.RacePredictionResult? = null,
    /** False when the linked run is in the trash or was purged; the link is suspended. */
    val raceActivityAvailable: Boolean = false,
    /** Every event date in the visible week, including resolved history. */
    val eventDates: Set<LocalDate> = emptySet(),
    val explanation: TodayExplanationUi? = null,
    val readinessExplanation: TodayExplanationUi? = null,
    /** Null when there is not enough load history; the UI must not invent a ratio. */
    val acwr: Double? = null,
    /** Explains why load/ACWR are missing or outdated; null when no load snapshot exists. */
    val loadDataState: LoadDataState? = null,
    /** Load metric of today; `SRPE` means the day is estimated from perceived effort (ADR 0039). */
    val loadMetric: LoadMetric? = null,
    val avatarPath: String? = null,
    /** Imported runs compatible with today's planned session; never auto-linked. */
    val activitySuggestions: List<ActivitySuggestionUi> = emptyList(),
    /** Summary of today's execution when the planned session is already done. */
    val todayExecution: TodayExecutionUi? = null,
    /** Result of the last completion attempt, shown as passive copy in the card. */
    val completionMessage: String? = null,
    /** Blocks completion and explains why the day is a rest/illness day. */
    val illnessOrInjury: Boolean = false,
    /** Post-race recovery window for today, when active (ADR 0037). */
    val recovery: RecoveryUi? = null,
) {
    /** The race event resolved for today, when the daily decision is competition. */
    val raceEvent: com.meucoach.core.model.RaceEvent?
        get() = (decision as? com.meucoach.core.domain.today.TodayDecision.Competition)?.event
}

fun demoTodayUiState(today: LocalDate, now: LocalTime = LocalTime.NOON): TodayUiState {
    val labels = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    val loads = listOf(0.0, 4.2, 0.0, 5.6, 0.0, 8.1, 0.0)
    val todayIndex = today.dayOfWeek.value - 1
    val days =
        labels.mapIndexed { index, label ->
            DayLoadUi(label = label, loadKm = loads[index], isToday = index == todayIndex)
        }

    return TodayUiState(
        today = today,
        athleteName = "Rafael",
        greeting = greetingFor(now),
        dateLine = formatDateLine(today),
        weather = "Natal · 28°",
        readinessScore = 72,
        readinessLevel = ReadinessLevel.GREEN,
        readinessConfidence = DataConfidence.MEDIUM,
        readinessSignals =
        listOf(
            ReadinessSignalUi(
                label = "Sono",
                value = "6h10",
                hint = "abaixo do ideal",
                tone = SignalTone.WARNING,
            ),
            ReadinessSignalUi(
                label = "Variabilidade",
                value = "média",
                hint = "7 noites",
                tone = SignalTone.NEUTRAL,
            ),
            ReadinessSignalUi(
                label = "Carga",
                value = "1,1",
                hint = "aumento seguro",
                tone = SignalTone.POSITIVE,
            ),
        ),
        workout =
        WorkoutUi(
            title = "Fácil, sem pressa",
            zone = "Z2 · leve",
            duration = "40",
            distance = "6,0",
            pace = "7:40–8:20",
            heartRate = "146–161",
            rationale = "Base aeróbica em ritmo fácil. O volume semanal sobe 6%, sem treino forte.",
        ),
        weeklyLoadKm = loads.sum(),
        days = days,
        ctl = 24,
        atl = 31,
        tsb = -7,
        recentActivity =
        RecentActivityUi(
            id = "demo-activity",
            title = "Corrida de rua",
            subtitle = "5k do parque · 24 out",
            distance = "5,11 km",
            duration = "30:59",
            pace = "6:04 /km",
            zone = "Z5 · máximo",
        ),
        hasCheckedIn = true,
    )
}

private fun greetingFor(time: LocalTime): String = when {
    time.hour < 12 -> "Bom dia"
    time.hour < 18 -> "Boa tarde"
    else -> "Boa noite"
}

private fun formatDateLine(date: LocalDate): String {
    val locale = Locale.forLanguageTag("pt-BR")
    val formatted = date
        .format(DateTimeFormatter.ofPattern("EEE, d 'de' MMMM", locale))
        .replace(".", "")
    return formatted.replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase(locale) else character.toString()
    }
}
