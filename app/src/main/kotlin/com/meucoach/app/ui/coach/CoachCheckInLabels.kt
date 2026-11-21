package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachNextContext
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.prescription.PrescriptionAdjustment

/**
 * Short ack for the check-in system turn (PRD_COACH_CHAT F4, first slice).
 *
 * Pure and local: it only rephrases what the [CoachNextContext] already says.
 * A kept quality session points back to the plan; a blocked one reuses the
 * same safe copy as the card; without a persisted prescription the decision
 * is honestly still in the Hoje tab. Anything else (no check-in, eve, race)
 * returns null and the turn stays hidden.
 */
object CoachCheckInLabels {
    const val SYSTEM_LINE: String = "Check-in feito ✓"

    fun ack(context: CoachNextContext): String? {
        if (!context.hasCheckIn) return null
        if (!context.isToday) return null
        if (context.subject !is CoachNextSubject.Training) return null
        val prescription = context.prescription ?: return "Valeu pelo check-in! A decisão de hoje sai no Hoje."
        val reason = prescription.qualityBlockReason
        if (prescription.adjustment == PrescriptionAdjustment.KEEP && reason == null) {
            return "Valeu pelo check-in! Hoje segue como planejado."
        }
        val motive = if (reason != null) " ${CoachNextLabels.blockCopy(reason)}" else ""
        return "Valeu pelo check-in!$motive"
    }
}
