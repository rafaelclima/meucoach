package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtExportKey
import com.meucoach.core.domain.story.ArtPlanFingerprint
import com.meucoach.core.domain.story.ArtPlanFingerprints
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.StoryPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** Number of rendered PNG/JPG files kept in the art cache; older ones are pruned. */
private const val ART_CACHE_FILE_LIMIT = 8

/** Extensions counted by the shared PNG+JPG cache; `.tmp` never counts (ADR 0061 §2.6). */
private val ART_CACHE_EXTENSIONS = setOf("png", "jpg")

private const val TAG = "ArtPngExporter"

/** Outcome of one art export for the Studio CTAs (ADR 0058 §2.7, ADR 0061 §2.6). */
sealed interface ArtPngExport {
    /** The plan has no route; the UI keeps the CTAs disabled instead. */
    data object NoRoute : ArtPngExport

    data class Published(
        val uri: Uri,
        val savedToGallery: Boolean,
        /** True when [Compartilhar] reused the image of an unchanged recipe. */
        val reused: Boolean,
    ) : ArtPngExport

    /** The recipe or photo changed while rendering; nothing was published. */
    data object Stale : ArtPngExport

    data class Failure(val message: String) : ArtPngExport
}

/**
 * Synchronous, full-resolution export of the art card (ADR 0058 §9.1, ADR
 * 0061 §2.6): render 1080 px, write a PNG (transparent) or JPEG q92 (photo) in
 * the cache and publish via [GalleryImagePublisher]. No Worker — the Studio
 * stays open and shows a busy CTA. Sharing reuses the last image while
 * activity + plan fingerprint + recipe stay unchanged and the cache file,
 * MediaStore URI and photo still exist; saving always publishes a new copy.
 */
interface ArtPngExporter {
    suspend fun export(
        plan: StoryPlan,
        localDate: LocalDate?,
        recipe: ArtRecipe,
        activityId: String,
        forShare: Boolean,
    ): ArtPngExport

    /**
     * Export that also checks the recipe still on screen just before
     * publishing. The Studio passes its current state so an edit made while
     * the render is in flight is discarded instead of creating a gallery row.
     */
    suspend fun export(
        plan: StoryPlan,
        localDate: LocalDate?,
        recipe: ArtRecipe,
        activityId: String,
        forShare: Boolean,
        currentRecipe: () -> ArtRecipe,
    ): ArtPngExport = export(plan, localDate, recipe, activityId, forShare)
}

