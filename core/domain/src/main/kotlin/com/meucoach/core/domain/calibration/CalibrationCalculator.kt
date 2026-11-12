package com.meucoach.core.domain.calibration

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Pace
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class CalibrationTier {
    A,
    B,
    C,
}

enum class CalibrationWarning {
    MAX_HEART_RATE_INCONSISTENT,
    NO_RECENT_PERFORMANCE,
    NO_EFFORT_HEART_RATE,
    PERFORMANCE_DURATION_OUT_OF_RANGE,
    NO_SUSTAINED_EFFORT,
    EFFORT_PACE_OUT_OF_RANGE,
}

data class CalibrationPerformance(
    val distanceM: Double,
    val durationSec: Int,
    val date: LocalDate,
    val averageHeartRate: Int? = null,
) {
    init {
        require(distanceM.isFinite() && distanceM > 0.0) { "Performance distance must be positive" }
        require(durationSec > 0) { "Performance duration must be positive" }
        require(averageHeartRate == null || averageHeartRate in 30..250) {
            "Performance heart rate out of range"
        }
    }
}

data class CalibrationInput(
    val profile: AthleteProfile,
    val today: LocalDate,
    val performance: CalibrationPerformance? = null,
    val recentActivitiesWithHeartRateAndPace: Int = 0,
    val trainingEfforts: List<CalibrationPerformance> = emptyList(),
) {
    init {
        require(recentActivitiesWithHeartRateAndPace >= 0) {
            "Recent activity count must not be negative"
        }
    }
}

data class CalibrationResult(
    val tier: CalibrationTier,
    val vdot: Double?,
    val thresholdPace: Pace?,
    val lthr: Int?,
    val confidence: DataConfidence,
    val ruleId: String,
    val evidenceIds: List<String>,
    val warnings: Set<CalibrationWarning>,
    val recommendation: String,
)

/**
 * Deterministic calibration from a recent race or 30-minute time trial.
 *
 * VDOT follows Daniels' VO2/%VO2max equations. Threshold pace is the velocity
 * at 88% of VDOT. LTHR uses the effort's average heart rate only for efforts
 * between 25 and 35 minutes; outside that window the app does not invent a
 * threshold.
 *
 * Tier B (ADR 0035): without a recent performance, the fastest sustained
 * training effort of 20–35 minutes with heart rate estimates the threshold
 * pace; VDOT is derived from it and LTHR comes from that same effort only
 * within the 25–35 minute window. Efforts outside the criteria keep the
 * labeled Tier B without estimates.
 */
object CalibrationCalculator {
    private const val RECENT_DAYS = 56L
    private const val MIN_VDOT = 10.0
    private const val MAX_VDOT = 90.0
    private const val THRESHOLD_VDOT_FRACTION = 0.88
    private const val X = 0.000104
    private const val Y = 0.182258
    private const val Z = 4.60

    /** Minimum recent efforts with heart rate and pace required to label Tier B (R-CAL-02). */
    const val MIN_TIER_B_EFFORTS = 3
    private const val MIN_SUSTAINED_SEC = 20 * 60
    private const val MAX_SUSTAINED_SEC = 35 * 60
    private const val MIN_LTHR_SEC = 25 * 60
    private const val MAX_LTHR_SEC = 35 * 60

