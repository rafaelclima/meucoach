package com.meucoach.core.domain.load

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Pace
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

data class LoadActivity(
    val date: LocalDate,
    val durationSec: Int,
    val averageHeartRate: Int? = null,
    val averagePace: Pace? = null,
    /** Start instant, used only to report the most recent activity in the coverage window. */
    val startTime: Instant? = null,
) {
    init {
        require(durationSec > 0) { "Activity duration must be positive" }
    }
}

enum class LoadMetric {
    RTSS,
    TRIMP,
}

/** Whether the load series can be trusted for a quality/progression decision. */
enum class LoadDataState {
    KNOWN,
    INSUFFICIENT,
    STALE,
}

/**
 * Freshness of the activity source. A day without activity is only rest when
 * the source is fresh; otherwise it is missing data, not a physiological zero.
 */
data class ActivitySourceStatus(
    val lastImportedAt: Instant?,
    val checkedAt: Instant,
    val maxAgeDays: Long = DEFAULT_MAX_AGE_DAYS,
) {
    init {
        require(maxAgeDays > 0) { "Source max age must be positive" }
    }

    val isFresh: Boolean
        get() = lastImportedAt != null &&
            !lastImportedAt.isBefore(checkedAt.minus(Duration.ofDays(maxAgeDays)))

    companion object {
        const val DEFAULT_MAX_AGE_DAYS = 14L

        fun unknown(checkedAt: Instant): ActivitySourceStatus = ActivitySourceStatus(
            lastImportedAt = null,
            checkedAt = checkedAt,
        )
    }
}

/**
 * Coverage of the 28-day load window.
 *
 * [daysWithLoad] are days with a computable load metric. [daysWithoutActivity]
 * are rest days (no activity) observed with a fresh source. [daysWithoutData]
 * are days the app cannot classify: activity without a usable metric, or any
 * day without activity while the source is stale.
 */
data class LoadCoverage(
    val daysInWindow: Int,
    val daysWithLoad: Int,
    val daysWithoutActivity: Int,
    val daysWithoutData: Int,
    val lastActivityAt: Instant?,
    val sourceFresh: Boolean,
) {
    init {
        require(daysInWindow >= 0) { "Window size must not be negative" }
        require(daysWithLoad >= 0) { "Days with load must not be negative" }
        require(daysWithoutActivity >= 0) { "Days without activity must not be negative" }
        require(daysWithoutData >= 0) { "Days without data must not be negative" }
        require(daysWithLoad + daysWithoutActivity + daysWithoutData == daysInWindow) {
            "Coverage buckets must add up to the window"
        }
    }
}

data class DailyLoad(val date: LocalDate, val load: Double, val metric: LoadMetric?)

data class LoadSnapshot(
    val daily: List<DailyLoad>,
    val ctlSeries: List<Double>,
    val atlSeries: List<Double>,
    val ctl: Double,
    val atl: Double,
    val tsb: Double,
    val acuteAverage7d: Double,
    val chronicAverage28d: Double,
    val acwr: Double?,
    val monotony: Double?,
    val missingDays: Int,
    val coverage: LoadCoverage,
) {
    val hasData: Boolean get() = daily.any { it.metric != null }

    /**
     * ACWR is never a permission. When coverage or freshness is missing the
     * snapshot declares it instead of treating absence as a safe ratio.
     */
    val dataState: LoadDataState
        get() = when {
            !coverage.sourceFresh && coverage.lastActivityAt == null && coverage.daysWithLoad == 0 ->
                LoadDataState.INSUFFICIENT

            !coverage.sourceFresh -> LoadDataState.STALE

            coverage.daysWithLoad < MIN_DAYS_WITH_LOAD -> LoadDataState.INSUFFICIENT

            acwr == null -> LoadDataState.INSUFFICIENT

            else -> LoadDataState.KNOWN
        }

    /** Daily training stress balance: base minus fatigue, same unit as the other PMC series. */
    val tsbSeries: List<Double>
        get() = ctlSeries.zip(atlSeries) { ctl, atl -> TrainingLoadCalculator.tsb(ctl, atl) }

    companion object {
        /** Minimum days with load in the 28-day window to trust ACWR (≈2 runs/week). */
        const val MIN_DAYS_WITH_LOAD = 8
    }
}