class CanvasArtPngExporter
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val publisher: GalleryImagePublisher,
    private val photoStore: ArtPhotoStore,
    private val photoLoader: ArtPhotoLoader,
) : ArtPngExporter {
    private var lastKey: ArtExportKey? = null
    private var lastUri: Uri? = null
    private var lastFile: File? = null

    override suspend fun export(
        plan: StoryPlan,
        localDate: LocalDate?,
        recipe: ArtRecipe,
        activityId: String,
        forShare: Boolean,
    ): ArtPngExport = export(plan, localDate, recipe, activityId, forShare) { recipe }

    override suspend fun export(
        plan: StoryPlan,
        localDate: LocalDate?,
        recipe: ArtRecipe,
        activityId: String,
        forShare: Boolean,
        currentRecipe: () -> ArtRecipe,
    ): ArtPngExport = withContext(Dispatchers.Default) {
        if (plan.route.points.size < 2) return@withContext ArtPngExport.NoRoute
        val fingerprint = ArtPlanFingerprints.of(plan)
        val key = keyFor(fingerprint, recipe, activityId)
        if (forShare) {
            reusableUri(key, recipe)?.let { uri ->
                return@withContext ArtPngExport.Published(uri, savedToGallery = true, reused = true)
            }
        }
        var rendered: File? = null
        try {
            val file = render(plan, localDate, recipe)
            rendered = file
            if (!isStillCurrent(fingerprint, recipe, activityId, currentRecipe) || !photoExists(recipe)) {
                discard(file)
                return@withContext ArtPngExport.Stale
            }
            val published = publish(file, recipe)
            lastKey = key
            lastUri = published.uri
            lastFile = file
            pruneCache(keep = file)
            ArtPngExport.Published(published.uri, published.savedToGallery, reused = false)
        } catch (error: Throwable) {
            // The technical detail is useful in the log; the UI gets the copy.
            rendered?.let(::discard)
            Log.w(TAG, "Falha ao renderizar/publicar a arte", error)
            ArtPngExport.Failure(StoryMessages.ART_EXPORT_FAILED)
        }
    }

    /** Reuses only while the key, cache file, MediaStore URI and photo are all valid. */
    private fun reusableUri(key: ArtExportKey, recipe: ArtRecipe): Uri? {
        val sameKey = lastKey == key
        val fileExists = lastFile?.isFile == true
        val uri = lastUri
        return uri?.takeIf { sameKey && fileExists && canOpen(it) && photoExists(recipe) }
    }

    private fun isStillCurrent(
        fingerprint: ArtPlanFingerprint,
        recipe: ArtRecipe,
        activityId: String,
        currentRecipe: () -> ArtRecipe,
    ): Boolean {
        val current = runCatching { currentRecipe() }.getOrNull() ?: return false
        return keyFor(fingerprint, current, activityId) == keyFor(fingerprint, recipe, activityId)
    }

    private fun keyFor(fingerprint: ArtPlanFingerprint, recipe: ArtRecipe, activityId: String): ArtExportKey =
        ArtExportKey(
            activityId = activityId,
            planFingerprint = fingerprint,
            recipe = recipe.normalizedForKey(),
            format = recipe.format,
        )

    /**
     * A transparent recipe may still carry the waiting photo; it must not
     * invalidate a PNG that the user can see (ADR 0061 §2.6).
     */
    private fun ArtRecipe.normalizedForKey(): ArtRecipe =
        if (background == ArtBackgroundMode.TRANSPARENT) copy(photo = null) else this

    private fun photoExists(recipe: ArtRecipe): Boolean {
        if (recipe.background == ArtBackgroundMode.TRANSPARENT) return true
        val photo = recipe.photo ?: return false
        return runCatching {
            photoStore.isUsablePhotoFile(photoStore.photoFile(photo.photoId))
        }.getOrDefault(false)
    }

    private fun canOpen(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)

    private fun publish(file: File, recipe: ArtRecipe): ImagePublishResult =
        if (recipe.background == ArtBackgroundMode.PHOTO) {
            publisher.publishJpg(file)
        } else {
            publisher.publish(file)
        }

    /**
     * Keeps the cache bounded after a successful publish. The freshly
     * published file is always kept — it becomes `lastFile`, the reuse source.
     */
    private fun pruneCache(keep: File) {
        val files = keep.parentFile?.listFiles { file ->
            file.isFile && file.extension.lowercase() in ART_CACHE_EXTENSIONS
        } ?: return
        files.sortedByDescending { it.lastModified() }
            .drop(ART_CACHE_FILE_LIMIT)
            .filter { it != keep }
            .forEach { stale -> stale.delete() }
    }

    private fun discard(file: File) {
        if (file != lastFile) file.delete()
    }

    private suspend fun render(plan: StoryPlan, localDate: LocalDate?, recipe: ArtRecipe): File {
        val renderer = StoryArtRenderer(
            recipe = recipe,
            typeface = recipe.font.typeface(context),
            logoBitmap = StoryBranding.logo(context),
        )
        val dir = File(context.cacheDir, ART_CACHE_DIRECTORY).apply { mkdirs() }
        return if (recipe.background == ArtBackgroundMode.PHOTO) {
            renderJpg(renderer, plan, localDate, recipe, dir)
        } else {
            renderPng(renderer, plan, localDate, recipe, dir)
        }
    }

    private suspend fun renderJpg(
        renderer: StoryArtRenderer,
        plan: StoryPlan,
        localDate: LocalDate?,
        recipe: ArtRecipe,
        dir: File,
    ): File {
        val photo = requireNotNull(recipe.photo) { "A foto é obrigatória no modo Foto." }
        val bitmap = photoLoader.load(photo, recipe.format, recipe.format.width)
            ?: error("A foto da arte não pôde ser carregada.")
        try {
            val file = File(dir, StoryFileNames.artPhoto(plan, recipe.format, nextStamp(dir)))
            file.outputStream().use { output ->
                check(renderer.writeJpg(plan, localDate, bitmap, output)) {
                    "Sem foto suficiente para a arte."
                }
            }
            return file
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderPng(
        renderer: StoryArtRenderer,
        plan: StoryPlan,
        localDate: LocalDate?,
        recipe: ArtRecipe,
        dir: File,
    ): File {
        val file = File(dir, StoryFileNames.art(plan, recipe.format, nextStamp(dir)))
        file.outputStream().use { output ->
            check(renderer.writePng(plan, localDate, recipe.format.width, recipe.format.height, output)) {
                "Sem rota suficiente para a arte."
            }
        }
        return file
    }

    /** Keeps PNG and JPG names from the same millisecond apart inside the cache. */
    private fun nextStamp(dir: File): Long {
        var stamp = System.currentTimeMillis()
        val names = dir.listFiles()?.map { it.name }.orEmpty()
        while (names.any { it.contains("-$stamp-arte") }) stamp++
        return stamp
    }

    private companion object {
        const val ART_CACHE_DIRECTORY = "art"
    }
}
