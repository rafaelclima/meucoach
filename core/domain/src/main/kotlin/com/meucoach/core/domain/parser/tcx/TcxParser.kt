package com.meucoach.core.domain.parser.tcx

import com.meucoach.core.domain.parser.xml.SecureXml
import com.meucoach.core.domain.parser.xml.child
import com.meucoach.core.domain.parser.xml.children
import com.meucoach.core.domain.parser.xml.double
import com.meucoach.core.domain.parser.xml.elements
import com.meucoach.core.domain.parser.xml.int
import com.meucoach.core.domain.parser.xml.text
import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.w3c.dom.Element
import java.time.Duration
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class TcxParseException(message: String, cause: Throwable? = null) :
    IllegalArgumentException("TCX: $message", cause)

/**
 * Parses Garmin Training Center XML into the canonical import model.
 *
 * TCX cadence is stored as strides per minute by the Zepp exports used as
 * fixtures; running/treadmill values are doubled to steps per minute, matching
 * the FIT adapter. Positions are optional, so a TCX without GPS still imports.
 */
class TcxParser {
    fun parse(bytes: ByteArray): ImportedActivity {
        if (bytes.isEmpty()) throw TcxParseException("File is empty")

        val document = try {
            SecureXml.parse(bytes)
        } catch (error: Exception) {
            throw TcxParseException("Invalid XML", error)
        }
        val activity = document.elements("Activity").firstOrNull()
            ?: throw TcxParseException("No Activity element")
        val sport = sport(activity.getAttribute("Sport"))
        val laps = activity.children("Lap")
        val allPoints = activity.elements("Trackpoint")
        val startTime = activity.text("Id")?.toInstantOrNull()
            ?: laps.firstNotNullOfOrNull { it.getAttribute("StartTime").toInstantOrNull() }
            ?: allPoints.mapNotNull { it.text("Time")?.toInstantOrNull() }.minOrNull()
            ?: throw TcxParseException("No activity start time")

        val samples = buildSamples(allPoints, startTime, sport)
        val duration = laps.sumOf { lapDuration(it) }
            .takeIf { it > 0 }
            ?: pointSpan(allPoints)
                .takeIf { it > 0 }
            ?: throw TcxParseException("Activity has no positive duration")

        val lapDistances = laps.map { lapDistance(it) }
        var distance = lapDistances.sum()
        if (distance <= 0.0) distance = distanceFromPoints(allPoints)
        if (distance <= 0.0) distance = distanceFromSamples(samples)

        val averageHeartRate = averageHeartRate(laps) ?: samples.mapNotNull { it.heartRate }.averageOrNull()
        val maxHeartRate = maxHeartRate(laps) ?: samples.mapNotNull { it.heartRate }.maxOrNull()
        val averageCadence = samples.mapNotNull { it.cadenceSpm }.filter { it > 0 }.averageOrNull()
        val maxCadence = samples.mapNotNull { it.cadenceSpm }.maxOrNull()
        val averagePower = samples.mapNotNull { it.powerW }.averageOrNull()
        val maxPower = samples.mapNotNull { it.powerW }.maxOrNull()
        val (elevationGain, elevationLoss) = elevationStats(samples)
        val calories = laps.mapNotNull { it.int("Calories") }.takeIf { it.isNotEmpty() }?.sum()
        val averagePace = averagePace(duration, distance, samples)

        val modelLaps = if (laps.isEmpty()) {
            listOf(
                ActivityLap(
                    index = 0,
                    durationSec = duration,
                    distanceM = distance,
                    startTime = startTime,
                    averageHeartRate = averageHeartRate,
                    maxHeartRate = maxHeartRate,
                    averagePace = averagePace,
                    averageCadenceSpm = averageCadence,
                    averagePowerW = averagePower,
                    elevationGainM = elevationGain,
                ),
            )
        } else {
            laps.mapIndexed { index, lap ->
                val lapStart = lap.getAttribute("StartTime").toInstantOrNull()
                val lapSamples = buildSamples(lap.elements("Trackpoint"), startTime, sport)
                val lapDistance = lapDistances[index]
                val lapDuration = lapDuration(lap)
                ActivityLap(
                    index = index,
                    durationSec = lapDuration,
                    distanceM = lapDistance,
                    startTime = lapStart,
                    averageHeartRate = heartRate(lap, "AverageHeartRateBpm")
                        ?: lapSamples.mapNotNull { it.heartRate }.averageOrNull(),
                    maxHeartRate = heartRate(lap, "MaximumHeartRateBpm")
                        ?: lapSamples.mapNotNull { it.heartRate }.maxOrNull(),
                    averagePace = averagePace(lapDuration, lapDistance, lapSamples),
                    averageCadenceSpm = lapSamples.mapNotNull { it.cadenceSpm }
                        .filter { it > 0 }
                        .averageOrNull(),
                    averagePowerW = lapSamples.mapNotNull { it.powerW }.averageOrNull(),
                    elevationGainM = elevationStats(lapSamples).first,
                )
            }
        }

        return ImportedActivity(
            source = ActivitySource.TCX,
            sport = sport,
            startTime = startTime,
            durationSec = duration,
            distanceM = distance,
            sourceId = activity.text("Id"),
            movingTimeSec = duration,
            averageHeartRate = averageHeartRate,
            maxHeartRate = maxHeartRate,
            averagePace = averagePace,
            averageCadenceSpm = averageCadence,
            maxCadenceSpm = maxCadence,
            averagePowerW = averagePower,
            maxPowerW = maxPower,
            elevationGainM = elevationGain,
            elevationLossM = elevationLoss,
            calories = calories,
            laps = modelLaps,
            samples = samples,
            deviceName = activity.child("Creator")?.text("Name"),
            parserVersion = PARSER_VERSION,
        )
    }

