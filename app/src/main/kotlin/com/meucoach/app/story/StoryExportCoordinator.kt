package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.meucoach.core.domain.story.StoryRecipe
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Boundary between the detail screen and the background export (ADR 0018).
 * An interface so the view model can be tested without WorkManager timing.
 */
interface StoryExportCoordinator {
    /**
     * Stages the preview map (optional, but expected in practice) and enqueues
     * the export as unique per-activity work. REPLACE so a new generation with
     * a fresh preview cannot run behind a stale one. [isCancelled] is polled
     * before enqueueing: cancelling while the map snapshot is being staged must
     * not leave a job behind.
     */
    suspend fun start(
        activityId: String,
        recipe: StoryRecipe,
        mapBackground: Bitmap?,
        palette: StoryPalette,
        isCancelled: () -> Boolean = { false },
    )

    fun observe(activityId: String): Flow<List<WorkInfo>>

    fun cancel(activityId: String)

    companion object {
        fun uniqueName(activityId: String): String = "story-export-$activityId"

        /** Work input as a pure function: WorkInfo does not expose it for tests. */
        internal fun buildInput(
            activityId: String,
            recipe: StoryRecipe,
            mapPath: String?,
            palette: StoryPalette,
        ): Data = Data.Builder()
            .putString(StoryExportWorker.KEY_ACTIVITY_ID, activityId)
            .putString(StoryExportWorker.KEY_MAP_PATH, mapPath)
            .putString(StoryExportWorker.KEY_RECIPE_JSON, StoryRecipeCodec.encode(recipe))
            .putIntArray(StoryPaletteCodec.KEY, StoryPaletteCodec.encode(palette))
            .build()
    }
}

/**
 * WorkManager-backed coordinator.
 *
 * Staging happens before enqueueing so the worker never has to re-render the
 * map snapshot from the preview — the snapshot PNG is written into the cache
 * and only its path travels in the work input. The plan itself is always
 * rebuilt by [StoryExportProcessor] from the repositories, because UI state
 * is not required to survive until the worker runs.
 */
class WorkManagerStoryExportCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
) : StoryExportCoordinator {
    override suspend fun start(
        activityId: String,
        recipe: StoryRecipe,
        mapBackground: Bitmap?,
        palette: StoryPalette,
        isCancelled: () -> Boolean,
    ) {
        val mapFile = mapBackground?.let { stageMap(it, activityId) }
        if (isCancelled()) {
            // The user cancelled while the preview snapshot was being written.
            mapFile?.let { runCatching { it.delete() } }
            return
        }
        val request = OneTimeWorkRequestBuilder<StoryExportWorker>()
            .setInputData(StoryExportCoordinator.buildInput(activityId, recipe, mapFile?.absolutePath, palette))
            // The retry path re-downloads the map; dictionary backoff suits a tile fetch.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MS, TimeUnit.MILLISECONDS)
            .addTag(StoryExportWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            StoryExportCoordinator.uniqueName(activityId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun observe(activityId: String): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(StoryExportCoordinator.uniqueName(activityId))

    override fun cancel(activityId: String) {
        workManager.cancelUniqueWork(StoryExportCoordinator.uniqueName(activityId))
    }

    /** The preview snapshot is big (~2106×3744); PNG keeps the stage cheap to decode. */
    private suspend fun stageMap(bitmap: Bitmap, activityId: String): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "stories").apply { mkdirs() }
        val file = File(directory, "story-map-$activityId.png")
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                StoryMessages.MAP_STAGE_FAILED
            }
        }
        file
    }

    private companion object {
        const val BACKOFF_MS = 10_000L
    }
}
