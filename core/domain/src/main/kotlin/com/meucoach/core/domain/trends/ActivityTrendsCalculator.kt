package com.meucoach.core.domain.trends

import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

data class WeeklyVolume(val weekStart: LocalDate, val kilometers: Double, val runs: Int)

data class ConsistencySummary(val windowDays: Int, val activeDays: Int, val runs: Int, val daysSinceLastRun: Long?)

data class PaceHeartRatePoint(val date: LocalDate, val pace: Pace, val heartRate: Int) {
    /**
     * Descriptive running-economy proxy: speed in meters per minute divided by
     * heart rate (meters per heartbeat). Heat/fatigue confound it, so compare
     * under similar conditions rather than treating it as a verdict.
     */
    val efficiencyMetersPerBeat: Double
        get() = (60_000.0 / pace.secondsPerKm) / heartRate
}

/**
 * Descriptive best efforts from imported runs. The best-pace record ignores
 * efforts shorter than [MIN_RECORD_DISTANCE_KM] to avoid strides and partial
 * imports; it is a summary record, not a race prediction.
 */
data class PersonalRecords(
    val longestDistanceKm: Double? = null,
    val longestDistanceDate: LocalDate? = null,
    val bestPace: Pace? = null,
    val bestPaceDate: LocalDate? = null,
    val bestPaceDistanceKm: Double? = null,
    val longestDurationSec: Int? = null,
    val longestDurationDate: LocalDate? = null,
) {
    val hasData: Boolean get() = longestDistanceKm != null || longestDurationSec != null
}

data class ActivityTrends(
    val weeks: List<WeeklyVolume>,
    val consistency: ConsistencySummary,
    val paceHeartRate: List<PaceHeartRatePoint>,
    val totalKilometers: Double,
    val averagePace: Pace?,
    val averageHeartRate: Int?,
    val records: PersonalRecords = PersonalRecords(),
)

class ActivityTrendsCalculator {
    fun calculate(
        activities: List<ActivitySummary>,
        today: LocalDate,
        zoneId: ZoneId,
        weeks: Int = 6,
        consistencyDays: Int = 28,
    ): ActivityTrends {
        require(weeks in 1..26) { "Week window must be between 1 and 26" }
        require(consistencyDays in 7..365) { "Consistency window must be between 7 and 365 days" }
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStarts = (weeks - 1 downTo 0).map { currentWeekStart.minusWeeks(it.toLong()) }
        val byLocalDate = activities.groupBy { it.startTime.atZone(zoneId).toLocalDate() }
        val weekly = weekStarts.map { weekStart ->
            val days = (0..6).map { weekStart.plusDays(it.toLong()) }
            val runs = days.sumOf { day -> byLocalDate[day]?.size ?: 0 }
            val km = days.sumOf { day -> byLocalDate[day]?.sumOf { it.distance.kilometers } ?: 0.0 }
            WeeklyVolume(weekStart, km, runs)
        }
        val consistencyStart = today.minusDays(consistencyDays - 1L)
        val consistencyActivities = activities.filter {
            val date = it.startTime.atZone(zoneId).toLocalDate()
            !date.isBefore(consistencyStart) && !date.isAfter(today)
        }
        val activeDays = consistencyActivities
            .map { it.startTime.atZone(zoneId).toLocalDate() }
            .distinct()
            .size
        val lastRun = consistencyActivities.maxByOrNull { it.startTime }
            ?.startTime
            ?.atZone(zoneId)
            ?.toLocalDate()
        val consistency = ConsistencySummary(
            windowDays = consistencyDays,
            activeDays = activeDays,
            runs = consistencyActivities.size,
            daysSinceLastRun = lastRun?.let { ChronoUnit.DAYS.between(it, today) },
        )
        val pacePoints = consistencyActivities
            .filter { it.averagePace != null && it.averageHeartRate != null }
            .sortedBy { it.startTime }
            .map {
                PaceHeartRatePoint(
                    date = it.startTime.atZone(zoneId).toLocalDate(),
                    pace = it.averagePace!!,
                    heartRate = it.averageHeartRate!!,
                )
            }
        val records = records(activities = activities, zoneId = zoneId)
        val totalKm = consistencyActivities.sumOf { it.distance.kilometers }
        val paceValues = consistencyActivities.mapNotNull { it.averagePace?.secondsPerKm }
        val heartRates = consistencyActivities.mapNotNull { it.averageHeartRate }
        return ActivityTrends(
            weeks = weekly,
            consistency = consistency,
            paceHeartRate = pacePoints,
            totalKilometers = totalKm,
            averagePace = paceValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.let(Pace::fromSecondsPerKm),
            averageHeartRate = heartRates.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            records = records,
        )
    }

    private fun records(activities: List<ActivitySummary>, zoneId: ZoneId): PersonalRecords {
        val runs = activities.filter { it.sport == Sport.RUNNING || it.sport == Sport.TREADMILL }
        val distanceRuns = runs.filter { it.distance.meters > 0.0 }
        val longest = distanceRuns.maxByOrNull { it.distance.meters }
        val longestTime = runs.maxByOrNull { it.durationSec }
        val bestPace = distanceRuns
            .filter { it.distance.kilometers >= MIN_RECORD_DISTANCE_KM }
            .mapNotNull { activity ->
                (activity.averagePace ?: activity.calculatedPace)?.let { pace -> activity to pace }
            }
            .minByOrNull { (_, pace) -> pace.secondsPerKm }
        return PersonalRecords(
            longestDistanceKm = longest?.distance?.kilometers,
            longestDistanceDate = longest?.startTime?.atZone(zoneId)?.toLocalDate(),
            bestPace = bestPace?.second,
            bestPaceDate = bestPace?.first?.startTime?.atZone(zoneId)?.toLocalDate(),
            bestPaceDistanceKm = bestPace?.first?.distance?.kilometers,
            longestDurationSec = longestTime?.durationSec,
            longestDurationDate = longestTime?.startTime?.atZone(zoneId)?.toLocalDate(),
        )
    }

    private companion object {
        const val MIN_RECORD_DISTANCE_KM = 3.0
    }
}
