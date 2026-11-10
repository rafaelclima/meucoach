package com.meucoach.core.domain.today

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.model.RaceEvent
import java.time.LocalDate

/**
 * The single daily authority exposed to every surface (ADR 0030/0023).
 *
 * Exactly one branch is returned: a normal training prescription or a race
 * event. Competition wins even when a legacy prescription exists for the date;
 * the legacy prescription is neither deleted nor rewritten.
 */
sealed interface TodayDecision {
    data class Training(val prescription: DailyPrescription) : TodayDecision

    data class Competition(val event: RaceEvent) : TodayDecision
}

object TodayDecisionResolver {
    /** Returns `null` only when there is neither an event nor a prescription (no profile). */
    fun resolve(date: LocalDate, event: RaceEvent?, prescription: DailyPrescription?): TodayDecision? {
        require(event == null || event.targetDate == date) {
            "Race event date must match the day decision date"
        }
        return when {
            event != null -> TodayDecision.Competition(event)
            prescription != null -> TodayDecision.Training(prescription)
            else -> null
        }
    }
}
