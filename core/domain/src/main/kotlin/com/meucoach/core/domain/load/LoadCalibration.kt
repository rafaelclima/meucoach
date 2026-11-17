package com.meucoach.core.domain.load

/**
 * FR-405 / ADR 0046 §2.4: a ratio computed over an incomplete 28-day window is
 * informative, never a verdict. The UI shows it as "em calibração" with the
 * objective baseline progress ("N de 28 dias de histórico"), never as
 * "carga subiu rápido".
 *
 * Returns null when the ratio is reliable (nothing to declare) or when there
 * is no fresh objective history to report — a stale source says "desatualizado"
 * and an empty one falls back to the existing "insufficient data" copy.
 */
fun LoadSnapshot.calibrationNote(): String? {
    if (acwrReliable) return null
    if (!coverage.sourceFresh || baselineDays <= 0) return null
    return "$baselineDays de ${coverage.daysInWindow} dias de histórico; ACWR em calibração"
}
