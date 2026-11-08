package com.meucoach.app.data

import android.content.Context
import androidx.work.WorkManager
import com.meucoach.app.firstrun.FirstRunMetricsStore
import com.meucoach.app.firstrun.FirstRunTipStore
import com.meucoach.app.health.HealthSyncCoordinator
import com.meucoach.app.health.HealthSyncWorker
import com.meucoach.app.importer.ImportNotifier
import com.meucoach.app.importer.ImportStaging
import com.meucoach.app.importer.ImportWorker
import com.meucoach.app.importer.ImportedRunPromptStore
import com.meucoach.app.profile.ProfilePhotoStore
import com.meucoach.app.reminder.ReminderNotifier
import com.meucoach.app.reminder.ReminderWorker
import com.meucoach.app.story.StoryExportFailureStore
import com.meucoach.app.story.StoryExportWorker
import com.meucoach.app.story.StoryIntroStore
import com.meucoach.app.story.StoryNotifier
import com.meucoach.app.ui.tutorial.TutorialStore
import com.meucoach.core.data.epoch.DataEpochStore
import com.meucoach.core.data.epoch.DataWriteMutex
import com.meucoach.core.data.repository.UserDataDeletion
import com.meucoach.core.data.repository.UserDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DataResetResult(
    val deletion: UserDataDeletion,
    val epoch: String,
    val failedCleanups: List<String> = emptyList(),
)

/** Boundary used by the data screen so tests can exercise reset without WorkManager. */
interface DataResetter {
    suspend fun reset(): DataResetResult
}

/**
 * One reset path shared by the UI and (in the future) any other destructive
 * action: rotate the epoch first, cancel work, clear Room, then clear files,
 * caches and preferences. The epoch is the durable barrier against stale
 * workers; cancellation is best effort. Health Connect records are never
 * deleted here — only the local copy and the sync watermark are.
 */
@Singleton
class DataResetCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userData: UserDataRepository,
    private val epochStore: DataEpochStore,
    private val writeMutex: DataWriteMutex,
    private val workManager: WorkManager,
    private val staging: ImportStaging,
    private val photos: ProfilePhotoStore,
    private val importedRunPrompts: ImportedRunPromptStore,
    private val tutorial: TutorialStore,
    private val firstRunMetrics: FirstRunMetricsStore,
    private val firstRunTips: FirstRunTipStore,
    private val storyFailures: StoryExportFailureStore,
    private val storyIntro: StoryIntroStore,
    private val healthSync: HealthSyncCoordinator,
    private val reminderNotifier: ReminderNotifier,
    private val importNotifier: ImportNotifier,
    private val storyNotifier: StoryNotifier,
) : DataResetter {
    override suspend fun reset(): DataResetResult = withContext(Dispatchers.IO) {
        writeMutex.mutex.withLock {
            // The epoch must be durable before the first destructive step.
            val epoch = epochStore.rotate()
            cancelWork()
            val deletion = userData.deleteAllData()
            val failures = mutableListOf<String>()
            cleanup("staging de importação") { if (!staging.clear()) error("diretório não removido") }
                ?.let(failures::add)
            cleanup("avatar") { if (!photos.clear()) error("diretório não removido") }
                ?.let(failures::add)
            cleanup("oferta de corrida importada") { importedRunPrompts.reset() }?.let(failures::add)
            cleanup("cache de story") {
                val directory = File(context.cacheDir, "stories")
                if (directory.exists() && !directory.deleteRecursively()) error("cache não removido")
            }?.let(failures::add)
            cleanup("tutorial") { tutorial.reset() }?.let(failures::add)
            cleanup("métricas de primeiro uso") { firstRunMetrics.reset() }?.let(failures::add)
            cleanup("dicas de primeiro uso") { firstRunTips.reset() }?.let(failures::add)
            cleanup("falhas de story") { storyFailures.clearAll() }?.let(failures::add)
            cleanup("aviso de story") { storyIntro.reset() }?.let(failures::add)
            healthSync.resetState()
            reminderNotifier.cancelAll()
            importNotifier.cancel()
            storyNotifier.cancelAll()
            DataResetResult(deletion = deletion, epoch = epoch, failedCleanups = failures)
        }
    }

    private fun cancelWork() {
        listOf(
            ImportWorker.TAG,
            HealthSyncWorker.TAG,
            ReminderWorker.TAG,
            StoryExportWorker.TAG,
            TrashPurgeWorker.TAG,
        ).forEach { tag -> runCatching { workManager.cancelAllWorkByTag(tag) } }
    }

    private suspend fun cleanup(label: String, block: suspend () -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            block()
            null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            "$label: ${error.message ?: "falhou"}"
        }
    }
}
