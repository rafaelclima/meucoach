package com.meucoach.app.story

import com.meucoach.app.ui.activitydetail.RouteData
import com.meucoach.app.ui.activitydetail.routeData
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.story.StoryKmEffort
import com.meucoach.core.domain.story.StoryVerdict
import com.meucoach.core.domain.story.StoryVerdictCalculator
import com.meucoach.core.domain.story.StoryVerdictTone
import com.meucoach.core.domain.zones.HeartRateZoneCalculator
import com.meucoach.core.domain.zones.HrZoneDistributionCalculator
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.HeartRateZone
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

enum class StorySegmentType { MOVE, CARD, FINAL }

data class StorySplit(
    val km: Int,
    val pace: Pace,
    val heartRate: Int?,
    val calories: Int?,
    val progress: Float,
    val verdict: StoryVerdictTone? = null,
)

/** Stretch of the route painted with the heart-rate zone of that moment. */
data class StoryZoneRun(val startProgress: Float, val endProgress: Float, val zone: HeartRateZone)

data class StorySegment(
    val startSec: Float,
    val endSec: Float,
    val type: StorySegmentType,
    val progressStart: Float,
    val progressEnd: Float,
    val split: StorySplit? = null,
)

/**
 * Geographic extent of the run, in WGS-84 degrees. Kept separate from the
 * normalized [RouteData] so a future map background can render exactly the
 * same region the animated route was projected into (ADR 0015 §2.7).
 */
data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

data class StoryPlan(
    val route: RouteData,
    val splits: List<StorySplit>,
    val segments: List<StorySegment>,
    val durationSec: Float,
    val distanceKm: Double,
    val durationTotalSec: Int,
    val calories: Int?,
    val averagePace: Pace?,
    val averageHeartRate: Int?,
    val boundingBox: BoundingBox?,
    val zoneRuns: List<StoryZoneRun> = emptyList(),
    val verdict: StoryVerdict? = null,
)

/**
 * Builds a 9:16 story timeline from a GPS run. The route moves between km
 * cards; each card pauses for [CARD_SECONDS] at its km position and the video
 * always closes with a [StorySegmentType.FINAL] summary (ADR 0015 §2.6).
 * Pure planning so the video renderer stays a thin Android adapter.
 */
object StoryPlanCalculator {
    private const val CARD_SECONDS = 2.0
    private const val FINAL_SECONDS = 3.0
    private const val MAX_CARDS = 8
    private const val MIN_ROUTE_METERS = 50.0

    fun calculate(
        activity: ImportedActivity,
        targetDurationSec: Int = 30,
        lthrBpm: Int? = null,
        session: PlannedSession? = null,
        nextSession: PlannedSession? = null,
    ): StoryPlan? {
        if (targetDurationSec <= 0) return null
        val route = routeData(activity.samples) ?: return null
        val points = geoPoints(activity.samples)
        if (points.size < 2) return null
        val cumulative = cumulativeMeters(points)
        val totalMeters = cumulative.last()
        if (totalMeters < MIN_ROUTE_METERS) return null

        val rawSplits = kmSplits(
            points = points,
            cumulative = cumulative,
            totalMeters = totalMeters,
            totalCalories = activity.calories,
        )
        val verdict = StoryVerdictCalculator.calculate(
            session = session,
            distribution = lthrBpm?.let { HrZoneDistributionCalculator.calculate(activity.samples, it) },
            lthrBpm = lthrBpm,
            efforts = rawSplits.map { StoryKmEffort(km = it.km, averageHeartRateBpm = it.heartRate) },
            nextSession = nextSession,
        )
        val stamps = verdict.stamps.associateBy { it.km }
        val splits = capCards(
            rawSplits.map { split -> split.copy(verdict = stamps[split.km]?.tone) },
        )

        val durationSec = max(targetDurationSec.toDouble(), FINAL_SECONDS)
        val segments = timeline(
            splits = splits,
            targetDurationSec = durationSec,
        )
        val distanceKm = totalMeters / 1_000.0
        return StoryPlan(
            route = route,
            splits = splits,
            segments = segments,
            durationSec = durationSec.toFloat(),
            distanceKm = distanceKm,
            durationTotalSec = activity.durationSec,
            calories = activity.calories,
            averagePace = activity.averagePace ?: averagePace(activity.durationSec, distanceKm),
            averageHeartRate = activity.averageHeartRate ?: averageHeartRate(points),
            boundingBox = bounds(points),
            zoneRuns = lthrBpm?.let { zoneRuns(points, cumulative, totalMeters, it) }.orEmpty(),
            verdict = verdict,
        )
    }

