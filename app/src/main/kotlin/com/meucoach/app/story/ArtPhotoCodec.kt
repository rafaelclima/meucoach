package com.meucoach.app.story

import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtPhotoCrop
import com.meucoach.core.domain.story.ArtPhotoRef
import org.json.JSONObject

/**
 * Effective background and photo of one activity, as persisted by the photo
 * codec (ADR 0061 §2.3). A `TRANSPARENT` record may still carry a waiting photo
 * so switching the toggle back to `PHOTO` reuses it.
 */
data class ArtPhotoRecord(val background: ArtBackgroundMode, val photo: ArtPhotoRef?) {
    init {
        require(background != ArtBackgroundMode.PHOTO || photo != null) {
            "PHOTO background requires a photo reference."
        }
    }
}

/** Owner-qualified metadata: the activity id inside the file must match its consumer. */
data class ArtPhotoMetadata(val activityId: String, val record: ArtPhotoRecord) {
    init {
        require(activityId.isNotBlank()) { "activityId must not be blank." }
    }
}

/**
 * Versioned JSON for the per-activity photo record (ADR 0061 §2.3). The decoder
 * is forgiving per field: an unknown background becomes `TRANSPARENT`, invalid
 * focus/zoom are clamped/defaulted, and an invalid or missing photo id drops the
 * photo. A corrupt payload, an unknown version or a blank activity id returns
 * `null`, and the store treats that as no metadata (transparent).
 */
object ArtPhotoCodec {
    const val VERSION = 1

    private const val KEY_VERSION = "v"
    private const val KEY_ACTIVITY = "activityId"
    private const val KEY_BACKGROUND = "background"
    private const val KEY_PHOTO_ID = "photoId"
    private const val KEY_FOCUS_X = "focusX"
    private const val KEY_FOCUS_Y = "focusY"
    private const val KEY_ZOOM = "zoom"

    fun encode(activityId: String, record: ArtPhotoRecord): String {
        require(activityId.isNotBlank()) { "activityId must not be blank." }
        return JSONObject().apply {
            put(KEY_VERSION, VERSION)
            put(KEY_ACTIVITY, activityId)
            put(KEY_BACKGROUND, record.background.name)
            record.photo?.let { photo ->
                put(KEY_PHOTO_ID, photo.photoId)
                put(KEY_FOCUS_X, photo.crop.focusX.toDouble())
                put(KEY_FOCUS_Y, photo.crop.focusY.toDouble())
                put(KEY_ZOOM, photo.crop.zoom.toDouble())
            }
        }.toString()
    }

    fun decode(json: String?): ArtPhotoMetadata? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(json)
            require(root.optInt(KEY_VERSION, 0) == VERSION) { "Unsupported art photo version" }
            val activityId = root.optString(KEY_ACTIVITY)
            require(activityId.isNotBlank()) { "Missing art photo activity id" }
            val background = enumOr(root.optString(KEY_BACKGROUND), ArtBackgroundMode.TRANSPARENT)
            val photo = decodePhoto(root, activityId)
            val normalizedBackground = if (background == ArtBackgroundMode.PHOTO && photo == null) {
                ArtBackgroundMode.TRANSPARENT
            } else {
                background
            }
            ArtPhotoMetadata(activityId, ArtPhotoRecord(normalizedBackground, photo))
        }.getOrNull()
    }

    private fun decodePhoto(root: JSONObject, activityId: String): ArtPhotoRef? {
        val photoId = root.optString(KEY_PHOTO_ID)
        if (photoId.isBlank()) return null
        val crop = ArtPhotoCrop(
            focusX = sanitizedFocus(root, KEY_FOCUS_X),
            focusY = sanitizedFocus(root, KEY_FOCUS_Y),
            zoom = sanitizedZoom(root),
        )
        return runCatching { ArtPhotoRef(photoId = photoId, activityId = activityId, crop = crop) }
            .getOrNull()
    }

    private fun sanitizedFocus(root: JSONObject, key: String): Float {
        val value = root.optDouble(key, Double.NaN)
        return if (value.isFinite()) value.toFloat().coerceIn(0f, 1f) else 0.5f
    }

    private fun sanitizedZoom(root: JSONObject): Float {
        val value = root.optDouble(KEY_ZOOM, Double.NaN)
        return if (value.isFinite()) value.toFloat().coerceIn(1f, 4f) else 1f
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
