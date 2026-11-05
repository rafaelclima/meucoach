package com.meucoach.core.domain.load

import com.meucoach.core.model.Sex
import kotlin.math.exp
import kotlin.math.sqrt

object TrainingLoadCalculator {
    private const val SECONDS_PER_HOUR = 3_600.0

    fun trimp(
        durationSec: Int,
        averageHeartRateBpm: Int,
        restingHeartRateBpm: Int,
        maxHeartRateBpm: Int,
        sex: Sex,
    ): Double {
        require(durationSec > 0) { "Duration must be positive" }
        require(maxHeartRateBpm > restingHeartRateBpm) { "Max HR must be greater than resting HR" }
        require(averageHeartRateBpm in restingHeartRateBpm..maxHeartRateBpm) {
            "Average HR must be between resting and max HR"
        }

        val x =
            (averageHeartRateBpm - restingHeartRateBpm).toDouble() /
                (maxHeartRateBpm - restingHeartRateBpm)
        val (coefficient, exponent) =
            when (sex) {
                Sex.MALE -> 0.64 to 1.92
                Sex.FEMALE -> 0.86 to 1.67
                Sex.OTHER, Sex.UNSPECIFIED -> 0.75 to 1.80
            }
        // Banister's TRIMP is defined per minute of effort; durationSec must be converted.
        val durationMin = durationSec / 60.0
        return durationMin * x * coefficient * exp(exponent * x)
    }

    fun rTss(durationSec: Int, normalizedPaceSecPerKm: Int, thresholdPaceSecPerKm: Int): Double {
        require(durationSec > 0) { "Duration must be positive" }
        require(normalizedPaceSecPerKm > 0) { "Normalized pace must be positive" }
        require(thresholdPaceSecPerKm > 0) { "Threshold pace must be positive" }

        val intensity = thresholdPaceSecPerKm.toDouble() / normalizedPaceSecPerKm
        return durationSec * intensity * intensity * 100.0 / SECONDS_PER_HOUR
    }

    fun srpe(rpe: Int, durationMin: Int): Double {
        require(rpe in 1..10) { "RPE must be between 1 and 10" }
        require(durationMin > 0) { "Duration must be positive" }
        return rpe.toDouble() * durationMin
    }

    fun ewma(dailyLoads: List<Double>, timeConstantDays: Int): List<Double> {
        require(timeConstantDays > 0) { "Time constant must be positive" }
        var value = 0.0
        return dailyLoads.map { load ->
            require(load.isFinite() && load >= 0.0) { "Daily load must be finite and non-negative" }
            value += (load - value) / timeConstantDays
            value
        }
    }

    fun ctl(dailyLoads: List<Double>): List<Double> = ewma(dailyLoads, timeConstantDays = 42)

    fun atl(dailyLoads: List<Double>): List<Double> = ewma(dailyLoads, timeConstantDays = 7)

    fun tsb(ctl: Double, atl: Double): Double = ctl - atl

    fun acwr(acuteAverage7d: Double, chronicAverage28d: Double): Double? {
        require(acuteAverage7d.isFinite() && acuteAverage7d >= 0.0) {
            "Acute load must be finite and non-negative"
        }
        require(chronicAverage28d.isFinite() && chronicAverage28d >= 0.0) {
            "Chronic load must be finite and non-negative"
        }
        return if (chronicAverage28d == 0.0) null else acuteAverage7d / chronicAverage28d
    }

    fun monotony(dailyLoads: List<Double>): Double? {
        require(dailyLoads.all { it.isFinite() && it >= 0.0 }) {
            "Daily loads must be finite and non-negative"
        }
        if (dailyLoads.size < 2) return null

        val mean = dailyLoads.average()
        val variance = dailyLoads.sumOf { load -> (load - mean) * (load - mean) } / dailyLoads.size
        if (variance == 0.0) return null

        return mean / sqrt(variance)
    }
}
