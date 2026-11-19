package com.meucoach.core.domain.prescription

import com.meucoach.core.model.Experience

/**
 * Initial level hint for a fresh profile (ADR 0053 §2.3).
 *
 * Pure and conservative: only suggests INTERMEDIATE when the profile is still
 * BEGINNER but already carries a recent VDOT with provenance (the cold-start
 * allowance is valid). Never auto-applies; the change stays explicit in
 * Perfil/Onboarding and participates in T4 via EvaluateReplan.
 */
object InitialExperienceSuggester {
    data class Suggestion(val to: Experience, val reason: String)

    fun suggest(current: Experience, coldStartValid: Boolean): Suggestion? {
        if (current != Experience.BEGINNER) return null
        if (!coldStartValid) return null
        return Suggestion(
            to = Experience.INTERMEDIATE,
            reason = "Com prova ou estimativa recente com VDOT, " +
                "considere Intermediário para liberar 1 treino forte curto por semana.",
        )
    }
}
