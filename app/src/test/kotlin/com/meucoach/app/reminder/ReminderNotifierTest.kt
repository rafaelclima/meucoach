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

    @Test
    fun `updates an active reminder without creating a second notification`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = ReminderNotifier(context)
        notifier.notify(workout(title = "Treino de hoje: Intervalado", body = "40 min"))

        notifier.updateIfActive(workout(title = "Treino de hoje: Fácil", body = "24 min ajustados"))

        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = Shadows.shadowOf(manager).allNotifications
        assertThat(notifications).hasSize(1)
        val shadow = Shadows.shadowOf(notifications.single())
        assertThat(shadow.contentTitle.toString()).isEqualTo("Treino de hoje: Fácil")
        assertThat(shadow.contentText.toString()).contains("24 min ajustados")
    }

    @Test
    fun `never recreates a dismissed reminder`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = ReminderNotifier(context)

        notifier.updateIfActive(workout(title = "Treino de hoje: Fácil", body = "24 min"))

        val manager = context.getSystemService(NotificationManager::class.java)
        assertThat(Shadows.shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun `cancels only the workout reminder`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = ReminderNotifier(context)
        notifier.notify(
            ReminderMessage(
                id = ReminderContent.CHECK_IN_ID,
                kind = ReminderKind.CHECK_IN,
                title = "Check-in",
                body = "15 s",
                deepLink = DEEP_LINK_CHECK_IN,
            ),
        )
        notifier.notify(workout(title = "Treino de hoje: Fácil", body = "24 min"))

        notifier.cancelWorkout()

        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = Shadows.shadowOf(manager).allNotifications
        assertThat(notifications).hasSize(1)
        assertThat(Shadows.shadowOf(notifications.single()).contentTitle.toString()).isEqualTo("Check-in")
    }

    private fun workout(title: String, body: String) = ReminderMessage(
        id = ReminderContent.WORKOUT_ID,
        kind = ReminderKind.WORKOUT,
        title = title,
        body = body,
        deepLink = DEEP_LINK_TODAY,
    )
}
