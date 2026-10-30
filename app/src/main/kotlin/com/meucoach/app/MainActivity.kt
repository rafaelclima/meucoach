package com.meucoach.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.meucoach.app.importer.ImportCoordinator
import com.meucoach.app.importer.ShareIntentReader
import com.meucoach.app.importer.importEnqueueMessage
import com.meucoach.app.reminder.NotificationNavigation
import com.meucoach.core.designsystem.theme.McTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var importCoordinator: ImportCoordinator

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNavigationIntent(intent)
        requestNotificationPermission()
        setContent {
            McTheme {
                MeuCoachApp()
            }
        }
        // A configuration change reuses the original intent; only the first creation should import.
        if (savedInstanceState == null) handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        val route = NotificationNavigation.routeFor(intent?.dataString) ?: return
        NotificationNavigation.request(route)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun handleShareIntent(intent: Intent?) {
        val uris = ShareIntentReader.uris(intent)
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            val message = importEnqueueMessage(importCoordinator.enqueue(uris))
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }
}
