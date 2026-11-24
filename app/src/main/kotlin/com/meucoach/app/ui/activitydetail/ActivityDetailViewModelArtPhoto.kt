package com.meucoach.app.ui.activitydetail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.meucoach.app.story.ArtLayoutEngine
import com.meucoach.app.story.ArtLayoutTextFactory
import com.meucoach.app.story.ArtPhotoImportError
import com.meucoach.app.story.ArtPhotoImportResult
import com.meucoach.app.story.ArtPhotoRecord
import com.meucoach.app.story.StoryBranding
import com.meucoach.app.story.typeface
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtBlock
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtLayout
import com.meucoach.core.domain.story.ArtOffset
import com.meucoach.core.domain.story.ArtPhotoCrop
import com.meucoach.core.domain.story.ArtPhotoRef
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.ArtTemplate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Photo studio actions of the detail ViewModel (ADR 0061 F5).
 *
 * Lives outside [ActivityDetailViewModel] so the screen ViewModel stays under
 * the LargeClass gate; the state still lives in the ViewModel and these
 * extensions update it through internal accessors.
 */
internal fun ActivityDetailViewModel.selectBackground(mode: ArtBackgroundMode) {
    val studio = currentActivityState().storyStudio
    if (studio.artRecipe.background == mode) return
    if (studio.artPhotoBusy || studio.artExport is ArtExportUiState.Exporting) return
    val photo = studio.artRecipe.photo
    if (mode == ArtBackgroundMode.PHOTO && photo == null) {
        // Transient photo-less state: not persisted (the record requires a
        // file), the CTA stays disabled until a photo is chosen.
        updateArtRecipe { it.copy(background = ArtBackgroundMode.PHOTO, photo = null) }
        return
    }
    updateActivityState {
        it.copy(storyStudio = it.storyStudio.copy(artPhotoBusy = true, artPhotoError = null))
    }
    viewModelScope.launch {
        try {
            val record = ArtPhotoRecord(background = mode, photo = photo)
            withContext(Dispatchers.IO) { artPhotoStore.save(activityId, record) }
            updateActivityState { state ->
                state.copy(
                    storyStudio = state.storyStudio.copy(
                        artRecipe = state.storyStudio.artRecipe.copy(
                            background = mode,
                            photo = photo,
                        ),
                        artPhotoBusy = false,
                    ),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(ART_PHOTO_LOG_TAG, "Art photo store failed", error)
            updateActivityState {
                it.copy(
                    storyStudio = it.storyStudio.copy(
                        artPhotoBusy = false,
                        artPhotoError = photoErrorMessage(ArtPhotoImportError.STORAGE),
                    ),
                )
            }
        }
    }
}

internal fun ActivityDetailViewModel.selectTemplate(template: ArtTemplate) {
    updateArtRecipe { it.copy(layout = it.layout.copy(template = template)) }
}

internal fun ActivityDetailViewModel.selectBlock(block: ArtBlock) {
    updateActivityState { it.copy(storyStudio = it.storyStudio.copy(artSelectedBlock = block)) }
}

/**
 * Accessible nudge: [dx]/[dy] are fractions of the canvas (step 0.02).
 * The persisted offset is the effective one after the box-aware clamp, so
 * the renderer never corrects silently (PRD §5.3).
 */
internal fun ActivityDetailViewModel.nudgeBlock(block: ArtBlock, dx: Float, dy: Float) {
    val studio = currentActivityState().storyStudio
    if (studio.artPhotoBusy || studio.artExport is ArtExportUiState.Exporting) return
    val current = studio.artRecipe.layout.offset(block)
    val desired = ArtOffset(
        dx = (current.dx + dx).coerceIn(ArtOffset.MIN, ArtOffset.MAX),
        dy = (current.dy + dy).coerceIn(ArtOffset.MIN, ArtOffset.MAX),
    )
    val effective = effectiveOffsetFor(block, desired) ?: desired
    if (effective == current) return
    updateArtRecipe { recipe ->
        recipe.copy(layout = recipe.layout.withOffset(block, effective))
    }
}

internal fun ActivityDetailViewModel.setTextScale(percent: Int) {
    if (percent !in ArtLayout.MIN_TEXT_SCALE_PERCENT..ArtLayout.MAX_TEXT_SCALE_PERCENT) return
    updateArtRecipe { it.copy(layout = it.layout.copy(textScalePercent = percent)) }
}

/** PhotoPicker result: null is a cancel and stays a no-op. */
internal fun ActivityDetailViewModel.pickPhoto(uri: Uri?) {
    if (uri == null) return
    val studio = currentActivityState().storyStudio
    if (studio.artPhotoBusy || studio.artExport is ArtExportUiState.Exporting) return
    lastPhotoUri = uri
    updateActivityState {
        it.copy(storyStudio = it.storyStudio.copy(artPhotoBusy = true, artPhotoError = null))
    }
    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            // The importer holds the store mutex, so concurrent imports,
            // toggles and removals serialize (PRD §5.2).
            artPhotoImporter.import(activityId, uri)
        }
        when (result) {
            is ArtPhotoImportResult.Success -> {
                val size = withContext(Dispatchers.IO) { photoSourceSize(result.photo.photoId) }
                val warning = qualityWarningFor(
                    sourceWidth = size?.first,
                    sourceHeight = size?.second,
                    format = currentActivityState().storyStudio.artRecipe.format,
                    crop = result.photo.crop,
                )
                updateActivityState { state ->
                    state.copy(
                        storyStudio = state.storyStudio.copy(
                            artRecipe = state.storyStudio.artRecipe.copy(
                                background = ArtBackgroundMode.PHOTO,
                                photo = result.photo,
                            ),
                            artPhotoBusy = false,
                            artPhotoError = null,
                            artPhotoSourceWidth = size?.first,
                            artPhotoSourceHeight = size?.second,
                            artPhotoQualityWarning = warning,
                            artCropDraft = result.photo.crop,
                        ),
                    )
                }
            }

            is ArtPhotoImportResult.Failure -> {
                updateActivityState {
                    it.copy(
                        storyStudio = it.storyStudio.copy(
                            artPhotoBusy = false,
                            artPhotoError = photoErrorMessage(result.error),
                        ),
                    )
                }
            }
        }
    }
}

