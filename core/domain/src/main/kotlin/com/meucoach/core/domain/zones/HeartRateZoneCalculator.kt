package com.meucoach.core.domain.zones

import com.meucoach.core.model.HeartRateZone
import kotlin.math.ceil

data class HeartRateZoneRange(val zone: HeartRateZone, val minBpm: Int, val maxBpm: Int?) {
    init {
        require(minBpm >= 0) { "Minimum heart rate must not be negative" }
        require(maxBpm == null || maxBpm >= minBpm) { "Maximum heart rate must be >= minimum" }
    }

    fun contains(bpm: Int): Boolean = bpm >= minBpm && (maxBpm == null || bpm <= maxBpm)
}

object HeartRateZoneCalculator {
    private const val MIN_LTHR = 100
    private const val MAX_LTHR = 230
    private const val MIN_HR = 30
    private const val MAX_HR = 250

    fun zoneFor(heartRateBpm: Int, lthrBpm: Int): HeartRateZone {
        require(lthrBpm in MIN_LTHR..MAX_LTHR) { "LTHR must be between $MIN_LTHR and $MAX_LTHR bpm" }
        require(heartRateBpm in MIN_HR..MAX_HR) { "Heart rate must be between $MIN_HR and $MAX_HR bpm" }

        val ratio = heartRateBpm.toDouble() / lthrBpm
        return when {
            ratio < 0.81 -> HeartRateZone.Z1
            ratio < 0.90 -> HeartRateZone.Z2
            ratio < 0.94 -> HeartRateZone.Z3
            ratio < 1.00 -> HeartRateZone.Z4
            else -> HeartRateZone.Z5
        }
    }

    fun ranges(lthrBpm: Int): List<HeartRateZoneRange> {
        require(lthrBpm in MIN_LTHR..MAX_LTHR) { "LTHR must be between $MIN_LTHR and $MAX_LTHR bpm" }

        val z2Start = ceil(0.81 * lthrBpm).toInt()
        val z3Start = ceil(0.90 * lthrBpm).toInt()
        val z4Start = ceil(0.94 * lthrBpm).toInt()
        val z5Start = ceil(1.00 * lthrBpm).toInt()

        return listOf(
            HeartRateZoneRange(HeartRateZone.Z1, minBpm = 0, maxBpm = z2Start - 1),
            HeartRateZoneRange(HeartRateZone.Z2, minBpm = z2Start, maxBpm = z3Start - 1),
            HeartRateZoneRange(HeartRateZone.Z3, minBpm = z3Start, maxBpm = z4Start - 1),
            HeartRateZoneRange(HeartRateZone.Z4, minBpm = z4Start, maxBpm = z5Start - 1),
            HeartRateZoneRange(HeartRateZone.Z5, minBpm = z5Start, maxBpm = null),
        )
    }
}
