package com.meucoach.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meucoach.app.MainActivity
import com.meucoach.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ReminderNotifier @Inject constructor(@ApplicationContext context: Context) {
    private val appContext = context.applicationContext

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Lembretes de treino",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Check-in diário e treino do dia."
            },
        )
    }

    fun notify(message: ReminderMessage) {
        post(message, silentUpdate = false)
    }

    /**
     * Rewrites an already-posted reminder without alerting again. The day can
     * change after the 07:00 notification (readiness check-in, load update),
     * and the notification shade must show the same day authority as Hoje.
     * A notification the user already dismissed is never recreated.
     */
    fun updateIfActive(message: ReminderMessage) {
        if (!isActive(message.id)) return
        post(message, silentUpdate = true)
    }

    fun isActive(id: Int): Boolean = NotificationManagerCompat.from(appContext).activeNotifications.any { it.id == id }

    fun cancelWorkout() {
        NotificationManagerCompat.from(appContext).cancel(ReminderContent.WORKOUT_ID)
    }

    private fun post(message: ReminderMessage, silentUpdate: Boolean) {
        ensureChannel()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(message.deepLink)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            message.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(silentUpdate)
            .setSilent(silentUpdate)
            .build()
        NotificationManagerCompat.from(appContext).notify(message.id, notification)
    }

    fun cancelAll() {
        val manager = NotificationManagerCompat.from(appContext)
        manager.cancel(ReminderContent.CHECK_IN_ID)
        manager.cancel(ReminderContent.WORKOUT_ID)
        manager.cancel(ReminderContent.RACE_ID)
    }

    companion object {
        const val CHANNEL_ID = "meucoach-reminders"
    }
}
