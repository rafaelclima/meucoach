package com.meucoach.core.model

import java.time.Instant
import kotlin.math.roundToInt

enum class Sport {
    RUNNING,
    TREADMILL,
    WALKING,
    OTHER,
}

enum class ActivitySource {
    FIT,
    TCX,
    GPX,
    HEALTH_CONNECT,
    MANUAL,
}

enum class DataConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

enum class ReadinessLevel {
    GREEN,
    AMBER,
    RED,
}

enum class HeartRateZone(val number: Int) {
    Z1(1),
    Z2(2),
    Z3(3),
    Z4(4),
    Z5(5),
}

enum class PaceZone {
    E,
    M,
    T,
    I,
    R,
}

data class ActivitySummary(
    val id: String,
    val source: ActivitySource,
    val sport: Sport,
    val startTime: Instant,
    val durationSec: Int,
    val distance: Distance,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val averagePace: Pace? = null,
    val averageCadenceSpm: Int? = null,
    val trainingLoad: Double? = null,
    val sources: List<ActivitySource> = listOf(source),
) {
    init {
        require(id.isNotBlank()) { "Activity id must not be blank" }
        require(durationSec > 0) { "Duration must be positive" }
        require(averageHeartRate == null || averageHeartRate in 30..250) {
            "Average heart rate out of range"
        }
        require(maxHeartRate == null || maxHeartRate in 30..250) {
            "Max heart rate out of range"
        }
        require(averageCadenceSpm == null || averageCadenceSpm > 0) {
            "Cadence must be positive when present"
        }
        require(trainingLoad == null || trainingLoad >= 0.0) {
            "Training load must not be negative"
        }
        require(sources.isNotEmpty()) { "At least the canonical source is required" }
        require(source in sources) { "Canonical source must be part of sources" }
        require(sources.size == sources.distinct().size) { "Sources must not repeat" }
    }

    val calculatedPace: Pace?
        get() =
            if (distance.meters > 0.0) {
                Pace.fromSecondsPerKm((durationSec / distance.kilometers).roundToInt())
            } else {
                null
            }
}
