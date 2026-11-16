package com.meucoach.core.domain.health

import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
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

/** Aggregated days plus the sleep diagnostics the sync UI may report. */
data class HealthAggregation(
    val days: List<DailyMetrics>,
    val suspectSleepNights: Int = 0,
    val fragmentedSleepNights: Int = 0,
    /** Dates whose stored sleep must be cleared, not kept from an older sync. */
    val suspectSleepDates: Set<LocalDate> = emptySet(),
) {
    init {
        require(suspectSleepNights >= 0) { "Suspect nights must not be negative" }
        require(fragmentedSleepNights >= 0) { "Fragmented nights must not be negative" }
    }
}

/**
 * Merges Health Connect records into one row per local day.
 *
 * Sleep sessions are assigned to the local date they end and validated by
 * [SleepNightValidator]: an implausible night becomes `null` (absent), never a
 * short night, and is counted in [HealthAggregation.suspectSleepNights].
 * Metrics are averaged per day; days without data are not emitted.
 */
object HealthMetricsAggregator {
    fun aggregate(data: HealthRawData, zoneId: ZoneId, from: LocalDate, to: LocalDate): List<DailyMetrics> =
        aggregateWithDiagnostics(data, zoneId, from, to).days

    fun aggregateWithDiagnostics(
        data: HealthRawData,
        zoneId: ZoneId,
        from: LocalDate,
        to: LocalDate,
    ): HealthAggregation {
        require(!to.isBefore(from)) { "End date must not be before start date" }
        val sleep = sleepByDate(data.sleepSessions, zoneId, from, to)
        val rhr = averagesByDate(data.restingHeartRates, zoneId, from, to)
        val hrv = averagesByDate(data.hrvRmssd, zoneId, from, to)
        val spo2 = averagesByDate(data.oxygenSaturation, zoneId, from, to)

        val days = (sleep.byDate.keys + rhr.keys + hrv.keys + spo2.keys)
            .sorted()
            .map { date ->
                DailyMetrics(
                    date = date,
                    sleepDurationMin = sleep.byDate[date],
                    restingHeartRate = rhr[date],
                    hrvRmssdMs = hrv[date],
                    spo2Avg = spo2[date],
                    source = DailyMetricsSource.HEALTH_CONNECT,
                )
            }
        return HealthAggregation(
            days = days,
            suspectSleepNights = sleep.suspectDates.size,
            fragmentedSleepNights = sleep.fragmentedNights,
            suspectSleepDates = sleep.suspectDates,
        )
    }

    private fun sleepByDate(
        sessions: List<HealthSleepSession>,
        zoneId: ZoneId,
        from: LocalDate,
        to: LocalDate,
    ): SleepAggregation {
        val sessionsByDate = mutableMapOf<LocalDate, MutableList<HealthSleepSession>>()
        sessions.forEach { session ->
            val date = session.end.atZone(zoneId).toLocalDate()
            if (date.isBefore(from) || date.isAfter(to)) return@forEach
            sessionsByDate.getOrPut(date) { mutableListOf() } += session
        }
        val byDate = mutableMapOf<LocalDate, Int?>()
        val suspectDates = mutableSetOf<LocalDate>()
        var fragmentedNights = 0
        sessionsByDate.toSortedMap().forEach { (date, daySessions) ->
            val night = SleepNightValidator.validate(date, daySessions, zoneId)
            byDate[date] = night.minutes
            if (night.suspect) suspectDates += date
            if (!night.suspect && night.fragmented) fragmentedNights++
        }
        return SleepAggregation(byDate, suspectDates, fragmentedNights)
    }

    private data class SleepAggregation(
        val byDate: Map<LocalDate, Int?>,
        val suspectDates: Set<LocalDate>,
        val fragmentedNights: Int,
    )

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
