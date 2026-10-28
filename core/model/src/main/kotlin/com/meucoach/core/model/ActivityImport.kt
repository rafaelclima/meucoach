package com.meucoach.core.model

import java.time.Instant

/**
 * Canonical result of an import adapter, before the importer assigns a local id.
 *
 * [samples] are ordered by time. Missing values stay null; zero is a measured
 * value, never a placeholder for absence.
 */
data class ImportedActivity(
    val source: ActivitySource,
    val sport: Sport,
    val startTime: Instant,
    val durationSec: Int,
    val distanceM: Double,
    val sourceId: String? = null,
    val subSport: String? = null,
    val movingTimeSec: Int? = null,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val averagePace: Pace? = null,
    val averageCadenceSpm: Int? = null,
    val maxCadenceSpm: Int? = null,
    val averagePowerW: Int? = null,
    val maxPowerW: Int? = null,
    val averageStepLengthM: Double? = null,
    val elevationGainM: Double? = null,
    val elevationLossM: Double? = null,
    val calories: Int? = null,
    val laps: List<ActivityLap> = emptyList(),
    val samples: List<ActivitySample> = emptyList(),
    val deviceName: String? = null,
    val parserVersion: String = "1.0.0",
) {
    init {
        require(durationSec > 0) { "Duration must be positive" }
        require(distanceM.isFinite() && distanceM >= 0.0) {
            "Distance must be finite and non-negative"
        }
        require(averageHeartRate == null || averageHeartRate in 30..250) {
            "Average heart rate out of range"
        }
        require(maxHeartRate == null || maxHeartRate in 30..250) {
            "Max heart rate out of range"
        }
        require(averageCadenceSpm == null || averageCadenceSpm in 0..300) {
            "Average cadence out of range"
        }
        require(maxCadenceSpm == null || maxCadenceSpm in 0..300) {
            "Max cadence out of range"
        }
        require(averagePowerW == null || averagePowerW >= 0) {
            "Average power must not be negative"
        }
        require(maxPowerW == null || maxPowerW >= 0) {
            "Max power must not be negative"
        }
        require(averageStepLengthM == null || averageStepLengthM >= 0.0) {
            "Average step length must not be negative"
        }
        require(elevationGainM == null || elevationGainM >= 0.0) {
            "Elevation gain must not be negative"
        }
        require(elevationLossM == null || elevationLossM >= 0.0) {
            "Elevation loss must not be negative"
        }
        require(calories == null || calories >= 0) { "Calories must not be negative" }
        require(parserVersion.isNotBlank()) { "Parser version must not be blank" }
    }
}

data class ActivityLap(
    val index: Int,
    val durationSec: Int,
    val distanceM: Double,
    val startTime: Instant? = null,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val averagePace: Pace? = null,
    val averageCadenceSpm: Int? = null,
    val averagePowerW: Int? = null,
    val elevationGainM: Double? = null,
) {
    init {
        require(index >= 0) { "Lap index must not be negative" }
        require(durationSec >= 0) { "Lap duration must not be negative" }
        require(distanceM.isFinite() && distanceM >= 0.0) {
            "Lap distance must be finite and non-negative"
        }
        require(averageHeartRate == null || averageHeartRate in 30..250) {
            "Lap average heart rate out of range"
        }
        require(maxHeartRate == null || maxHeartRate in 30..250) {
            "Lap max heart rate out of range"
        }
        require(averageCadenceSpm == null || averageCadenceSpm in 0..300) {
            "Lap cadence out of range"
        }
        require(averagePowerW == null || averagePowerW >= 0) {
            "Lap average power must not be negative"
        }
        require(elevationGainM == null || elevationGainM >= 0.0) {
            "Lap elevation gain must not be negative"
        }
    }
}

data class ActivitySample(
    val offsetSec: Int,
    val timestamp: Instant? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeM: Double? = null,
    val speedMps: Double? = null,
    val heartRate: Int? = null,
    val cadenceSpm: Int? = null,
    val powerW: Int? = null,
    val stepLengthM: Double? = null,
    val developerFields: Map<String, Double> = emptyMap(),
) {
    init {
        require(offsetSec >= 0) { "Sample offset must not be negative" }
        require(latitude == null || latitude in -90.0..90.0) {
            "Latitude out of range"
        }
        require(longitude == null || longitude in -180.0..180.0) {
            "Longitude out of range"
        }
        require(altitudeM == null || altitudeM in -500.0..9000.0) {
            "Altitude out of range"
        }
        require(speedMps == null || (speedMps.isFinite() && speedMps >= 0.0)) {
            "Speed must be finite and non-negative"
        }
        require(heartRate == null || heartRate in 30..250) {
            "Heart rate out of range"
        }
        require(cadenceSpm == null || cadenceSpm in 0..300) {
            "Cadence out of range"
        }
        require(powerW == null || powerW in 0..2500) { "Power out of range" }
        require(stepLengthM == null || (stepLengthM.isFinite() && stepLengthM >= 0.0)) {
            "Step length must be finite and non-negative"
        }
        require(developerFields.keys.all { it.isNotBlank() }) {
            "Developer field names must not be blank"
        }
        require(developerFields.values.all { it.isFinite() }) {
            "Developer field values must be finite"
        }
    }
}
