package com.meucoach.core.data.importer

import com.meucoach.core.data.db.dao.ImportJobDao
import com.meucoach.core.data.db.entity.ImportJobEntity
import com.meucoach.core.data.db.entity.ImportJobStatus
import kotlinx.coroutines.CancellationException
import java.time.Clock

/**
 * Guarantees that a job already written to the database reaches a terminal state.
 *
 * The pipeline can fail anywhere (SQLite, parser, notification, process kill).
 * Whatever escapes [run] is recorded as `FAILED`, so the list never shows a file
 * as "Lendo" forever and WorkManager never retries a job nobody can close.
 */
internal class ImportJobGuard(private val dao: ImportJobDao, private val clock: Clock) {
    suspend fun run(job: ImportJobEntity, block: suspend () -> ImportResult): ImportResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        recordFailure(job)
        ImportResult.Failed(job.id, ImportErrorCode.STORAGE_ERROR, UNEXPECTED_MESSAGE)
    }

    /**
     * Best effort: if the database itself is unavailable there is nothing else
     * to write, and the worker still reports the failure to the user.
     */
    private suspend fun recordFailure(job: ImportJobEntity) {
        runCatching {
            dao.update(
                job.copy(
                    status = ImportJobStatus.FAILED,
                    errorCode = ImportErrorCode.STORAGE_ERROR.name,
                    errorMessage = UNEXPECTED_MESSAGE,
                    finishedAt = clock.instant().toEpochMilli(),
                ),
            )
        }
    }

    companion object {
        const val UNEXPECTED_MESSAGE = "A importação foi interrompida. Importe o arquivo de novo."
    }
}
