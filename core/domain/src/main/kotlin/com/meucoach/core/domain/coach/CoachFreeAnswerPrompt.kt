package com.meucoach.core.domain.coach

import java.time.DayOfWeek

/**
 * Payload the app sends to the `free-answer` proxy kind (ADR 0056/0063).
 *
 * It carries the parser intent with validated slots, the sanitized question
 * the athlete typed and the numbers the motor already decided, so the answer
 * can only cite those. [engineContext] is deterministic engine copy
 * (headline/rationale/facts), never anything the athlete typed. The question
 * is sent only after the local safety gate and only with opt-in; the app never
 * adds name, GPS or exact timestamps (the typed question may contain whatever
 * the athlete wrote).
 */
data class CoachFreeAnswerPrompt(
    val inputsHash: String,
    val locale: String = "pt-BR",
    val intent: FreeInputIntent,
    val dayOfWeek: DayOfWeek? = null,
    val minutes: Int? = null,
    val allowedMinutes: Set<Int>,
    val allowedPercents: Set<Int>,
    val allowedDistancesKm: Set<Double>,
    /** Bare numbers (counts, days, weeks) the answer may cite. */
    val allowedCounts: Set<Int> = emptySet(),
    /** Sanitized typed question; closed to `null` only for legacy callers. */
    val question: String? = null,
    val engineContext: String,
) {
    init {
        require(inputsHash.isNotBlank()) { "inputsHash must not be blank" }
        require(engineContext.isNotBlank()) { "engineContext must not be blank" }
        require(intent != FreeInputIntent.ASK_COACHING || !question.isNullOrBlank()) {
            "An open answer requires the sanitized question"
        }
    }
}
