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
