package com.meucoach.core.domain.story

import com.meucoach.core.model.Pace
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The three always-on metrics of the story. They cannot be removed from the
 * recipe: a run without distance, pace or total time is not a run story
 * (ADR 0040 §2.1).
 */
enum class StoryEssential {
    DISTANCE,
    AVG_PACE,
    TOTAL_TIME,
}

/**
 * Optional summary metrics. At most two are selected by the recipe. Only
 * [ELEVATION_GAIN], [AVG_HR], [CADENCE] and [FASTEST_KM] have a per-kilometre
 * representation; calories are a total-only extra because dividing them by km
 * would invent a precision the measurement does not have.
 */
enum class StoryExtra {
    CALORIES_TOTAL,
    ELEVATION_GAIN,
    AVG_HR,
    MAX_HR,
    CADENCE,
    FASTEST_KM,
    DATE,
}

/** Why a metric cannot be shown. The label is the pt-BR copy used by the UI. */
enum class StoryMetricReason(val label: String) {
    NO_DISTANCE("sem distância"),
    NO_PACE("sem ritmo"),
    NO_TIME("sem tempo"),
    NO_CALORIES("sem calorias"),
    NO_ELEVATION("sem elevação"),
    NO_HEART_RATE("sem FC"),
    NO_MAX_HEART_RATE("sem FC máxima"),
    NO_CADENCE("sem cadência"),
    NO_SPLITS("sem kms completos"),
    NO_DATE("sem data"),
}

/** Availability of one metric for one activity. Null reason means it can be shown. */
data class StoryMetricAvailability(val reason: StoryMetricReason? = null) {
    val isAvailable: Boolean get() = reason == null

    companion object {
        val Available = StoryMetricAvailability()
    }
}

/**
 * Summary facts used to decide availability and format the story metrics.
 * Every missing value stays null; `0` is only a measured value when the source
 * explicitly reports it (for example, a flat run with 0 m of elevation gain).
 */
data class StoryMetricValues(
    val distanceKm: Double? = null,
    val totalTimeSec: Int? = null,
    val averagePace: Pace? = null,
    val calories: Int? = null,
    val elevationGainM: Double? = null,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val averageCadenceSpm: Int? = null,
    val completedSplits: Int = 0,
    val fastestKm: Int? = null,
    val fastestPace: Pace? = null,
    val localDate: LocalDate? = null,
) {
    init {
        require(distanceKm == null || (distanceKm.isFinite() && distanceKm >= 0.0)) {
            "Distance must be finite and non-negative"
        }
        require(completedSplits >= 0) { "Completed splits must not be negative" }
    }
}

/**
 * Single source of truth for which story metric is available, how it reads and
 * whether it belongs to a km card. Pure Kotlin so the same decisions feed the
 * preview, the worker and the tests (ADR 0040 §2.8).
 */
object StoryMetricCatalog {
    private val PT_BR = Locale.forLanguageTag("pt-BR")
    private val MONTHS_PT_BR = listOf(
        "jan",
        "fev",
        "mar",
        "abr",
        "mai",
        "jun",
        "jul",
        "ago",
        "set",
        "out",
        "nov",
        "dez",
    )

    val essentials: List<StoryEssential> = StoryEssential.entries

    fun label(metric: StoryEssential): String = when (metric) {
        StoryEssential.DISTANCE -> "DISTÂNCIA"
        StoryEssential.AVG_PACE -> "RITMO MÉDIO"
        StoryEssential.TOTAL_TIME -> "TEMPO TOTAL"
    }

    fun label(metric: StoryExtra): String = when (metric) {
        StoryExtra.CALORIES_TOTAL -> "CALORIAS"
        StoryExtra.ELEVATION_GAIN -> "ELEVAÇÃO"
        StoryExtra.AVG_HR -> "FC MÉDIA"
        StoryExtra.MAX_HR -> "FC MÁXIMA"
        StoryExtra.CADENCE -> "CADÊNCIA"
        StoryExtra.FASTEST_KM -> "KM MAIS RÁPIDO"
        StoryExtra.DATE -> "DATA"
    }

