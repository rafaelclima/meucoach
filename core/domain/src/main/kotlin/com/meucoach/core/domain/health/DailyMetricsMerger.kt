package com.meucoach.core.domain.health

import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource

/**
 * Merges a day that may arrive from more than one source without dropping data.
 *
 * Precedence per field: `MANUAL` > `HEALTH_CONNECT` > `CSV`. A lower-priority
 * source only fills fields the higher-priority day left null; it never erases a
 * stored value. Equal sources update in place, so re-importing a file corrects
 * the day instead of being ignored.
 *
 * `source` keeps the dominant source (the highest-precedence one with data).
 * Provenance is not tracked per field yet; see ADR 0016.
 */
object DailyMetricsMerger {
    fun merge(existing: DailyMetrics, incoming: DailyMetrics): DailyMetrics {
        require(existing.date == incoming.date) {
            "Cannot merge metrics from different dates"
        }
        val existingWins = existing.source.priority > incoming.source.priority
        return DailyMetrics(
            date = incoming.date,
            sleepDurationMin = pick(existing.sleepDurationMin, incoming.sleepDurationMin, existingWins),
            restingHeartRate = pick(existing.restingHeartRate, incoming.restingHeartRate, existingWins),
            hrvRmssdMs = pick(existing.hrvRmssdMs, incoming.hrvRmssdMs, existingWins),
            spo2Avg = pick(existing.spo2Avg, incoming.spo2Avg, existingWins),
            source = dominantSource(existing, incoming),
        )
    }

    private fun <T> pick(existing: T?, incoming: T?, existingWins: Boolean): T? = when {
        existing == null -> incoming
        incoming == null -> existing
        existingWins -> existing
        else -> incoming
    }

    private fun dominantSource(existing: DailyMetrics, incoming: DailyMetrics): DailyMetricsSource = when {
        !incoming.hasAnyMetric -> existing.source
        !existing.hasAnyMetric -> incoming.source
        existing.source.priority > incoming.source.priority -> existing.source
        else -> incoming.source
    }

    private val DailyMetricsSource.priority: Int
        get() = when (this) {
            DailyMetricsSource.MANUAL -> 3
            DailyMetricsSource.HEALTH_CONNECT -> 2
            DailyMetricsSource.CSV -> 1
        }
}
