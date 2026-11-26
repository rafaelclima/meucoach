package com.meucoach.core.domain.prescription

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** Where a taper baseline comes from (ADR 0066 §2.1). */
enum class TaperBaselineSource {
    AVERAGE_3_WEEKS,
    FALLBACK_MAX_RECENT,
    NO_HISTORY,
}

/**
 * Taper reference: the average of three eligible weeks, a named fallback to
 * the largest recent strong week, or no numeric baseline at all.
 *
 * The baseline is its own reference and never replaces the weekly progression
 * base of ADR 0038/0050.
 */
data class TaperBaseline(
    val minutes: Int?,
    val source: TaperBaselineSource,
    val weekStarts: List<LocalDate> = emptyList(),
) {
    init {
        require((minutes == null) == (source == TaperBaselineSource.NO_HISTORY)) {
            "Only NO_HISTORY has a null baseline"
        }
        require(minutes == null || minutes > 0) { "Taper baseline must be positive" }
        require(
            minutes != null ||
                weekStarts.isEmpty(),
        ) { "A missing baseline cannot name source weeks" }
    }
}

/**
 * Resolves the taper baseline from the same eligible weeks as the volume base
 * (ADR 0050): planned from its own Monday, normal (no deload/taper/race/
 * recovery), with a plan and real strong minutes. Special weeks are skipped and
 * the window walks back until three eligible weeks are found or history ends.
 */
object TaperBaselineResolver {
    fun resolve(references: List<VolumeReference>, firstTaperWeekStart: LocalDate): TaperBaseline {
        require(firstTaperWeekStart.dayOfWeek == DayOfWeek.MONDAY) {
            "First taper week must start on Monday"
        }
        val eligible = references
            .filter { it.isEligibleTaperReference(firstTaperWeekStart) }
            .sortedByDescending { it.weekStart }
            .take(MAX_BASELINE_WEEKS)
        return when {
            eligible.size == MAX_BASELINE_WEEKS -> TaperBaseline(
                minutes = (eligible.sumOf { it.strongMinutes }.toDouble() / MAX_BASELINE_WEEKS)
                    .roundToInt(),
                source = TaperBaselineSource.AVERAGE_3_WEEKS,
                weekStarts = eligible.map { it.weekStart },
            )

            eligible.isNotEmpty() -> TaperBaseline(
                minutes = eligible.maxOf { it.strongMinutes },
                source = TaperBaselineSource.FALLBACK_MAX_RECENT,
                weekStarts = eligible.map { it.weekStart },
            )

            else -> TaperBaseline(minutes = null, source = TaperBaselineSource.NO_HISTORY)
        }
    }

    private fun VolumeReference.isEligibleTaperReference(firstTaperWeekStart: LocalDate): Boolean {
        if (!weekStart.isBefore(firstTaperWeekStart)) return false
        if (createdOn != weekStart) return false
        if (specialWeek) return false
        if (!hasPlan) return false
        if (plannedSessions == 0) return false
        if (strongMinutes <= 0) return false
        return true
    }

    private const val MAX_BASELINE_WEEKS = 3
}
