package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachNextContext

/**
 * Short ack for the post-import system turn (PRD_COACH_CHAT F4, second slice).
 *
 * Pure and local: it only confirms that today's import already feeds the load
 * the cards describe. Without an import today — or without a plan to feed —
 * it returns null and the turn stays hidden.
 */
object CoachImportLabels {
    const val SYSTEM_LINE: String = "Importação feita ✓"

    fun ack(context: CoachNextContext): String? {
        if (!context.importedToday || !context.hasPlan) return null
        return "Recebi sua corrida. Já entrou na carga."
    }
}
