package com.meucoach.core.domain.coach

import java.time.Instant
import java.time.LocalDate

/**
 * Read-only view of one validated Coach turn (Room v19, ADR 0052).
 *
 * The history is copy the athlete can read and erase; it never becomes input for
 * load, pace or planning, because the engine remains the only authority.
 */
data class CoachMemoryMessage(
    val id: String,
    val kind: CoachMessageKind,
    val title: String,
    val body: String,
    val bullets: List<String>,
    val createdAt: Instant,
)

/** Read-only view of the validated narrative of one closed week. */
data class CoachMemorySummary(
    val weekStart: LocalDate,
    val title: String,
    val body: String,
    val bullets: List<String>,
    val createdAt: Instant,
)