    fun calibrate(input: CalibrationInput): CalibrationResult {
        val performance = input.performance
        val warnings = linkedSetOf<CalibrationWarning>()
        if (performance == null) warnings += CalibrationWarning.NO_RECENT_PERFORMANCE

        if (performance != null && !isRecent(performance, input.today)) {
            warnings += CalibrationWarning.NO_RECENT_PERFORMANCE
        }
        val usable = performance?.takeIf { isRecent(it, input.today) && isUsable(it, warnings) }
        if (usable == null) {
            return withoutPerformance(input, warnings)
        }

        val vdot = vdot(usable.distanceM, usable.durationSec)
        if (vdot == null) {
            warnings += CalibrationWarning.PERFORMANCE_DURATION_OUT_OF_RANGE
            return withoutPerformance(input, warnings)
        }

        val lthr = lthrFrom(usable)
        if (lthr == null) warnings += CalibrationWarning.NO_EFFORT_HEART_RATE

        checkMaxHeartRate(input.profile, usable.averageHeartRate, warnings)

        return CalibrationResult(
            tier = CalibrationTier.A,
            vdot = vdot,
            thresholdPace = thresholdPaceForVdot(vdot),
            lthr = lthr,
            confidence = DataConfidence.HIGH,
            ruleId = "R-CAL-01",
            evidenceIds = EVIDENCE,
            warnings = warnings,
            recommendation = "Use os ritmos de treino e as zonas de FC pelo seu limite de esforço. " +
                "Recalibre em 6–8 semanas.",
        )
    }

    /** Daniels: VO2max = -4.6 + 0.182258·v + 0.000104·v², v em m/min. */
    fun vdot(distanceM: Double, durationSec: Int): Double? =
        vdotRaw(distanceM, durationSec)?.takeIf { it.isFinite() && it in MIN_VDOT..MAX_VDOT }

    /**
     * Unclamped Daniels VDOT, shared with the race predictor's inverse search.
     * The public [vdot] keeps the 10–90 sanity limits for calibration.
     */
    internal fun vdotRaw(distanceM: Double, durationSec: Int): Double? {
        if (distanceM <= 0.0 || durationSec <= 0) return null
        val minutes = durationSec / 60.0
        val velocity = distanceM / minutes
        val vo2 = -Z + Y * velocity + X * velocity * velocity
        val percent =
            0.8 +
                0.1894393 * exp(-0.012778 * minutes) +
                0.2989558 * exp(-0.1932605 * minutes)
        if (percent <= 0.0) return null
        val value = vo2 / percent
        return value.takeIf { it.isFinite() }
    }

    /** Inverts the VO2 equation at 88% of VDOT to get threshold velocity. */
    fun thresholdPaceForVdot(vdot: Double): Pace {
        require(vdot > 0.0) { "VDOT must be positive" }
        val target = THRESHOLD_VDOT_FRACTION * vdot
        val discriminant = Y * Y + 4 * X * (Z + target)
        val velocity = (-Y + sqrt(discriminant)) / (2 * X)
        val secondsPerKm = (1_000.0 / velocity * 60.0).roundToInt().coerceIn(150, 900)
        return Pace.fromSecondsPerKm(secondsPerKm)
    }

    private fun isRecent(performance: CalibrationPerformance, today: LocalDate): Boolean {
        if (performance.date.isAfter(today)) return false
        return ChronoUnit.DAYS.between(performance.date, today) <= RECENT_DAYS
    }

    private fun isUsable(performance: CalibrationPerformance, warnings: MutableSet<CalibrationWarning>): Boolean {
        val minutes = performance.durationSec / 60.0
        val usable = performance.distanceM in 1_000.0..100_000.0 && minutes in 8.0..180.0
        if (!usable) warnings += CalibrationWarning.PERFORMANCE_DURATION_OUT_OF_RANGE
        return usable
    }

    private fun lthrFrom(performance: CalibrationPerformance): Int? {
        val minutes = performance.durationSec / 60.0
        if (minutes !in 25.0..35.0) return null
        return performance.averageHeartRate
    }

