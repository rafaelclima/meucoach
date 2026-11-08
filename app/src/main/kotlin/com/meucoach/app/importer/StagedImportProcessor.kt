package com.meucoach.app.importer

import com.meucoach.app.application.OfferImportedRun
import com.meucoach.core.data.epoch.DataEpochStore
import com.meucoach.core.data.importer.ImportRequest
import com.meucoach.core.data.importer.ImportResult
import com.meucoach.core.data.importer.ImportService
import com.meucoach.core.data.repository.ProfileRepository
import javax.inject.Inject

/** Reads one staged file, imports it and removes the staged copy when done. */
class StagedImportProcessor @Inject constructor(
    private val staging: ImportStaging,
    private val importService: ImportService,
    private val profiles: ProfileRepository? = null,
    private val epochStore: DataEpochStore? = null,
    private val offerImportedRun: OfferImportedRun? = null,
) {
    suspend fun process(relativePath: String, displayName: String, mime: String?): ImportResult {
        // A reset may have removed the profile and the staging directory while
        // this job was queued; do not recreate anything or retry the missing file.
        if (profiles != null && profiles.getProfile() == null) {
            runCatching { staging.delete(relativePath) }
            return ImportResult.Skipped
        }
        val epoch = epochStore?.current()
        if (epochStore != null && epochStore.current() != epoch) {
            runCatching { staging.delete(relativePath) }
            return ImportResult.Skipped
        }
        val bytes = staging.read(relativePath)
        val result = importService.import(
            ImportRequest(
                bytes = bytes,
                fileName = displayName,
                mime = mime,
                rawFilePath = relativePath,
            ),
        )
        // A prompt must never turn a successful import into a failed job.
        runCatching {
            when (result) {
                is ImportResult.Imported -> offerImportedRun?.evaluate(result.activityId)
                is ImportResult.Duplicate -> offerImportedRun?.evaluate(result.activityId)
                else -> Unit
            }
        }
        // Cleanup must not turn a successful import into a failed job.
        runCatching { staging.delete(relativePath) }
        if (epochStore != null && epochStore.current() != epoch) return ImportResult.Skipped
        return result
    }
}
