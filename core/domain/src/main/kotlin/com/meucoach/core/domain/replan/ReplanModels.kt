package com.meucoach.core.domain.replan

import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.RacePlan
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

    /** T4 — the planning fingerprint or temporal context differs from the evaluated checkpoint. */
    PROFILE_CHANGED,
}

/** Every reason that contributed to a revision; a suggestion may have more than one. */
enum class ReplanCause {
    PROFILE_CHANGED,
    TIMEZONE_CONTEXT_CHANGED,
    MISSED_SESSIONS,
    AVAILABILITY_CHANGED,
    USER_REQUEST,
}

/**
 * A revision the user may accept. It never applies itself: the caller must
 * revalidate [expectedVersion]/hashes before persisting (R-02).
 */
data class ReplanSuggestion(
    val weekStart: LocalDate,
    val trigger: ReplanTrigger,
    /** All concurrent causes, so copy never attributes a change to a single factor. */
    val causes: List<ReplanCause>,
    /** Eligible past `PLANNED` sessions that motivated T1; product heuristic, not science. */
    val missedTrainableCount: Int,
    val expectedVersion: Int,
    /** Hash of every input used to build the candidate. */
    val expectedInputsHash: String,
    /** Stored availability hash at evaluation time; `null` when the baseline was unknown. */
    val expectedAvailabilityHash: String?,
    /** Availability hash the candidate was computed against; persist it after acceptance. */
    val availabilityHash: String,
    /** Planning fingerprint the candidate was computed against (ADR 0031). */
    val expectedFingerprintVersion: String,
    val expectedFingerprint: String,
    val profileSnapshot: PlanningProfileSnapshot,
    /** Temporal context of the coherent snapshot used to evaluate the week. */
    val expectedTemporalContextHash: String,
    val rationale: String,
    /** Rules actually emitted by the regenerated future; never new physiological rules. */
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
) {
    init {
        require(causes.isNotEmpty()) { "Suggestion must record at least one cause" }
        require(missedTrainableCount >= 0) { "Missed session count must not be negative" }
        require(expectedVersion >= 1) { "Expected version must be >= 1" }
        require(expectedFingerprintVersion.isNotBlank()) { "Fingerprint version must not be blank" }
        require(expectedFingerprint.isNotBlank()) { "Fingerprint must not be blank" }
        require(expectedTemporalContextHash.isNotBlank()) { "Temporal context hash must not be blank" }
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
        /** Evaluated planning fingerprint/context, persisted as the checkpoint without a revision. */
        val checkpoint: PlanEvaluationCheckpoint,
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
    /** Last evaluated fingerprint checkpoint; both fields `null` on a legacy plan (ADR 0031). */
    val currentEvaluatedFingerprintVersion: String? = null,
    val currentEvaluatedFingerprint: String? = null,
    /** Persisted temporal context checkpoint; `null` on a legacy plan. */
    val currentTemporalContextHash: String? = null,
    val trigger: ReplanTrigger,
    val profile: AthleteProfile,
    /** Current planning fingerprint of [profile]; computed by default so callers cannot forget it. */
    val planningFingerprint: PlanningFingerprint = PlanningFingerprints.of(profile),
    /** Current temporal context derived from the persisted profile timezone. */
    val temporalContextHash: String = PlanningFingerprints.temporalContextHash(profile),
    val readiness: ReadinessResult,
    val load: LoadSnapshot? = null,
    val previousWeek: WeeklyPlan? = null,
    val weekNumberInBlock: Int = 1,
    val nonTrainablePastDates: Set<LocalDate> = emptySet(),
    /** Active race for taper/cutoff; falls back to the legacy profile goal when absent. */
    val race: RacePlan? = null,
    /** Every event date that must not receive an ordinary session. */
    val eventDates: Set<LocalDate> = emptySet(),
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
