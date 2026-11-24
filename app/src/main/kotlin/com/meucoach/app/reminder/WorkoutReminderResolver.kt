package com.meucoach.app.reminder

import com.meucoach.app.application.RefreshTodayPrescription
import com.meucoach.core.domain.today.TodayDecision
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the workout reminder from the same day authority as the Hoje screen
 * (ADR 0023).
 *
 * The weekly plan is only the intent; readiness, load, heat and a previous
 * downgrade must reach the notification, so the athlete never gets a reminder
 * for a session the app already adjusted.
 */
@Singleton
class WorkoutReminderResolver @Inject constructor(private val refresh: RefreshTodayPrescription) {
    suspend fun message(today: LocalDate): ReminderMessage? {
        val authority = refresh() ?: return null
        return when (val decision = authority.decision) {
            is TodayDecision.Competition -> ReminderContent.race(decision.event)

            is TodayDecision.Training -> {
                val plan = authority.plan ?: return null
                val date = authority.today.takeIf { it == today } ?: today
                ReminderContent.workout(date, plan, decision.prescription)
            }

            null -> null
        }
    }
}