    private fun buildSamples(points: List<Element>, startTime: Instant, sport: Sport): List<ActivitySample> {
        val normalizeCadence = sport == Sport.RUNNING || sport == Sport.TREADMILL
        return points.mapNotNull { point ->
            val timestamp = point.text("Time")?.toInstantOrNull() ?: return@mapNotNull null
            val offset = Duration.between(startTime, timestamp).seconds
            if (offset < 0 || offset > Int.MAX_VALUE) return@mapNotNull null
            val rawCadence = point.int("Cadence")
            val cadence = if (normalizeCadence) rawCadence?.times(2) else rawCadence
            val position = point.child("Position")
            ActivitySample(
                offsetSec = offset.toInt(),
                timestamp = timestamp,
                latitude = position?.double("LatitudeDegrees")?.takeIf { it in -90.0..90.0 },
                longitude = position?.double("LongitudeDegrees")?.takeIf { it in -180.0..180.0 },
                altitudeM = point.double("AltitudeMeters")?.takeIf { it in -500.0..9000.0 },
                speedMps = extensionDouble(point, "Speed")?.takeIf { it in 0.0..30.0 },
                heartRate = heartRate(point, "HeartRateBpm"),
                cadenceSpm = cadence?.takeIf { it in 0..300 },
                powerW = extensionDouble(point, "Watts")?.roundToInt()?.takeIf { it in 0..2500 },
            )
        }.sortedBy { it.offsetSec }
    }

    private fun lapDuration(lap: Element): Int = lap.double("TotalTimeSeconds")?.takeIf { it > 0.0 }?.roundToInt()
        ?: pointSpan(lap.elements("Trackpoint"))

    private fun lapDistance(lap: Element): Double {
        val declared = lap.double("DistanceMeters")?.takeIf { it > 0.0 }
        if (declared != null) return declared
        val points = lap.elements("Trackpoint")
        val fromPoints = distanceFromPoints(points)
        if (fromPoints > 0.0) return fromPoints
        return distanceFromSamples(buildSamples(points, pointStart(points), Sport.OTHER))
    }

    private fun pointStart(points: List<Element>): Instant =
        points.mapNotNull { it.text("Time")?.toInstantOrNull() }.minOrNull() ?: Instant.EPOCH

