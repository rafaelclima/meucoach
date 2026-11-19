package com.meucoach.app.coach

import javax.inject.Inject

/**
 * Deterministic Coach sources grouped in one dependency (P-09).
 *
 * The ViewModel stays focused on state orchestration; Hilt resolves each
 * source interface through the existing bindings.
 */
data class CoachSources @Inject constructor(
    val plans: CoachPlanSource,
    val reviews: CoachReviewSource,
    val next: CoachNextSource,
    val guards: CoachGuardsSource,
    val progress: CoachProgressSource,
)
