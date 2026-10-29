package com.meucoach.core.domain.parser.gpx

import com.meucoach.core.domain.parser.xml.SecureXml
import com.meucoach.core.domain.parser.xml.double
import com.meucoach.core.domain.parser.xml.elements
import com.meucoach.core.domain.parser.xml.text
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

class GpxParseException(message: String, cause: Throwable? = null) :
    IllegalArgumentException("GPX: $message", cause)

/**
 * Parses GPX tracks into the canonical import model.
 *
 * GPX has no laps or sport. This adapter uses one inferred activity, keeps
 * missing elevation as null, and understands the common Garmin TrackPoint
 * extensions (hr, cad, speed and power). Running cadence is normalized from
 * strides/min to steps/min.
 */
class GpxParser {
    fun parse(bytes: ByteArray): ImportedActivity {
        if (bytes.isEmpty()) throw GpxParseException("File is empty")

        val document = try {
            SecureXml.parse(bytes)
        } catch (error: Exception) {
            throw GpxParseException("Invalid XML", error)
        }
        val points = document.elements("trkpt")
        if (points.isEmpty()) throw GpxParseException("No track points")

        val times = points.mapNotNull { it.text("time")?.toInstantOrNull() }
        val startTime = times.minOrNull() ?: throw GpxParseException("No track point time")
        val endTime = times.maxOrNull() ?: throw GpxParseException("No track point time")
        val duration = Duration.between(startTime, endTime).seconds.toInt()
        if (duration <= 0) throw GpxParseException("Activity has no positive duration")

        val sport = sport(document)
        val samples = buildSamples(points, startTime, sport)
        val distance = distanceFromPoints(points).takeIf { it > 0.0 }
            ?: distanceFromSamples(samples)
        val averageHeartRate = samples.mapNotNull { it.heartRate }.averageOrNull()
        val maxHeartRate = samples.mapNotNull { it.heartRate }.maxOrNull()
        val averageCadence = samples.mapNotNull { it.cadenceSpm }.filter { it > 0 }.averageOrNull()
        val maxCadence = samples.mapNotNull { it.cadenceSpm }.maxOrNull()
        val averagePower = samples.mapNotNull { it.powerW }.averageOrNull()
        val maxPower = samples.mapNotNull { it.powerW }.maxOrNull()
        val (elevationGain, elevationLoss) = elevationStats(samples)

        return ImportedActivity(
            source = ActivitySource.GPX,
            sport = sport,
            startTime = startTime,
            durationSec = duration,
            distanceM = distance,
            movingTimeSec = duration,
            averageHeartRate = averageHeartRate,
            maxHeartRate = maxHeartRate,
            averagePace = averagePace(duration, distance, samples),
            averageCadenceSpm = averageCadence,
            maxCadenceSpm = maxCadence,
            averagePowerW = averagePower,
            maxPowerW = maxPower,
            elevationGainM = elevationGain,
            elevationLossM = elevationLoss,
            samples = samples,
            deviceName = deviceName(document),
            parserVersion = PARSER_VERSION,
        )
    }

    private fun buildSamples(points: List<Element>, startTime: Instant, sport: Sport): List<ActivitySample> {
        val normalizeCadence = sport == Sport.RUNNING || sport == Sport.TREADMILL
        return points.mapNotNull { point ->
            val timestamp = point.text("time")?.toInstantOrNull() ?: return@mapNotNull null
            val offset = Duration.between(startTime, timestamp).seconds
            if (offset < 0 || offset > Int.MAX_VALUE) return@mapNotNull null
            val rawCadence = extensionInt(point, "cad")
            val cadence = if (normalizeCadence) rawCadence?.times(2) else rawCadence
            ActivitySample(
                offsetSec = offset.toInt(),
                timestamp = timestamp,
                latitude = point.getAttribute("lat").toDoubleOrNull()?.takeIf { it in -90.0..90.0 },
                longitude = point.getAttribute("lon").toDoubleOrNull()?.takeIf { it in -180.0..180.0 },
                altitudeM = point.double("ele")?.takeIf { it in -500.0..9000.0 },
                speedMps = extensionDouble(point, "speed")?.takeIf { it in 0.0..30.0 },
                heartRate = extensionInt(point, "hr")?.takeIf { it in 30..250 },
                cadenceSpm = cadence?.takeIf { it in 0..300 },
                powerW = extensionDouble(point, "power")?.roundToInt()?.takeIf { it in 0..2500 },
            )
        }.sortedBy { it.offsetSec }
    }

    private fun extensionDouble(point: Element, name: String): Double? =
        point.elements(name).firstOrNull()?.textContent?.trim()?.toDoubleOrNull()

    private fun extensionInt(point: Element, name: String): Int? = extensionDouble(point, name)?.roundToInt()

    private fun distanceFromPoints(points: List<Element>): Double {
        var distance = 0.0
        var previous: Pair<Double, Double>? = null
        for (point in points) {
            val latitude = point.getAttribute("lat").toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
                ?: continue
            val longitude = point.getAttribute("lon").toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
                ?: continue
            val current = latitude to longitude
            val last = previous
            if (last != null) distance += haversineMeters(last, current)
            previous = current
        }
        return distance
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

    private fun averagePace(durationSec: Int, distanceM: Double, samples: List<ActivitySample>): Pace? {
        if (distanceM > 0.0) {
            val secondsPerKm = (durationSec / (distanceM / 1_000.0)).roundToInt()
            if (secondsPerKm > 0) return Pace.fromSecondsPerKm(secondsPerKm)
        }
        val speeds = samples.mapNotNull { it.speedMps }.filter { it > 0.0 }
        if (speeds.isEmpty()) return null
        return Pace.fromSpeedMetersPerSecond(speeds.average())
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

    private fun deviceName(document: org.w3c.dom.Document): String? =
        document.elements("creator").firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            ?: document.documentElement.getAttribute("creator").trim().takeIf { it.isNotEmpty() }

    private fun sport(document: org.w3c.dom.Document): Sport {
        val type = document.elements("type").firstOrNull()?.textContent?.trim()?.uppercase()
        return when (type) {
            "RUNNING", "RUN" -> Sport.RUNNING
            "TREADMILL" -> Sport.TREADMILL
            "WALKING", "WALK" -> Sport.WALKING
            else -> Sport.RUNNING
        }
    }

    private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(trim()) }.getOrNull()

    private fun List<Int>.averageOrNull(): Int? = takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    companion object {
        const val PARSER_VERSION = "1.0.0"
    }
}
