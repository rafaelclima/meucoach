package com.meucoach.core.domain.review

import com.meucoach.core.domain.prescription.PlanCompletionWeek
import com.meucoach.core.domain.readiness.ReadinessHistoryDay
import com.meucoach.core.domain.trends.WeeklyIntensity
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

enum class WeeklyReviewStatus {
    /** A plan exists for the week; adherence and every available section are reported. */
    AVAILABLE,

    /** Runs or health data exist, but there was no plan to follow. */
    EMPTY_NO_PLAN,

    /** Nothing was planned, run or measured: there is no week to consolidate. */
    EMPTY_NO_DATA,
}

/** Adherence copied from the persisted plan; ratios stay `null` without a denominator. */
data class WeeklyAdherence(
    val planned: Int,
    val done: Int,
    val verified: Int,
    val manual: Int,
    val legacy: Int,
    val skipped: Int,
) {
    init {
        require(planned >= 0) { "Planned sessions must not be negative" }
        require(done in 0..planned) { "Done sessions must fit the planned total" }
        require(verified in 0..done) { "Verified sessions must fit the done total" }
        require(manual in 0..done) { "Manual completions must fit the done total" }
        require(legacy in 0..done) { "Legacy completions must fit the done total" }
        require(skipped in 0..planned) { "Skipped sessions must fit the planned total" }
    }

    /** Null when the week had no planned session. */
    val completionRatio: Double?
        get() = if (planned == 0) null else done.toDouble() / planned

    /** Share of planned sessions backed by strong evidence (activity or guided). */
    val verifiedRatio: Double?
        get() = if (planned == 0) null else verified.toDouble() / planned
}

/**
 * Planned versus executed duration. The percentage only exists when both sides
 * do, so a missing plan or a missing run is never reported as zero.
 */
data class WeeklyVolume(val plannedMin: Int?, val executedMin: Int?, val percentOfPlan: Int?) {
    init {
        require(plannedMin == null || plannedMin > 0) { "Planned volume must be positive when present" }
        require(executedMin == null || executedMin > 0) { "Executed volume must be positive when present" }
        require(percentOfPlan == null || percentOfPlan >= 0) { "Volume percentage must not be negative" }
        require((percentOfPlan != null) == (plannedMin != null && executedMin != null)) {
            "Volume percentage requires both planned and executed durations"
        }
    }
}

/** Readiness coverage of the week, reconstructed day by day. */
data class WeeklyReadiness(val measuredDays: Int, val averageScore: Int?, val redDays: Int) {
    init {
        require(measuredDays in 1..DAYS_IN_WEEK) { "Readiness days must fit the week" }
        require(averageScore == null || averageScore in 0..100) { "Average readiness must be a score" }
        require(redDays in 0..measuredDays) { "Red days must fit the measured days" }
    }
}

/** Sleep coverage of the week; `shortNights` counts nights below `R-READY-04`'s threshold. */
data class WeeklySleep(val measuredNights: Int, val averageMinutes: Int?, val shortNights: Int) {
    init {
        require(measuredNights in 1..DAYS_IN_WEEK) { "Sleep nights must fit the week" }
        require(averageMinutes == null || averageMinutes in 0..MINUTES_IN_DAY) {
            "Average sleep must be a duration"
        }
        require(shortNights in 0..measuredNights) { "Short nights must fit the measured nights" }
    }
}

enum class WeeklyReviewObservationKind {
    /** A check-in reported illness or injury; safety comes before adherence. */
    ILLNESS_SIGNAL,

    /** Repeated nights below the sleep threshold (`R-READY-04`). */
    SHORT_SLEEP,

    /** Repeated red readiness days (`R-READY-03`). */
    LOW_READINESS,

    /** Some planned sessions were completed, some were not. */
    ADHERENCE_PARTIAL,

    /** The week had planned sessions and none was completed. */
    ADHERENCE_NONE,

    /** Executed volume stayed well below the plan. */
    VOLUME_BELOW_PLAN,

    /** Executed volume went well above the plan. */
    VOLUME_ABOVE_PLAN,

    /** Measured time spent above the easy zone crossed the 80/20 heuristic. */
    INTENSITY_ABOVE_EASY_TARGET,

    /** Nothing to flag: the week stayed inside the expected ranges. */
    NO_ALERTS,
}

