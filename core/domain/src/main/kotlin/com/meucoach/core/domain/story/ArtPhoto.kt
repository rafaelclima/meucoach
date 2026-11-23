package com.meucoach.core.domain.story

/**
 * Crop of one photo, expressed as a normalized focus plus zoom instead of a
 * free rectangle (ADR 0061 §2.2). The focus is a point in the upright source
 * image; changing the card format keeps it and re-derives the rectangle.
 *
 * `zoom = 1` is the largest crop of the target aspect inside the source, so a
 * portrait photo never reveals empty space. Values are validated once here and
 * [resolve] is a pure, deterministic function of the crop and source size.
 */
data class ArtPhotoCrop(val focusX: Float = 0.5f, val focusY: Float = 0.5f, val zoom: Float = 1f) {
    init {
        require(focusX.isFinite() && focusY.isFinite() && zoom.isFinite()) {
            "Photo crop must be finite (focusX=$focusX, focusY=$focusY, zoom=$zoom)."
        }
        require(focusX in 0f..1f && focusY in 0f..1f) {
            "Photo focus must stay inside the source (focusX=$focusX, focusY=$focusY)."
        }
        require(zoom in MIN_ZOOM..MAX_ZOOM) { "Photo zoom must be between 1 and 4: $zoom." }
    }

    /**
     * Derives the normalized rectangle of the source that feeds [format].
     *
     * The base is the largest rectangle of the target aspect inside the source,
     * centered on the focus and clamped to the edges. Zoom divides both sides
     * around the focus, preserving the aspect. Invalid source dimensions return
     * `null`; the function never divides by zero.
     */
    fun resolve(format: ArtFormat, sourceWidth: Int, sourceHeight: Int): ArtCropRect? {
        if (sourceWidth <= 0 || sourceHeight <= 0) return null

        val targetAspect = format.width.toDouble() / format.height.toDouble()
        val sourceAspect = sourceWidth.toDouble() / sourceHeight.toDouble()
        val baseWidth: Double
        val baseHeight: Double
        if (sourceAspect >= targetAspect) {
            baseHeight = sourceHeight.toDouble()
            baseWidth = baseHeight * targetAspect
        } else {
            baseWidth = sourceWidth.toDouble()
            baseHeight = baseWidth / targetAspect
        }

        val cropWidth = baseWidth / zoom.toDouble()
        val cropHeight = baseHeight / zoom.toDouble()
        val focusCenterX = focusX.toDouble() * sourceWidth
        val focusCenterY = focusY.toDouble() * sourceHeight
        val maxLeft = (sourceWidth - cropWidth).coerceAtLeast(0.0)
        val maxTop = (sourceHeight - cropHeight).coerceAtLeast(0.0)
        val left = (focusCenterX - cropWidth / 2.0).coerceIn(0.0, maxLeft)
        val top = (focusCenterY - cropHeight / 2.0).coerceIn(0.0, maxTop)

        return ArtCropRect(
            left = (left / sourceWidth).toFloat().coerceIn(0f, 1f),
            top = (top / sourceHeight).toFloat().coerceIn(0f, 1f),
            right = ((left + cropWidth) / sourceWidth).toFloat().coerceIn(0f, 1f),
            bottom = ((top + cropHeight) / sourceHeight).toFloat().coerceIn(0f, 1f),
        )
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f
    }
}

/**
 * Derived crop rectangle, normalized to the source (0..1). It is never
 * persisted: the photo store keeps [ArtPhotoCrop] and the loader resolves the
 * rectangle for the current format.
 */
data class ArtCropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            "Crop rectangle must be finite (left=$left, top=$top, right=$right, bottom=$bottom)."
        }
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "Crop rectangle must stay normalized inside the source."
        }
        require(left < right && top < bottom) {
            "Crop rectangle must have a positive width and height."
        }
    }
}

/**
 * Reference to the single private photo of an activity: a UUID file name, the
 * owning activity and the crop. The owner is checked by the photo store so one
 * run can never display the photo of another (ADR 0061 §2.2).
 */
data class ArtPhotoRef(val photoId: String, val activityId: String, val crop: ArtPhotoCrop = ArtPhotoCrop()) {
    init {
        require(UUID_PATTERN.matches(photoId)) { "photoId must be a UUID: $photoId." }
        require(activityId.isNotBlank()) { "activityId must not be blank." }
    }
}

private val UUID_PATTERN = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
)
