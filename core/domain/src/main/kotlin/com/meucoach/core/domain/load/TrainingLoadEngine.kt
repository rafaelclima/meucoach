package com.meucoach.core.domain.load

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Pace
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
    SRPE,
}

/**
 * A subjectively logged execution (ADR 0039): duration in whole minutes and the
 * perceived effort (RPE 1-10) the athlete reported. It feeds the SRPE tier of
 * the load series — `sRPE = RPE × minutes` (Foster, EV-FOSTER-2001) — and never
 * replaces an objective metric.
 */
data class LoadExecution(val date: LocalDate, val durationMin: Int, val rpe: Int) {
    init {
        require(durationMin > 0) { "Execution duration must be positive" }
        require(rpe in 1..10) { "RPE must be between 1 and 10" }
    }
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
 *
 * The source has two lifelines (ADR 0039): the last import **or** the last
 * subjectively logged execution with RPE. Either within [maxAgeDays] of
 * [checkedAt] keeps the source fresh — declaring effort is declaring what
 * happened, even if less precisely than a file import.
 */
data class ActivitySourceStatus(
    val lastImportedAt: Instant?,
    val checkedAt: Instant,
    val lastSubjectiveLogAt: Instant? = null,
    val maxAgeDays: Long = DEFAULT_MAX_AGE_DAYS,
    /**
     * First known activity instant (ADR 0046). A `null` value lets the engine
     * derive it from the oldest activity it receives; it never means "no
     * training". Days before this instant are missing data, not rest.
     */
    val firstActivityAt: Instant? = null,
) {
    init {
        require(maxAgeDays > 0) { "Source max age must be positive" }
    }

    val isFresh: Boolean
        get() {
            val lastEvent = when {
                lastImportedAt == null -> lastSubjectiveLogAt
                lastSubjectiveLogAt == null -> lastImportedAt
                else -> maxOf(lastImportedAt, lastSubjectiveLogAt)
            }
            return lastEvent != null &&
                !lastEvent.isBefore(checkedAt.minus(Duration.ofDays(maxAgeDays)))
        }

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
 * are rest days (no activity) observed with a fresh source and after the first
 * known history day. [daysWithoutData] are days the app cannot classify:
 * activity without a usable metric, any day while the source is stale, or any
 * day before the first known activity (ADR 0046). Pre-history is never rest.
 */
data class LoadCoverage(
    val daysInWindow: Int,
    val daysWithLoad: Int,
    val daysWithoutActivity: Int,
    val daysWithoutData: Int,
    val lastActivityAt: Instant?,
    val sourceFresh: Boolean,
    /** Days in the window before the first known activity/subjective log. */
    val daysBeforeHistory: Int = 0,
) {
    init {
        require(daysInWindow >= 0) { "Window size must not be negative" }
        require(daysWithLoad >= 0) { "Days with load must not be negative" }
        require(daysWithoutActivity >= 0) { "Days without activity must not be negative" }
        require(daysWithoutData >= 0) { "Days without data must not be negative" }
        require(daysBeforeHistory in 0..daysInWindow) {
            "Days before history must fit inside the window"
        }
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
    /** Days in the 28-day window whose load came from subjective sRPE (ADR 0039). */
    val subjectiveDaysInWindow: Int = 0,
    /**
     * Weekly strain (Foster): sum of the last 7 daily loads × monotony.
     * Null when monotony is null. Computed by [TrainingLoadEngine]; tests may
     * inject it directly. Never a permission alone.
     */
    val strain: Double? = null,
) {
    val hasData: Boolean get() = daily.any { it.metric != null }

    /**
     * Days with an objective metric (TRIMP/rTSS). Subjective load conserves —
     * it never licenses progression — so quality and volume gates decide on
     * this number, not on [LoadCoverage.daysWithLoad] (ADR 0039).
     */
    val objectiveDaysWithLoad: Int
        get() = coverage.daysWithLoad - subjectiveDaysInWindow

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

    /**
     * [dataState] restricted to objective metrics. Subjective-only load keeps
     * guardrails alive ([dataState] can be KNOWN) but never satisfies the
     * objective bar for quality or volume growth (ADR 0039, D5).
     */
    val objectiveDataState: LoadDataState
        get() = when {
            subjectiveDaysInWindow == 0 -> dataState
            !coverage.sourceFresh -> LoadDataState.STALE
            objectiveDaysWithLoad < MIN_DAYS_WITH_LOAD -> LoadDataState.INSUFFICIENT
            else -> LoadDataState.KNOWN
        }

    /** Daily training stress balance: base minus fatigue, same unit as the other PMC series. */
    val tsbSeries: List<Double>
        get() = ctlSeries.zip(atlSeries) { ctl, atl -> TrainingLoadCalculator.tsb(ctl, atl) }

    /**
     * Monotony gate (R-LOAD-03 v2, proposto): training must vary.
     * Null monotony blocks (conservative): uniform or insufficient week.
     */
    val monotonyOk: Boolean
        get() = monotony != null && monotony < MONOTONY_MAX

    /**
     * Strain spike: current weekly strain above twice the chronic weekly
     * baseline (chronic daily average × 7). Requires a positive baseline;
     * without it there is no spike, only the monotony gate applies.
     * Heuristic LIMITED, never a diagnosis.
     */
    val strainSpike: Boolean
        get() {
            val current = strain ?: return false
            if (!current.isFinite() || current < 0.0) return false
            val baselineWeekly = chronicAverage28d * 7.0
            if (!baselineWeekly.isFinite() || baselineWeekly <= 0.0) return false
            return current > STRAIN_SPIKE_FACTOR * baselineWeekly
        }

    /**
     * ACWR above the single documented limit (1.5) only reduces the day when
     * the ratio is reliable. An unstable ratio never becomes a physiological
     * verdict (ADR 0046).
     */
    val acwrHigh: Boolean
        get() = acwrReliable && (acwr ?: 0.0) > ACWR_MAX

    /**
     * True when the 28-day window is fully covered by history (ADR 0046):
     * fresh source, no day before the first activity, no day without data,
     * enough objective load days and no subjective-only day. When false the
     * ratio may be displayed as "em calibração", but it never decides.
     */
    val acwrReliable: Boolean
        get() = acwr != null &&
            coverage.sourceFresh &&
            coverage.daysBeforeHistory == 0 &&
            coverage.daysWithoutData == 0 &&
            coverage.daysWithLoad >= MIN_DAYS_WITH_LOAD &&
            subjectiveDaysInWindow == 0

    /** Strain shares the ACWR baseline: without it, strain informs but never blocks. */
    val strainReliable: Boolean
        get() = acwrReliable

    /** Days of the 28-day window covered by history (first known activity onward). */
    val historyDays: Int
        get() = (coverage.daysInWindow - coverage.daysBeforeHistory).coerceIn(0, coverage.daysInWindow)

    /** Days with objective load in the window; the baseline behind the ratio. */
    val baselineDays: Int
        get() = objectiveDaysWithLoad

    /**
     * Quality permission (R-LOAD-03 v3, ADR 0046): objective load KNOWN,
     * monotony < 2.0, no reliable strain spike and no reliable ACWR > 1.5.
     * An unreliable ACWR never vetoes quality.
     */
    val permitsQuality: Boolean
        get() = objectiveDataState == LoadDataState.KNOWN &&
            monotonyOk &&
            !(strainSpike && strainReliable) &&
            !acwrHigh

    /**
     * Progression permission: quality plus a reliable ACWR inside 0.8–1.3.
     * A missing baseline conserves volume; it never accelerates (ADR 0046).
     */
    val permitsProgression: Boolean
        get() {
            val ratio = acwr ?: return false
            return permitsQuality &&
                acwrReliable &&
                ratio in ACWR_PROGRESSION_MIN..ACWR_PROGRESSION_MAX
        }

    companion object {
        /** Minimum days with load in the 28-day window to trust ACWR (≈2 runs/week). */
        const val MIN_DAYS_WITH_LOAD = 8

        /** The single documented ACWR reduction limit; no path uses 1.3 as a veto. */
        const val ACWR_MAX = 1.5

        /** Progression band; missing or outside it only maintains volume. */
        const val ACWR_PROGRESSION_MIN = 0.8
        const val ACWR_PROGRESSION_MAX = 1.3

        /** Foster: weekly monotony above this blocks quality/progression. */
        const val MONOTONY_MAX = 2.0

        /** Strain above twice the chronic weekly baseline is a spike. */
        const val STRAIN_SPIKE_FACTOR = 2.0
    }
}

/**
 * Builds the daily load series for the PMC.
 *
 * One metric per day, never summed (R-LOAD-01), in this order: TRIMP when
 * heart-rate reserve data is available; rTSS when only pace and threshold pace
 * exist; SRPE (`RPE × minutes`, Foster) only when neither objective metric
 * applies (ADR 0039) — subjective load conserves, it never licenses progression.
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
        executions: List<LoadExecution> = emptyList(),
    ): LoadSnapshot {
        require(days >= 28) { "Load series needs at least 28 days" }
        require(warmupDays >= 0) { "Warm-up days must not be negative" }
        val zone = ZoneId.of(profile.timezone)
        val displayStart = today.minusDays(days - 1L)
        val seriesStart = displayStart.minusDays(warmupDays.toLong())
        val inWindow: (LocalDate) -> Boolean = { date ->
            !date.isBefore(seriesStart) && !date.isAfter(today)
        }
        val filtered = activities.filter { inWindow(it.date) }
        val byDate = filtered.groupBy { it.date }
        val filteredExecutions = executions.filter { inWindow(it.date) }
        val executionsByDate = filteredExecutions.groupBy { it.date }
        // ADR 0046: history starts at the first known activity or subjective
        // log. The source may report it explicitly; otherwise the oldest row
        // received is the best available evidence. Null means no history at all.
        val historyStart = sourceStatus.firstActivityAt?.atZone(zone)?.toLocalDate()
            ?: (activities.map { it.date } + executions.map { it.date }).minOrNull()

        val fullSeries = (0 until days + warmupDays).map { offset ->
            val date = seriesStart.plusDays(offset.toLong())
            estimate(
                activities = byDate[date].orEmpty(),
                executions = executionsByDate[date].orEmpty(),
                profile = profile,
                date = date,
            )
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
            historyStart = historyStart,
        )
        val acwr = if (sourceStatus.isFresh && coverage.daysWithLoad >= LoadSnapshot.MIN_DAYS_WITH_LOAD) {
            TrainingLoadCalculator.acwr(acute, chronic)
        } else {
            null
        }
        val subjectiveDaysInWindow = coverageCount(daily, today) { it.metric == LoadMetric.SRPE }
        val weeklyMonotony = TrainingLoadCalculator.monotony(last7)
        val weeklySum = last7.sum()
        val weeklyStrain = weeklyMonotony?.let { monotony -> weeklySum * monotony }

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
            monotony = weeklyMonotony,
            missingDays = daily.count { it.metric == null },
            coverage = coverage,
            subjectiveDaysInWindow = subjectiveDaysInWindow,
            strain = weeklyStrain,
        )
    }

    private fun coverageCount(daily: List<DailyLoad>, today: LocalDate, predicate: (DailyLoad) -> Boolean): Int {
        val windowStart = today.minusDays(COVERAGE_DAYS - 1L)
        return daily.count { !it.date.isBefore(windowStart) && predicate(it) }
    }

    private fun coverage(
        daily: List<DailyLoad>,
        activitiesByDate: Map<LocalDate, List<LoadActivity>>,
        sourceStatus: ActivitySourceStatus,
        today: LocalDate,
        displayStart: LocalDate,
        historyStart: LocalDate?,
    ): LoadCoverage {
        val windowStart = today.minusDays(COVERAGE_DAYS - 1L)
        val window = daily.filter { !it.date.isBefore(windowStart) }
        val activityDates = activitiesByDate.keys.filter { !it.isBefore(windowStart) }.toSet()
        val daysWithLoad = window.count { it.metric != null }
        val daysBeforeHistory = if (historyStart == null) {
            window.size
        } else {
            ChronoUnit.DAYS.between(windowStart, historyStart)
                .coerceIn(0L, window.size.toLong())
                .toInt()
        }
        // A day without activity is only observed rest when the source is
        // fresh AND that day already belongs to known history. Before the
        // first import it is missing data, never a physiological zero.
        val daysWithoutActivity = if (sourceStatus.isFresh && historyStart != null) {
            window.count { day ->
                day.metric == null &&
                    day.date !in activityDates &&
                    !day.date.isBefore(historyStart)
            }
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
            daysBeforeHistory = daysBeforeHistory,
        )
    }

    private fun estimate(
        activities: List<LoadActivity>,
        executions: List<LoadExecution>,
        profile: AthleteProfile,
        date: LocalDate,
    ): DailyLoad {
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
        if (rtss != null) return DailyLoad(date, rtss, LoadMetric.RTSS)

        // Third tier (ADR 0039): perceived effort fills the day only when no
        // objective metric does — never on top of TRIMP/rTSS (R-LOAD-01).
        val srpe = executions
            .map { TrainingLoadCalculator.srpe(rpe = it.rpe, durationMin = it.durationMin) }
            .takeIf { it.isNotEmpty() }
            ?.sum()
        return if (srpe != null) {
            DailyLoad(date, srpe, LoadMetric.SRPE)
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
