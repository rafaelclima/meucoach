package com.meucoach.app.reminder

import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DailyCheckIn
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

const val DEEP_LINK_CHECK_IN = "meucoach://check-in"
const val DEEP_LINK_TODAY = "meucoach://today"

enum class ReminderKind {
    CHECK_IN,
    WORKOUT,
}

data class ReminderMessage(
    val id: Int,
    val kind: ReminderKind,
    val title: String,
    val body: String,
    val deepLink: String,
)

object ReminderContent {
    const val CHECK_IN_ID = 1_001
    const val WORKOUT_ID = 1_002

    fun checkIn(today: LocalDate, checkIn: DailyCheckIn?): ReminderMessage? {
        if (checkIn?.date == today) return null
        return ReminderMessage(
            id = CHECK_IN_ID,
            kind = ReminderKind.CHECK_IN,
            title = "Check-in de hoje",
            body = "15 s para registrar sono, energia e dor. Isso ajusta o treino de amanhã.",
            deepLink = DEEP_LINK_CHECK_IN,
        )
    }

    fun workout(today: LocalDate, plan: WeeklyPlan?): ReminderMessage? {
        val session = plan?.sessions?.firstOrNull { it.date == today } ?: return null
        if (session.status != SessionStatus.PLANNED) return null
        if (session.type == WorkoutType.REST) return null
        val duration = session.durationMin?.let { " · $it min" } ?: ""
        return ReminderMessage(
            id = WORKOUT_ID,
            kind = ReminderKind.WORKOUT,
            title = "Treino de hoje: ${session.title}",
            body = "$duration${if (duration.isEmpty()) "" else " · "}${session.rationale}",
            deepLink = DEEP_LINK_TODAY,
        )
    }

    /** Delay until the next occurrence of [hour]:[minute] in the given zone. */
    fun nextDelay(now: ZonedDateTime, hour: Int, minute: Int = 0): Duration {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        var target = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target)
    }
}
