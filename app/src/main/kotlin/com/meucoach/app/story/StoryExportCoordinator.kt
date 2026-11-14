package com.meucoach.app.story

import android.content.Context
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
        mapBackground: StoryMapSnapshot?,
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
        mapBackground: StoryMapSnapshot?,
        palette: StoryPalette,
        isCancelled: () -> Boolean,
    ) {
        // ENC-05: the recipe owns the map style; the palette only carries
        // colors. Staging under the recipe style keeps the variant hash and
        // the manifest on the same style the worker will render.
        val mapFile = mapBackground?.let { stageMap(it, activityId, recipe.mapStyle) }
        if (isCancelled()) {
            // The user cancelled while the preview snapshot was being written.
            // Only the follow shot staged above is removed: bookends from a
            // previous variant stay cached for the next attempt.
            mapFile?.let { StoryMapSnapshotStore.delete(it) }
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

    /** The preview snapshot is big (~2430×4320); the route pixels travel in a sidecar. */
    private suspend fun stageMap(
        snapshot: StoryMapSnapshot,
        activityId: String,
        style: com.meucoach.core.domain.story.StoryMapStyle,
    ): File = withContext(Dispatchers.IO) {
        val storiesDir = File(context.cacheDir, "stories").apply { mkdirs() }
        // Legacy flat entries from previous slices never feed the worker: the
        // worker only reads the path in the work input, and orphaned files are
        // reclaimed by the LRU trim below or by the data reset.
        File(storiesDir, "story-map-$activityId.jpg").let { legacy ->
            if (legacy.isFile) runCatching { StoryMapSnapshotStore.delete(legacy) }
        }
        File(storiesDir, "story-map-$activityId.png").let { legacy ->
            if (legacy.isFile) runCatching { StoryMapSnapshotStore.delete(legacy) }
        }
        val variantHash = StoryMapVariant.variantHash(
            style,
            snapshot.bitmap.width,
            snapshot.bitmap.height,
            snapshot.routePixels,
        )
        val variantDir = StoryMapVariant.variantDir(context.cacheDir, activityId, variantHash)
        val file = StoryMapVariant.followFile(variantDir)
        // A reexport with the same variant keeps the bookends staged by the
        // previous worker run: only the follow shot is overwritten here.
        val previous = StoryMapVariant.readManifest(variantDir)?.takeIf { it.style == style }
        variantDir.mkdirs()
        StoryMapSnapshotStore.write(file, snapshot)
        StoryMapVariant.writeManifest(
            variantDir,
            StoryMapVariant.Manifest(
                activityId = activityId,
                variantHash = variantHash,
                style = style,
                follow = StoryMapVariant.Entry(
                    StoryMapVariant.FOLLOW_NAME,
                    snapshot.bitmap.width,
                    snapshot.bitmap.height,
                ),
                opening = previous?.opening?.takeIf { File(variantDir, it.file).isFile },
                final = previous?.final?.takeIf { File(variantDir, it.file).isFile },
            ),
        )
        StoryMapDiskCache.trim(storiesDir)
        file
    }

    private companion object {
        const val BACKOFF_MS = 10_000L
    }
}
