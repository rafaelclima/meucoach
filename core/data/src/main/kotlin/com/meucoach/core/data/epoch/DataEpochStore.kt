package com.meucoach.core.data.epoch

import android.content.Context
import com.meucoach.core.data.id.UuidV7
import java.time.Clock

/**
 * Opaque generation marker that survives a full local reset. Every asynchronous
 * writer captures it before doing work and rechecks it immediately before
 * persisting; a reset rotates the value, so stale work becomes a no-op.
 */
class DataEpochStore(context: Context, private val clock: Clock = Clock.systemUTC(), prefsName: String = PREFS_NAME) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun current(): String = prefs.getString(KEY_EPOCH, null).orEmpty()

    /** Writes a fresh epoch synchronously so it is durable before any cleanup. */
    fun rotate(): String {
        val epoch = UuidV7.generate(clock)
        check(prefs.edit().putString(KEY_EPOCH, epoch).commit()) {
            "Não foi possível gravar a época de dados."
        }
        return epoch
    }

    companion object {
        const val PREFS_NAME = "meucoach_data_epoch"
        private const val KEY_EPOCH = "epoch"
    }
}
