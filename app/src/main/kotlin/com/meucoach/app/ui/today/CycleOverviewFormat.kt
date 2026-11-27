package com.meucoach.app.ui.today

import com.meucoach.core.domain.cycle.CycleDeload
import com.meucoach.core.domain.cycle.CycleMilestone
import com.meucoach.core.domain.cycle.CycleMilestoneKind
import com.meucoach.core.domain.cycle.CycleOverview
import com.meucoach.core.domain.cycle.CycleStrongIntent
import com.meucoach.core.domain.cycle.CycleVolumeIntent
import com.meucoach.core.domain.cycle.CycleWeek
import com.meucoach.core.domain.cycle.CycleWeekState
import com.meucoach.core.domain.prescription.MesoPhase
import com.meucoach.core.domain.prescription.MesoPhaseLabels
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** pt-BR copy and date formatting of the cycle sheet (ADR 0072). */
internal object CycleOverviewFormat {
    private val locale = Locale.forLanguageTag("pt-BR")
    private val month = DateTimeFormatter.ofPattern("MMM", locale)
    private val fullMonth = DateTimeFormatter.ofPattern("MMMM", locale)

    fun phase(phase: MesoPhase?): String = phase?.let(MesoPhaseLabels::of) ?: "Ciclo geral"

    fun weekState(week: CycleWeek): String = when {
        week.state == CycleWeekState.RACE -> "Prova"
        week.isCurrent -> "Esta semana"
        week.isPreview -> "Prévia"
        week.isNext -> "Próxima"
        else -> "A confirmar"
    }

    fun volume(intent: CycleVolumeIntent): String = when (intent) {
        CycleVolumeIntent.BUILD -> "Constrói"
        CycleVolumeIntent.MAINTAIN -> "Estável"
        CycleVolumeIntent.REDUCE -> "Reduz"
        CycleVolumeIntent.RACE -> "Prova"
        CycleVolumeIntent.RECOVER -> "Recupera"
        CycleVolumeIntent.UNKNOWN -> "Segue os sinais"
    }

    fun strong(week: CycleWeek): String {
        if (week.strong == CycleStrongIntent.MICRO_DOSE) {
            val days = week.strongDays.joinToString(" e ") { dayLabel(it) }
            return if (days.isEmpty()) "1 estímulo curto" else "1 estímulo curto: $days"
        }
        if (week.strongDays.isNotEmpty()) {
            val days = week.strongDays.joinToString(" e ") { dayLabel(it) }
            return if (week.strongDays.size == 1) {
                "1 treino forte: $days"
            } else {
                "${week.strongDays.size} treinos fortes: $days"
            }
        }
        return when (week.strong) {
            CycleStrongIntent.NONE -> "Sem treino forte"
            CycleStrongIntent.MICRO_DOSE -> "1 estímulo curto"
            CycleStrongIntent.UP_TO_ONE -> "Até 1 treino forte"
            CycleStrongIntent.UP_TO_TWO -> "Até 2 treinos fortes"
        }
    }

    fun deload(deload: CycleDeload): String? = when (deload) {
        CycleDeload.NONE -> null
        CycleDeload.CONFIRMED -> "Descarga"
        CycleDeload.CONDITIONAL -> "Descarga a confirmar"
    }

    fun minutes(week: CycleWeek): String? = week.plannedMinutes?.let { "$it min" }

    fun weekRange(weekStart: LocalDate): String {
        val end = weekStart.plusDays(6)
        return if (weekStart.month == end.month) {
            "${weekStart.dayOfMonth}–${end.dayOfMonth} ${monthShort(end)}"
        } else {
            "${weekStart.dayOfMonth} ${monthShort(weekStart)}–" +
                "${end.dayOfMonth} ${monthShort(end)}"
        }
    }

    fun weekDescription(weekStart: LocalDate): String {
        val end = weekStart.plusDays(6)
        val startMonth = weekStart.format(fullMonth)
        val endMonth = end.format(fullMonth)
        return if (startMonth == endMonth) {
            "Semana de ${weekStart.dayOfMonth} a ${end.dayOfMonth} de $endMonth"
        } else {
            "Semana de ${weekStart.dayOfMonth} de $startMonth a " +
                "${end.dayOfMonth} de $endMonth"
        }
    }

    fun fullDate(date: LocalDate): String = "${date.dayOfMonth} de ${date.format(fullMonth)}"

    fun raceHeadline(overview: CycleOverview): String {
        val race = overview.race ?: return "Sem prova planejada"
        return "${distance(race.distance.meters)} · ${race.targetDate.dayOfMonth} de " +
            race.targetDate.format(fullMonth)
    }

    fun raceCountdown(overview: CycleOverview): String {
        val race = overview.race ?: return "Sem prova planejada"
        return when (race.weeksToRace) {
            0 -> "A prova é esta semana."
            1 -> "Falta 1 semana para a prova."
            else -> "Faltam ${race.weeksToRace} semanas para a prova."
        }
    }

    fun milestone(milestone: CycleMilestone): String = when (milestone.kind) {
        CycleMilestoneKind.PREPARATION_START -> "Preparação específica"
        CycleMilestoneKind.TAPER_START -> "Redução para a prova"
        CycleMilestoneKind.DELOAD -> if (milestone.conditional) "Descarga a confirmar" else "Descarga"
        CycleMilestoneKind.RACE -> "Prova"
    }

    fun milestoneDescription(milestone: CycleMilestone): String {
        val week = weekDescription(milestone.weekStart).replaceFirstChar { it.lowercase(locale) }
        return when (milestone.kind) {
            CycleMilestoneKind.PREPARATION_START -> "A preparação específica começa na $week."

            CycleMilestoneKind.TAPER_START -> "A redução para a prova começa na $week."

            CycleMilestoneKind.DELOAD -> if (milestone.conditional) {
                "A descarga pode cair na $week."
            } else {
                "A descarga está marcada na $week."
            }

            CycleMilestoneKind.RACE -> "A prova é na $week."
        }
    }

    fun deloadCondition(weeks: Int): String = if (weeks == 1) {
        "Se você completar esta semana de carga, a descarga cai aqui."
    } else {
        "Se você completar as próximas $weeks semanas de carga, a descarga cai aqui."
    }

    fun weekAccessibility(week: CycleWeek): String = buildString {
        append(weekDescription(week.weekStart))
        append(", ")
        append(phase(week.phase))
        append(", ")
        append(volume(week.volume))
        append(", ")
        append(strongDescription(week))
        deload(week.deload)?.let { append(", $it") }
        append(", ")
        append(weekState(week))
    }

    fun strongDescription(week: CycleWeek): String = when {
        week.strongDays.isNotEmpty() -> strong(week)
        week.strong == CycleStrongIntent.NONE -> "Sem treino forte"
        week.strong == CycleStrongIntent.MICRO_DOSE -> "Um estímulo curto"
        week.strong == CycleStrongIntent.UP_TO_ONE -> "Até um treino forte"
        week.strong == CycleStrongIntent.UP_TO_TWO -> "Até dois treinos fortes"
        else -> "Sem treino forte"
    }

    private fun monthShort(date: LocalDate): String = date.format(month).replace(".", "")

    private fun dayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "seg"
        2 -> "ter"
        3 -> "qua"
        4 -> "qui"
        5 -> "sex"
        6 -> "sáb"
        else -> "dom"
    }

    private fun distance(meters: Double): String = when {
        meters >= 42_000 -> "Maratona"
        meters >= 21_000 -> "Meia maratona"
        else -> "${(meters / 1_000).toInt()} km"
    }
}
