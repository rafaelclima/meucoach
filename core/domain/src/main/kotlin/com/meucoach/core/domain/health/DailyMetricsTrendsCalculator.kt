package com.meucoach.core.domain.health

import com.meucoach.core.model.DailyMetrics
import java.time.LocalDate

/** The four daily signals the coach can read from Health Connect or a CSV. */
enum class HealthMetric {
    SLEEP,
    RESTING_HEART_RATE,
    HRV,
    SPO2,
}

/** Canonical units. The UI decides how to render them; the domain never formats numbers. */
enum class HealthMetricUnit {
    MINUTES,
    BPM,
    MILLISECONDS,
    PERCENT,
}

data class HealthMetricPoint(val date: LocalDate, val value: Double)

/**
 * One metric over the selected window.
 *
 * [points] only contains days with a measurement. [rollingAverage7d] is a true trailing mean, so
 * it can have a value on a day without a measurement when the previous six days do.
 */
data class HealthMetricTrend(
    val metric: HealthMetric,
    val unit: HealthMetricUnit,
    val points: List<HealthMetricPoint>,
    val rollingAverage7d: List<HealthMetricPoint>,
    val latest: HealthMetricPoint?,
    val average7d: Double?,
    val baseline: Double?,
    val change: Double?,
    val targetMin: Double? = null,
    val targetMax: Double? = null,
) {
    val hasData: Boolean get() = points.isNotEmpty()
}

data class DailyMetricsTrends(
    val windowDays: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: List<DailyMetrics>,
    val trends: List<HealthMetricTrend>,
) {
    fun trend(metric: HealthMetric): HealthMetricTrend? = trends.firstOrNull { it.metric == metric }

    val daysWithAnyMetric: Int get() = days.count { it.hasAnyMetric }

    val coverage: Double
        get() = if (windowDays == 0) 0.0 else daysWithAnyMetric.toDouble() / windowDays

    val latestDate: LocalDate? get() = days.lastOrNull { it.hasAnyMetric }?.date

    val hasData: Boolean get() = trends.any { it.hasData }
}

/**
 * Turns raw daily metrics into chart-ready series with honest gaps, trailing averages and
 * individual baselines. No target is invented when the evidence is person-specific (HRV, RHR).
 */
class DailyMetricsTrendsCalculator {
    fun calculate(
        metrics: List<DailyMetrics>,
        today: LocalDate,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
    ): DailyMetricsTrends {
        require(windowDays > 0) { "Window must be positive" }
        val start = today.minusDays(windowDays - 1L)
        val days = metrics
            .filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
            .associateBy { it.date }
            .values
            .sortedBy { it.date }
        val baselineStart = today.minusDays(windowDays - 1L)
        val last7Start = today.minusDays(6)
        val trends = HealthMetric.entries.map { metric ->
            val extractor = extractor(metric)
            val points = days.mapNotNull { day ->
                extractor(day)?.let { HealthMetricPoint(day.date, it) }
            }
            val latest = points.lastOrNull()
            val average7d = points
                .filter { !it.date.isBefore(last7Start) }
                .map { it.value }
                .takeIf { it.isNotEmpty() }
                ?.average()
            val baseline = points
                .filter { !it.date.isBefore(baselineStart) }
                .map { it.value }
                .takeIf { it.isNotEmpty() }
                ?.average()
            HealthMetricTrend(
                metric = metric,
                unit = unit(metric),
                points = points,
                rollingAverage7d = rollingAverage(points, start, today),
                latest = latest,
                average7d = average7d,
                baseline = baseline,
                change = if (average7d != null && baseline != null) average7d - baseline else null,
                targetMin = target(metric)?.start,
                targetMax = target(metric)?.endInclusive,
            )
        }
        return DailyMetricsTrends(
            windowDays = windowDays,
            startDate = start,
            endDate = today,
            days = days,
            trends = trends,
        )
    }

    private fun rollingAverage(
        points: List<HealthMetricPoint>,
        start: LocalDate,
        today: LocalDate,
    ): List<HealthMetricPoint> {
        if (points.isEmpty()) return emptyList()
        val result = mutableListOf<HealthMetricPoint>()
        var date = start
        while (!date.isAfter(today)) {
            val windowStart = date.minusDays(6)
            val values = points
                .filter { !it.date.isBefore(windowStart) && !it.date.isAfter(date) }
                .map { it.value }
            if (values.isNotEmpty()) {
                result += HealthMetricPoint(date, values.average())
            }
            date = date.plusDays(1)
        }
        return result
    }

    private fun extractor(metric: HealthMetric): (DailyMetrics) -> Double? = when (metric) {
        HealthMetric.SLEEP -> { day -> day.sleepDurationMin?.toDouble() }
        HealthMetric.RESTING_HEART_RATE -> { day -> day.restingHeartRate }
        HealthMetric.HRV -> { day -> day.hrvRmssdMs }
        HealthMetric.SPO2 -> { day -> day.spo2Avg }
    }

    private fun unit(metric: HealthMetric): HealthMetricUnit = when (metric) {
        HealthMetric.SLEEP -> HealthMetricUnit.MINUTES
        HealthMetric.RESTING_HEART_RATE -> HealthMetricUnit.BPM
        HealthMetric.HRV -> HealthMetricUnit.MILLISECONDS
        HealthMetric.SPO2 -> HealthMetricUnit.PERCENT
    }

    /** Only the two ranges with a widely accepted reference band are exposed. */
    private fun target(metric: HealthMetric): ClosedFloatingPointRange<Double>? = when (metric) {
        HealthMetric.SLEEP -> SLEEP_TARGET_MIN..SLEEP_TARGET_MAX

        HealthMetric.SPO2 -> SPO2_TARGET_MIN..SPO2_TARGET_MAX

        HealthMetric.RESTING_HEART_RATE,
        HealthMetric.HRV,
        -> null
    }

    companion object {
        const val DEFAULT_WINDOW_DAYS = 30
        const val SLEEP_TARGET_MIN = 7.0 * 60
        const val SLEEP_TARGET_MAX = 9.0 * 60
        const val SPO2_TARGET_MIN = 95.0
        const val SPO2_TARGET_MAX = 100.0
    }
}
