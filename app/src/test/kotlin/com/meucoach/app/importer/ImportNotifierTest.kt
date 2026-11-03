package com.meucoach.app.importer

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.importer.ImportResult
import com.meucoach.core.model.ActivitySource
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportNotifierTest {
    @Test
    fun `creates a low importance import channel`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notifier = ImportNotifier(context)

        notifier.ensureChannel()

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(ImportNotifier.CHANNEL_ID)
        assertThat(channel).isNotNull
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    fun `posts the import result`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = ImportNotifier(context)

        notifier.notify(
            ImportResult.Imported(
                jobId = "job-1",
                activityId = "activity-1",
                source = ActivitySource.FIT,
            ),
            fileName = "corrida.fit",
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = Shadows.shadowOf(manager).allNotifications
        assertThat(notifications).hasSize(1)
        assertThat(Shadows.shadowOf(notifications.single()).contentTitle.toString())
            .isEqualTo("Treino importado")
    }

    @Test
    fun `reuses the same notification for a batch`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = ImportNotifier(context)

        notifier.notify(
            ImportResult.Imported(jobId = "job-1", activityId = "activity-1", source = ActivitySource.FIT),
            fileName = "primeira.fit",
        )
        notifier.notify(
            ImportResult.Imported(jobId = "job-2", activityId = "activity-2", source = ActivitySource.TCX),
            fileName = "segunda.tcx",
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = Shadows.shadowOf(manager).allNotifications
        assertThat(notifications).hasSize(1)
        assertThat(Shadows.shadowOf(notifications.single()).contentText.toString())
            .isEqualTo("segunda.tcx. Toque para abrir.")
    }
}
