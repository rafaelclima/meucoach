package com.meucoach.app.ui.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meucoach.app.R
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTheme

/**
 * Contextual notification priming. The app never asks for POST_NOTIFICATIONS on launch;
 * the system dialog only appears after the user chooses to enable reminders.
 */
@Composable
internal fun NotificationReminderCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = McTheme.colors
    var enabled by remember { mutableStateOf(notificationsEnabled(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        enabled = notificationsEnabled(context)
    }

    McCard(
        variant = McCardVariant.INSET,
        modifier = modifier.testTag("profile-notification-card"),
    ) {
        Text(
            text = stringResource(R.string.profile_reminders_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.profile_reminders_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (enabled) {
            McSecondaryButton(
                text = stringResource(R.string.profile_reminders_enabled),
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile-reminders-enabled"),
            )
        } else {
            McSecondaryButton(
                text = stringResource(R.string.profile_reminders_enable),
                onClick = {
                    val needsRuntimePermission =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                    if (needsRuntimePermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile-reminders-enable"),
            )
            McTextButton(
                text = stringResource(R.string.profile_reminders_settings),
                onClick = { openNotificationSettings(context) },
                trailingArrow = false,
                contentColor = colors.textSecondary,
                modifier = Modifier.testTag("profile-reminders-settings"),
            )
        }
    }
}

private fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
}
