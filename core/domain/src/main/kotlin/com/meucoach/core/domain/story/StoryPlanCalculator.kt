package com.meucoach.core.domain.story

import com.meucoach.core.domain.prescription.PlannedSession
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

/**
 * Builds the 9:16 story plan from a GPS run. The route moves between km cards;
 * each card pauses for its effort-based duration and the video always closes
 * with a [StorySegmentType.FINAL] summary (ADR 0015 §2.6, ADR 0040 §2.5).
 * Pure planning so the video renderer stays a thin Android adapter.
 */
object StoryPlanCalculator {
    private const val MIN_ROUTE_METERS = 50.0
    private const val MAX_GEO_POINTS = 400
    private const val MIN_SPLIT_HR_SAMPLES = 2
    private const val MIN_SPLIT_CADENCE_SAMPLES = 2

    fun calculate(
        activity: ImportedActivity,
        targetDurationSec: Int = StoryDuration.AUTO.targetSeconds(activity.distanceM / 1_000.0),
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

        val rawSplits = kmSplits(points = points, cumulative = cumulative, totalMeters = totalMeters)
        val verdict = StoryVerdictCalculator.calculate(
            session = session,
            distribution = lthrBpm?.let { HrZoneDistributionCalculator.calculate(activity.samples, it) },
            lthrBpm = lthrBpm,
            efforts = rawSplits.map { StoryKmEffort(km = it.km, averageHeartRateBpm = it.heartRate) },
            nextSession = nextSession,
        )
        val stamps = verdict.stamps.associateBy { it.km }
        val allSplits = rawSplits.map { split -> split.copy(verdict = stamps[split.km]?.tone) }
        val cards = StoryCardSelector.select(allSplits)
        val timeline = StoryTimelinePlanner.plan(
            splits = cards,
            targetDurationSec = targetDurationSec,
            requiredKms = requiredKms(cards),
        )
        val distanceKm = totalMeters / 1_000.0
        return StoryPlan(
            route = route,
            splits = timeline.cards,
            allSplits = allSplits,
            splitBar = StorySplitBarCalculator.calculate(
                allSplits = allSplits,
                cardKms = timeline.cards.map { it.km }.toSet(),
            ),
            segments = timeline.segments,
            durationSec = timeline.durationSec,
            distanceKm = distanceKm,
            durationTotalSec = activity.durationSec,
            calories = activity.calories,
            averagePace = activity.averagePace ?: averagePace(activity.durationSec, distanceKm),
            averageHeartRate = activity.averageHeartRate ?: averageHeartRate(points),
            maxHeartRate = activity.maxHeartRate ?: points.mapNotNull { it.heartRate }.maxOrNull(),
            elevationGainM = activity.elevationGainM ?: StoryElevation.gain(points.mapNotNull { it.altitudeM }),
            averageCadenceSpm = activity.averageCadenceSpm ?: averageCadence(points),
            boundingBox = bounds(points),
            zoneRuns = lthrBpm?.let { zoneRuns(points, cumulative, totalMeters, it) }.orEmpty(),
            verdict = verdict,
            geoPoints = sampledGeoPoints(points),
        )
    }

    private data class GeoPoint(
        val latitude: Double,
        val longitude: Double,
        val offsetSec: Double,
        val heartRate: Int?,
        val cadenceSpm: Int?,
        val altitudeM: Double?,
    )

    private fun geoPoints(samples: List<ActivitySample>): List<GeoPoint> = samples.mapNotNull { sample ->
        val latitude = sample.latitude ?: return@mapNotNull null
        val longitude = sample.longitude ?: return@mapNotNull null
        GeoPoint(
            latitude = latitude,
            longitude = longitude,
            offsetSec = sample.offsetSec.toDouble(),
            heartRate = sample.heartRate,
            cadenceSpm = sample.cadenceSpm,
            altitudeM = sample.altitudeM,
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

    private fun sampledGeoPoints(points: List<GeoPoint>, maxPoints: Int = MAX_GEO_POINTS): List<StoryGeoPoint> {
        if (points.size <= maxPoints) return points.map { StoryGeoPoint(it.latitude, it.longitude) }
        val step = ceil(points.size.toDouble() / maxPoints).toInt().coerceAtLeast(1)
        val sampled = buildList {
            points.forEachIndexed { index, point ->
                if (index % step == 0) add(StoryGeoPoint(point.latitude, point.longitude))
            }
            val last = points.last()
            if (lastOrNull()?.latitude != last.latitude || lastOrNull()?.longitude != last.longitude) {
                add(StoryGeoPoint(last.latitude, last.longitude))
            }
        }
        return sampled
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

    private fun averageCadence(points: List<GeoPoint>): Int? = points
        .mapNotNull { it.cadenceSpm }
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

    private fun kmSplits(points: List<GeoPoint>, cumulative: DoubleArray, totalMeters: Double): List<StorySplit> {
        // A 10.000 km run can measure a few metres short after haversine
        // rounding; the last complete kilometre must still produce a split.
        val fullKilometers = (totalMeters / 1_000.0 + 5e-3).toInt()
        if (fullKilometers <= 0) return emptyList()
        var previousTime = 0.0
        var previousIndex = 0
        return (1..fullKilometers).mapNotNull { km ->
            val target = (km * 1_000.0).coerceAtMost(totalMeters)
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
            val samples = points.subList(previousIndex, endIndex + 1)
            val heartRates = samples.mapNotNull { it.heartRate }.filter { it > 0 }
            val cadences = samples.mapNotNull { it.cadenceSpm }.filter { it > 0 }
            previousTime = time
            previousIndex = endIndex
            StorySplit(
                km = km,
                pace = Pace.fromSecondsPerKm(pace),
                heartRate = heartRates.takeIf { it.size >= MIN_SPLIT_HR_SAMPLES }?.average()?.roundToInt(),
                maxHeartRate = heartRates.maxOrNull(),
                cadenceSpm = cadences.takeIf { it.size >= MIN_SPLIT_CADENCE_SAMPLES }?.average()?.roundToInt(),
                elevationGainM = StoryElevation.gain(samples.mapNotNull { it.altitudeM }),
                progress = (target / totalMeters).toFloat().coerceIn(0f, 1f),
            )
        }
    }

    private fun requiredKms(cards: List<StorySplit>): Set<Int> {
        if (cards.isEmpty()) return emptySet()
        return buildSet {
            add(cards.first().km)
            add(cards.last().km)
            cards.minByOrNull { it.pace.secondsPerKm }?.let { add(it.km) }
            cards.maxByOrNull { it.pace.secondsPerKm }?.let { add(it.km) }
        }
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
