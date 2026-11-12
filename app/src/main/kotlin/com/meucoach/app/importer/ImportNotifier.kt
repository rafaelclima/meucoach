package com.meucoach.app.importer

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
import com.meucoach.core.data.importer.ImportResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Posts the result of a background import. Uses a low-importance channel so a
 * finished import never interrupts the user, and opens the activity detail when
 * there is one to show.
 */
class ImportNotifier @Inject constructor(@ApplicationContext context: Context) {
    private val appContext = context.applicationContext

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Importação de treinos",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Resultado da importação de arquivos FIT, TCX e GPX."
                setShowBadge(false)
            },
        )
    }

    fun notify(result: ImportResult, fileName: String) {
        if (result is ImportResult.Skipped) return
        show(ImportNotification.message(result, fileName))
    }

    /** Reports a failure that never produced a job row, so the runner is not kept in the dark. */
    fun notifyFailure(fileName: String, message: String) {
        show(ImportNotification.failure(fileName, message))
    }

    private fun show(message: ImportNotificationMessage) {
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
            ImportNotification.ID,
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(appContext).notify(ImportNotification.ID, notification)
    }

    fun cancel() {
        NotificationManagerCompat.from(appContext).cancel(ImportNotification.ID)
    }

    companion object {
        const val CHANNEL_ID = "meucoach-imports"
    }
}
