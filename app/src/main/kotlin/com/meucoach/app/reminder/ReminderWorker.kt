package com.meucoach.app.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val checkIns: CheckInRepository,
    private val plans: TrainingPlanRepository,
    private val timeProvider: TimeProvider,
    private val notifier: ReminderNotifier,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.failure()
        return runCatching {
            val today = timeProvider.currentDate()
            val message = when (kind) {
                ReminderKind.CHECK_IN.name -> ReminderContent.checkIn(today, checkIns.get(today))

                ReminderKind.WORKOUT.name -> {
                    val weekStart = timeProvider.currentWeekStart()
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
