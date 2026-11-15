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
    companion object {
        /** Plan loaded: the map phase owns the first 10% of the bar (ENC-06). */
        const val PLAN_READY_FRACTION = 0.02f

        /** Follow snapshot ready (staged or freshly rendered). */
        const val FOLLOW_READY_FRACTION = 0.05f

        /** Opening + final bookends ready: the encode owns 10–100%. */
        const val MAP_READY_FRACTION = 0.10f
    }

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
        onProgress(PLAN_READY_FRACTION, null)
        val variantDir = variantDirFor(mapPath)
        // ENC-05: the recipe is the single source of the map style; the palette
        // only carries colors. A stale palette must not resurrect another style.
        val style = recipe.mapStyle
        // MAP-07: low-RAM devices render the follow shot at 1,25× and keep two
        // keyframes (follow + final). The opening bookend falls back to the
        // follow snapshot in the renderer, which stays aligned.
        val lowRam = mapRenderer.lowRam
        val mapBackground = mapBackground(
            plan,
            mapPath,
            style,
            StoryMapScript.FOLLOW_PITCH_DEGREES,
            lowRam,
        ) ?: return StoryExportOutcome.Retry(StoryMessages.MAP_UNAVAILABLE)
        onProgress(FOLLOW_READY_FRACTION, null)
        // Bookends are best-effort: a failed opening/final render falls back to
        // the follow snapshot in the renderer, which keeps the route aligned.
        // Staged bookends in the variant dir are reused without a tile fetch;
        // a miss renders fresh and stages JPEG 95 + manifest for the reexport.
        val openingMap = if (lowRam) {
            null
        } else {
            cachedOrRenderBookend(
                variantDir = variantDir,
                style = style,
                plan = plan,
                key = BookendKey.OPENING,
                lowRam = lowRam,
            )
        }
        val finalMap = cachedOrRenderBookend(
            variantDir = variantDir,
            style = style,
            plan = plan,
            key = BookendKey.FINAL,
            lowRam = lowRam,
        )
        onProgress(MAP_READY_FRACTION, null)
        val eta = StoryEtaEstimator()
        try {
            val result = exporter.export(
                plan = plan,
                palette = palette,
                recipe = recipe,
                mapBackground = mapBackground,
                openingMap = openingMap,
                finalMap = finalMap,
            ) { fraction ->
                // ENC-06: the map phase owns 0–10% of the bar; the encode owns
                // 10–100%. The mapping is linear, so the ETA rate math is
                // unchanged and the estimate stays stable across the handoff.
                val overall = MAP_READY_FRACTION + (1f - MAP_READY_FRACTION) * fraction
                onProgress(overall, eta.remainingSec(overall, atMs()))
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
        } finally {
            // ENC-08: the worker owns these decoded/rendered bitmaps for this
            // attempt; the files stay cached for the reexport, only the native
            // memory is released. The exporter already finished encoding.
            runCatching { mapBackground.bitmap.recycle() }
            openingMap?.let { runCatching { it.bitmap.recycle() } }
            finalMap?.let { runCatching { it.bitmap.recycle() } }
        }
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
        lowRam: Boolean,
    ): StoryMapSnapshot? {
        if (variantDir == null) {
            return renderBookend(
                plan,
                style,
                key.width(lowRam),
                key.height(lowRam),
                key.pitchDegrees,
            )
        }
        loadStagedBookend(variantDir, style, key, lowRam)?.let { return it }
        val rendered = renderBookend(
            plan,
            style,
            key.width(lowRam),
            key.height(lowRam),
            key.pitchDegrees,
        ) ?: return null
        stageBookend(variantDir, key, rendered)
        return rendered
    }

    private fun loadStagedBookend(
        variantDir: File,
        style: StoryMapStyle,
        key: BookendKey,
        lowRam: Boolean,
    ): StoryMapSnapshot? {
        val manifest = StoryMapVariant.readManifest(variantDir) ?: return null
        if (manifest.style != style) return null
        val entry = key.entryOf(manifest) ?: return null
        if (entry.file != key.fileName || entry.width != key.width(lowRam) || entry.height != key.height(lowRam)) {
            return null
        }
        val file = File(variantDir, entry.file)
        val staged = StoryMapSnapshotStore.read(file) ?: return null
        if (staged.bitmap.width != key.width(lowRam) || staged.bitmap.height != key.height(lowRam)) {
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

        fun width(lowRam: Boolean): Int = when (this) {
            OPENING -> StoryMapScript.openingWidth(lowRam)
            FINAL -> StoryMapScript.finalWidth(lowRam)
        }

        fun height(lowRam: Boolean): Int = when (this) {
            OPENING -> StoryMapScript.openingHeight(lowRam)
            FINAL -> StoryMapScript.finalHeight(lowRam)
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
        lowRam: Boolean,
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
                    StoryVideoExporter.mapWidth(lowRam),
                    StoryVideoExporter.mapHeight(lowRam),
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