/**
 * Builds the daily load series for the PMC.
 *
 * rTSS is preferred when pace and threshold pace exist; TRIMP is the fallback
 * when heart-rate reserve data is available. The two metrics are never summed
 * in the same day (R-LOAD-01).
 *
 * CTL/ATL are exponential moving averages: their value depends on the load
 * before the displayed window. The engine therefore feeds a bounded warm-up
 * ([DEFAULT_WARMUP_DAYS]) instead of restarting the 42-day EMA at zero whenever
 * older activities exist (BUG-03). When there is no pre-window history, the
 * warm-up is empty and the series honestly starts at zero.
 *
 * The engine also reports coverage and source freshness. ACWR is only exposed
 * when the source is fresh and at least [LoadSnapshot.MIN_DAYS_WITH_LOAD] days
 * have a computable load in the last 28 days (ADR 0026).
 */
class TrainingLoadEngine {
    fun calculate(
        activities: List<LoadActivity>,
        profile: AthleteProfile,
        today: LocalDate,
        sourceStatus: ActivitySourceStatus,
        days: Int = 42,
        warmupDays: Int = DEFAULT_WARMUP_DAYS,
    ): LoadSnapshot {
        require(days >= 28) { "Load series needs at least 28 days" }
        require(warmupDays >= 0) { "Warm-up days must not be negative" }
        val displayStart = today.minusDays(days - 1L)
        val seriesStart = displayStart.minusDays(warmupDays.toLong())
        val filtered = activities.filter { !it.date.isBefore(seriesStart) && !it.date.isAfter(today) }
        val byDate = filtered.groupBy { it.date }

        val fullSeries = (0 until days + warmupDays).map { offset ->
            val date = seriesStart.plusDays(offset.toLong())
            estimate(byDate[date].orEmpty(), profile, date)
        }
        val daily = fullSeries.takeLast(days)
        val loads = fullSeries.map { it.load }
        val ctlSeries = TrainingLoadCalculator.ctl(loads).takeLast(days)
        val atlSeries = TrainingLoadCalculator.atl(loads).takeLast(days)
        val displayLoads = daily.map { it.load }
        val last7 = displayLoads.takeLast(7)
        val last28 = displayLoads.takeLast(28)
        val acute = last7.average()
        val chronic = last28.average()

        val coverage = coverage(
            daily = daily,
            activitiesByDate = byDate,
            sourceStatus = sourceStatus,
            today = today,
            displayStart = displayStart,
        )
        val acwr = if (sourceStatus.isFresh && coverage.daysWithLoad >= LoadSnapshot.MIN_DAYS_WITH_LOAD) {
            TrainingLoadCalculator.acwr(acute, chronic)
        } else {
            null
        }

        return LoadSnapshot(
            daily = daily,
            ctlSeries = ctlSeries,
            atlSeries = atlSeries,
            ctl = ctlSeries.last(),
            atl = atlSeries.last(),
            tsb = TrainingLoadCalculator.tsb(ctlSeries.last(), atlSeries.last()),
            acuteAverage7d = acute,
            chronicAverage28d = chronic,
            acwr = acwr,
            monotony = TrainingLoadCalculator.monotony(last7),
            missingDays = daily.count { it.metric == null },
            coverage = coverage,
        )
    }

    private fun coverage(
        daily: List<DailyLoad>,
        activitiesByDate: Map<LocalDate, List<LoadActivity>>,
        sourceStatus: ActivitySourceStatus,
        today: LocalDate,
        displayStart: LocalDate,
    ): LoadCoverage {
        val windowStart = today.minusDays(COVERAGE_DAYS - 1L)
        val window = daily.filter { !it.date.isBefore(windowStart) }
        val activityDates = activitiesByDate.keys.filter { !it.isBefore(windowStart) }.toSet()
        val daysWithLoad = window.count { it.metric != null }
        val daysWithoutActivity = if (sourceStatus.isFresh) {
            window.count { it.metric == null && it.date !in activityDates }
        } else {
            0
        }
        val daysWithoutData = window.size - daysWithLoad - daysWithoutActivity
        // Warm-up days only feed the EMA; they are not part of the displayed window.
        val lastActivityAt = activitiesByDate
            .filterKeys { !it.isBefore(displayStart) }
            .values
            .flatten()
            .mapNotNull { it.startTime }
            .maxOrNull()

        return LoadCoverage(
            daysInWindow = window.size,
            daysWithLoad = daysWithLoad,
            daysWithoutActivity = daysWithoutActivity,
            daysWithoutData = daysWithoutData,
            lastActivityAt = lastActivityAt,
            sourceFresh = sourceStatus.isFresh,
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

    private companion object {
        const val COVERAGE_DAYS = 28L

        /**
         * Bounded burn-in for the 42-day CTL EMA (≈4.3 time constants).
         * Reading more than this adds no meaningful accuracy; reading less
         * would let the displayed window start from zero again (BUG-03).
         */
        const val DEFAULT_WARMUP_DAYS = 180
    }
}