/**
 * One deterministic reading of the week. Observations are descriptive facts
 * about persisted data, never a grade, a prescription or a promise.
 *
 * `evidenceIds` is empty for observations about the athlete's own persisted
 * record (adherence/volume): only physiological claims cite a source.
 */
data class WeeklyReviewObservation(
    val kind: WeeklyReviewObservationKind,
    val ruleId: String,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
) {
    init {
        require(ruleId.isNotBlank()) { "Observation must cite a rule" }
    }
}

/**
 * Consolidation of one calendar week (Monday–Sunday) for the weekly review
 * (FR-011). Every section is optional and only exists when its input exists:
 * absence is reported as absence, never as zero.
 */
data class WeeklyReview(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val status: WeeklyReviewStatus,
    val adherence: WeeklyAdherence?,
    val volume: WeeklyVolume?,
    val intensity: WeeklyIntensity?,
    val intensitySessions: Int,
    val readiness: WeeklyReadiness?,
    val sleep: WeeklySleep?,
    val observations: List<WeeklyReviewObservation>,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
) {
    init {
        require(weekEnd == weekStart.plusDays(6)) { "Review must cover exactly one Monday–Sunday week" }
        require(intensitySessions >= 0) { "Intensity sessions must not be negative" }
        require((intensity == null) == (intensitySessions == 0)) {
            "Measured intensity and its session count must be present together"
        }
        require(intensity == null || intensity.weekStart == weekStart) {
            "Intensity must belong to the reviewed week"
        }
        require(observations.size <= WeeklyReviewCalculator.MAX_OBSERVATIONS) {
            "A review reports at most ${WeeklyReviewCalculator.MAX_OBSERVATIONS} observations"
        }
        require(status != WeeklyReviewStatus.EMPTY_NO_DATA || observations.isEmpty()) {
            "An empty review has nothing to observe"
        }
        require(status != WeeklyReviewStatus.EMPTY_NO_DATA || adherence == null) {
            "An empty review has no adherence"
        }
    }
}

/**
 * Builds the weekly review from already-computed inputs (FR-011).
 *
 * The calculator never re-reads a repository, never recalculates a target and
 * never consults an LLM: it consolidates adherence, volume, measured intensity
 * and the health record of the week, then selects at most three deterministic
 * observations — safety and recovery first, then adherence, volume and
 * intensity. Thresholds are documented as product conventions in ADR 0034.
 */
private const val DAYS_IN_WEEK = 7
private const val MINUTES_IN_DAY = 1_440

class WeeklyReviewCalculator {
    fun calculate(
        weekStart: LocalDate,
        today: LocalDate,
        hasPlan: Boolean = false,
        completion: PlanCompletionWeek? = null,
        plannedDurationMin: Int? = null,
        executedDurationMin: Int? = null,
        intensity: WeeklyIntensity? = null,
        intensitySessions: Int = 0,
        readinessDays: List<ReadinessHistoryDay> = emptyList(),
        metrics: List<DailyMetrics> = emptyList(),
        checkIns: List<DailyCheckIn> = emptyList(),
    ): WeeklyReview {
        require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "Week must start on Monday" }
        require(!today.isBefore(weekStart)) { "Review cannot be built before the week starts" }
        require(plannedDurationMin == null || plannedDurationMin > 0) {
            "Planned volume must be positive when present"
        }
        require(executedDurationMin == null || executedDurationMin > 0) {
            "Executed volume must be positive when present"
        }
        require(intensitySessions >= 0) { "Intensity sessions must not be negative" }
        require(intensity == null || intensitySessions > 0) { "Measured intensity requires its sessions" }
        require(intensity != null || intensitySessions == 0) { "Intensity sessions require measured intensity" }
        require(intensity == null || intensity.weekStart == weekStart) {
            "Intensity must belong to the reviewed week"
        }
        require(!hasPlan || completion != null) { "A week with a plan requires its completion" }
        require(completion == null || completion.weekStart == weekStart) {
            "Completion must belong to the reviewed week"
        }
        require(hasPlan || completion == null || completion.planned == 0) {
            "A completion with planned sessions requires a plan"
        }

        val weekEnd = weekStart.plusDays(6)
        val effectiveEnd = minOf(weekEnd, today)

        fun LocalDate.inWeek(): Boolean = !isBefore(weekStart) && !isAfter(effectiveEnd)

