package com.meucoach.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNavigationIntent(intent)
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

    private fun handleShareIntent(intent: Intent?) {
        val uris = ShareIntentReader.uris(intent)
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            val message = importEnqueueMessage(importCoordinator.enqueue(uris))
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }
}
