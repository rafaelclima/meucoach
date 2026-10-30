package com.meucoach.app.reminder

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        WorkManagerReminderScheduler(workManager).schedule()

        val checkIn = workManager
            .getWorkInfosForUniqueWork("meucoach-reminder-check_in")
            .get()
        val workout = workManager
            .getWorkInfosForUniqueWork("meucoach-reminder-workout")
            .get()

        assertThat(checkIn).hasSize(1)
        assertThat(workout).hasSize(1)
        assertThat(checkIn.single().tags).contains(ReminderWorker.TAG)
        assertThat(workout.single().tags).contains(ReminderWorker.TAG)
    }
}
