package com.meucoach.core.domain.coach

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import java.time.LocalDate

/**
 * What the Coach tab shows as the next relevant event (PRD evolução, F1).
 *
 * A race is a subject on its own: the weekly generator removes sessions from
 * the race date, so a race state can never rely on an artificial session.
 */
sealed interface CoachNextSubject {
    val date: LocalDate

    data class Training(val session: PlannedSession) : CoachNextSubject {
        override val date: LocalDate get() = session.date
    }

    data class Race(val event: RaceEvent) : CoachNextSubject {
        override val date: LocalDate get() = event.targetDate
    }
}

data class CoachNextSession(val subject: CoachNextSubject, val isToday: Boolean)

/**
 * Next relevant event for the Coach tab (PRD evolução, F1).
 *
 * Read-only pick from the persisted week and the active race event: today
 * `PLANNED` wins while pending, otherwise the earliest future `PLANNED`
 * session. `DONE`/`CLOSED` are history and past `PLANNED` rows are missed work
 * this resolver never reopens. A race today or tomorrow wins; a later race
 * wins only when it happens before the next session (or when nothing else is
 * left). It never computes or persists a prescription; the day authority stays
 * in `DailyPrescription`/`GetTodayDecision`.
 */
object CoachNextResolver {
    fun resolve(
        sessions: List<PlannedSession>,
        today: LocalDate,
        races: List<RaceEvent> = emptyList(),
    ): CoachNextSession? {
        val nextSession = nextSession(sessions, today)
        val nextRace = races
            .filter { it.status == RaceStatus.PLANNED && !it.targetDate.isBefore(today) }
            .minByOrNull { it.targetDate }
        val subject = when {
            nextRace != null && raceHasPrecedence(nextRace, nextSession, today) -> CoachNextSubject.Race(nextRace)
            nextSession != null -> CoachNextSubject.Training(nextSession)
            else -> null
        }
        return subject?.let { CoachNextSession(it, isToday = it.date == today) }
    }

    private fun nextSession(sessions: List<PlannedSession>, today: LocalDate): PlannedSession? {
        sessions.firstOrNull { it.date == today && it.status == SessionStatus.PLANNED }
            ?.let { return it }
        return sessions
            .filter { it.date.isAfter(today) && it.status == SessionStatus.PLANNED }
            .minByOrNull { it.date }
    }

    /**
     * Race precedence follows `GetTodayDecision`: the event is the authority of
     * its day. Today/tomorrow always surface (the eve must say "Amanhã é prova")
     * and an earlier race beats the next workout; a workout before a later race
     * still wins so the card never hides the next session.
     */
    private fun raceHasPrecedence(race: RaceEvent, nextSession: PlannedSession?, today: LocalDate): Boolean {
        if (race.targetDate == today || race.targetDate == today.plusDays(1)) return true
        val sessionDate = nextSession?.date ?: return true
        return race.targetDate.isBefore(sessionDate)
    }
}
