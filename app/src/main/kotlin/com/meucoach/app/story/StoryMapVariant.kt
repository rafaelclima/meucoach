package com.meucoach.app.story

import android.graphics.PointF
import com.meucoach.core.domain.story.StoryMapStyle
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Variant cache for the story map (MAP-06, fatia 2).
 *
 * - Directory: `cache/stories/<activityId>/<variantHash>/` with `follow.jpg`,
 *   `opening.jpg`, `final.jpg` (JPEG 95 via [StoryMapSnapshotStore]) plus their
 *   `.route` sidecars and a `manifest.json`.
 * - `variantHash` covers style + keyframes + resolution + route pixels: changing
 *   extras/duration does not invalidate the map; a new style, resolution or
 *   import does. Pixels come from `pixelForLatLng`, so the hash is a function
 *   of the GPS through the exact native projection.
 * - The manifest tracks which keyframes are staged; missing/corrupt entries
 *   are a cache miss, never a guessed route.
 */
internal object StoryMapVariant {
    const val MANIFEST_VERSION = 1
    const val MANIFEST_NAME = "manifest.json"
    const val FOLLOW_NAME = "follow.jpg"
    const val OPENING_NAME = "opening.jpg"
    const val FINAL_NAME = "final.jpg"

    data class Entry(val file: String, val width: Int, val height: Int)

    data class Manifest(
        val activityId: String,
        val variantHash: String,
        val style: StoryMapStyle,
        val follow: Entry,
        val opening: Entry? = null,
        val final: Entry? = null,
    )

    fun variantHash(style: StoryMapStyle, followWidth: Int, followHeight: Int, routePixels: List<PointF>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(style.name.toByteArray(Charsets.UTF_8))
        updateInt(digest, followWidth)
        updateInt(digest, followHeight)
        updateInt(digest, StoryMapScript.openingWidth())
        updateInt(digest, StoryMapScript.openingHeight())
        updateInt(digest, StoryMapScript.finalWidth())
        updateInt(digest, StoryMapScript.finalHeight())
        updateFloat(digest, StoryMapScript.OPENING_PITCH_DEGREES)
        updateFloat(digest, StoryMapScript.FOLLOW_PITCH_DEGREES)
        updateFloat(digest, StoryMapScript.FINAL_PITCH_DEGREES)
        updateInt(digest, routePixels.size)
        routePixels.forEach { point ->
            updateInt(digest, java.lang.Float.floatToIntBits(point.x))
            updateInt(digest, java.lang.Float.floatToIntBits(point.y))
        }
        return digest.digest().toHex().take(16)
    }

    fun variantDir(cacheDir: File, activityId: String, variantHash: String): File {
        val safeActivity = activityId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "activity" }
        return File(File(File(cacheDir, "stories"), safeActivity), variantHash)
    }

    fun manifestFile(variantDir: File): File = File(variantDir, MANIFEST_NAME)

    fun followFile(variantDir: File): File = File(variantDir, FOLLOW_NAME)

    fun openingFile(variantDir: File): File = File(variantDir, OPENING_NAME)

    fun finalFile(variantDir: File): File = File(variantDir, FINAL_NAME)

    fun writeManifest(variantDir: File, manifest: Manifest) {
        variantDir.mkdirs()
        val root = JSONObject().apply {
            put("v", MANIFEST_VERSION)
            put("activityId", manifest.activityId)
            put("variant", manifest.variantHash)
            put("style", manifest.style.name)
            put("follow", entryJson(manifest.follow))
            if (manifest.opening != null) put("opening", entryJson(manifest.opening))
            if (manifest.final != null) put("final", entryJson(manifest.final))
        }
        manifestFile(variantDir).writeText(root.toString())
    }

    fun readManifest(variantDir: File): Manifest? = runCatching {
        val root = JSONObject(manifestFile(variantDir).readText())
        check(root.optInt("v", 0) == MANIFEST_VERSION) { "Versão de manifesto não suportada." }
        val activityId = root.optString("activityId", "").takeIf { it.isNotBlank() }
            ?: error("Manifesto sem atividade.")
        val variant = root.optString("variant", "").takeIf { it.isNotBlank() }
            ?: error("Manifesto sem variante.")
        // The directory name is the variant: a manifest copied from another
        // variant must not describe this one.
        check(variantDir.name == variant) { "Manifesto de outra variante." }
        val style = StoryMapStyle.entries.firstOrNull { it.name == root.optString("style", "") }
            ?: error("Estilo do manifesto desconhecido.")
        val follow = entryOrNull(root.optJSONObject("follow")) ?: error("Manifesto sem follow.")
        Manifest(
            activityId = activityId,
            variantHash = variant,
            style = style,
            follow = follow,
            opening = entryOrNull(root.optJSONObject("opening")),
            final = entryOrNull(root.optJSONObject("final")),
        )
    }.getOrNull()

    private fun entryJson(entry: Entry): JSONObject = JSONObject().apply {
        put("file", entry.file)
        put("w", entry.width)
        put("h", entry.height)
    }

    private fun entryOrNull(json: JSONObject?): Entry? {
        if (json == null) return null
        val file = json.optString("file", "").takeIf { it.isNotBlank() } ?: return null
        val width = json.optInt("w", 0).takeIf { it > 0 } ?: return null
        val height = json.optInt("h", 0).takeIf { it > 0 } ?: return null
        return Entry(file, width, height)
    }

    private fun updateInt(digest: MessageDigest, value: Int) {
        digest.update(
            byteArrayOf(
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte(),
            ),
        )
    }

    private fun updateFloat(digest: MessageDigest, value: Float) {
        updateInt(digest, java.lang.Float.floatToIntBits(value))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