    private data class GeoPoint(
        val latitude: Double,
        val longitude: Double,
        val offsetSec: Double,
        val heartRate: Int?,
    )

    private fun geoPoints(samples: List<ActivitySample>): List<GeoPoint> = samples.mapNotNull { sample ->
        val latitude = sample.latitude ?: return@mapNotNull null
        val longitude = sample.longitude ?: return@mapNotNull null
        GeoPoint(
            latitude = latitude,
            longitude = longitude,
            offsetSec = sample.offsetSec.toDouble(),
            heartRate = sample.heartRate,
        )
    }

    private fun bounds(points: List<GeoPoint>): BoundingBox? {
        if (points.isEmpty()) return null
        return BoundingBox(
            minLat = points.minOf { it.latitude },
            maxLat = points.maxOf { it.latitude },
            minLon = points.minOf { it.longitude },
            maxLon = points.maxOf { it.longitude },
        )
    }

    private fun averagePace(durationSec: Int, distanceKm: Double): Pace? {
        if (durationSec <= 0 || distanceKm <= 0.0) return null
        val secondsPerKm = (durationSec / distanceKm).roundToInt()
        return if (secondsPerKm > 0) Pace.fromSecondsPerKm(secondsPerKm) else null
    }

    private fun averageHeartRate(points: List<GeoPoint>): Int? = points
        .mapNotNull { it.heartRate }
        .filter { it > 0 }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.roundToInt()

    private fun cumulativeMeters(points: List<GeoPoint>): DoubleArray {
        val result = DoubleArray(points.size)
        for (index in 1 until points.size) {
            result[index] = result[index - 1] + haversine(points[index - 1], points[index])
        }
        return result
    }

