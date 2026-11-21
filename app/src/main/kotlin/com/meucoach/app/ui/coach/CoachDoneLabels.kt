package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachNextContext
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.prescription.SessionStatus

/**
 * Short cheer for the post-workout system turn (PRD_COACH_CHAT F4, second slice).
 *
 * Pure and local: it only celebrates a `DONE` status the plan already carries.
 * No pace, no load, no judgment — the card below keeps the numbers. Anything
 * else (pending, missing, race day) returns null and the turn stays hidden.
 */
object CoachDoneLabels {
    const val SYSTEM_LINE: String = "Treino feito ✓"

    fun cheer(context: CoachNextContext): String? {
        val done = context.todaySession?.takeIf { it.status == SessionStatus.DONE } ?: return null
        if (context.subject is CoachNextSubject.Race) return null
        return "Boa! ${done.title} feito."
    }
}
