package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachNextContext
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.QualityBlockReason
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Friendly copy for the next-session card (PRD evolução, F1).
 *
 * The engine stays the authority: every line derives from the persisted
 * session, the latest persisted prescription of its date or a race event on
 * the date. No number is invented and no block reason is exposed raw.
 */
object CoachNextLabels {
    fun dayLabel(today: LocalDate, date: LocalDate, isToday: Boolean): String = when {
        isToday || date == today -> "Hoje"
        date == today.plusDays(1) -> "Amanhã"
        else -> weekday(date.dayOfWeek)
    }

    fun headline(context: CoachNextContext): String {
        if (context.raceEvent != null) return "Dia de prova"
        val day = dayLabel(context.today, context.session.date, context.isToday)
        val duration = context.session.durationMin?.let { " · $it min" }.orEmpty()
        return "$day · ${context.session.title}$duration"
    }

    fun statusLine(context: CoachNextContext): String {
        if (context.raceEvent != null) return "Dia de prova — sem treino planejado."
        val prescription = context.prescription
            ?: return "Planejado. A decisão do dia sai no Hoje."
        val base = when (prescription.adjustment) {
            PrescriptionAdjustment.KEEP -> "Mantido como planejado."
            PrescriptionAdjustment.REDUCE -> "Reduzido para o dia."
            PrescriptionAdjustment.DOWNGRADE -> "Ajustado para fácil."
            PrescriptionAdjustment.REST -> "Descanso."
            PrescriptionAdjustment.NO_PLAN -> "Fácil conservador."
        }
        val reason = prescription.qualityBlockReason ?: return base
        val hint = prescription.unlockHint?.let { " $it" }.orEmpty()
        return "$base ${blockCopy(reason)}$hint"
    }

    fun blockCopy(reason: QualityBlockReason): String = when (reason) {
        QualityBlockReason.ILLNESS_OR_RED_READINESS -> "Dia de cuidado: o corpo pede descanso."
        QualityBlockReason.BEGINNER_GUARD -> "Base em construção: o treino forte chega com a graduação."
        QualityBlockReason.SUBJECTIVE_MISSING -> "Falta seu check-in de hoje."
        QualityBlockReason.SHORT_SLEEP -> "Sono curto: sem treino forte hoje."
        QualityBlockReason.LOW_READINESS -> "Recuperação parcial: dia leve."
        QualityBlockReason.RECOVERY_WINDOW -> "Em recuperação pós-prova."
        QualityBlockReason.TAPER_WINDOW -> "Semana de taper: volume reduzido."
        QualityBlockReason.RACE_WEEK -> "Semana de prova: micro-dose mantida."
        QualityBlockReason.LOAD_STALE -> "Dados de carga desatualizados."
        QualityBlockReason.LOAD_INSUFFICIENT -> "Base de carga em calibração."
        QualityBlockReason.MONOTONY_HIGH -> "Semana monótona: varie os estímulos."
        QualityBlockReason.ACWR_HIGH -> "Carga alta: dia leve."
        QualityBlockReason.STRAIN_SPIKE -> "Pico de esforço: dia leve."
    }

    private fun weekday(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "SEG"
        DayOfWeek.TUESDAY -> "TER"
        DayOfWeek.WEDNESDAY -> "QUA"
        DayOfWeek.THURSDAY -> "QUI"
        DayOfWeek.FRIDAY -> "SEX"
        DayOfWeek.SATURDAY -> "SÁB"
        DayOfWeek.SUNDAY -> "DOM"
    }
}