    private fun kmSplits(
        points: List<GeoPoint>,
        cumulative: DoubleArray,
        totalMeters: Double,
        totalCalories: Int?,
    ): List<StorySplit> {
        val fullKilometers = (totalMeters / 1_000.0).toInt()
        if (fullKilometers <= 0) return emptyList()
        val caloriesPerKm = totalCalories?.let { (it.toDouble() / (totalMeters / 1_000.0)).roundToInt() }
        var previousTime = 0.0
        var previousIndex = 0
        return (1..fullKilometers).mapNotNull { km ->
            val target = km * 1_000.0
            val endIndex = cumulative.indexOfFirst { it >= target }
            if (endIndex <= 0) return@mapNotNull null
            val startDistance = cumulative[endIndex - 1]
            val endDistance = cumulative[endIndex]
            val fraction = if (endDistance > startDistance) {
                ((target - startDistance) / (endDistance - startDistance)).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            val time = points[endIndex - 1].offsetSec +
                (points[endIndex].offsetSec - points[endIndex - 1].offsetSec) * fraction
            val pace = (time - previousTime).roundToInt().coerceAtLeast(1)
            val heartRates = points
                .subList(previousIndex, endIndex + 1)
                .mapNotNull { it.heartRate }
            previousTime = time
            previousIndex = endIndex
            StorySplit(
                km = km,
                pace = Pace.fromSecondsPerKm(pace),
                heartRate = heartRates.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                calories = caloriesPerKm,
                progress = (target / totalMeters).toFloat().coerceIn(0f, 1f),
            )
        }
    }

    private fun capCards(splits: List<StorySplit>): List<StorySplit> {
        if (splits.size <= MAX_CARDS) return splits
        val step = ceil(splits.size.toDouble() / MAX_CARDS).toInt().coerceAtLeast(1)
        return splits.filterIndexed { index, _ -> index % step == 0 }.take(MAX_CARDS)
    }

    private fun timeline(splits: List<StorySplit>, targetDurationSec: Double): List<StorySegment> {
        // The closing frame is part of the requested duration, not an extra tail.
        val finalStart = (targetDurationSec - FINAL_SECONDS).coerceAtLeast(0.0)
        val finalSegment = StorySegment(
            startSec = finalStart.toFloat(),
            endSec = targetDurationSec.toFloat(),
            type = StorySegmentType.FINAL,
            progressStart = 1f,
            progressEnd = 1f,
        )
        if (splits.isEmpty() || finalStart <= 0.0) {
            return buildList {
                if (finalStart > 0.0) {
                    add(
                        StorySegment(
                            startSec = 0f,
                            endSec = finalStart.toFloat(),
                            type = StorySegmentType.MOVE,
                            progressStart = 0f,
                            progressEnd = 1f,
                        ),
                    )
                }
                add(finalSegment)
            }
        }
        val cardSeconds = CARD_SECONDS.coerceAtMost(finalStart / (splits.size * 2.0))
        val moveSeconds = (finalStart - splits.size * cardSeconds).coerceAtLeast(0.0)
        val totalDelta = splits.zipWithNext { previous, next ->
            (next.progress - previous.progress).coerceAtLeast(0f)
        }.sum() + splits.first().progress + (1f - splits.last().progress)
        val segments = mutableListOf<StorySegment>()
        var cursor = 0.0
        var previousProgress = 0f
        splits.forEach { split ->
            val delta = (split.progress - previousProgress).coerceAtLeast(0f)
            if (delta > 0f && totalDelta > 0f) {
                val duration = moveSeconds * delta / totalDelta
                segments += StorySegment(
                    startSec = cursor.toFloat(),
                    endSec = (cursor + duration).toFloat(),
                    type = StorySegmentType.MOVE,
                    progressStart = previousProgress,
                    progressEnd = split.progress,
                )
                cursor += duration
            }
            segments += StorySegment(
                startSec = cursor.toFloat(),
                endSec = (cursor + cardSeconds).toFloat(),
                type = StorySegmentType.CARD,
                progressStart = split.progress,
                progressEnd = split.progress,
                split = split,
            )
            cursor += cardSeconds
            previousProgress = split.progress
        }
        val tail = (1f - previousProgress).coerceAtLeast(0f)
        if (tail > 0f && totalDelta > 0f) {
            val duration = moveSeconds * tail / totalDelta
            segments += StorySegment(
                startSec = cursor.toFloat(),
                endSec = (cursor + duration).toFloat(),
                type = StorySegmentType.MOVE,
                progressStart = previousProgress,
                progressEnd = 1f,
            )
        }
        segments += finalSegment
        return segments
    }

    private fun zoneRuns(
        points: List<GeoPoint>,
        cumulative: DoubleArray,
        totalMeters: Double,
        lthrBpm: Int,
    ): List<StoryZoneRun> {
        if (totalMeters <= 0.0) return emptyList()
        val runs = mutableListOf<StoryZoneRun>()
        var runStart = 0.0
        var runZone: HeartRateZone? = null
        points.forEachIndexed { index, point ->
            val zone = point.heartRate?.let { bpm ->
                runCatching { HeartRateZoneCalculator.zoneFor(bpm, lthrBpm) }.getOrNull()
            }
            val current = runZone
            if (zone != current) {
                if (current != null) {
                    runs += StoryZoneRun(
                        startProgress = (runStart / totalMeters).toFloat().coerceIn(0f, 1f),
                        endProgress = (cumulative[index] / totalMeters).toFloat().coerceIn(0f, 1f),
                        zone = current,
                    )
                }
                runStart = cumulative[index]
                runZone = zone
            }
        }
        runZone?.let { zone ->
            runs += StoryZoneRun(
                startProgress = (runStart / totalMeters).toFloat().coerceIn(0f, 1f),
                endProgress = 1f,
                zone = zone,
            )
        }
        return runs.filter { it.endProgress > it.startProgress }
    }

    private fun haversine(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(lat1) * cos(lat2) * kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
    }

    private const val EARTH_RADIUS_M = 6_371_000.0
}
