package com.meucoach.core.domain.readiness

import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import java.time.LocalDate

data class ReadinessHistoryDay(
    val date: LocalDate,
    val score: Int,
    val level: ReadinessLevel,
    val confidence: DataConfidence,
    val hasCheckIn: Boolean,
)

/**
 * Replays readiness day by day using only data available up to each date.
 * Days with neither a check-in nor a metric row are omitted.
 */
class ReadinessHistoryCalculator {
    fun calculate(
        checkIns: List<DailyCheckIn>,
        metrics: List<DailyMetrics>,
        today: LocalDate,
        days: Int = 14,
    ): List<ReadinessHistoryDay> {
        require(days in 1..60) { "History window must be between 1 and 60 days" }
        val checkInsByDate = checkIns.associateBy { it.date }
        val start = today.minusDays(days - 1L)

        return (0 until days).mapNotNull { offset ->
            val date = start.plusDays(offset.toLong())
            val checkIn = checkInsByDate[date]
            val hasMetrics = metrics.any { it.date == date && it.hasAnyMetric }
            if (checkIn == null && !hasMetrics) return@mapNotNull null

            val inputs = DailyMetricsReadinessBuilder.build(metrics, date)
            val signals = inputs.signals.copy(
                illnessOrInjury = inputs.signals.illnessOrInjury || checkIn?.illnessOrInjury == true,
            )
            val result = ReadinessCalculator.calculate(
                checkIn = checkIn?.toSubjectiveCheckIn(),
                sleep = inputs.sleep,
                hrv = inputs.hrv,
                restingHeartRate = inputs.restingHeartRate,
                signals = signals,
            )
            ReadinessHistoryDay(
                date = date,
                score = result.score,
                level = result.level,
                confidence = result.confidence,
                hasCheckIn = checkIn != null,
            )
        }
    }
}
