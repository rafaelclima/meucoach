package com.meucoach.core.domain.prescription

import com.meucoach.core.model.DataConfidence
import java.time.LocalDate

/** What the day decision did with the weekly plan intent. */
enum class PrescriptionAdjustment {
    /** Plan kept: type, duration and targets are the same. */
    KEEP,

    /** Same type with a shorter duration. */
    REDUCE,

    /** Strong session turned into an easier/shorter one. */
    DOWNGRADE,

    /** No training today. */
    REST,

    /** There was no plan for today; a conservative easy day was prescribed. */
    NO_PLAN,
}

/**
 * The single authority of a training day (ADR 0023).
 *
 * It preserves the plan intent ([plannedType], [plannedDurationMin]) and states
 * what was actually prescribed, with the adjustment, version and auditable
 * rule/evidence IDs. The UI never recalculates it.
 */
data class DailyPrescription(
    val date: LocalDate,
    val plannedSessionId: String?,
    val plannedType: WorkoutType?,
    val plannedTitle: String?,
    val plannedDurationMin: Int?,
    val prescribedType: WorkoutType,
    val title: String,
    val prescribedDurationMin: Int?,
    val targets: PrescriptionTargets,
    val adjustment: PrescriptionAdjustment,
    val rationale: String,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
    val engineVersion: String,
    val inputsHash: String,
    /**
     * Why the day did not confirm a planned quality (ADR 0044 §2.4/§3.1).
     * Null when there was no quality opportunity or when it was kept.
     */
    val qualityBlockReason: QualityBlockReason? = null,
    /** The value that failed, e.g. "ACWR 1,62 > 1,50" or "check-in ...". */
    val decisionDetail: String? = null,
    /** What unlocks the strong session, e.g. "Faça o check-in de hoje...". */
    val unlockHint: String? = null,
    /** Persistence id; null for a decision that was just computed and not stored yet. */
    val id: String? = null,
    val supersedesId: String? = null,
) {
    init {
        require(title.isNotBlank()) { "Prescription title must not be blank" }
        require(rationale.isNotBlank()) { "Prescription must have a rationale" }
        require(ruleIds.isNotEmpty()) { "Prescription must cite at least one rule" }
        require(evidenceIds.isNotEmpty()) { "Prescription must cite at least one evidence" }
        require(engineVersion.isNotBlank()) { "Engine version must not be blank" }
        require(inputsHash.isNotBlank()) { "Inputs hash must not be blank" }
        require(
            prescribedType != WorkoutType.REST || prescribedDurationMin == null,
        ) { "A rest prescription must not have a duration" }
        require(prescribedDurationMin == null || prescribedDurationMin > 0) {
            "Prescribed duration must be positive"
        }
        require(
            plannedDurationMin == null || prescribedDurationMin == null ||
                prescribedDurationMin <= plannedDurationMin,
        ) { "Prescription must never increase the planned duration" }
        require(qualityBlockReason == null || plannedType == WorkoutType.QUALITY) {
            "Only a planned quality can carry a quality block reason"
        }
        require(qualityBlockReason == null || prescribedType != WorkoutType.QUALITY) {
            "A blocked quality cannot be prescribed as quality"
        }
        require((qualityBlockReason == null) == (decisionDetail == null)) {
            "A quality block reason and its detail must come together"
        }
        require((qualityBlockReason == null) == (unlockHint == null)) {
            "A quality block reason and its unlock hint must come together"
        }
    }

    val isRest: Boolean get() = prescribedType == WorkoutType.REST

    /** The day differs from the plan intent; the UI shows "ajustado hoje". */
    val differsFromPlan: Boolean
        get() = plannedType != null && (
            adjustment != PrescriptionAdjustment.KEEP ||
                plannedDurationMin != prescribedDurationMin ||
                plannedType != prescribedType
            )

    companion object {
        const val COLLECTION = "daily_prescriptions"
    }
}

/**
 * C4-31 (ADR 0044 §2.5): the day restored a planned quality that an earlier
 * version downgraded — EASY back to QUALITY before the session was done.
 * Versions must be chronological (oldest first), as persisted by
 * `DailyPrescriptionRepository`. The UI shows "Atualizado: com o check-in, a
 * qualidade foi liberada" only in this case.
 */
fun List<DailyPrescription>.hasRestoredQuality(): Boolean {
    if (size < 2) return false
    val latest = last()
    if (latest.adjustment != PrescriptionAdjustment.KEEP ||
        latest.prescribedType != WorkoutType.QUALITY
    ) {
        return false
    }
    return any {
        it.adjustment == PrescriptionAdjustment.DOWNGRADE &&
            it.plannedType == WorkoutType.QUALITY &&
            it.prescribedType == WorkoutType.EASY
    }
}
