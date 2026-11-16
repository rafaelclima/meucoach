package com.meucoach.core.domain.prescription

import java.time.LocalDate

/**
 * Why the weekly plan created or withheld a strong opportunity (PRD §8.3).
 *
 * The codes are persisted by C4-50; the detail carries the human-readable
 * cause. The plan never changes an opportunity without a decision.
 */
enum class PlanDecisionReason {
    QUALITY_OPPORTUNITY,
    QUALITY_BLOCKED_BEGINNER,
    QUALITY_BLOCKED_DAYS,
    QUALITY_BLOCKED_SAFE_DAY,
    PROGRESSION_COMPLETED,
    PROGRESSION_BLOCKED_COMPLETION,
    PROGRESSION_BLOCKED_READINESS,
    PROGRESSION_BLOCKED_LOAD,
    PROGRESSION_BLOCKED_ACWR,
    PROGRESSION_BLOCKED_MONOTONY,
    PROGRESSION_BLOCKED_STRAIN,
    VOLUME_MAINTAINED,
    VOLUME_CAPPED_BY_AVAILABILITY,
    LONG_DAY_TOO_SHORT,
}

/** Auditable plan decision: a code plus the value/context that produced it. */
data class PlanDecision(val reason: PlanDecisionReason, val detail: String? = null) {
    init {
        require(detail == null || detail.isNotBlank()) { "Plan decision detail must not be blank" }
    }
}

/**
 * Read contract over a planned `QUALITY` session (ADR 0044 §2.2, PRD §5.2).
 *
 * The opportunity is structural: it never carries the readiness of the week's
 * first day. The day confirms or downgrades it. Strides are a micro-dose, not
 * a main opportunity.
 */
data class QualityOpportunity(
    val date: LocalDate,
    val kind: QualityKind,
    val structuralReason: String,
    val status: SessionStatus,
    val planDecision: PlanDecision? = null,
) {
    init {
        require(kind != QualityKind.STRIDES_ONLY) {
            "Strides are an easy micro-dose, not a quality opportunity"
        }
        require(structuralReason.isNotBlank()) { "Opportunity must explain its structure" }
    }

    /** Planned opportunities are still owned by the day; done/closed are history. */
    val isOpen: Boolean get() = status == SessionStatus.PLANNED

    companion object {
        /** Returns `null` for legacy quality rows without a kind and for strides. */
        fun from(session: PlannedSession): QualityOpportunity? {
            if (session.type != WorkoutType.QUALITY) return null
            val kind = session.qualityKind ?: return null
            if (kind == QualityKind.STRIDES_ONLY) return null
            return QualityOpportunity(
                date = session.date,
                kind = kind,
                structuralReason = session.rationale,
                status = session.status,
                planDecision = session.planDecision,
            )
        }
    }
}

/**
 * Day-level reason a planned opportunity was not kept (ADR 0044 §2.4).
 *
 * Eligibility is represented by a `null` reason, so there is no `NONE` value:
 * a single representation prevents callers from disagreeing with [QualityEligibility.eligible].
 */
enum class QualityBlockReason {
    ILLNESS_OR_RED_READINESS,
    BEGINNER_GUARD,
    SUBJECTIVE_MISSING,
    SHORT_SLEEP,
    RECOVERY_WINDOW,
    TAPER_WINDOW,
    RACE_WEEK,
    LOAD_STALE,
    LOAD_INSUFFICIENT,
    MONOTONY_HIGH,
    STRAIN_SPIKE,
    ACWR_HIGH,
}

/**
 * The single answer of the day-level quality gate (ADR 0044 §2.4).
 *
 * A block always names the failing reason, the value that failed, one rule,
 * at least one evidence and an unlock hint. An eligible opportunity has no
 * reason, detail or hint: the day keeps the plan.
 */
data class QualityEligibility(
    val eligible: Boolean,
    val reason: QualityBlockReason?,
    val detail: String?,
    val ruleId: String,
    val evidenceIds: List<String>,
    val unlockHint: String?,
) {
    init {
        require(eligible == (reason == null)) {
            "Eligibility and block reason must agree"
        }
        require(ruleId.isNotBlank()) { "Eligibility must cite a rule" }
        require(evidenceIds.isNotEmpty()) { "Eligibility must cite at least one evidence" }
        require(eligible || !detail.isNullOrBlank()) {
            "A blocked opportunity must explain the failing value"
        }
        require(eligible || !unlockHint.isNullOrBlank()) {
            "A blocked opportunity must say what unlocks it"
        }
    }
}
