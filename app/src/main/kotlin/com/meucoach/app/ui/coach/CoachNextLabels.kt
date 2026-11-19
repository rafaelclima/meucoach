package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachNextContext
import com.meucoach.app.ui.common.readinessLabel
import com.meucoach.app.ui.review.WeeklyReviewFormat
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.QualityBlockReason
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.model.ReadinessLevel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Friendly copy for the next-event card (PRD evolução, F1).
 *
 * The engine stays the authority: the headline follows the persisted
 * prescription when one exists (the plan intent becomes a secondary line), a
 * race never needs an artificial session, and every block reason or unlock
 * action is mapped here. Raw `unlockHint` and technical IDs never reach the
 * screen.
 */
object CoachNextLabels {
    fun dayLabel(today: LocalDate, date: LocalDate, isToday: Boolean): String = when {
        isToday || date == today -> "Hoje"
        date == today.plusDays(1) -> "Amanhã"
        else -> weekday(date.dayOfWeek)
    }

    fun headline(context: CoachNextContext): String {
        val subject = context.subject ?: return "Próximo treino"
        val day = dayLabel(context.today, subject.date, context.isToday)
        return when (subject) {
            is CoachNextSubject.Race -> "$day · Dia de prova"

            is CoachNextSubject.Training -> {
                val prescription = context.prescription
                // Without a persisted decision the plan intent is labelled as
                // such, never presented as if the engine had confirmed it.
                val prefix = if (prescription == null) "Planejado: " else ""
                val title = prescription?.title ?: subject.session.title
                // A prescription with no duration (rest) must never inherit the
                // planned duration: the prescribed day is the truth.
                val duration = (
                    if (prescription != null) {
                        prescription.prescribedDurationMin
                    } else {
                        subject.session.durationMin
                    }
                    )?.let { " · $it min" }.orEmpty()
                "$day · $prefix$title$duration"
            }
        }
    }

    /** The plan intent, shown only when the prescription diverged from it. */
    fun plannedLine(context: CoachNextContext): String? {
        val training = context.training ?: return null
        val prescription = context.prescription ?: return null
        if (!prescription.differsFromPlan) return null
        val title = prescription.plannedTitle ?: training.session.title
        val duration = prescription.plannedDurationMin?.let { " · $it min" }.orEmpty()
        return "Planejado: $title$duration."
    }

    fun statusLine(context: CoachNextContext): String = when (val subject = context.subject) {
        null -> "Semana concluída."
        is CoachNextSubject.Race -> raceLine(context.today, subject.event.targetDate)
        is CoachNextSubject.Training -> trainingLine(context)
    }

    /**
     * One deterministic action line. `unlockHint` is deliberately not rendered:
     * it may carry ACWR values or engine copy, so the reason picks safe copy.
     * A missing check-in without a stored block is handled by the card button
     * and the status line, without repeating the same instruction twice.
     */
    fun actionLine(context: CoachNextContext): String? {
        val reason = context.prescription?.qualityBlockReason ?: return null
        return unlockCopy(reason)
    }

    /**
     * Readiness is a today-only signal. A day without any measured component
     * says so instead of showing the calculator's neutral 50 as a level.
     */
    fun readinessLine(context: CoachNextContext): String? {
        if (!context.isToday) return null
        val result = context.readiness ?: return null
        if (ReadinessFlag.INSUFFICIENT_DATA in result.flags) {
            return "Prontidão ainda não medida — faça o check-in de 15 s."
        }
        val level = readinessLabel(result.level)
        val reasons = buildList {
            if (ReadinessFlag.ILLNESS_OR_INJURY in result.flags) {
                add("sinal de doença ou lesão")
            } else {
                if (ReadinessFlag.SHORT_SLEEP in result.flags) {
                    val sleep = context.sleepMinutes
                    add(if (sleep == null) "sono curto" else "sono ${WeeklyReviewFormat.minutes(sleep)}")
                }
                if (!context.hasCheckIn) add("sem check-in")
            }
        }
        val suffix = if (reasons.isEmpty()) "" else " — ${reasons.joinToString(" · ")}"
        return "Prontidão $level · ${result.score}/100$suffix."
    }

    /** Level for the semantic dot; null when readiness is missing or unmeasured. */
    fun readinessLevel(context: CoachNextContext): ReadinessLevel? {
        if (!context.isToday) return null
        val result = context.readiness ?: return null
        if (ReadinessFlag.INSUFFICIENT_DATA in result.flags) return null
        return result.level
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

    /** What unlocks the strong session, derived from the reason, never from the raw hint. */
    fun unlockCopy(reason: QualityBlockReason): String = when (reason) {
        QualityBlockReason.SUBJECTIVE_MISSING -> "Faça o check-in de hoje para liberar o treino forte."
        QualityBlockReason.SHORT_SLEEP -> "Com uma noite cheia de sono, o treino forte pode voltar."
        QualityBlockReason.LOW_READINESS -> "Quando a prontidão subir, a qualidade volta."
        QualityBlockReason.ILLNESS_OR_RED_READINESS -> "Volte ao treino forte quando estiver recuperado."
        QualityBlockReason.BEGINNER_GUARD -> "O treino forte entra com a graduação de nível."
        QualityBlockReason.RECOVERY_WINDOW -> "A qualidade volta ao fim da recuperação pós-prova."
        QualityBlockReason.TAPER_WINDOW -> "A qualidade volta depois da prova."
        QualityBlockReason.RACE_WEEK -> "A qualidade volta depois da prova."
        QualityBlockReason.LOAD_STALE -> "Importe suas corridas para atualizar a carga."
        QualityBlockReason.LOAD_INSUFFICIENT -> "A qualidade volta quando a base de carga fechar a calibração."
        QualityBlockReason.MONOTONY_HIGH -> "Varie os estímulos para a qualidade voltar."
        QualityBlockReason.ACWR_HIGH -> "A qualidade volta quando a carga estiver na faixa."
        QualityBlockReason.STRAIN_SPIKE -> "A qualidade volta quando a carga estiver na faixa."
    }

    private fun trainingLine(context: CoachNextContext): String {
        val prescription = context.prescription ?: return plannedCopy(context)
        val base = when (prescription.adjustment) {
            PrescriptionAdjustment.KEEP -> "Mantido como planejado."
            PrescriptionAdjustment.REDUCE -> "Reduzido para o dia."
            PrescriptionAdjustment.DOWNGRADE -> "Ajustado para fácil."
            PrescriptionAdjustment.REST -> "Descanso prescrito."
            PrescriptionAdjustment.NO_PLAN -> "Fácil conservador."
        }
        val reason = prescription.qualityBlockReason ?: return base
        return "$base ${blockCopy(reason)}"
    }

    private fun plannedCopy(context: CoachNextContext): String = when {
        !context.isToday && context.subject?.date == context.today.plusDays(1) ->
            "Planejado; a confirmação sai no check-in da manhã."

        !context.isToday -> "Planejado. A decisão do dia sai no Hoje."

        context.hasCheckIn -> "A decisão de hoje ainda não saiu."

        else -> "A decisão de hoje ainda não saiu — faça o check-in."
    }

    private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

    private fun raceLine(today: LocalDate, raceDate: LocalDate): String = when (raceDate) {
        today -> "Dia de prova hoje — sem treino planejado."
        today.plusDays(1) -> "Dia de prova amanhã — sem treino planejado."
        else -> "Próxima prova em ${raceDate.format(dayFormat)} — sem treino planejado."
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
