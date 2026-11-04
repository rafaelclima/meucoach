package com.meucoach.app.story

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meucoach.app.MainActivity
import com.meucoach.app.R
import com.meucoach.app.importer.DEEP_LINK_ACTIVITY
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Copy for story notifications. Pure so the wording is testable without Android. */
object StoryNotification {
    const val ID = 2_002

    fun completed(): String = "O vídeo do treino está na galeria. Toque para abrir e compartilhar."

    fun failed(message: String): String = "$message Toque para tentar de novo."
}

/**
 * Posts the result of a background story export. Same low-importance channel
 * policy as the import notifications: a finished export never interrupts the
 * user, it just waits in the shade.
 */
class StoryNotifier @Inject constructor(@ApplicationContext context: Context) {
    private val appContext = context.applicationContext

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Story do treino",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Resultado da geração do vídeo do treino."
                setShowBadge(false)
            },
        )
    }

    fun notifyCompleted(activityId: String) {
        notify(activityId, title = "Story pronto", body = StoryNotification.completed())
    }

    fun notifyFailed(activityId: String, message: String) {
        notify(activityId, title = "Não foi possível gerar o story", body = StoryNotification.failed(message))
    }

    private fun notify(activityId: String, title: String, body: String) {
        ensureChannel()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("$DEEP_LINK_ACTIVITY/$activityId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            StoryNotification.ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(appContext).notify(StoryNotification.ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "meucoach-story"
    }
}