    private fun withoutPerformance(
        input: CalibrationInput,
        warnings: MutableSet<CalibrationWarning>,
    ): CalibrationResult {
        val efforts = input.trainingEfforts
        val gate = if (efforts.isEmpty()) {
            input.recentActivitiesWithHeartRateAndPace >= MIN_TIER_B_EFFORTS
        } else {
            efforts.count { it.averageHeartRate != null && isRecent(it, input.today) } >= MIN_TIER_B_EFFORTS
        }
        if (!gate) {
            return CalibrationResult(
                tier = CalibrationTier.C,
                vdot = null,
                thresholdPace = null,
                lthr = null,
                confidence = DataConfidence.LOW,
                ruleId = "R-CAL-02",
                evidenceIds = EVIDENCE,
                warnings = warnings,
                recommendation = "Complete 2 semanas de base e faça um teste de 30 min guiado " +
                    "antes de prescrever intensidade.",
            )
        }
        if (efforts.isEmpty()) {
            return labeledTierB(warnings)
        }

        val candidate = efforts
            .asSequence()
            .filter { it.averageHeartRate != null && isRecent(it, input.today) && isUsable(it, warnings) }
            .filter { it.durationSec in MIN_SUSTAINED_SEC..MAX_SUSTAINED_SEC }
            .minWithOrNull(
                compareBy<CalibrationPerformance> { paceSecondsPerKm(it) }.thenByDescending { it.date },
            )
        if (candidate == null) {
            warnings += CalibrationWarning.NO_SUSTAINED_EFFORT
            return labeledTierB(warnings)
        }

        val paceSecPerKm = paceSecondsPerKm(candidate)
        val velocity = 60_000.0 / paceSecPerKm
        val vo2 = -Z + Y * velocity + X * velocity * velocity
        val vdot = vo2 / THRESHOLD_VDOT_FRACTION
        if (!vdot.isFinite() || vdot !in MIN_VDOT..MAX_VDOT) {
            warnings += CalibrationWarning.EFFORT_PACE_OUT_OF_RANGE
            return labeledTierB(warnings)
        }

        val lthr = if (candidate.durationSec in MIN_LTHR_SEC..MAX_LTHR_SEC) candidate.averageHeartRate else null
        if (lthr == null) warnings += CalibrationWarning.NO_EFFORT_HEART_RATE
        checkMaxHeartRate(input.profile, candidate.averageHeartRate, warnings)

        return CalibrationResult(
            tier = CalibrationTier.B,
            vdot = vdot,
            thresholdPace = Pace.fromSecondsPerKm(paceSecPerKm),
            lthr = lthr,
            confidence = DataConfidence.MEDIUM,
            ruleId = "R-CAL-02",
            evidenceIds = EVIDENCE,
            warnings = warnings,
            recommendation = "Estimativa pelo seu melhor esforço sustentado de 20–35 min. " +
                "Confirme com um teste de 30 min quando possível; até lá, ajuste pelo esforço percebido.",
        )
    }

    private fun labeledTierB(warnings: MutableSet<CalibrationWarning>): CalibrationResult = CalibrationResult(
        tier = CalibrationTier.B,
        vdot = null,
        thresholdPace = null,
        lthr = null,
        confidence = DataConfidence.MEDIUM,
        ruleId = "R-CAL-02",
        evidenceIds = EVIDENCE,
        warnings = warnings,
        recommendation = "Faça um teste de 30 min para estimar seu limite de esforço e seu ritmo de limiar. " +
            "Até lá, use a FC e o esforço percebido.",
    )

    private fun checkMaxHeartRate(
        profile: AthleteProfile,
        effortHeartRate: Int?,
        warnings: MutableSet<CalibrationWarning>,
    ) {
        profile.maxHeartRateObserved?.let { maxHeartRate ->
            if (effortHeartRate != null && effortHeartRate > maxHeartRate * 0.95) {
                warnings += CalibrationWarning.MAX_HEART_RATE_INCONSISTENT
            }
        }
    }

    /** Average pace of the effort in seconds per kilometer, matching `ActivitySummary.calculatedPace`. */
    private fun paceSecondsPerKm(performance: CalibrationPerformance): Int =
        (performance.durationSec / (performance.distanceM / 1_000.0)).roundToInt()

    private val EVIDENCE = listOf("EV-DANIELS-2014", "EV-FRIEL-2018")
}
