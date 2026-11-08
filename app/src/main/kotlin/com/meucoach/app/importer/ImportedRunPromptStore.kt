package com.meucoach.app.importer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Durable queue of imported runs that may become today's workout.
 *
 * The import runs in WorkManager, outside the screen that started it. Storing
 * the activity id here lets the UI react when the user comes back (or when the
 * process was recreated) without scanning the whole library for a new run.
 */
class ImportedRunPromptStore(context: Context, prefsName: String = PREFS_NAME) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val pendingIds = MutableStateFlow(read())

    val pending: StateFlow<Set<String>> = pendingIds.asStateFlow()

    fun add(activityId: String) {
        if (activityId.isBlank()) return
        synchronized(this) {
            persist((pendingIds.value + activityId).toList().takeLast(MAX_PENDING).toSet())
        }
    }

    fun remove(activityIds: Set<String>) {
        if (activityIds.isEmpty()) return
        synchronized(this) {
            persist(pendingIds.value - activityIds)
        }
    }

    fun clear() {
        synchronized(this) {
            persist(emptySet())
        }
    }

    fun reset() {
        clear()
    }

    private fun read(): Set<String> = prefs.getStringSet(KEY_PENDING, emptySet())?.toSet() ?: emptySet()

    private fun persist(ids: Set<String>) {
        // commit() instead of apply(): the import worker may be killed right
        // after the import finishes, and the prompt must survive that.
        prefs
            .edit()
            .apply {
                if (ids.isEmpty()) {
                    remove(KEY_PENDING)
                } else {
                    putStringSet(KEY_PENDING, ids)
                }
            }
            .commit()
        pendingIds.value = ids
    }

    companion object {
        const val PREFS_NAME = "meucoach_import_prompts"
        private const val KEY_PENDING = "pendingActivityIds"
        private const val MAX_PENDING = 5
    }
}
