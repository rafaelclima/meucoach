package com.meucoach.app.story

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
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
import kotlin.math.roundToInt

/** Copy for story notifications. Pure so the wording is testable without Android. */
object StoryNotification {
    const val ID = 2_002
    const val PROGRESS_ID = 2_003

    fun completed(): String = "O vídeo do treino está na galeria. Toque para abrir e compartilhar."

    fun failed(message: String): String = "$message Toque para tentar de novo."

    fun progressText(progress: Float, etaSec: Int?): String {
        val percent = (progress.coerceIn(0f, 1f) * 100).roundToInt()
        return when {
            progress <= 0f -> "Preparando o mapa…"
            percent >= 100 -> "Finalizando…"
            etaSec != null -> "$percent% · cerca de ${etaSec}s restantes"
            else -> "$percent% concluído"
        }
    }
}

/**
 * Posts the result of a background story export and keeps the ongoing
 * notification that backs the foreground service. Same low-importance channel
 * as the import notifications: an export never interrupts the user.
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

    /** Ongoing notification shown while the foreground worker encodes the video. */
    fun buildProgress(activityId: String, progress: Float, etaSec: Int?): Notification {
        ensureChannel()
        val percent = (progress.coerceIn(0f, 1f) * 100).roundToInt()
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Gerando seu story")
            .setContentText(StoryNotification.progressText(progress, etaSec))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(activityPendingIntent(activityId))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notifyCompleted(activityId: String) {
        notify(activityId, title = "Story pronto", body = StoryNotification.completed())
    }

    fun notifyFailed(activityId: String, message: String) {
        notify(activityId, title = "Não foi possível gerar o story", body = StoryNotification.failed(message))
    }

    fun cancelAll() {
        val manager = NotificationManagerCompat.from(appContext)
        manager.cancel(StoryNotification.ID)
        manager.cancel(StoryNotification.PROGRESS_ID)
    }

    @SuppressLint("MissingPermission") // canNotify() checks POST_NOTIFICATIONS first.
    private fun notify(activityId: String, title: String, body: String) {
        ensureChannel()
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(activityPendingIntent(activityId))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(appContext).notify(StoryNotification.ID, notification)
    }

    private fun activityPendingIntent(activityId: String): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("$DEEP_LINK_ACTIVITY/$activityId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            StoryNotification.ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canNotify(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "meucoach-story"
    }
}
