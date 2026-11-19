package com.meucoach.core.domain.coach

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import java.time.LocalDate

/**
 * Next relevant session for the Coach tab (PRD evolução, F1).
 *
 * Read-only pick from the persisted week: today `PLANNED` wins while pending,
 * otherwise the earliest future `PLANNED` session. `DONE`/`CLOSED` are history
 * and past `PLANNED` rows are missed work this resolver never reopens. It
 * never computes or persists a prescription; the day authority stays in
 * `DailyPrescription`/`GetTodayDecision`.
 */
data class CoachNextSession(val session: PlannedSession, val isToday: Boolean)

object CoachNextResolver {
    fun resolve(sessions: List<PlannedSession>, today: LocalDate): CoachNextSession? {
        sessions.firstOrNull { it.date == today && it.status == SessionStatus.PLANNED }
            ?.let { return CoachNextSession(it, isToday = true) }
        val next = sessions
            .filter { it.date.isAfter(today) && it.status == SessionStatus.PLANNED }
            .minByOrNull { it.date }
        return next?.let { CoachNextSession(it, isToday = false) }
    }
}
