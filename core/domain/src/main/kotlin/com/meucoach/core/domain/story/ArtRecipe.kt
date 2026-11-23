package com.meucoach.core.domain.story

/**
 * Card format of the transparent route art (ADR 0058 §2.2, extended by ADR
 * 0060). Export is always 1080 px wide; preview renders the same aspect at
 * ~540 px. `ROUTE_CROP` and an opaque 9:16 full-bleed are out of the MVP, so
 * no other entry may be added without a new ADR.
 */
enum class ArtFormat(val width: Int, val height: Int, val fileTag: String) {
    PORTRAIT_4_5(width = 1080, height = 1350, fileTag = "4x5"),
    SQUARE(width = 1080, height = 1080, fileTag = "1x1"),

    /** Transparent story card (ADR 0060); not an opaque full-bleed overlay. */
    PORTRAIT_9_16(width = 1080, height = 1920, fileTag = "9x16"),
}

/** Font families offered by the art picker; resolution to `Typeface` happens in the app. */
enum class ArtFont {
    INTER,
    GEIST,
    BEBAS_NEUE,
    ANTON,
    OSWALD,
    ARCHIVO_BLACK,
}

/**
 * The eight closed swatches of the art (ADR 0058 §2.4). Values are the tokens
 * already documented by the design system, kept here as the pure-Kotlin source
 * for renderers and contrast checks; UI code must keep referencing the tokens,
 * not literals. `accent/lime-pressed` is a pressed state, not a swatch.
 */
enum class ArtColor(val argb: Int) {
    LIME(0xFFD8FF3E.toInt()),
    CORAL(0xFFFF6B5E.toInt()),
    WHITE(0xFFF4F6F7.toInt()),
    BLACK(0xFF0A0B0D.toInt()),
    GRAY_LIGHT(0xFFA2A9B0.toInt()),
    GRAY_DARK(0xFF5C646D.toInt()),
    CYAN(0xFF5BD0E0.toInt()),
    BLUE(0xFF6C9CFF.toInt()),
}

/**
 * Deterministic description of the transparent route art. Essentials are fixed
 * (distance, total time, average pace) and come from the same story pipeline;
 * at most two extras are allowed. Text and route colors are chosen
 * independently. Same recipe feeds preview and export, so they cannot drift
 * (ADR 0058 §2.6).
 *
 * The photo studio extends the recipe additively (ADR 0061 §2.1): the default
 * remains transparent, with the TOP layout, 100% text and no photo. A PHOTO
 * recipe may temporarily omit [photo] while the picker/import is in flight;
 * persistence requires the file, but the in-memory recipe must never crash the
 * screen.
 */
data class ArtRecipe(
    val format: ArtFormat = ArtFormat.PORTRAIT_4_5,
    val extras: List<StoryExtra> = emptyList(),
    val font: ArtFont = ArtFont.INTER,
    val textColor: ArtColor = ArtColor.WHITE,
    val routeColor: ArtColor = ArtColor.LIME,
    val background: ArtBackgroundMode = ArtBackgroundMode.TRANSPARENT,
    val layout: ArtLayout = ArtLayout(),
    val photo: ArtPhotoRef? = null,
) {
    init {
        require(extras.size <= MAX_EXTRAS) { "A arte aceita no máximo 2 extras." }
        require(extras.distinct().size == extras.size) { "Extras duplicados." }
    }

    fun withExtras(extras: List<StoryExtra>): ArtRecipe = copy(extras = extras)

    /**
     * Toggles one extra, keeping the selection order used by the card. A third
     * extra is ignored instead of thrown: the Studio disables the chip, and a
     * stale tap must never crash the screen.
     */
    fun toggleExtra(extra: StoryExtra): ArtRecipe = when {
        extra in extras -> copy(extras = extras - extra)
        extras.size >= MAX_EXTRAS -> this
        else -> copy(extras = extras + extra)
    }

    /**
     * Maps the art into the video recipe so metrics keep being resolved by
     * [StoryRenderSpecResolver.fromPlan] — the art never reformats a metric by
     * itself; it only picks which extras appear.
     */
    fun toStoryRecipe(): StoryRecipe = StoryRecipe(preset = StoryPreset.CUSTOM, extras = extras)

    companion object {
        const val MAX_EXTRAS = 2

        /**
         * Extras the art chips offer, in display order. Video-only metrics
         * ([StoryExtra.MAX_HR], [StoryExtra.FASTEST_KM]) are not part of the
         * card; the Studio still filters by availability before rendering.
         */
        val EXTRA_CHOICES: List<StoryExtra> = listOf(
            StoryExtra.DATE,
            StoryExtra.AVG_HR,
            StoryExtra.ELEVATION_GAIN,
            StoryExtra.CALORIES_TOTAL,
            StoryExtra.CADENCE,
        )
    }
}

/**
 * Identity of one exported art image (ADR 0058 §2.7, extended by ADR 0061
 * §2.6): sharing reuses the last file only while activity, plan fingerprint,
 * recipe and format stay unchanged.
 */
data class ArtExportKey(
    val activityId: String,
    val planFingerprint: ArtPlanFingerprint,
    val recipe: ArtRecipe,
    val format: ArtFormat,
)
