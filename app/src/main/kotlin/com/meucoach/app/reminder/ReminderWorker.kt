package com.meucoach.app.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val checkIns: CheckInRepository,
    private val plans: TrainingPlanRepository,
    private val profiles: ProfileRepository,
    private val notifier: ReminderNotifier,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.failure()
        return runCatching {
            val profile = profiles.getProfile()
            val zone = profile
                ?.let { runCatching { ZoneId.of(it.timezone) }.getOrNull() }
                ?: ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val message = when (kind) {
                ReminderKind.CHECK_IN.name -> ReminderContent.checkIn(today, checkIns.get(today))

                ReminderKind.WORKOUT.name -> {
                    val weekStart = today.with(
                        TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY),
                    )
                    ReminderContent.workout(today, plans.getWeek(weekStart))
                }

                else -> null
            }
            message?.let(notifier::notify)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val TAG = "meucoach-reminder"
        const val KEY_KIND = "reminderKind"
    }
}
