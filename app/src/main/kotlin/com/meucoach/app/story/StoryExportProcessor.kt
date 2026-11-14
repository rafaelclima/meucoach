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
        val variantDir = variantDirFor(mapPath)
        val mapBackground = mapBackground(
            plan,
            mapPath,
            palette.mapStyle,
            StoryMapScript.FOLLOW_PITCH_DEGREES,
        ) ?: return StoryExportOutcome.Retry(StoryMessages.MAP_UNAVAILABLE)
        // Bookends are best-effort: a failed opening/final render falls back to
        // the follow snapshot in the renderer, which keeps the route aligned.
        // Staged bookends in the variant dir are reused without a tile fetch;
        // a miss renders fresh and stages JPEG 95 + manifest for the reexport.
        val openingMap = cachedOrRenderBookend(
            variantDir = variantDir,
            style = palette.mapStyle,
            plan = plan,
            key = BookendKey.OPENING,
        )
        val finalMap = cachedOrRenderBookend(
            variantDir = variantDir,
            style = palette.mapStyle,
            plan = plan,
            key = BookendKey.FINAL,
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
        // Variant cache stays for the reexport (LRU + reset reclaim it);
        // legacy flat staging is removed after the export as before.
        if (variantDir != null) {
            variantDir.parentFile?.parentFile?.let { storiesDir ->
                runCatching { StoryMapDiskCache.trim(storiesDir) }
            }
        } else {
            mapPath?.let { staged -> StoryMapSnapshotStore.delete(File(staged)) }
        }
        return StoryExportOutcome.Completed(result)
    }

    /**
     * Variant dir for bookend reuse: the parent of the staged follow shot when
     * the coordinator wrote a manifest there. Legacy flat staging has no
     * manifest and keeps rendering bookends fresh.
     */
    private fun variantDirFor(mapPath: String?): File? {
        if (mapPath == null) return null
        val parent = File(mapPath).parentFile ?: return null
        if (!StoryMapVariant.manifestFile(parent).isFile) return null
        return parent
    }

    /**
     * Loads a staged bookend from the variant dir or renders and stages it in
     * JPEG 95. Never fails the export: null falls back to the follow shot.
     */
    private suspend fun cachedOrRenderBookend(
        variantDir: File?,
        style: StoryMapStyle,
        plan: StoryPlan,
        key: BookendKey,
    ): StoryMapSnapshot? {
        if (variantDir == null) {
            return renderBookend(plan, style, key.width, key.height, key.pitchDegrees)
        }
        loadStagedBookend(variantDir, style, key)?.let { return it }
        val rendered = renderBookend(plan, style, key.width, key.height, key.pitchDegrees) ?: return null
        stageBookend(variantDir, key, rendered)
        return rendered
    }

    private fun loadStagedBookend(variantDir: File, style: StoryMapStyle, key: BookendKey): StoryMapSnapshot? {
        val manifest = StoryMapVariant.readManifest(variantDir) ?: return null
        if (manifest.style != style) return null
        val entry = key.entryOf(manifest) ?: return null
        if (entry.file != key.fileName || entry.width != key.width || entry.height != key.height) return null
        val file = File(variantDir, entry.file)
        val staged = StoryMapSnapshotStore.read(file) ?: return null
        if (staged.bitmap.width != key.width || staged.bitmap.height != key.height) {
            staged.bitmap.recycle()
            return null
        }
        return staged
    }

    private fun stageBookend(variantDir: File, key: BookendKey, snapshot: StoryMapSnapshot) {
        val manifest = StoryMapVariant.readManifest(variantDir) ?: return
        // Never resurrect a stale entry: only update when the staged file is
        // the one the manifest tracks.
        val current = key.entryOf(manifest)
        if (current != null && current.file != key.fileName) return
        runCatching {
            StoryMapSnapshotStore.write(File(variantDir, key.fileName), snapshot)
            StoryMapVariant.writeManifest(
                variantDir,
                key.withEntry(
                    manifest,
                    StoryMapVariant.Entry(key.fileName, snapshot.bitmap.width, snapshot.bitmap.height),
                ),
            )
        }
    }

    /** Fixed bookend keyframes staged in the variant dir (MAP-06 fatia 2). */
    private enum class BookendKey {
        OPENING,
        FINAL,
        ;

        val fileName: String
            get() = when (this) {
                OPENING -> StoryMapVariant.OPENING_NAME
                FINAL -> StoryMapVariant.FINAL_NAME
            }

        val width: Int
            get() = when (this) {
                OPENING -> StoryMapScript.openingWidth()
                FINAL -> StoryMapScript.finalWidth()
            }

        val height: Int
            get() = when (this) {
                OPENING -> StoryMapScript.openingHeight()
                FINAL -> StoryMapScript.finalHeight()
            }

        val pitchDegrees: Float
            get() = when (this) {
                OPENING -> StoryMapScript.OPENING_PITCH_DEGREES
                FINAL -> StoryMapScript.FINAL_PITCH_DEGREES
            }

        fun entryOf(manifest: StoryMapVariant.Manifest): StoryMapVariant.Entry? = when (this) {
            OPENING -> manifest.opening
            FINAL -> manifest.final
        }

        fun withEntry(manifest: StoryMapVariant.Manifest, entry: StoryMapVariant.Entry?): StoryMapVariant.Manifest =
            when (this) {
                OPENING -> manifest.copy(opening = entry)
                FINAL -> manifest.copy(final = entry)
            }
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
        width: Int,
        height: Int,
        pitchDegrees: Float,
    ): StoryMapSnapshot? {
        val bounds = plan.boundingBox ?: return null
        return runCatching {
            withTimeoutOrNull(StoryMessages.MAP_TIMEOUT_MS) {
                mapRenderer.render(
                    bounds,
                    plan.geoPoints,
                    width,
                    height,
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
