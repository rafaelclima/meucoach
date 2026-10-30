package com.meucoach.core.domain.zones

import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.HeartRateZone

data class HrZoneDuration(val zone: HeartRateZone, val durationSec: Int) {
    init {
        require(durationSec >= 0) { "Zone duration must not be negative" }
    }
}

data class HrZoneDistribution(val zones: List<HrZoneDuration>, val totalSec: Int) {
    init {
        require(totalSec > 0) { "Total duration must be positive" }
        require(zones.isNotEmpty()) { "At least one zone is required" }
        require(zones.sumOf { it.durationSec } == totalSec) {
            "Zone durations must add up to the total"
        }
    }

    fun durationOf(zone: HeartRateZone): Int = zones.firstOrNull { it.zone == zone }?.durationSec ?: 0

    fun percentOf(zone: HeartRateZone): Double = durationOf(zone) * 100.0 / totalSec
}

/**
 * Allocates time between consecutive samples to the zone of the earlier sample.
 * Intervals without a heart rate are left out of the total instead of being
 * assigned a neutral zone.
 */
object HrZoneDistributionCalculator {
    fun calculate(samples: List<ActivitySample>, lthrBpm: Int): HrZoneDistribution? {
        if (samples.size < 2) return null
        val ordered = samples.sortedBy { it.offsetSec }
        val durations = linkedMapOf<HeartRateZone, Int>()
        var total = 0

        ordered.zipWithNext().forEach { (current, next) ->
            val seconds = next.offsetSec - current.offsetSec
            val heartRate = current.heartRate
            if (seconds <= 0 || heartRate == null) return@forEach
            val zone = HeartRateZoneCalculator.zoneFor(heartRate, lthrBpm)
            durations[zone] = (durations[zone] ?: 0) + seconds
            total += seconds
        }
        if (total <= 0) return null

        return HrZoneDistribution(
            zones = HeartRateZone.entries.map { zone ->
                HrZoneDuration(zone = zone, durationSec = durations[zone] ?: 0)
            },
            totalSec = total,
        )
    }
}
