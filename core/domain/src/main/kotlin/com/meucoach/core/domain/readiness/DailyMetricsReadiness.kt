package com.meucoach.core.domain.readiness

import com.meucoach.core.model.DailyMetrics
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.sqrt

data class ReadinessInputs(
    val sleep: SleepMetrics?,
    val hrv: HrvMetrics?,
    val restingHeartRate: RestingHeartRateMetrics?,
    val signals: ReadinessSignals,
)

/**
 * Aggregates persisted daily metrics into the calculator inputs.
 *
 * HRV needs at least 4 valid nights plus a 7-day baseline; RHR needs a 30-day
 * baseline. Missing windows produce null instead of a neutral zero.
 */
object DailyMetricsReadinessBuilder {
    private const val MIN_HRV_NIGHTS = 4
    private const val MIN_BASELINE_DAYS = 7

    fun build(metrics: List<DailyMetrics>, today: LocalDate): ReadinessInputs {
        val ordered = metrics.filter { !it.date.isAfter(today) }.sortedBy { it.date }
        val sleep = ordered
            .lastOrNull { row ->
                val minutes = row.sleepDurationMin
                !row.date.isBefore(today.minusDays(1)) && minutes != null && minutes > 0
            }
            ?.sleepDurationMin
            ?.let(::SleepMetrics)

        val last7 = ordered.filter { it.date.isAfter(today.minusDays(7)) }
        val last30 = ordered.filter { it.date.isAfter(today.minusDays(30)) }
        val last60 = ordered.filter { it.date.isAfter(today.minusDays(60)) }

        val hrvValues = last7.mapNotNull { it.hrvRmssdMs }.filter { it.isFinite() && it > 0.0 }
        val hrvBaselineValues = last60.mapNotNull { it.hrvRmssdMs }.filter { it.isFinite() && it > 0.0 }
        val hrv = if (hrvValues.size >= MIN_HRV_NIGHTS && hrvBaselineValues.size >= MIN_BASELINE_DAYS) {
            val hrvLogs = hrvValues.map { ln(it) }
            val baselineLogs = hrvBaselineValues.map { ln(it) }
            val meanLog7d = hrvLogs.average()
            val meanLog60d = baselineLogs.average()
            val variance = baselineLogs.sumOf { (it - meanLog60d) * (it - meanLog60d) } / baselineLogs.size
            val sd = sqrt(variance)
            if (sd > 0.0) {
                HrvMetrics(
                    rmssd7dMs = hrvValues.average(),
                    baseline60dMs = hrvBaselineValues.average(),
                    meanLog7d = meanLog7d,
                    meanLog60d = meanLog60d,
                    sdLnMs = sd,
                    validNights = hrvValues.size,
                )
            } else {
                null
            }
        } else {
            null
        }

        val rhr7 = last7.mapNotNull { it.restingHeartRate }
        val rhr30 = last30.mapNotNull { it.restingHeartRate }
        val rhr = if (rhr7.isNotEmpty() && rhr30.isNotEmpty()) {
            RestingHeartRateMetrics(
                average7dBpm = rhr7.average(),
                baseline30dBpm = rhr30.average(),
            )
        } else {
            null
        }

        val baseline = rhr?.baseline30dBpm
        val lastTwoElevated = baseline != null && listOf(today, today.minusDays(1)).all { day ->
            ordered.firstOrNull { it.date == day }?.restingHeartRate?.let { it >= baseline + 7.0 } == true
        }

        return ReadinessInputs(
            sleep = sleep,
            hrv = hrv,
            restingHeartRate = rhr,
            signals = ReadinessSignals(restingHeartRateElevatedTwoDays = lastTwoElevated),
        )
    }
}
