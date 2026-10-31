package com.meucoach.core.domain.trends

import com.meucoach.core.domain.zones.HrZoneDistributionCalculator
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.HeartRateZone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One imported running session with the samples decoded from its blob. */
data class IntensitySession(val date: LocalDate, val samples: List<ActivitySample>)

/**
 * Time in the three polarized buckets of `R-ZONE-02`: easy (Z1–Z2), moderate
 * (Z3) and hard (Z4–Z5).
 */
data class WeeklyIntensity(val weekStart: LocalDate, val easySec: Int, val moderateSec: Int, val hardSec: Int) {
    init {
        require(easySec >= 0 && moderateSec >= 0 && hardSec >= 0) {
            "Intensity durations must not be negative"
        }
        require(easySec + moderateSec + hardSec > 0) {
            "Weekly intensity requires measured time"
        }
    }

    val totalSec: Int get() = easySec + moderateSec + hardSec

    fun easyPercent(): Double = easySec * 100.0 / totalSec

    fun qualityPercent(): Double = (moderateSec + hardSec) * 100.0 / totalSec
}

/**
 * Time-weighted intensity split for a window of runs. The 80/20 target is a
 * heuristic (`R-PLAN-06`, `R-ZONE-02`), so it is reported with the number of
 * contributing sessions and a confidence, never as a pass/fail grade.
 */
data class IntensityDistribution(
    val windowDays: Int,
    val easySec: Int,
    val moderateSec: Int,
    val hardSec: Int,
    val sessions: Int,
    val weeks: List<WeeklyIntensity>,
    val confidence: DataConfidence,
) {
    init {
        require(windowDays in MIN_WINDOW_DAYS..MAX_WINDOW_DAYS) {
            "Window must be between $MIN_WINDOW_DAYS and $MAX_WINDOW_DAYS days"
        }
        require(easySec >= 0 && moderateSec >= 0 && hardSec >= 0) {
            "Intensity durations must not be negative"
        }
        require(easySec + moderateSec + hardSec > 0) { "Intensity distribution requires time" }
        require(sessions > 0) { "Intensity distribution requires at least one session" }
        require(weeks.isNotEmpty()) { "Intensity distribution requires at least one week" }
    }

    val totalSec: Int get() = easySec + moderateSec + hardSec

    fun easyPercent(): Double = easySec * 100.0 / totalSec

    fun moderatePercent(): Double = moderateSec * 100.0 / totalSec

    fun hardPercent(): Double = hardSec * 100.0 / totalSec

    fun qualityPercent(): Double = moderatePercent() + hardPercent()

    /** Easy share at or above the 80% heuristic. Reporting only, not a prescription. */
    val meetsEasyTarget: Boolean get() = easyPercent() >= EASY_TARGET_PERCENT

    companion object {
        const val EASY_TARGET_PERCENT = 80.0
        const val MIN_WINDOW_DAYS = 7
        const val MAX_WINDOW_DAYS = 90
    }
}

/**
 * Measures the 80/20 split from decoded samples instead of session averages:
 * time is allocated to the zone of the earlier sample and intervals without a
 * heart rate stay out of the total (`R-ZONE-02`).
 *
 * Confidence reflects measurement coverage in the window, not training
 * quality: fewer than 3 sessions is low, 3–5 is medium and 6+ is high.
 */
class IntensityDistributionCalculator {
    fun calculate(
        sessions: List<IntensitySession>,
        today: LocalDate,
        lthrBpm: Int,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
    ): IntensityDistribution? {
        require(windowDays in IntensityDistribution.MIN_WINDOW_DAYS..IntensityDistribution.MAX_WINDOW_DAYS) {
            "Window must be between ${IntensityDistribution.MIN_WINDOW_DAYS} and " +
                "${IntensityDistribution.MAX_WINDOW_DAYS} days"
        }
        val start = today.minusDays(windowDays - 1L)
        val measured = sessions
            .filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
            .sortedBy { it.date }
            .mapNotNull { it.measure(lthrBpm) }
        if (measured.isEmpty()) return null

        val firstWeekStart = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = generateSequence(currentWeekStart) { it.minusWeeks(1) }
            .takeWhile { !it.isBefore(firstWeekStart) }
            .toList()
            .asReversed()
            .mapNotNull { weekStart -> weekStart.summarize(measured) }

        return IntensityDistribution(
            windowDays = windowDays,
            easySec = measured.sumOf { it.easySec },
            moderateSec = measured.sumOf { it.moderateSec },
            hardSec = measured.sumOf { it.hardSec },
            sessions = measured.size,
            weeks = weeks,
            confidence = confidenceFor(measured.size),
        )
    }

    private fun IntensitySession.measure(lthrBpm: Int): MeasuredSession? {
        val distribution = HrZoneDistributionCalculator.calculate(samples, lthrBpm) ?: return null
        val easy = distribution.durationOf(HeartRateZone.Z1) + distribution.durationOf(HeartRateZone.Z2)
        return MeasuredSession(
            date = date,
            easySec = easy,
            moderateSec = distribution.durationOf(HeartRateZone.Z3),
            hardSec = distribution.durationOf(HeartRateZone.Z4) + distribution.durationOf(HeartRateZone.Z5),
        )
    }

    private fun LocalDate.summarize(measured: List<MeasuredSession>): WeeklyIntensity? {
        val end = plusDays(6)
        val inWeek = measured.filter { !it.date.isBefore(this) && !it.date.isAfter(end) }
        if (inWeek.isEmpty()) return null
        return WeeklyIntensity(
            weekStart = this,
            easySec = inWeek.sumOf { it.easySec },
            moderateSec = inWeek.sumOf { it.moderateSec },
            hardSec = inWeek.sumOf { it.hardSec },
        )
    }

    private fun confidenceFor(sessions: Int): DataConfidence = when {
        sessions >= HIGH_CONFIDENCE_SESSIONS -> DataConfidence.HIGH
        sessions >= MEDIUM_CONFIDENCE_SESSIONS -> DataConfidence.MEDIUM
        else -> DataConfidence.LOW
    }

    private data class MeasuredSession(val date: LocalDate, val easySec: Int, val moderateSec: Int, val hardSec: Int)

    private companion object {
        const val DEFAULT_WINDOW_DAYS = 28
        const val MEDIUM_CONFIDENCE_SESSIONS = 3
        const val HIGH_CONFIDENCE_SESSIONS = 6
    }
}
