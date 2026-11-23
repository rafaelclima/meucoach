package com.meucoach.core.domain.story

import java.security.MessageDigest
import java.util.Locale

/**
 * Stable identity of the story plan used by the export cache (ADR 0061 §2.6).
 * The export key combines it with the activity, recipe and format, so a changed
 * date, metric or route never reuses an image from an older plan.
 */
@JvmInline
value class ArtPlanFingerprint(val value: String) {
    init {
        require(value.isNotBlank()) { "Art plan fingerprint must not be blank." }
    }
}

object ArtPlanFingerprints {
    const val VERSION = "art-plan-v1"

    /**
     * SHA-256 of the date, distance, total duration, average pace and the
     * normalized route points. Route coordinates are rendered with five
     * decimal places so tiny float noise from the projection does not
     * invalidate a valid cache entry.
     */
    fun of(plan: StoryPlan): ArtPlanFingerprint {
        val canonical = buildString {
            append(VERSION)
            append("|date=").append(plan.localDate?.toString() ?: "null")
            append("|distanceKm=").append(decimal(plan.distanceKm))
            append("|durationTotalSec=").append(plan.durationTotalSec)
            append("|paceSecondsPerKm=").append(plan.averagePace?.secondsPerKm ?: "null")
            append("|routePoints=").append(plan.route.points.size).append('[')
            plan.route.points.forEachIndexed { index, point ->
                if (index > 0) append(';')
                append(decimal(point.x.toDouble())).append(',').append(decimal(point.y.toDouble()))
            }
            append(']')
        }
        return ArtPlanFingerprint(sha256(canonical))
    }

    private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.5f", value)
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    val digits = "0123456789abcdef"
    val hex = CharArray(digest.size * 2)
    digest.forEachIndexed { index, byte ->
        val unsigned = byte.toInt() and 0xFF
        hex[index * 2] = digits[unsigned ushr 4]
        hex[index * 2 + 1] = digits[unsigned and 0x0F]
    }
    return String(hex)
}
