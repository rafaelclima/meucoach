package com.meucoach.core.domain.replan

import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import java.time.LocalDate

/**
 * Deterministic triggers for revising the current week (ADR 0028, R-01).
 *
 * T1 and T2 are always evaluated; [USER_REQUEST] (T3) forces evaluation so the
 * explicit "replan my week" action never depends on a detected deviation.
 */
enum class ReplanTrigger {
    /** T1 — eligible past sessions stayed `PLANNED`. Counting them never closes them. */
    MISSED_SESSIONS,

    /** T2 — the current availability differs from the stored baseline hash. */
    AVAILABILITY_CHANGED,

    /** T3 — the user explicitly asked to replan the current week. */
    USER_REQUEST,
}

/**
 * A revision the user may accept. It never applies itself: the caller must
 * revalidate [expectedVersion]/hashes before persisting (R-02).
 */
data class ReplanSuggestion(
    val weekStart: LocalDate,
    val trigger: ReplanTrigger,
    /** Eligible past `PLANNED` sessions that motivated T1; product heuristic, not science. */
    val missedTrainableCount: Int,
    val expectedVersion: Int,
    /** Hash of every input used to build the candidate. */
    val expectedInputsHash: String,
    /** Stored availability hash at evaluation time; `null` when the baseline was unknown. */
    val expectedAvailabilityHash: String?,
    /** Availability hash the candidate was computed against; persist it after acceptance. */
    val availabilityHash: String,
    val rationale: String,
    /** Rules actually emitted by the regenerated future; never new physiological rules. */
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
) {
    init {
        require(missedTrainableCount >= 0) { "Missed session count must not be negative" }
        require(expectedVersion >= 1) { "Expected version must be >= 1" }
        require(rationale.isNotBlank()) { "Suggestion must have a rationale" }
        require(ruleIds.isNotEmpty()) { "Suggestion must cite at least one rule" }
        require(evidenceIds.isNotEmpty()) { "Suggestion must cite at least one evidence" }
    }
}

sealed interface ReplanResult {
    /** [candidate] is the merged full-week plan, not only the regenerated future sessions. */
    data class Suggest(val suggestion: ReplanSuggestion, val candidate: WeeklyPlan) : ReplanResult

    data class NoChange(
        val reason: String,
        /** Current availability hash; safe to persist as the baseline even without a revision. */
        val availabilityHash: String,
    ) : ReplanResult {
        init {
            require(reason.isNotBlank()) { "No-change reason must not be blank" }
        }
    }
}

/**
 * Inputs of a pure replanning evaluation.
 *
 * [currentPlan] is the plan of the week that contains [today]; only its strictly
 * future `PLANNED` sessions may be replaced. [nonTrainablePastDates] carries days
 * already known as rest/illness so they never count as missed work.
 */
data class ReplanInput(
    val currentPlan: WeeklyPlan,
    val currentVersion: Int,
    /** Persisted hash of the current version, when it exists; R-02 compares it at apply time. */
    val currentInputsHash: String? = null,
    /** Persisted availability baseline; `null` means "unknown, do not infer a historical change". */
    val currentAvailabilityHash: String? = null,
    val trigger: ReplanTrigger,
    val profile: AthleteProfile,
    val readiness: ReadinessResult,
    val load: LoadSnapshot? = null,
    val previousWeek: WeeklyPlan? = null,
    val weekNumberInBlock: Int = 1,
    val nonTrainablePastDates: Set<LocalDate> = emptySet(),
    /** Product heuristic: how many eligible missed sessions trigger T1. */
    val missedSessionThreshold: Int = PlanReplanner.PRODUCT_MISSED_SESSION_THRESHOLD,
    val today: LocalDate,
) {
    init {
        require(currentVersion >= 1) { "Current plan version must be >= 1" }
        require(missedSessionThreshold >= 1) { "Missed session threshold must be >= 1" }
        require(weekNumberInBlock >= 1) { "Week number in block must be >= 1" }
    }
}
