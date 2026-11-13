package com.meucoach.core.domain.story

import kotlin.math.min
import kotlin.math.roundToInt

/** Target length of the exported story. Values are seconds of final video. */
enum class StoryDuration {
    AUTO,
    SEC_15,
    SEC_30,
    SEC_45,
    SEC_60,
    ;

    /**
     * AUTO follows the plan: `clamp(15, 45, 8 + min(km, 12) * 2)`. Short runs
     * stay long enough to read the closing frame; long runs never become a
     * slideshow. Explicit choices are honored as-is.
     */
    fun targetSeconds(distanceKm: Double? = null): Int = when (this) {
        AUTO -> autoSeconds(distanceKm)
        SEC_15 -> 15
        SEC_30 -> 30
        SEC_45 -> 45
        SEC_60 -> 60
    }

    companion object {
        const val MIN_AUTO_SECONDS = 15
        const val MAX_AUTO_SECONDS = 45

        fun autoSeconds(distanceKm: Double?): Int {
            val km = distanceKm?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            val seconds = (8.0 + min(km, 12.0) * 2.0).roundToInt()
            return seconds.coerceIn(MIN_AUTO_SECONDS, MAX_AUTO_SECONDS)
        }
    }
}

/**
 * Map treatment of the story. Ordinals are part of the palette codec contract:
 * new values may only be appended, and legacy values stay declared so old
 * WorkManager jobs still decode (MAP-08). The new default is [MEUCOACH_DARK];
 * the actual autoral style is implemented in Sprint B.
 */
enum class StoryMapStyle {
    /** Legacy OpenFreeMap liberty style. */
    CLASSIC,

    /** Satellite imagery. Out of the MVP by license; kept for old jobs. */
    @Deprecated("Satellite is out of the MVP by license (ADR 0040 §2.4).")
    SATELLITE,

    /** Adds a hillshade layer fed by public elevation tiles (Mapzen/AWS). */
    TERRAIN,

    /** Autoral dark style; Sprint B (MAP-04). */
    MEUCOACH_DARK,

    /** Autoral light style; Sprint B (MAP-04). */
    MEUCOACH_LIGHT,
}

/**
 * Encode profile of the story video. 60 fps is defined but not offered until
 * the encoder is validated on device (ENC-02/DEV-04).
 */
enum class StoryQuality(val width: Int, val height: Int, val fps: Int, val bitrate: Int) {
    STANDARD_1080P30(width = 1080, height = 1920, fps = 30, bitrate = 16_000_000),
    SMOOTH_1080P60(width = 1080, height = 1920, fps = 60, bitrate = 16_000_000),
}

/** Starting point of the recipe. Extras are only a preference, never a promise. */
enum class StoryPreset {
    ESSENTIAL,
    PERFORMANCE,
    PHYSIOLOGICAL,
    COMPLETE,
    CUSTOM,
    ;

    /** Extras requested by the preset, in display order. */
    fun requestedExtras(): List<StoryExtra> = when (this) {
        ESSENTIAL -> emptyList()
        PERFORMANCE -> listOf(StoryExtra.FASTEST_KM, StoryExtra.ELEVATION_GAIN)
        PHYSIOLOGICAL -> listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE)
        COMPLETE -> listOf(StoryExtra.AVG_HR, StoryExtra.CALORIES_TOTAL)
        CUSTOM -> emptyList()
    }
}

/**
 * Deterministic description of what the story shows. The three essentials are
 * fixed; at most two extras are allowed. Same recipe feeds preview, worker and
 * poster, so all three cannot drift (ADR 0040 §2.1).
 */
data class StoryRecipe(
    val preset: StoryPreset = StoryPreset.ESSENTIAL,
    val extras: List<StoryExtra> = emptyList(),
    val duration: StoryDuration = StoryDuration.AUTO,
    val mapStyle: StoryMapStyle = StoryMapStyle.MEUCOACH_DARK,
    val quality: StoryQuality = StoryQuality.STANDARD_1080P30,
) {
    init {
        require(extras.size <= MAX_EXTRAS) { "O story aceita no máximo 2 extras." }
        require(extras.distinct().size == extras.size) { "Extras duplicados." }
    }

    val essentials: List<StoryEssential> get() = ESSENTIALS

    /** Extras that are selected and have data for this activity, in recipe order. */
    fun resolvedExtras(values: StoryMetricValues): List<StoryExtra> =
        extras.filter { StoryMetricCatalog.isAvailable(it, values) }.take(MAX_EXTRAS)

    fun withExtras(extras: List<StoryExtra>): StoryRecipe = copy(preset = StoryPreset.CUSTOM, extras = extras)

    /**
     * Toggles one extra and moves the recipe to [StoryPreset.CUSTOM]. A third
     * extra is ignored instead of thrown: the Studio disables the chip, and a
     * stale tap must never crash the screen. The order of selection is kept,
     * because it is also the order used by the card and closing frame.
     */
    fun toggleExtra(extra: StoryExtra): StoryRecipe = when {
        extra in extras -> copy(preset = StoryPreset.CUSTOM, extras = extras - extra)
        extras.size >= MAX_EXTRAS -> this
        else -> copy(preset = StoryPreset.CUSTOM, extras = extras + extra)
    }

    fun targetDurationSec(distanceKm: Double?): Int = duration.targetSeconds(distanceKm)

    companion object {
        const val MAX_EXTRAS = 2
        val ESSENTIALS: List<StoryEssential> = StoryEssential.entries

        /**
         * Preset selection never turns on an unavailable metric: it keeps the
         * available subset (up to two) and leaves the remaining slot free.
         */
        fun fromPreset(
            preset: StoryPreset,
            values: StoryMetricValues,
            duration: StoryDuration = StoryDuration.AUTO,
            mapStyle: StoryMapStyle = StoryMapStyle.MEUCOACH_DARK,
            quality: StoryQuality = StoryQuality.STANDARD_1080P30,
        ): StoryRecipe {
            val extras = preset.requestedExtras()
                .filter { StoryMetricCatalog.isAvailable(it, values) }
                .distinct()
                .take(MAX_EXTRAS)
            return StoryRecipe(
                preset = preset,
                extras = extras,
                duration = duration,
                mapStyle = mapStyle,
                quality = quality,
            )
        }
    }
}