internal fun ActivityDetailViewModel.retryPhotoImport() {
    val uri = lastPhotoUri ?: return
    if (currentActivityState().storyStudio.artPhotoBusy) return
    pickPhoto(uri)
}

internal fun ActivityDetailViewModel.dismissPhotoError() {
    updateActivityState { it.copy(storyStudio = it.storyStudio.copy(artPhotoError = null)) }
}

internal fun ActivityDetailViewModel.dismissMissingPhotoWarning() {
    updateActivityState { it.copy(storyStudio = it.storyStudio.copy(artMissingPhotoWarning = false)) }
}

internal fun ActivityDetailViewModel.openCrop() {
    val photo = currentActivityState().storyStudio.artRecipe.photo ?: return
    updateActivityState {
        it.copy(
            storyStudio = it.storyStudio.copy(
                artCropEditing = true,
                artCropDraft = photo.crop,
            ),
        )
    }
    refreshPhotoQualityWarning()
}

internal fun ActivityDetailViewModel.cancelCrop() {
    val photo = currentActivityState().storyStudio.artRecipe.photo
    updateActivityState {
        it.copy(
            storyStudio = it.storyStudio.copy(
                artCropEditing = false,
                artCropDraft = photo?.crop ?: ArtPhotoCrop(),
            ),
        )
    }
}

internal fun ActivityDetailViewModel.updateCrop(focusX: Float, focusY: Float, zoom: Float) {
    val studio = currentActivityState().storyStudio
    if (!studio.artCropEditing) return
    val maxZoom = maxZoomFor(
        sourceWidth = studio.artPhotoSourceWidth,
        sourceHeight = studio.artPhotoSourceHeight,
        format = studio.artRecipe.format,
    )
    val draft = ArtPhotoCrop(
        focusX = focusX.coerceIn(0f, 1f),
        focusY = focusY.coerceIn(0f, 1f),
        zoom = zoom.coerceIn(ArtPhotoCrop.MIN_ZOOM, maxZoom),
    )
    val warning = qualityWarningFor(
        sourceWidth = studio.artPhotoSourceWidth,
        sourceHeight = studio.artPhotoSourceHeight,
        format = studio.artRecipe.format,
        crop = draft,
    )
    updateActivityState {
        it.copy(
            storyStudio = it.storyStudio.copy(
                artCropDraft = draft,
                artPhotoQualityWarning = warning,
            ),
        )
    }
}

internal fun ActivityDetailViewModel.centerCropFocus() {
    val draft = currentActivityState().storyStudio.artCropDraft
    updateCrop(focusX = 0.5f, focusY = 0.5f, zoom = draft.zoom)
}

internal fun ActivityDetailViewModel.frameCrop() {
    updateCrop(focusX = 0.5f, focusY = 0.5f, zoom = ArtPhotoCrop.MIN_ZOOM)
}

