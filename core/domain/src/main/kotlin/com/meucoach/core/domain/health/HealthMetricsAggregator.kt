package com.meucoach.core.domain.health

import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HealthSleepSession(val start: Instant, val end: Instant) {
    init {
        require(!end.isBefore(start)) { "Sleep session must not end before it starts" }
    }
}

data class HealthSample(val time: Instant, val value: Double) {
    init {
        require(value.isFinite() && value >= 0.0) { "Health sample must be finite and non-negative" }
    }
}

data class HealthRawData(
    val sleepSessions: List<HealthSleepSession> = emptyList(),
    val restingHeartRates: List<HealthSample> = emptyList(),
    val hrvRmssd: List<HealthSample> = emptyList(),
    val oxygenSaturation: List<HealthSample> = emptyList(),
)

/**
 * Merges Health Connect records into one row per local day.
 *
 * Sleep sessions are assigned to the local date they end and overlapping
 * sessions are merged so a duplicated record does not inflate the night.
 * Metrics are averaged per day; days without data are not emitted.
 */
object HealthMetricsAggregator {
    fun aggregate(data: HealthRawData, zoneId: ZoneId, from: LocalDate, to: LocalDate): List<DailyMetrics> {
        require(!to.isBefore(from)) { "End date must not be before start date" }
        val sleep = sleepByDate(data.sleepSessions, zoneId, from, to)
        val rhr = averagesByDate(data.restingHeartRates, zoneId, from, to)
        val hrv = averagesByDate(data.hrvRmssd, zoneId, from, to)
        val spo2 = averagesByDate(data.oxygenSaturation, zoneId, from, to)

        return (sleep.keys + rhr.keys + hrv.keys + spo2.keys)
            .sorted()
            .map { date ->
                DailyMetrics(
                    date = date,
                    sleepDurationMin = sleep[date],
                    restingHeartRate = rhr[date],
                    hrvRmssdMs = hrv[date],
                    spo2Avg = spo2[date],
                    source = DailyMetricsSource.HEALTH_CONNECT,
                )
            }
    }

    private fun sleepByDate(
        sessions: List<HealthSleepSession>,
        zoneId: ZoneId,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Int> {
        val intervals = mutableMapOf<LocalDate, MutableList<Pair<Instant, Instant>>>()
        sessions.forEach { session ->
            val date = session.end.atZone(zoneId).toLocalDate()
            if (date.isBefore(from) || date.isAfter(to)) return@forEach
            intervals.getOrPut(date) { mutableListOf() } += session.start to session.end
        }
        return intervals.mapValues { (_, dayIntervals) ->
            val sorted = dayIntervals.sortedBy { it.first }
            var totalSeconds = 0L
            var currentStart: Instant? = null
            var currentEnd: Instant? = null
            sorted.forEach { (start, end) ->
                val openStart = currentStart
                val openEnd = currentEnd
                if (openStart == null || openEnd == null) {
                    currentStart = start
                    currentEnd = end
                } else if (start.isAfter(openEnd)) {
                    totalSeconds += Duration.between(openStart, openEnd).seconds
                    currentStart = start
                    currentEnd = end
                } else if (end.isAfter(openEnd)) {
                    currentEnd = end
                }
            }
            currentStart?.let { start ->
                currentEnd?.let { end ->
                    totalSeconds += Duration.between(start, end).seconds
                }
            }
            (totalSeconds / 60).toInt().coerceIn(0, 1_440)
        }
    }

    private fun averagesByDate(
        samples: List<HealthSample>,
        zoneId: ZoneId,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Double> = samples
        .mapNotNull { sample ->
            val date = sample.time.atZone(zoneId).toLocalDate()
            if (date.isBefore(from) || date.isAfter(to)) null else date to sample.value
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.average() }
}
