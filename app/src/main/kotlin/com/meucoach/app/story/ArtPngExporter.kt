package com.meucoach.app.story

import android.content.Context
import android.net.Uri
import com.meucoach.core.domain.story.ArtExportKey
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.StoryPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** Outcome of one art export for the Studio CTAs (ADR 0058 §2.7). */
sealed interface ArtPngExport {
    /** The plan has no route; the UI keeps the CTAs disabled instead. */
    data object NoRoute : ArtPngExport

    data class Published(
        val uri: Uri,
        val savedToGallery: Boolean,
        /** True when [Compartilhar] reused the PNG of an unchanged recipe. */
        val reused: Boolean,
    ) : ArtPngExport

    data class Failure(val message: String) : ArtPngExport
}

/**
 * Synchronous, full-resolution export of the art card (ADR 0058 §9.1): render
 * 1080 px, write a PNG in the cache and publish via [GalleryImagePublisher].
 * No Worker — the Studio stays open and shows a busy CTA. Sharing reuses the
 * last PNG while activity + recipe stay unchanged; saving always publishes a
 * new copy, because saving is the explicit intent.
 */
interface ArtPngExporter {
    suspend fun export(
        plan: StoryPlan,
        localDate: LocalDate?,
        recipe: ArtRecipe,
        activityId: String,
        forShare: Boolean,
    ): ArtPngExport
}

class CanvasArtPngExporter
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val publisher: GalleryImagePublisher,
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
    ): ArtPngExport = withContext(Dispatchers.Default) {
        if (plan.route.points.size < 2) return@withContext ArtPngExport.NoRoute
        val key = ArtExportKey(activityId, recipe, recipe.format)
        if (forShare) {
            val file = lastFile
            val uri = lastUri
            if (lastKey == key && uri != null && file?.exists() == true) {
                return@withContext ArtPngExport.Published(uri, savedToGallery = true, reused = true)
            }
        }
        try {
            val file = render(plan, localDate, recipe)
            val published = publisher.publish(file)
            lastKey = key
            lastUri = published.uri
            lastFile = file
            ArtPngExport.Published(published.uri, published.savedToGallery, reused = false)
        } catch (error: Throwable) {
            ArtPngExport.Failure(error.message ?: "Não foi possível gerar o PNG da arte.")
        }
    }

    private fun render(plan: StoryPlan, localDate: LocalDate?, recipe: ArtRecipe): File {
        val renderer = StoryArtRenderer(
            recipe = recipe,
            typeface = recipe.font.typeface(context),
            logoBitmap = StoryBranding.logo(context),
        )
        val dir = File(context.cacheDir, "art").apply { mkdirs() }
        val file = File(dir, StoryFileNames.art(plan, recipe.format, System.currentTimeMillis()))
        file.outputStream().use { output ->
            check(renderer.writePng(plan, localDate, recipe.format.width, recipe.format.height, output)) {
                "Sem rota suficiente para a arte."
            }
        }
        return file
    }
}