internal fun ActivityDetailViewModel.applyCrop() {
    val studio = currentActivityState().storyStudio
    val photo = studio.artRecipe.photo ?: return
    if (!studio.artCropEditing) return
    if (studio.artPhotoBusy) return
    val draft = studio.artCropDraft
    updateActivityState {
        it.copy(storyStudio = it.storyStudio.copy(artPhotoBusy = true, artPhotoError = null))
    }
    viewModelScope.launch {
        try {
            val updated = photo.copy(crop = draft)
            val record = ArtPhotoRecord(
                background = studio.artRecipe.background,
                photo = updated,
            )
            withContext(Dispatchers.IO) { artPhotoStore.save(activityId, record) }
            val warning = qualityWarningFor(
                sourceWidth = currentActivityState().storyStudio.artPhotoSourceWidth,
                sourceHeight = currentActivityState().storyStudio.artPhotoSourceHeight,
                format = currentActivityState().storyStudio.artRecipe.format,
                crop = draft,
            )
            updateActivityState { state ->
                state.copy(
                    storyStudio = state.storyStudio.copy(
                        artRecipe = state.storyStudio.artRecipe.copy(photo = updated),
                        artPhotoBusy = false,
                        artCropEditing = false,
                        artPhotoQualityWarning = warning,
                    ),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(ART_PHOTO_LOG_TAG, "Art photo store failed", error)
            updateActivityState {
                it.copy(
                    storyStudio = it.storyStudio.copy(
                        artPhotoBusy = false,
                        artPhotoError = photoErrorMessage(ArtPhotoImportError.STORAGE),
                    ),
                )
            }
        }
    }
}

internal fun ActivityDetailViewModel.requestRemovePhoto() {
    if (currentActivityState().storyStudio.artRecipe.photo == null) return
    updateActivityState { it.copy(storyStudio = it.storyStudio.copy(artShowRemoveConfirm = true)) }
}

internal fun ActivityDetailViewModel.cancelRemovePhoto() {
    updateActivityState { it.copy(storyStudio = it.storyStudio.copy(artShowRemoveConfirm = false)) }
}

/** Confirmed removal: back to transparent, file deleted (F7 owns reset/delete). */
internal fun ActivityDetailViewModel.removePhoto() {
    val studio = currentActivityState().storyStudio
    if (studio.artPhotoBusy) return
    updateActivityState {
        it.copy(
            storyStudio = it.storyStudio.copy(
                artPhotoBusy = true,
                artPhotoError = null,
                artShowRemoveConfirm = false,
            ),
        )
    }
    viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { artPhotoStore.remove(activityId) }
            updateActivityState { state ->
                state.copy(
                    storyStudio = state.storyStudio.copy(
                        artRecipe = state.storyStudio.artRecipe.copy(
                            background = ArtBackgroundMode.TRANSPARENT,
                            photo = null,
                        ),
                        artPhotoBusy = false,
                        artCropEditing = false,
                        artCropDraft = ArtPhotoCrop(),
                        artPhotoSourceWidth = null,
                        artPhotoSourceHeight = null,
                        artPhotoQualityWarning = null,
                    ),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(ART_PHOTO_LOG_TAG, "Art photo store failed", error)
            updateActivityState {
                it.copy(
                    storyStudio = it.storyStudio.copy(
                        artPhotoBusy = false,
                        artPhotoError = photoErrorMessage(ArtPhotoImportError.STORAGE),
                    ),
                )
            }
        }
    }
}

/** Preview/crop loader for the UI: the caller owns the returned bitmap. */
internal suspend fun ActivityDetailViewModel.loadPhotoBitmap(
    photo: ArtPhotoRef,
    format: ArtFormat,
    targetWidth: Int,
): Bitmap? = artPhotoLoader.load(photo, format, targetWidth)

internal fun ActivityDetailViewModel.effectiveRecipe(style: ArtRecipe, record: ArtPhotoRecord): ArtRecipe =
    style.copy(background = record.background, photo = record.photo)

internal fun ActivityDetailViewModel.photoSourceSize(photoId: String): Pair<Int, Int>? = runCatching {
    val file = artPhotoStore.photoFile(photoId)
    if (!file.isFile) return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    options.outWidth to options.outHeight
}.getOrNull()

internal fun ActivityDetailViewModel.qualityWarningFor(
    sourceWidth: Int?,
    sourceHeight: Int?,
    format: ArtFormat,
    crop: ArtPhotoCrop?,
): String? {
    val width = sourceWidth ?: return null
    val height = sourceHeight ?: return null
    val validCrop = crop ?: return null
    if (width <= 0 || height <= 0) return null
    val rect = validCrop.resolve(format, width, height) ?: return null
    val cropWidth = ((rect.right - rect.left) * width).toInt()
    val cropHeight = ((rect.bottom - rect.top) * height).toInt()
    val side = minOf(cropWidth, cropHeight)
    return when {
        side < ActivityDetailViewModel.MIN_CROP_SIDE -> ActivityDetailViewModel.PHOTO_LOW_STRONG
        side < ActivityDetailViewModel.LOW_RES_SIDE -> ActivityDetailViewModel.PHOTO_LOW_SOFT
        else -> null
    }
}

internal fun ActivityDetailViewModel.hasInvalidSize(width: Int?, height: Int?): Boolean =
    width == null || height == null || width <= 0 || height <= 0

internal fun ActivityDetailViewModel.maxZoomFor(sourceWidth: Int?, sourceHeight: Int?, format: ArtFormat): Float {
    val width = sourceWidth ?: return ArtPhotoCrop.MAX_ZOOM
    val height = sourceHeight ?: return ArtPhotoCrop.MAX_ZOOM
    if (width <= 0 || height <= 0) {
        return ArtPhotoCrop.MAX_ZOOM
    }
    val base = ArtPhotoCrop().resolve(format, width, height) ?: return ArtPhotoCrop.MAX_ZOOM
    val baseWidth = ((base.right - base.left) * width)
    val baseHeight = ((base.bottom - base.top) * height)
    val baseSide = minOf(baseWidth, baseHeight)
    if (baseSide < ActivityDetailViewModel.MIN_CROP_SIDE) return ArtPhotoCrop.MAX_ZOOM
    return (baseSide / ActivityDetailViewModel.MIN_CROP_SIDE).coerceIn(ArtPhotoCrop.MIN_ZOOM, ArtPhotoCrop.MAX_ZOOM)
}

internal fun ActivityDetailViewModel.refreshPhotoQualityWarning() {
    val studio = currentActivityState().storyStudio
    val warning = qualityWarningFor(
        sourceWidth = studio.artPhotoSourceWidth,
        sourceHeight = studio.artPhotoSourceHeight,
        format = studio.artRecipe.format,
        crop = if (studio.artCropEditing) studio.artCropDraft else studio.artRecipe.photo?.crop,
    )
    // Keep the draft-agnostic warning only when it changed; the crop editor
    // already updates it on every keystroke.
    if (warning != studio.artPhotoQualityWarning && !studio.artCropEditing) {
        updateActivityState { it.copy(storyStudio = it.storyStudio.copy(artPhotoQualityWarning = warning)) }
    } else if (studio.artCropEditing) {
        updateActivityState { it.copy(storyStudio = it.storyStudio.copy(artPhotoQualityWarning = warning)) }
    }
}

internal fun ActivityDetailViewModel.photoErrorMessage(error: ArtPhotoImportError): String = when (error) {
    ArtPhotoImportError.SOURCE_UNREADABLE -> "Não foi possível ler a foto. Tente outra imagem."
    ArtPhotoImportError.INVALID_IMAGE -> "Essa imagem não pôde ser usada. Escolha outra foto."
    ArtPhotoImportError.STORAGE -> "Não foi possível salvar a foto. Verifique o espaço e tente de novo."
}

/**
 * Measures the current card and clamps [desired] to the box-aware safe
 * area, so the stored offset is already effective. Falls back to [desired]
 * when the plan, the text rows or the typeface are unavailable (preview and
 * export still clamp, but the Studio never crashes without a route).
 */
internal fun ActivityDetailViewModel.effectiveOffsetFor(block: ArtBlock, desired: ArtOffset): ArtOffset? {
    val studio = currentActivityState().storyStudio
    val plan = studio.plan ?: return null
    val recipe = studio.artRecipe
    val text = ArtLayoutTextFactory.fromPlan(plan, plan.localDate, recipe) ?: return null
    val typeface = runCatching { recipe.font.typeface(appContext) }.getOrElse { Typeface.DEFAULT }
    val logoAspect = runCatching {
        StoryBranding.logo(appContext)?.let { logo ->
            if (logo.width > 0 && logo.height > 0) logo.width.toFloat() / logo.height else null
        }
    }.getOrNull()
    return runCatching {
        ArtLayoutEngine(typeface).effectiveOffset(
            format = recipe.format,
            background = recipe.background,
            layout = recipe.layout,
            block = block,
            desired = desired,
            text = text,
            canvasHeight = recipe.format.height.toFloat(),
            logoAspectRatio = logoAspect,
        )
    }.getOrNull()
}

internal const val ART_PHOTO_LOG_TAG = "ActivityDetailVM"
