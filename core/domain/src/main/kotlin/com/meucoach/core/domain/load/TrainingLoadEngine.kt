package com.meucoach.core.domain.load

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Pace
import java.time.LocalDate

data class LoadActivity(
    val date: LocalDate,
    val durationSec: Int,
    val averageHeartRate: Int? = null,
    val averagePace: Pace? = null,
) {
    init {
        require(durationSec > 0) { "Activity duration must be positive" }
    }
}

enum class LoadMetric {
    RTSS,
    TRIMP,
}

data class DailyLoad(val date: LocalDate, val load: Double, val metric: LoadMetric?)

data class LoadSnapshot(
    val daily: List<DailyLoad>,
    val ctl: Double,
    val atl: Double,
    val tsb: Double,
    val acuteAverage7d: Double,
    val chronicAverage28d: Double,
    val acwr: Double?,
    val monotony: Double?,
    val missingDays: Int,
) {
    val hasData: Boolean get() = daily.any { it.metric != null }
}

/**
 * Builds the daily load series for the PMC.
 *
 * rTSS is preferred when pace and threshold pace exist; TRIMP is the fallback
 * when heart-rate reserve data is available. The two metrics are never summed
 * in the same day (R-LOAD-01).
 */
class TrainingLoadEngine {
    fun calculate(
        activities: List<LoadActivity>,
        profile: AthleteProfile,
        today: LocalDate,
        days: Int = 42,
    ): LoadSnapshot {
        require(days >= 28) { "Load series needs at least 28 days" }
        val start = today.minusDays(days - 1L)
        val byDate = activities
            .filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
            .groupBy { it.date }

        val daily = (0 until days).map { offset ->
            val date = start.plusDays(offset.toLong())
            estimate(byDate[date].orEmpty(), profile, date)
        }

        val loads = daily.map { it.load }
        val ctlSeries = TrainingLoadCalculator.ctl(loads)
        val atlSeries = TrainingLoadCalculator.atl(loads)
        val last7 = loads.takeLast(7)
        val last28 = loads.takeLast(28)
        val acute = last7.average()
        val chronic = last28.average()

        return LoadSnapshot(
            daily = daily,
            ctl = ctlSeries.last(),
            atl = atlSeries.last(),
            tsb = TrainingLoadCalculator.tsb(ctlSeries.last(), atlSeries.last()),
            acuteAverage7d = acute,
            chronicAverage28d = chronic,
            acwr = TrainingLoadCalculator.acwr(acute, chronic),
            monotony = TrainingLoadCalculator.monotony(last7),
            missingDays = daily.count { it.metric == null },
        )
    }

    private fun estimate(activities: List<LoadActivity>, profile: AthleteProfile, date: LocalDate): DailyLoad {
        val resting = profile.restingHeartRate
        val max = profile.maxHeartRateObserved
        val trimp = if (resting == null || max == null) {
            null
        } else {
            activities
                .mapNotNull { activity ->
                    val average = activity.averageHeartRate ?: return@mapNotNull null
                    if (average !in resting..max) return@mapNotNull null
                    TrainingLoadCalculator.trimp(
                        durationSec = activity.durationSec,
                        averageHeartRateBpm = average,
                        restingHeartRateBpm = resting,
                        maxHeartRateBpm = max,
                        sex = profile.sex,
                    )
                }
                .takeIf { it.isNotEmpty() }
                ?.sum()
        }
        if (trimp != null) return DailyLoad(date, trimp, LoadMetric.TRIMP)

        val thresholdPace = profile.thresholdPace
        val rtss = if (thresholdPace == null) {
            null
        } else {
            activities
                .mapNotNull { activity ->
                    val pace = activity.averagePace ?: return@mapNotNull null
                    runCatching {
                        TrainingLoadCalculator.rTss(
                            durationSec = activity.durationSec,
                            normalizedPaceSecPerKm = pace.secondsPerKm,
                            thresholdPaceSecPerKm = thresholdPace.secondsPerKm,
                        )
                    }.getOrNull()
                }
                .takeIf { it.isNotEmpty() }
                ?.sum()
        }
        return if (rtss != null) {
            DailyLoad(date, rtss, LoadMetric.RTSS)
        } else {
            DailyLoad(date, 0.0, null)
        }
    }
}
