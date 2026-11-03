package com.meucoach.app.ui.today

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

data class ReadinessSignalUi(val label: String, val value: String, val hint: String, val tone: SignalTone)

data class WorkoutUi(
    val title: String,
    val zone: String,
    val duration: String,
    val distance: String,
    val pace: String,
    val heartRate: String,
    val rationale: String,
)

data class DayLoadUi(val label: String, val loadKm: Double, val isToday: Boolean)

data class RecentActivityUi(
    val title: String,
    val subtitle: String,
    val distance: String,
    val duration: String,
    val pace: String,
    val zone: String,
)

data class TodayUiState(
    val athleteName: String,
    val greeting: String,
    val dateLine: String,
    val weather: String,
    val readinessScore: Int,
    val readinessLevel: ReadinessLevel,
    val readinessConfidence: DataConfidence,
    val readinessSignals: List<ReadinessSignalUi>,
    val workout: WorkoutUi,
    val weeklyLoadKm: Double,
    val days: List<DayLoadUi>,
    val ctl: Int,
    val atl: Int,
    val tsb: Int,
    val recentActivity: RecentActivityUi,
    val hasCheckedIn: Boolean = false,
    val plan: com.meucoach.core.domain.prescription.WeeklyPlan? = null,
    val explanation: TodayExplanationUi? = null,
    /** Null when there is not enough load history; the UI must not invent a ratio. */
    val acwr: Double? = null,
)

fun demoTodayUiState(today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()): TodayUiState {
    val labels = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    val loads = listOf(0.0, 4.2, 0.0, 5.6, 0.0, 8.1, 0.0)
    val todayIndex = today.dayOfWeek.value - 1
    val days =
        labels.mapIndexed { index, label ->
            DayLoadUi(label = label, loadKm = loads[index], isToday = index == todayIndex)
        }

    return TodayUiState(
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
                label = "HRV",
                value = "−0,4 SWC",
                hint = "dentro da variação",
                tone = SignalTone.NEUTRAL,
            ),
            ReadinessSignalUi(
                label = "Carga",
                value = "1,1",
                hint = "ACWR · zona segura",
                tone = SignalTone.POSITIVE,
            ),
        ),
        workout =
        WorkoutUi(
            title = "Fácil, sem pressa",
            zone = "Z2",
            duration = "40",
            distance = "6,0",
            pace = "7:40–8:20",
            heartRate = "146–161",
            rationale = "Base aeróbica em Z2. O volume semanal sobe 6%, sem adicionar qualidade.",
        ),
        weeklyLoadKm = loads.sum(),
        days = days,
        ctl = 24,
        atl = 31,
        tsb = -7,
        recentActivity =
        RecentActivityUi(
            title = "Corrida de rua",
            subtitle = "5k do parque · 24 out",
            distance = "5,11 km",
            duration = "30:59",
            pace = "6:04 /km",
            zone = "Z5",
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
