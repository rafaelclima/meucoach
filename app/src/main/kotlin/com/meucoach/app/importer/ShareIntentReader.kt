package com.meucoach.app.importer

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Extracts document URIs from `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intents. */
object ShareIntentReader {
    fun uris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val streams = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
            )

            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            ) ?: emptyList()

            else -> emptyList()
        }
        val clipUris = buildList {
            val clip = intent.clipData ?: return@buildList
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let { add(it) }
            }
        }
        return (streams + clipUris).distinct()
    }
}
