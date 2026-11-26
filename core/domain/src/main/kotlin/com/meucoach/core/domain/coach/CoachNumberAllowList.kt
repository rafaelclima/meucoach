package com.meucoach.core.domain.coach

/**
 * Typed numeric facts the Coach may quote for one answer (COACH-10/U-1/U-3).
 *
 * Each category is independent: a minute only authorizes a `NN min` token, a
 * percent only authorizes a `NN%` token, and a bare integer only authorizes a
 * count. A decimal without a unit needs an explicit entry in [decimals]. An
 * empty set means "no number of this kind was computed", never "unchecked".
 */
data class CoachNumberAllowList(
    val minutes: Set<Int> = emptySet(),
    val percents: Set<Int> = emptySet(),
    val distancesKm: Set<Double> = emptySet(),
    val counts: Set<Int> = emptySet(),
    val decimals: Set<Double> = emptySet(),
)