        val readiness = readinessDays
            .filter { it.date.inWeek() }
            .associateBy { it.date }
            .values
            .toList()
        val readinessSummary = readiness.takeIf { it.isNotEmpty() }?.let { days ->
            WeeklyReadiness(
                measuredDays = days.size,
                averageScore = days.map { it.score }.average().roundToInt(),
                redDays = days.count { it.level == ReadinessLevel.RED },
            )
        }

        // The last entry wins when the same date arrives twice; repositories
        // return one merged row per day, so this only guards a duplicated input.
        val sleepMinutes = metrics
            .filter { it.date.inWeek() && it.sleepDurationMin != null }
            .associateBy { it.date }
            .values
            .mapNotNull { it.sleepDurationMin }
        val sleep = sleepMinutes.takeIf { it.isNotEmpty() }?.let { nights ->
            WeeklySleep(
                measuredNights = nights.size,
                averageMinutes = nights.average().roundToInt(),
                shortNights = nights.count { it < SHORT_SLEEP_MINUTES },
            )
        }

        val illness = checkIns
            .filter { it.date.inWeek() }
            .associateBy { it.date }
            .values
            .any { it.illnessOrInjury }

        val adherence = if (hasPlan) {
            completion?.let {
                WeeklyAdherence(
                    planned = it.planned,
                    done = it.done,
                    verified = it.verified,
                    manual = it.manual,
                    legacy = it.legacy,
                    skipped = it.skipped,
                )
            }
        } else {
            null
        }

        val volume = if (plannedDurationMin == null && executedDurationMin == null) {
            null
        } else {
            WeeklyVolume(
                plannedMin = plannedDurationMin,
                executedMin = executedDurationMin,
                percentOfPlan = if (plannedDurationMin != null && executedDurationMin != null) {
                    (executedDurationMin * 100.0 / plannedDurationMin).roundToInt()
                } else {
                    null
                },
            )
        }

        val status = when {
            !hasPlan && plannedDurationMin == null && executedDurationMin == null && intensity == null &&
                readinessSummary == null && sleep == null && !illness ->
                WeeklyReviewStatus.EMPTY_NO_DATA

            !hasPlan -> WeeklyReviewStatus.EMPTY_NO_PLAN

            else -> WeeklyReviewStatus.AVAILABLE
        }

        val confidence = confidenceFor(
            status = status,
            adherenceAvailable = adherence?.completionRatio != null,
            hasIntensity = intensity != null,
            readinessDays = readinessSummary?.measuredDays ?: 0,
            sleepNights = sleep?.measuredNights ?: 0,
        )

        val alerts = buildList {
            if (illness) {
                add(observation(WeeklyReviewObservationKind.ILLNESS_SIGNAL, "R-SAFE-01", ILLNESS_EVIDENCE))
            }
            if (sleep != null && sleep.shortNights >= SHORT_SLEEP_NIGHTS) {
                add(
                    observation(
                        kind = WeeklyReviewObservationKind.SHORT_SLEEP,
                        ruleId = "R-READY-04",
                        evidenceIds = SLEEP_EVIDENCE,
                        confidence = if (sleep.measuredNights >= MIN_COVERAGE_DAYS) {
                            DataConfidence.MEDIUM
                        } else {
                            DataConfidence.LOW
                        },
                    ),
                )
            }
            if (readinessSummary != null && readinessSummary.redDays >= LOW_READINESS_DAYS) {
                add(
                    observation(
                        kind = WeeklyReviewObservationKind.LOW_READINESS,
                        ruleId = "R-READY-03",
                        evidenceIds = READINESS_EVIDENCE,
                        confidence = if (readinessSummary.measuredDays >= MIN_COVERAGE_DAYS) {
                            DataConfidence.MEDIUM
                        } else {
                            DataConfidence.LOW
                        },
                    ),
                )
            }
            adherence?.completionRatio?.let { ratio ->
                when {
                    adherence.done == 0 && adherence.planned > 0 ->
                        add(observation(WeeklyReviewObservationKind.ADHERENCE_NONE))

                    ratio < ADHERENCE_FULL_RATIO ->
                        add(observation(WeeklyReviewObservationKind.ADHERENCE_PARTIAL))
                }
            }
            volume?.percentOfPlan?.let { percent ->
                when {
                    percent < VOLUME_BELOW_PERCENT ->
                        add(observation(WeeklyReviewObservationKind.VOLUME_BELOW_PLAN))

                    percent > VOLUME_ABOVE_PERCENT ->
                        add(observation(WeeklyReviewObservationKind.VOLUME_ABOVE_PLAN))
                }
            }
            if (intensity != null && intensitySessions >= MIN_INTENSITY_SESSIONS &&
                intensity.easyPercent() < EASY_TARGET_PERCENT
            ) {
                add(
                    observation(
                        kind = WeeklyReviewObservationKind.INTENSITY_ABOVE_EASY_TARGET,
                        ruleId = "R-PLAN-06",
                        evidenceIds = INTENSITY_EVIDENCE,
                    ),
                )
            }
        }.take(MAX_OBSERVATIONS)

