package com.meucoach.core.domain.review

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.WorkoutType
import java.time.LocalDate

/**
 * What the days decided about the planned quality opportunities of a closed
 * week (FR-401/FR-408, ADR 0044/0050).
 *
 * The plan owns the opportunities ([planned]); each day confirms or downgrades
 * its own ([kept]/[downgraded]). A week without stored day decisions reports
 * no decision instead of inventing one — absence is absence (FR-011).
 */
data class WeeklyQuality(
    val planned: Int,
    val kept: Int,
    val downgraded: Int,
    /** The value that failed on the first downgraded day, e.g. the check-in note. */
    val firstDowngradeDetail: String?,
) {
    init {
        require(planned >= 0) { "Planned qualities must not be negative" }
        require(kept >= 0) { "Kept qualities must not be negative" }
        require(downgraded >= 0) { "Downgraded qualities must not be negative" }
        require(kept + downgraded <= planned) { "Decided qualities must fit the planned opportunities" }
        require((downgraded == 0) == (firstDowngradeDetail == null)) {
            "A downgrade detail requires a downgrade"
        }
    }

    /** At least one opportunity got a stored day decision. */
    val hasDecision: Boolean get() = kept + downgraded > 0
}

/**
 * Summarizes the week from persisted decisions only. The latest prescription
 * of each planned quality date decides: a prescribed `QUALITY` keeps the
 * opportunity, a `qualityBlockReason` downgrades it. Dates without a stored
 * prescription stay undecided.
 */
object QualityWeekSummary {
    fun summarize(qualityDates: List<LocalDate>, latestByDate: Map<LocalDate, DailyPrescription>): WeeklyQuality {
        val ordered = qualityDates.sorted()
        var kept = 0
        var downgraded = 0
        var firstDetail: String? = null
        ordered.forEach { date ->
            val prescription = latestByDate[date] ?: return@forEach
            if (prescription.qualityBlockReason != null) {
                downgraded++
                if (firstDetail == null) firstDetail = prescription.decisionDetail
            } else if (prescription.prescribedType == WorkoutType.QUALITY) {
                kept++
            }
        }
        return WeeklyQuality(
            planned = ordered.size,
            kept = kept,
            downgraded = downgraded,
            firstDowngradeDetail = firstDetail,
        )
    }
}
