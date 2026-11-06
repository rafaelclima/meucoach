package com.meucoach.core.data.importer

import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.ImportJobEntity
import com.meucoach.core.data.db.entity.ImportJobStatus
import com.meucoach.core.data.epoch.DataEpochStore
import com.meucoach.core.data.epoch.DataWriteMutex
import com.meucoach.core.data.id.UuidV7
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.SaveActivityResult
import com.meucoach.core.domain.parser.fit.FitParser
import com.meucoach.core.domain.parser.gpx.GpxParser
import com.meucoach.core.domain.parser.tcx.TcxParser
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/**
 * Coordinates one file import: format detection, parsing, duplicate checks,
 * persistence and the import-job audit trail.
 *
 * The service is deliberately synchronous; WorkManager will call it from a
 * background coroutine and persist the job before parsing starts.
 */
class ImportService(
    private val database: MeuCoachDatabase,
    private val repository: ActivityRepository,
    private val fitParser: FitParser = FitParser(),
    private val tcxParser: TcxParser = TcxParser(),
    private val gpxParser: GpxParser = GpxParser(),
    private val clock: Clock = Clock.systemUTC(),
    private val jobIdGenerator: () -> String = { UuidV7.generate(clock) },
    private val profileRepository: ProfileRepository? = null,
    private val epochStore: DataEpochStore? = null,
    private val writeMutex: DataWriteMutex? = null,
) {
    suspend fun import(request: ImportRequest): ImportResult {
        if (profileRepository != null && profileRepository.getProfile() == null) {
            return ImportResult.Skipped
        }
        val epoch = epochStore?.current()
        val sha256 = Sha256.hex(request.bytes)
        val jobId = jobIdGenerator()
        val createdAt = clock.instant()
        val initialJob = ImportJobEntity(
            id = jobId,
            fileName = request.fileName,
            sha256 = sha256,
            mime = request.mime,
            sizeBytes = request.bytes.size.toLong(),
            status = ImportJobStatus.QUEUED,
            parserVersion = null,
            activityId = null,
            errorCode = null,
            errorMessage = null,
            createdAt = createdAt.toEpochMilli(),
            finishedAt = null,
        )
        val started = if (epochStore != null && writeMutex != null) {
            writeMutex.mutex.withLock {
                if (epochStore.current() != epoch) {
                    false
                } else {
                    insertInitialJob(initialJob)
                    true
                }
            }
        } else {
            insertInitialJob(initialJob)
            true
        }
        if (!started) return ImportResult.Skipped

        database.activityDao().findBySha256IncludingDeleted(sha256)?.let { existing ->
            if (existing.deletedAt != null) {
                markTrashConflict(initialJob, existing.id)
                return ImportResult.TrashConflict(jobId = jobId, activityId = existing.id)
            }
            markDuplicate(initialJob, existing.id)
            return ImportResult.Duplicate(jobId = jobId, activityId = existing.id)
        }
        database.activityAlternateDao().findBySha256(sha256)?.let { alternate ->
            val parent = database.activityDao().findByIdIncludingDeleted(alternate.activityId)
            if (parent?.deletedAt != null) {
                markTrashConflict(initialJob, alternate.activityId)
                return ImportResult.TrashConflict(jobId = jobId, activityId = alternate.activityId)
            }
            markDuplicate(initialJob, alternate.activityId)
            return ImportResult.Duplicate(jobId = jobId, activityId = alternate.activityId)
        }

        val format = ImportFormat.detect(request)
            ?: return fail(
                initialJob,
                ImportErrorCode.UNSUPPORTED_FORMAT,
                "Formato não suportado. Use FIT, TCX ou GPX.",
            )

        val parsed = try {
            parse(format, request.bytes)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return fail(
                initialJob,
                ImportErrorCode.PARSE_ERROR,
                "Não foi possível ler este arquivo ${format.name}. " +
                    "Verifique se ele está completo e não está corrompido.",
            )
        }

        return try {
            val saved = if (epochStore != null && epochStore.current() != epoch) {
                null
            } else {
                val save: suspend () -> SaveActivityResult? = {
                    if (epochStore != null && epochStore.current() != epoch) {
                        null
                    } else {
                        repository.saveImportedActivity(parsed.activity, sha256, request.rawFilePath)
                    }
                }
                writeMutex?.let { guard -> guard.mutex.withLock { save() } } ?: save()
            }
            if (saved == null) return ImportResult.Skipped
            when (saved) {
                is SaveActivityResult.Saved -> {
                    markDone(initialJob, parsed.activity, saved.id)
                    ImportResult.Imported(
                        jobId = jobId,
                        activityId = saved.id,
                        source = parsed.activity.source,
                        warnings = parsed.warnings,
                    )
                }

                is SaveActivityResult.Replaced -> {
                    markDone(initialJob, parsed.activity, saved.id)
                    ImportResult.Imported(
                        jobId = jobId,
                        activityId = saved.id,
                        source = parsed.activity.source,
                        warnings = parsed.warnings,
                        replacedSource = saved.previousSource,
                    )
                }

                is SaveActivityResult.Restored -> {
                    markDone(initialJob, parsed.activity, saved.id)
                    ImportResult.Imported(
                        jobId = jobId,
                        activityId = saved.id,
                        source = parsed.activity.source,
                        warnings = parsed.warnings,
                        restored = true,
                    )
                }

                is SaveActivityResult.ConflictingTrash -> {
                    markTrashConflict(initialJob, saved.id)
                    ImportResult.TrashConflict(jobId = jobId, activityId = saved.id)
                }

                is SaveActivityResult.Duplicate -> {
                    markDuplicate(initialJob, saved.id)
                    ImportResult.Duplicate(jobId = jobId, activityId = saved.id)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            fail(
                initialJob,
                ImportErrorCode.STORAGE_ERROR,
                "Não foi possível salvar a atividade. Tente importar o arquivo de novo.",
            )
        }
    }

    private suspend fun insertInitialJob(job: ImportJobEntity) {
        database.importJobDao().insert(job)
        database.importJobDao().update(job.copy(status = ImportJobStatus.PARSING))
    }

    private fun parse(format: ImportFormat, bytes: ByteArray): ParsedImport = when (format) {
        ImportFormat.FIT -> fitParser.parse(bytes).let { result ->
            ParsedImport(
                activity = result.activity,
                warnings = result.warnings.map { "${it.code}: ${it.message}" },
            )
        }

        ImportFormat.TCX -> ParsedImport(tcxParser.parse(bytes))

        ImportFormat.GPX -> ParsedImport(gpxParser.parse(bytes))
    }

    private suspend fun markDone(job: ImportJobEntity, activity: ImportedActivity, activityId: String) {
        database.importJobDao().update(
            job.copy(
                status = ImportJobStatus.DONE,
                parserVersion = activity.parserVersion,
                activityId = activityId,
                finishedAt = clock.instant().toEpochMilli(),
            ),
        )
    }

    private suspend fun markDuplicate(job: ImportJobEntity, activityId: String) {
        database.importJobDao().update(
            job.copy(
                status = ImportJobStatus.DUPLICATE,
                activityId = activityId,
                finishedAt = clock.instant().toEpochMilli(),
            ),
        )
    }

    private suspend fun markTrashConflict(job: ImportJobEntity, activityId: String) {
        database.importJobDao().update(
            job.copy(
                status = ImportJobStatus.TRASH_CONFLICT,
                activityId = activityId,
                errorMessage = "Atividade na lixeira. Restaure-a em Dados antes de importar de novo.",
                finishedAt = clock.instant().toEpochMilli(),
            ),
        )
    }

    private suspend fun fail(job: ImportJobEntity, code: ImportErrorCode, message: String): ImportResult.Failed {
        val trimmed = message.take(MAX_ERROR_MESSAGE_LENGTH)
        database.importJobDao().update(
            job.copy(
                status = ImportJobStatus.FAILED,
                errorCode = code.name,
                errorMessage = trimmed,
                finishedAt = clock.instant().toEpochMilli(),
            ),
        )
        return ImportResult.Failed(jobId = job.id, code = code, message = trimmed)
    }

    private data class ParsedImport(val activity: ImportedActivity, val warnings: List<String> = emptyList())

    private companion object {
        const val MAX_ERROR_MESSAGE_LENGTH = 500
    }
}

data class ImportRequest(
    val bytes: ByteArray,
    val fileName: String,
    val mime: String? = null,
    val rawFilePath: String? = null,
) {
    init {
        require(fileName.isNotBlank()) { "File name must not be blank" }
    }
}

enum class ImportFormat {
    FIT,
    TCX,
    GPX,
    ;

    companion object {
        fun detect(request: ImportRequest): ImportFormat? {
            val extension = request.fileName.substringAfterLast('.', "").lowercase()
            val bytes = request.bytes
            if (hasFitMagic(bytes) || extension == "fit") return FIT

            val head = sniffXml(bytes)
            if (head.contains("TrainingCenterDatabase", ignoreCase = true)) return TCX
            if (head.contains("<gpx", ignoreCase = true)) return GPX
            when (extension) {
                "tcx" -> return TCX
                "gpx" -> return GPX
            }

            return when (request.mime?.lowercase()) {
                "application/vnd.garmin.fit", "application/vnd.ant.fit" -> FIT
                "application/vnd.garmin.tcx+xml" -> TCX
                "application/gpx+xml" -> GPX
                else -> null
            }
        }

        private fun hasFitMagic(bytes: ByteArray): Boolean = bytes.size >= FIT_MAGIC.size + FIT_MAGIC_OFFSET &&
            FIT_MAGIC.indices.all { index ->
                bytes[FIT_MAGIC_OFFSET + index] == FIT_MAGIC[index]
            }

        private fun sniffXml(bytes: ByteArray): String =
            bytes.copyOfRange(0, minOf(bytes.size, SNIFF_BYTES)).toString(Charsets.UTF_8)

        private const val FIT_MAGIC_OFFSET = 8
        private val FIT_MAGIC = ".FIT".toByteArray(Charsets.US_ASCII)
        private const val SNIFF_BYTES = 8 * 1024
    }
}

enum class ImportErrorCode {
    UNSUPPORTED_FORMAT,
    PARSE_ERROR,
    STORAGE_ERROR,
}

sealed interface ImportResult {
    data class Imported(
        val jobId: String,
        val activityId: String,
        val source: ActivitySource,
        val warnings: List<String> = emptyList(),
        val replacedSource: ActivitySource? = null,
        val restored: Boolean = false,
    ) : ImportResult

    data class Duplicate(val jobId: String, val activityId: String) : ImportResult

    data class TrashConflict(val jobId: String, val activityId: String) : ImportResult

    data class Failed(val jobId: String, val code: ImportErrorCode, val message: String) : ImportResult

    /** Profile missing or a data reset happened while the file was being processed. */
    data object Skipped : ImportResult
}
