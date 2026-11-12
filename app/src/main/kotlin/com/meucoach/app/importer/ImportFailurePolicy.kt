package com.meucoach.app.importer

import java.io.FileNotFoundException
import java.io.IOException

/**
 * Decides what to do after an import attempt failed.
 *
 * Retrying is only useful while the staged file can still become readable.
 * A missing file, an invalid path, revoked access or a programming error fail
 * the same way forever, so the job is closed instead of looping in the queue.
 */
internal object ImportFailurePolicy {
    const val MAX_ATTEMPTS = 3

    const val GIVE_UP_MESSAGE = "Não foi possível ler o arquivo. Importe o arquivo de novo."

    enum class Action { RETRY, GIVE_UP }

    fun decide(error: Throwable, runAttemptCount: Int): Action = when {
        error is ImportStagingException -> Action.GIVE_UP
        error is FileNotFoundException -> Action.GIVE_UP
        error is SecurityException -> Action.GIVE_UP
        error !is IOException -> Action.GIVE_UP
        runAttemptCount + 1 >= MAX_ATTEMPTS -> Action.GIVE_UP
        else -> Action.RETRY
    }

    /**
     * User-facing reason. Only [ImportStagingException] carries copy written for
     * the runner; other errors could expose paths or internals, so they fall
     * back to a generic message.
     */
    fun message(error: Throwable): String =
        (error as? ImportStagingException)?.message?.takeIf { it.isNotBlank() } ?: GIVE_UP_MESSAGE
}
