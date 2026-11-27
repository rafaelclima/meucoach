package com.meucoach.core.domain.prescription

/**
 * Canonical user-facing names of [MesoPhase] (ADR 0071 §2.2).
 *
 * The engine never interpolates the enum name into user text; the plan
 * rationale and the cycle projection share this single mapping so both always
 * say the same thing.
 */
object MesoPhaseLabels {
    fun of(phase: MesoPhase): String = when (phase) {
        MesoPhase.BASE -> "Base"
        MesoPhase.BUILD -> "Construção"
        MesoPhase.PEAK -> "Pico"
        MesoPhase.TAPER -> "Semana de prova"
        MesoPhase.RECOVERY -> "Recuperação"
    }
}
