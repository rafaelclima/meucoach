package com.meucoach.app.story

import android.os.SystemClock
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRecipe
import kotlinx.coroutines.CancellationException
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
        recipe: StoryRecipe,
        palette: StoryPalette,
        mapPath: String?,
        onProgress: suspend (fraction: Float, etaSec: Int?) -> Unit,
        atMs: () -> Long = { SystemClock.elapsedRealtime() },
    ): StoryExportOutcome {
        val plan = planLoader.load(activityId, recipe) ?: return StoryExportOutcome.Failed(StoryMessages.MISSING_GPS)
        val mapBackground = mapBackground(
            plan,
            mapPath,
            palette.mapStyle,
            StoryMapScript.FOLLOW_PITCH_DEGREES,
        ) ?: return StoryExportOutcome.Retry(StoryMessages.MAP_UNAVAILABLE)
        // Bookends are best-effort: a failed opening/final render falls back to
        // the follow snapshot in the renderer, which keeps the route aligned.
        val openingMap =
            renderBookend(
                plan,
                palette.mapStyle,
                StoryMapScript::openingWidth,
                StoryMapScript::openingHeight,
                StoryMapScript.OPENING_PITCH_DEGREES,
            )
        val finalMap =
            renderBookend(
                plan,
                palette.mapStyle,
                StoryMapScript::finalWidth,
                StoryMapScript::finalHeight,
                StoryMapScript.FINAL_PITCH_DEGREES,
            )
        val eta = StoryEtaEstimator()
        val result = exporter.export(
            plan = plan,
            palette = palette,
            recipe = recipe,
            mapBackground = mapBackground,
            openingMap = openingMap,
            finalMap = finalMap,
        ) { fraction ->
            onProgress(fraction, eta.remainingSec(fraction, atMs()))
        }
        // The staged snapshot is a picture of the run's region: keep it only
        // while the export needs it.
        mapPath?.let { staged -> StoryMapSnapshotStore.delete(File(staged)) }
        return StoryExportOutcome.Completed(result)
    }

    /**
     * The staged map from the preview is preferred (no duplicate tile fetch);
     * if the cache entry or its route sidecar is gone, the snapshot is rendered
     * again with both the bitmap and the exact `pixelForLatLng` pixels.
     */
    private suspend fun mapBackground(
        plan: StoryPlan,
        mapPath: String?,
        style: StoryMapStyle,
        pitchDegrees: Float,
    ): StoryMapSnapshot? {
        val bounds = plan.boundingBox ?: return null
        mapPath?.let { path ->
            // A staged entry can be evicted between preview and export; skip
            // straight to a fresh snapshot instead of decoding a missing file.
            val staged = StoryMapSnapshotStore.read(File(path))
            if (staged != null) return staged
        }
        return runCatching {
            withTimeoutOrNull(StoryMessages.MAP_TIMEOUT_MS) {
                mapRenderer.render(
                    bounds,
                    plan.geoPoints,
                    StoryVideoExporter.mapWidth(),
                    StoryVideoExporter.mapHeight(),
                    style,
                    pitchDegrees,
                )
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            null
        }
    }

    /**
     * One bookend keyframe (opening/final) with its own native pixels. Never
     * fails the export: null means the renderer falls back to the follow shot.
     */
    private suspend fun renderBookend(
        plan: StoryPlan,
        style: StoryMapStyle,
        width: () -> Int,
        height: () -> Int,
        pitchDegrees: Float,
    ): StoryMapSnapshot? {
        val bounds = plan.boundingBox ?: return null
        return runCatching {
            withTimeoutOrNull(StoryMessages.MAP_TIMEOUT_MS) {
                mapRenderer.render(
                    bounds,
                    plan.geoPoints,
                    width(),
                    height(),
                    style,
                    pitchDegrees,
                )
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            null
        }
    }
}
