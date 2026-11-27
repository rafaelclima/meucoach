package com.meucoach.app.reminder

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderSchedulerTest {
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `schedules check-in and workout reminders`() {
        WorkManagerReminderScheduler(workManager, FakeTimeProvider()).schedule()

        val checkIn = workManager
            .getWorkInfosForUniqueWork("meucoach-reminder-check_in")
            .get()
        val workout = workManager
            .getWorkInfosForUniqueWork("meucoach-reminder-workout")
            .get()
        val weekPreview = workManager
            .getWorkInfosForUniqueWork("meucoach-reminder-week_preview")
            .get()

        assertThat(checkIn).hasSize(1)
        assertThat(workout).hasSize(1)
        assertThat(weekPreview).hasSize(1)
        assertThat(checkIn.single().tags).contains(ReminderWorker.TAG)
        assertThat(workout.single().tags).contains(ReminderWorker.TAG)
        assertThat(weekPreview.single().tags).contains(ReminderWorker.TAG)
    }

    @Test
    fun `derives check-in and workout reminders before the usual training time`() {
        val profile = AthleteProfile(
            id = "athlete-1",
            birthDate = LocalDate.of(1990, 1, 1),
            sex = Sex.MALE,
            preferredTrainingTimeMinutes = 4 * 60,
        )

        assertThat(ReminderTimes.checkIn(profile)).isEqualTo(ReminderClock(hour = 3, minute = 30))
        assertThat(ReminderTimes.workout(profile)).isEqualTo(ReminderClock(hour = 3, minute = 45))
    }

    @Test
    fun `keeps legacy reminder times without a declared training time`() {
        assertThat(ReminderTimes.checkIn(null)).isEqualTo(ReminderClock(hour = 18, minute = 0))
        assertThat(ReminderTimes.workout(null)).isEqualTo(ReminderClock(hour = 7, minute = 0))
    }
}
