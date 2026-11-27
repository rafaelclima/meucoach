package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachNextContext
import com.meucoach.core.domain.coach.CoachNextSubject

/**
 * Data-driven opening line for the Coach chat (PRD_COACH_CHAT F4, first slice).
 *
 * The bubble greets by time of day and points at what matters using only the
 * [CoachNextContext] the screen already has: race day, tomorrow's session
 * (planned, never presented as prescribed) or a missing check-in. Anything
 * else stays with the short generic invite — the cards below carry the detail.
 * Every variant fits in a single ≤140 bubble.
 */
object CoachOpeningLabels {
    fun greeting(hour: Int): String = when (hour) {
        in 5..11 -> "Bom dia!"
        in 12..17 -> "Boa tarde!"
        else -> "Boa noite!"
    }

    fun opening(context: CoachNextContext, hour: Int): String {
        val hello = greeting(hour)
        return when (val subject = context.subject) {
            is CoachNextSubject.Race -> when (subject.event.targetDate) {
                context.today -> "$hello Hoje é dia de prova. Sem treino — boa prova!"
                context.today.plusDays(1) -> "$hello Amanhã é dia de prova. Dorme bem hoje que conta."
                else -> "Oi! Bora ver o que importa?"
            }

            is CoachNextSubject.Training -> when {
                !context.isToday -> {
                    val day = CoachNextLabels.dayLabel(context.today, subject.date, false)
                    val duration = subject.session.durationMin?.let { ", $it min" }.orEmpty()
                    val workout = subject.session.title.replaceFirstChar { it.lowercase() }
                    "$hello $day tem $workout$duration. Dorme bem hoje que conta."
                }

                context.prescription == null && !context.hasCheckIn ->
                    "$hello Falta seu check-in de 15 s pra eu liberar o treino de hoje."

                else -> "Oi! Bora ver o que importa?"
            }

            null -> "Oi! Bora ver o que importa?"
        }
    }
}
