package com.meucoach.app.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderNotifierTest {
    @Test
    fun `creates the reminder channel`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notifier = ReminderNotifier(context)

        notifier.ensureChannel()

        val manager = context.getSystemService(NotificationManager::class.java)
        assertThat(manager.getNotificationChannel(ReminderNotifier.CHANNEL_ID)).isNotNull
    }

    @Test
    fun `posts a reminder notification`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = ReminderNotifier(context)

        notifier.notify(
            ReminderMessage(
                id = 7,
                kind = ReminderKind.CHECK_IN,
                title = "Check-in",
                body = "15 s",
                deepLink = DEEP_LINK_CHECK_IN,
            ),
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        assertThat(Shadows.shadowOf(manager).allNotifications).hasSize(1)
    }
}
