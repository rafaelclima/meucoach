package com.meucoach.app.reminder

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.model.AthleteProfile
import java.time.DayOfWeek
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface ReminderScheduler {
    fun schedule(profile: AthleteProfile? = null)
}

/** Local wall-clock time of a daily reminder. */
data class ReminderClock(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23) { "Reminder hour must be between 0 and 23" }
        require(minute in 0..59) { "Reminder minute must be between 0 and 59" }
    }
}

/**
 * Derives reminder times from the athlete's usual training time.
 *
 * The check-in is the first prompt, 30 min before training; the workout
 * reminder follows 15 min before. Without a declared time the legacy 07:00 and
 * 18:00 are kept, so existing users see no unrequested change.
 */
object ReminderTimes {
    const val CHECK_IN_LEAD_MINUTES = 30
    const val WORKOUT_LEAD_MINUTES = 15
    const val DEFAULT_WORKOUT_MINUTES = 7 * 60
    const val DEFAULT_CHECK_IN_MINUTES = 18 * 60

    fun checkIn(profile: AthleteProfile?): ReminderClock =
        clock(profile?.preferredTrainingTimeMinutes, CHECK_IN_LEAD_MINUTES, DEFAULT_CHECK_IN_MINUTES)

    fun workout(profile: AthleteProfile?): ReminderClock =
        clock(profile?.preferredTrainingTimeMinutes, WORKOUT_LEAD_MINUTES, DEFAULT_WORKOUT_MINUTES)

    private fun clock(trainingMinutes: Int?, leadMinutes: Int, fallbackMinutes: Int): ReminderClock {
        val minutes = trainingMinutes?.let { (it - leadMinutes).coerceAtLeast(0) } ?: fallbackMinutes
        return ReminderClock(hour = minutes / 60, minute = minutes % 60)
    }
}

class WorkManagerReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val timeProvider: TimeProvider,
) : ReminderScheduler {
    override fun schedule(profile: AthleteProfile?) {
        val checkIn = ReminderTimes.checkIn(profile)
        val workout = ReminderTimes.workout(profile)
        schedule(kind = ReminderKind.CHECK_IN, hour = checkIn.hour, minute = checkIn.minute)
        schedule(kind = ReminderKind.WORKOUT, hour = workout.hour, minute = workout.minute)
        scheduleWeekly(
            kind = ReminderKind.WEEK_PREVIEW,
            dayOfWeek = DayOfWeek.SUNDAY,
            hour = PREVIEW_HOUR,
            minute = PREVIEW_MINUTE,
        )
    }

    private fun schedule(kind: ReminderKind, hour: Int, minute: Int) {
        val now = timeProvider.now().atZone(timeProvider.zoneId.value)
        val delay = ReminderContent.nextDelay(now, hour, minute)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay)
            .setInputData(workDataOf(ReminderWorker.KEY_KIND to kind.name))
            .addTag(ReminderWorker.TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            workerName(kind),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun scheduleWeekly(kind: ReminderKind, dayOfWeek: DayOfWeek, hour: Int, minute: Int) {
        val now = timeProvider.now().atZone(timeProvider.zoneId.value)
        val delay = ReminderContent.nextWeeklyDelay(now, dayOfWeek, hour, minute)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay)
            .setInputData(workDataOf(ReminderWorker.KEY_KIND to kind.name))
            .addTag(ReminderWorker.TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            workerName(kind),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun workerName(kind: ReminderKind): String = "meucoach-reminder-${kind.name.lowercase()}"

    companion object {
        const val CHECK_IN_HOUR = 18
        const val WORKOUT_HOUR = 7
        const val PREVIEW_HOUR = 18
        const val PREVIEW_MINUTE = 0
    }
}
