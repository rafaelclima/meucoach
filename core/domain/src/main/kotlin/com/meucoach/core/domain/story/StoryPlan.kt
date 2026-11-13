package com.meucoach.core.domain.story

import com.meucoach.core.model.HeartRateZone
import com.meucoach.core.model.Pace
import java.time.LocalDate

enum class StorySegmentType { MOVE, CARD, FINAL }

/**
 * One completed kilometre. Missing measurements stay null: a km without heart
 * rate, cadence or elevation samples simply has no value for that extra. There
 * is no calories-per-km field — dividing the total by km invents precision and
 * was removed from the product (ADR 0040 §2.1).
 */
data class StorySplit(
    val km: Int,
    val pace: Pace,
    val heartRate: Int?,
    val maxHeartRate: Int? = null,
    val cadenceSpm: Int? = null,
    val elevationGainM: Double? = null,
    val progress: Float,
    val verdict: StoryVerdictTone? = null,
) {
    init {
        require(km > 0) { "Split kilometer must be positive" }
        require(progress in 0f..1f) { "Split progress must be between 0 and 1" }
        require(elevationGainM == null || elevationGainM >= 0.0) {
            "Elevation gain must not be negative"
        }
    }
}

/** Stretch of the route painted with the heart-rate zone of that moment. */
data class StoryZoneRun(val startProgress: Float, val endProgress: Float, val zone: HeartRateZone)

data class StorySegment(
    val startSec: Float,
    val endSec: Float,
    val type: StorySegmentType,
    val progressStart: Float,
    val progressEnd: Float,
    val split: StorySplit? = null,
) {
    init {
        require(endSec >= startSec) { "Segment must not end before it starts" }
    }
}

/**
 * One segment of the split bar. Short runs get one segment per km; long runs
 * are grouped so a 42 km story still has a readable bar. [startKm] and
 * [endKm] are inclusive.
 */
data class StorySplitBarSegment(val startKm: Int, val endKm: Int, val hasCard: Boolean) {
    init {
        require(startKm > 0) { "Split bar segment must start at km 1 or later" }
        require(endKm >= startKm) { "Split bar segment range must be valid" }
    }

    val label: String
        get() = if (startKm == endKm) "$startKm" else "$startKm–$endKm"
}

/** All completed kilometres, with the ones that received a card marked. */
data class StorySplitBar(val segments: List<StorySplitBarSegment>) {
    companion object {
        val Empty = StorySplitBar(emptyList())
    }
}

/** Geographic extent of the run, in WGS-84 degrees. Kept separate from the
 * normalized [RouteData] so a future map background can render exactly the
 * same region the animated route was projected into (ADR 0015 §2.7). */
data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

/** One route vertex in geographic coordinates, for the tilted projection. */
data class StoryGeoPoint(val latitude: Double, val longitude: Double)

data class StoryPlan(
    val route: RouteData,
    /** Kilometres selected for cards, ordered by km. */
    val splits: List<StorySplit>,
    /** Every completed kilometre, used by the split bar, ticks and highlights. */
    val allSplits: List<StorySplit>,
    val splitBar: StorySplitBar,
    val segments: List<StorySegment>,
    val durationSec: Float,
    val distanceKm: Double,
    val durationTotalSec: Int,
    val calories: Int?,
    val averagePace: Pace?,
    val averageHeartRate: Int?,
    val maxHeartRate: Int? = null,
    val elevationGainM: Double? = null,
    val averageCadenceSpm: Int? = null,
    val boundingBox: BoundingBox?,
    /** Local date of the activity; used by the DATE extra. */
    val localDate: LocalDate? = null,
    val zoneRuns: List<StoryZoneRun> = emptyList(),
    val verdict: StoryVerdict? = null,
    /** Route vertices used to project the line onto a tilted snapshot. */
    val geoPoints: List<StoryGeoPoint> = emptyList(),
)
