package com.meucoach.app.story

/**
 * Smooths coarse progress updates into a stable remaining-time estimate.
 * Pure: callers only supply a wall-clock sample with every fraction update,
 * so it can be tested without a coroutine scheduler.
 *
 * Smoothing matters because one 1080×1920 frame can take very different times
 * around a keyframe; without it the displayed ETA would flicker.
 */
class StoryEtaEstimator(
    /** Exponential smoothing factor for the completion rate. */
    private val smoothing: Double = 0.3,
    private val maxEtaSec: Int = 600,
) {
    private var warmed = false
    private var lastFraction = 0f
    private var lastAtMs = 0L
    private var ratePerSec = 0.0

    /**
     * Feeds one progress sample (fraction 0..1 at wall-clock [atMs]) and returns
     * the estimated remaining seconds. Returns null before there is enough
     * signal (first sample, or a rate too small to be trusted).
     */
    fun remainingSec(fraction: Float, atMs: Long): Int? {
        val current = fraction.coerceIn(0f, 1f)
        if (current >= 1f) return 0
        if (!warmed) {
            warmed = true
            lastFraction = current
            lastAtMs = atMs
            return null
        }
        val delta = (current - lastFraction).toDouble()
        if (delta > 0.0) {
            val dtSec = (atMs - lastAtMs).coerceAtLeast(1L) / 1_000.0
            // A single late sample must not project an implausible burst.
            val instant = (delta / dtSec).coerceIn(0.0, MAX_RATE_PER_SEC)
            ratePerSec = if (ratePerSec <= MIN_RATE_PER_SEC) {
                instant
            } else {
                smoothing * instant + (1 - smoothing) * ratePerSec
            }
        }
        lastFraction = current
        lastAtMs = atMs
        if (ratePerSec <= MIN_RATE_PER_SEC) return null
        val eta = ((1 - current) / ratePerSec).coerceIn(0.0, maxEtaSec.toDouble())
        return kotlin.math.ceil(eta).toInt()
    }

    private companion object {
        /** Progress fraction per second; nothing encodes faster than 1 s total. */
        const val MAX_RATE_PER_SEC = 1.0

        /** Below this the ETA swings by minutes per sample: not trustworthy. */
        const val MIN_RATE_PER_SEC = 0.0005
    }
}