        val observations = when {
            alerts.isNotEmpty() -> alerts

            status == WeeklyReviewStatus.AVAILABLE ->
                listOf(observation(WeeklyReviewObservationKind.NO_ALERTS, confidence = confidence))

            else -> emptyList()
        }

        return WeeklyReview(
            weekStart = weekStart,
            weekEnd = weekEnd,
            status = status,
            adherence = adherence,
            volume = volume,
            intensity = intensity,
            intensitySessions = intensitySessions,
            readiness = readinessSummary,
            sleep = sleep,
            observations = observations,
            ruleIds = if (status == WeeklyReviewStatus.EMPTY_NO_DATA) {
                emptyList()
            } else {
                (listOf(RULE_ID) + observations.map { it.ruleId }).distinct()
            },
            evidenceIds = observations.flatMap { it.evidenceIds }.distinct(),
            confidence = confidence,
        )
    }

    private fun observation(
        kind: WeeklyReviewObservationKind,
        ruleId: String = RULE_ID,
        evidenceIds: List<String> = emptyList(),
        confidence: DataConfidence = DataConfidence.HIGH,
    ) = WeeklyReviewObservation(kind = kind, ruleId = ruleId, evidenceIds = evidenceIds, confidence = confidence)

    private fun confidenceFor(
        status: WeeklyReviewStatus,
        adherenceAvailable: Boolean,
        hasIntensity: Boolean,
        readinessDays: Int,
        sleepNights: Int,
    ): DataConfidence {
        if (status == WeeklyReviewStatus.EMPTY_NO_DATA) return DataConfidence.LOW
        val blocks = listOf(
            adherenceAvailable,
            hasIntensity,
            readinessDays >= MIN_COVERAGE_DAYS,
            sleepNights >= MIN_COVERAGE_DAYS,
        ).count { it }
        return when {
            blocks >= 3 -> DataConfidence.HIGH
            blocks == 2 -> DataConfidence.MEDIUM
            else -> DataConfidence.LOW
        }
    }

    companion object {
        /** Consolidation policy, not a physiological claim (ADR 0034). */
        const val RULE_ID = "R-REVIEW-01"

        /** At most three actionable readings; safety comes first. */
        const val MAX_OBSERVATIONS = 3

        /** `R-READY-04`: three days below 6 h 30 min block volume progression. */
        const val SHORT_SLEEP_MINUTES = 390
        const val SHORT_SLEEP_NIGHTS = 3

        /** `R-READY-03`: repeated red days are a recovery signal, not a score. */
        const val LOW_READINESS_DAYS = 3

        /** Fewer sessions would make the 80/20 split noise. */
        const val MIN_INTENSITY_SESSIONS = 3

        /** `R-PLAN-06`: the easy share heuristic this review reports against. */
        const val EASY_TARGET_PERCENT = 80.0

        /** Product band for volume against the plan (ADR 0034). */
        const val VOLUME_BELOW_PERCENT = 70
        const val VOLUME_ABOVE_PERCENT = 120

        /** Adherence at or above this share is reported without an observation. */
        const val ADHERENCE_FULL_RATIO = 0.8

        /** Days/nights needed to treat a section as covered rather than anecdotal. */
        const val MIN_COVERAGE_DAYS = 3

        private val ILLNESS_EVIDENCE = listOf("EV-SAW-2016")
        private val SLEEP_EVIDENCE = listOf("EV-WALSH-2021", "EV-WATSON-2015")
        private val READINESS_EVIDENCE = listOf("EV-SAW-2016")
        private val INTENSITY_EVIDENCE = listOf("EV-SEILER-2010")
    }
}