    private fun pointSpan(points: List<Element>): Int {
        val times = points.mapNotNull { it.text("Time")?.toInstantOrNull() }
        val first = times.minOrNull() ?: return 0
        val last = times.maxOrNull() ?: return 0
        return Duration.between(first, last).seconds.toInt().coerceAtLeast(0)
    }

    private fun averageHeartRate(laps: List<Element>): Int? {
        val values = laps.mapNotNull { heartRate(it, "AverageHeartRateBpm") }
        return values.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    }

    private fun maxHeartRate(laps: List<Element>): Int? {
        val values = laps.mapNotNull { heartRate(it, "MaximumHeartRateBpm") }
        return values.maxOrNull()
    }

    private fun heartRate(element: Element, name: String): Int? =
        element.child(name)?.int("Value")?.takeIf { it in 30..250 }

    private fun extensionDouble(point: Element, name: String): Double? =
        point.elements(name).firstOrNull()?.textContent?.trim()?.toDoubleOrNull()

    private fun averagePace(durationSec: Int, distanceM: Double, samples: List<ActivitySample>): Pace? {
        if (distanceM > 0.0) {
            val secondsPerKm = (durationSec / (distanceM / 1_000.0)).roundToInt()
            if (secondsPerKm > 0) return Pace.fromSecondsPerKm(secondsPerKm)
        }
        val speeds = samples.mapNotNull { it.speedMps }.filter { it > 0.0 }
        if (speeds.isEmpty()) return null
        return Pace.fromSpeedMetersPerSecond(speeds.average())
    }

    private fun distanceFromSamples(samples: List<ActivitySample>): Double {
        var distance = 0.0
        for (index in 1 until samples.size) {
            val previous = samples[index - 1]
            val current = samples[index]
            val previousSpeed = previous.speedMps ?: continue
            val currentSpeed = current.speedMps ?: continue
            val seconds = (current.offsetSec - previous.offsetSec).coerceAtLeast(0)
            distance += (previousSpeed + currentSpeed) / 2.0 * seconds
        }
        return distance
    }

    private fun distanceFromPoints(points: List<Element>): Double {
        var distance = 0.0
        var previous: Pair<Double, Double>? = null
        for (point in points) {
            val position = point.child("Position") ?: continue
            val latitude = position.double("LatitudeDegrees")?.takeIf { it in -90.0..90.0 } ?: continue
            val longitude = position.double("LongitudeDegrees")?.takeIf { it in -180.0..180.0 } ?: continue
            val current = latitude to longitude
            val last = previous
            if (last != null) distance += haversineMeters(last, current)
            previous = current
        }
        return distance
    }

    private fun elevationStats(samples: List<ActivitySample>): Pair<Double?, Double?> {
        val altitudes = samples.mapNotNull { it.altitudeM }
        if (altitudes.size < 2) return null to null
        var gain = 0.0
        var loss = 0.0
        for (index in 1 until altitudes.size) {
            val delta = altitudes[index] - altitudes[index - 1]
            if (delta > 0.0) gain += delta else loss -= delta
        }
        return gain to loss
    }

    private fun haversineMeters(from: Pair<Double, Double>, to: Pair<Double, Double>): Double {
        val earthRadius = 6_371_000.0
        val latitudeDelta = Math.toRadians(to.first - from.first)
        val longitudeDelta = Math.toRadians(to.second - from.second)
        val fromLatitude = Math.toRadians(from.first)
        val toLatitude = Math.toRadians(to.first)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(fromLatitude) * cos(toLatitude) * sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a.coerceIn(0.0, 1.0)))
    }

    private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(trim()) }.getOrNull()

    private fun List<Int>.averageOrNull(): Int? = takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    private fun sport(value: String?): Sport = when (value?.uppercase()) {
        "RUNNING" -> Sport.RUNNING
        "TREADMILL" -> Sport.TREADMILL
        "WALKING" -> Sport.WALKING
        else -> Sport.OTHER
    }

    companion object {
        const val PARSER_VERSION = "1.0.0"
    }
}