    fun availability(metric: StoryEssential, values: StoryMetricValues): StoryMetricAvailability = when (metric) {
        StoryEssential.DISTANCE ->
            if (positiveDistance(values) != null) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_DISTANCE)
            }

        StoryEssential.AVG_PACE ->
            if (averagePace(values) != null) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_PACE)
            }

        StoryEssential.TOTAL_TIME ->
            if (positiveInt(values.totalTimeSec) != null) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_TIME)
            }
    }

    fun availability(metric: StoryExtra, values: StoryMetricValues): StoryMetricAvailability = when (metric) {
        StoryExtra.CALORIES_TOTAL ->
            if (positiveInt(values.calories) != null) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_CALORIES)
            }

        StoryExtra.ELEVATION_GAIN ->
            if (values.elevationGainM != null && values.elevationGainM >= 0.0) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_ELEVATION)
            }

        StoryExtra.AVG_HR ->
            if (positiveInt(values.averageHeartRate) != null) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_HEART_RATE)
            }

        StoryExtra.MAX_HR ->
            if (positiveInt(values.maxHeartRate) != null) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_MAX_HEART_RATE)
            }

        StoryExtra.CADENCE ->
            if (positiveInt(values.averageCadenceSpm) != null) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_CADENCE)
            }

        StoryExtra.FASTEST_KM ->
            if (hasFastestSplit(values)) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_SPLITS)
            }

        StoryExtra.DATE ->
            if (values.localDate != null) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_DATE)
            }
    }

    fun isAvailable(metric: StoryEssential, values: StoryMetricValues): Boolean =
        availability(metric, values).isAvailable

    fun isAvailable(metric: StoryExtra, values: StoryMetricValues): Boolean = availability(metric, values).isAvailable

    /** Formatted value, or null when the metric must not be shown. */
    fun format(metric: StoryEssential, values: StoryMetricValues): String? = when (metric) {
        StoryEssential.DISTANCE -> positiveDistance(values)?.let { formatDistance(it) }
        StoryEssential.AVG_PACE -> averagePace(values)?.let { formatPace(it) }
        StoryEssential.TOTAL_TIME -> positiveInt(values.totalTimeSec)?.let { formatStoryDuration(it) }
    }

    fun format(metric: StoryExtra, values: StoryMetricValues): String? = when (metric) {
        StoryExtra.CALORIES_TOTAL -> positiveInt(values.calories)?.let { "$it kcal" }

        StoryExtra.ELEVATION_GAIN ->
            values.elevationGainM
                ?.takeIf { it >= 0.0 }
                ?.let { formatElevation(it) }

        StoryExtra.AVG_HR -> positiveInt(values.averageHeartRate)?.let { "$it bpm" }

        StoryExtra.MAX_HR -> positiveInt(values.maxHeartRate)?.let { "$it bpm" }

        StoryExtra.CADENCE -> positiveInt(values.averageCadenceSpm)?.let { "$it spm" }

        StoryExtra.FASTEST_KM -> {
            val km = positiveInt(values.fastestKm)
            val pace = values.fastestPace
            if (hasFastestSplit(values) && km != null && pace != null) {
                "km $km · ${formatPaceShort(pace)}"
            } else {
                null
            }
        }

        StoryExtra.DATE -> values.localDate?.let { formatDate(it) }
    }

    /**
     * Extras that have a value for one kilometre. FASTEST_KM is a badge, not a
     * numeric slot, so it is intentionally excluded from [formatPerKm].
     */
    fun appearsPerKm(metric: StoryExtra): Boolean = when (metric) {
        StoryExtra.ELEVATION_GAIN, StoryExtra.AVG_HR, StoryExtra.CADENCE -> true
        StoryExtra.CALORIES_TOTAL, StoryExtra.MAX_HR, StoryExtra.FASTEST_KM, StoryExtra.DATE -> false
    }

    fun isPerKmBadge(metric: StoryExtra): Boolean = metric == StoryExtra.FASTEST_KM

    fun perKmAvailability(metric: StoryExtra, split: StorySplit): StoryMetricAvailability = when (metric) {
        StoryExtra.AVG_HR ->
            if (positiveInt(split.heartRate) != null) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_HEART_RATE)
            }

        StoryExtra.CADENCE ->
            if (positiveInt(split.cadenceSpm) != null) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_CADENCE)
            }

        StoryExtra.ELEVATION_GAIN ->
            if (split.elevationGainM != null && split.elevationGainM >= 0.0) {
                StoryMetricAvailability.Available
            } else {
                StoryMetricAvailability(StoryMetricReason.NO_ELEVATION)
            }

        StoryExtra.CALORIES_TOTAL,
        StoryExtra.MAX_HR,
        StoryExtra.FASTEST_KM,
        StoryExtra.DATE,
        -> StoryMetricAvailability(StoryMetricReason.NO_DISTANCE)
    }

    fun formatPerKm(metric: StoryExtra, split: StorySplit): String? = when (metric) {
        StoryExtra.AVG_HR -> positiveInt(split.heartRate)?.let { "$it bpm" }

        StoryExtra.CADENCE -> positiveInt(split.cadenceSpm)?.let { "$it spm" }

        StoryExtra.ELEVATION_GAIN ->
            split.elevationGainM
                ?.takeIf { it >= 0.0 }
                ?.let { formatElevation(it) }

        StoryExtra.CALORIES_TOTAL,
        StoryExtra.MAX_HR,
        StoryExtra.FASTEST_KM,
        StoryExtra.DATE,
        -> null
    }

    /** Label for the per-km value, aligned with the final-frame label. */
    fun perKmLabel(metric: StoryExtra): String = label(metric)

    private fun positiveDistance(values: StoryMetricValues): Double? =
        values.distanceKm?.takeIf { it.isFinite() && it > 0.0 }

    private fun hasFastestSplit(values: StoryMetricValues): Boolean =
        values.completedSplits >= 2 && values.fastestKm != null && values.fastestPace != null

    private fun positiveInt(value: Int?): Int? = value?.takeIf { it > 0 }

    private fun averagePace(values: StoryMetricValues): Pace? {
        values.averagePace?.let { return it }
        val distance = positiveDistance(values) ?: return null
        val duration = positiveInt(values.totalTimeSec) ?: return null
        val secondsPerKm = (duration / distance).roundToInt()
        return if (secondsPerKm > 0) Pace.fromSecondsPerKm(secondsPerKm) else null
    }

    private fun formatDistance(kilometers: Double): String = String.format(PT_BR, "%.2f km", kilometers)

    private fun formatPace(pace: Pace): String =
        String.format(PT_BR, "%d:%02d /km", pace.secondsPerKm / 60, pace.secondsPerKm % 60)

    private fun formatPaceShort(pace: Pace): String =
        String.format(PT_BR, "%d:%02d", pace.secondsPerKm / 60, pace.secondsPerKm % 60)

    private fun formatElevation(meters: Double): String = String.format(PT_BR, "+%d m", meters.roundToInt())

    private fun formatDate(date: LocalDate): String {
        val month = MONTHS_PT_BR[date.monthValue - 1]
        return "${date.dayOfMonth} $month"
    }
}
