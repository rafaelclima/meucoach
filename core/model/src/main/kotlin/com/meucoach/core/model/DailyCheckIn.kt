package com.meucoach.core.model

import java.time.LocalDate

/**
 * Self-reported heat exposure for the day. It is a manual signal, not a
 * measured temperature: the athlete reports "hot" when conditions are clearly
 * above 26 °C. Used only to adjust pace conservatively (`R-PLAN-07`).
 */
enum class HeatLevel {
    NONE,
    HOT,
}

data class DailyCheckIn(
    val date: LocalDate,
    val sleepQuality: Int,
    val fatigue: Int,
    val soreness: Int,
    val stress: Int,
    val illnessOrInjury: Boolean = false,
    val heatLevel: HeatLevel = HeatLevel.NONE,
) {
    init {
        require(sleepQuality in 1..5) { "Sleep quality must be between 1 and 5" }
        require(fatigue in 1..5) { "Fatigue must be between 1 and 5" }
        require(soreness in 1..5) { "Soreness must be between 1 and 5" }
        require(stress in 1..5) { "Stress must be between 1 and 5" }
    }

    /** Hooper index with sleep inverted: 5 = excellent, others 5 = bad. */
    val hooperSum: Int
        get() = (6 - sleepQuality) + fatigue + soreness + stress
}
