package com.meucoach.app.health

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
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
class HealthSyncSchedulerTest {
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `schedules a unique daily health sync`() {
        WorkManagerHealthSyncScheduler(workManager).schedule()

        val work = workManager
            .getWorkInfosForUniqueWork(WorkManagerHealthSyncScheduler.UNIQUE_NAME)
            .get()

        assertThat(work).hasSize(1)
        assertThat(work.single().tags).contains(HealthSyncWorker.TAG)
        assertThat(work.single().state).isEqualTo(WorkInfo.State.ENQUEUED)
    }
}
