package com.meucoach.core.domain.calibration

import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Sport
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A recorded run that may represent the 30-minute threshold effort.
 *
 * This is only a candidate: the athlete still has to confirm that the run was
 * a maximal, continuous effort before anything is saved to the profile. A long
 * easy run must never silently become an LTHR.
 */
data class CalibrationCandidate(
    val distanceM: Double,
    val durationSec: Int,
    val date: LocalDate,
    val averageHeartRate: Int,
    val heartRateCoverage: Double,
)

/**
 * Selects runs that can pre-fill the calibration form.
 *
 * The window follows `SPORTS_SCIENCE.md` (§4.3): LTHR only comes from a
 * 25–35 min effort. On top of the duration, the detector checks that the file
 * has a usable heart-rate record and that the effort was sustained: enough HR
 * samples, no long pauses and a narrow enough HR band. A run older than the
 * 8-week calibration window is not offered either, because the calculator would
 * reject it. If any check fails the run stays a normal activity and the app does
 * not offer calibration.
 */
object CalibrationCandidateDetector {
    const val MIN_DURATION_SEC = 25 * 60
    const val MAX_DURATION_SEC = 35 * 60
    const val MIN_HEART_RATE_COVERAGE = 0.6
    const val RECENT_DAYS = 56L

    private const val MIN_DISTANCE_M = 1_000.0
    private const val MIN_HEART_RATE_SAMPLES = 10
    private const val MAX_HEART_RATE_SPREAD_BPM = 30
    private const val MAX_SAMPLE_GAP_SEC = 120
    private val ELIGIBLE_SPORTS = setOf(Sport.RUNNING, Sport.TREADMILL)

    fun detect(activity: ImportedActivity, date: LocalDate, today: LocalDate): CalibrationCandidate? {
        if (activity.sport !in ELIGIBLE_SPORTS) return null
        if (!isRecent(date, today)) return null
        if (activity.durationSec !in MIN_DURATION_SEC..MAX_DURATION_SEC) return null
        if (!activity.distanceM.isFinite() || activity.distanceM < MIN_DISTANCE_M) return null

        val averageHeartRate = activity.averageHeartRate ?: return null
        if (averageHeartRate !in 100..230) return null

        val heartRates = activity.samples.mapNotNull { it.heartRate }
        if (heartRates.size < MIN_HEART_RATE_SAMPLES) return null

        val coverage = heartRates.size.toDouble() / activity.samples.size
        if (coverage < MIN_HEART_RATE_COVERAGE) return null
        if (heartRateSpread(heartRates) > MAX_HEART_RATE_SPREAD_BPM) return null
        if (hasLongPause(activity.samples.map { it.offsetSec })) return null

        return CalibrationCandidate(
            distanceM = activity.distanceM,
            durationSec = activity.durationSec,
            date = date,
            averageHeartRate = averageHeartRate,
            heartRateCoverage = coverage,
        )
    }

    private fun isRecent(date: LocalDate, today: LocalDate): Boolean {
        if (date.isAfter(today)) return false
        return ChronoUnit.DAYS.between(date, today) <= RECENT_DAYS
    }

    /** Difference between the 10th and 90th percentile, ignoring sensor spikes. */
    private fun heartRateSpread(values: List<Int>): Int {
        val sorted = values.sorted()
        val p10 = sorted[((sorted.size - 1) * 0.10).toInt()]
        val p90 = sorted[((sorted.size - 1) * 0.90).toInt()]
        return p90 - p10
    }

    private fun hasLongPause(offsets: List<Int>): Boolean =
        offsets.zipWithNext().any { (previous, next) -> next - previous > MAX_SAMPLE_GAP_SEC }
}
