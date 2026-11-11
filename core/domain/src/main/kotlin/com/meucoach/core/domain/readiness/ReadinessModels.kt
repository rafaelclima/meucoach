package com.meucoach.core.domain.readiness

import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import kotlin.math.abs

enum class ReadinessComponent {
    SUBJECTIVE,
    SLEEP,
    HRV,
    RESTING_HEART_RATE,
}

enum class ReadinessFlag {
    ILLNESS_OR_INJURY,
    SHORT_SLEEP,
    RESTING_HEART_RATE_ELEVATED,
    NO_QUALITY,
    INSUFFICIENT_DATA,
}

data class SubjectiveCheckIn(val sleepQuality: Int, val fatigue: Int, val soreness: Int, val stress: Int) {
    init {
        require(sleepQuality in 1..5) { "Sleep quality must be between 1 and 5" }
        require(fatigue in 1..5) { "Fatigue must be between 1 and 5" }
        require(soreness in 1..5) { "Soreness must be between 1 and 5" }
        require(stress in 1..5) { "Stress must be between 1 and 5" }
    }

    /**
     * Hooper index invertido para sono: aqui 5 = excelente, enquanto fadiga,
     * dor e stress usam 5 = ruim. O índice bruto mantém a escala 4..20.
     */
    val hooperSum: Int
        get() = (6 - sleepQuality) + fatigue + soreness + stress
}

data class SleepMetrics(val durationMin: Int) {
    init {
        require(durationMin in 0..1_440) { "Sleep duration must be between 0 and 1440 minutes" }
    }
}

data class HrvMetrics(
    /** Arithmetic mean of the raw RMSSD (ms), kept for display only. */
    val rmssd7dMs: Double,
    /** Arithmetic mean of the raw 60-night RMSSD (ms), kept for display only. */
    val baseline60dMs: Double,
    /** Mean of ln(RMSSD) across the last seven valid nights. */
    val meanLog7d: Double,
    /** Mean of ln(RMSSD) across the 60-night baseline. */
    val meanLog60d: Double,
    /** Standard deviation of ln(RMSSD) across the baseline. */
    val sdLnMs: Double,
    val validNights: Int,
) {
    init {
        require(rmssd7dMs > 0.0) { "RMSSD must be positive" }
        require(baseline60dMs > 0.0) { "Baseline RMSSD must be positive" }
        require(meanLog7d.isFinite()) { "Seven-day log mean must be finite" }
        require(meanLog60d.isFinite()) { "Baseline log mean must be finite" }
        require(sdLnMs > 0.0) { "Standard deviation must be positive" }
        require(validNights >= 0) { "Valid nights must not be negative" }
    }

    /**
     * Log-space z-score: the seven-night mean of ln(RMSSD) minus the 60-night
     * baseline mean, divided by the baseline SD of ln(RMSSD). Raw RMSSD is
     * display-only; mixing its arithmetic mean with a log SD biased the score
     * (BUG-04, `SPORTS_SCIENCE.md` §7.4).
     *
     * A flat baseline has no meaningful dispersion: the two sample averages can
     * still differ by a few ULPs, so a near-zero difference is treated as the
     * average (z = 0) instead of being amplified by a near-zero SD.
     */
    val zScore: Double
        get() = if (abs(meanLog7d - meanLog60d) <= FLAT_BASELINE_TOLERANCE) {
            0.0
        } else {
            (meanLog7d - meanLog60d) / sdLnMs
        }

    private companion object {
        /** Log-RMSSD differences below this are floating-point noise (~1e-9). */
        const val FLAT_BASELINE_TOLERANCE = 1e-9
    }
}

data class RestingHeartRateMetrics(val average7dBpm: Double, val baseline30dBpm: Double) {
    init {
        require(average7dBpm > 0.0) { "Average resting heart rate must be positive" }
        require(baseline30dBpm > 0.0) { "Baseline resting heart rate must be positive" }
    }
}

data class ReadinessSignals(val illnessOrInjury: Boolean = false, val restingHeartRateElevatedTwoDays: Boolean = false)

data class ReadinessResult(
    val score: Int,
    val level: ReadinessLevel,
    val confidence: DataConfidence,
    val components: Map<ReadinessComponent, Int>,
    val missing: Set<ReadinessComponent>,
    val flags: Set<ReadinessFlag>,
)
