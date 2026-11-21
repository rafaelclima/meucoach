package com.meucoach.core.domain.coach

import java.time.DayOfWeek

/**
 * Payload the app sends to the `free-answer` proxy kind (ADR 0056 fatia 4).
 *
 * It carries the parser intent with validated slots plus the numbers the
 * motor already decided, so the answer can only cite those. There is
 * deliberately no field for user-typed text: it never leaves the device.
 * [engineContext] is deterministic engine copy (headline/rationale), never
 * anything the athlete typed.
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
    val engineContext: String,
) {
    init {
        require(inputsHash.isNotBlank()) { "inputsHash must not be blank" }
        require(engineContext.isNotBlank()) { "engineContext must not be blank" }
    }
}
