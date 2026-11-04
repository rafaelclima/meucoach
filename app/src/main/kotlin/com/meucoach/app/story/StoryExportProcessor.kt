package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

/** Result of one background export attempt. */
sealed interface StoryExportOutcome {
    data class Completed(val result: StoryExportResult) : StoryExportOutcome

    /** Definitive failure: reported to the user on the screen and by notification. */
    data class Failed(val message: String) : StoryExportOutcome

    /** Transient failure (usually the map download); WorkManager retries. */
    data class Retry(val message: String) : StoryExportOutcome
}

/**
 * The whole background story export in one testable unit, without Android
 * WorkManager plumbing: reloads the plan deterministically, picks the map
 * background and runs the codec pipeline (ADR 0018).
 */
class StoryExportProcessor @Inject constructor(
    private val planLoader: StoryPlanLoader,
    private val mapRenderer: MapBackgroundRenderer,
    private val exporter: StoryVideoExporter,
) {

    /**
     * [atMs] is the wall clock used by the ETA estimator; the worker keeps the
     * default, tests inject a deterministic one.
     */
    suspend fun export(
        activityId: String,
        palette: StoryPalette,
        mapPath: String?,
        onProgress: suspend (fraction: Float, etaSec: Int?) -> Unit,
        atMs: () -> Long = { SystemClock.elapsedRealtime() },
    ): StoryExportOutcome {
        val plan = planLoader.load(activityId) ?: return StoryExportOutcome.Failed(StoryMessages.MISSING_GPS)
        val mapBackground = mapBackground(plan, mapPath, palette.mapStyle)
            ?: return StoryExportOutcome.Retry(StoryMessages.MAP_UNAVAILABLE)
        val eta = StoryEtaEstimator()
        val result = exporter.export(plan, palette, mapBackground) { fraction ->
            onProgress(fraction, eta.remainingSec(fraction, atMs()))
        }
        // The staged snapshot is a picture of the run's region: keep it only
        // while the export needs it.
        mapPath?.let { staged -> runCatching { File(staged).delete() } }
        return StoryExportOutcome.Completed(result)
    }

    /**
     * The staged map from the preview is preferred (no duplicate tile fetch);
     * if the cache entry is gone the snapshot is rendered again.
     */
    private suspend fun mapBackground(plan: StoryPlan, mapPath: String?, style: StoryMapStyle): Bitmap? {
        val bounds = plan.boundingBox ?: return null
        mapPath?.let { path ->
            // A staged entry can be evicted between preview and export; skip
            // straight to a fresh snapshot instead of decoding a missing file.
            if (!File(path).isFile) return@let
            val decoded = runCatching {
                withContext(Dispatchers.Default) {
                    BitmapFactory.decodeFile(path, decodeOptions)
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }
        return runCatching {
            withTimeoutOrNull(StoryMessages.MAP_TIMEOUT_MS) {
                mapRenderer.render(
                    bounds,
                    StoryVideoExporter.mapWidth(),
                    StoryVideoExporter.mapHeight(),
                    style,
                )
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            null
        }
    }

    private val decodeOptions = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
}
