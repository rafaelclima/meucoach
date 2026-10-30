package com.meucoach.app.importer

import com.meucoach.core.data.importer.ImportRequest
import com.meucoach.core.data.importer.ImportResult
import com.meucoach.core.data.importer.ImportService
import javax.inject.Inject

/** Reads one staged file, imports it and removes the staged copy when done. */
class StagedImportProcessor @Inject constructor(
    private val staging: ImportStaging,
    private val importService: ImportService,
) {
    suspend fun process(relativePath: String, displayName: String, mime: String?): ImportResult {
        val bytes = staging.read(relativePath)
        val result = importService.import(
            ImportRequest(
                bytes = bytes,
                fileName = displayName,
                mime = mime,
                rawFilePath = relativePath,
            ),
        )
        // Cleanup must not turn a successful import into a failed job.
        runCatching { staging.delete(relativePath) }
        return result
    }
}
