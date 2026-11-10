package com.meucoach.core.domain.briefing

import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import java.time.LocalDate

/**
 * Read-only briefing of the day, derived from the persisted DailyPrescription (C3-03).
 *
 * Every number is copied from the prescription: the briefing never recalculates a
 * target, never adds a block/interval/warm-up and never rewrites a finished day.
 * The rule/evidence IDs stay in the contract for audit and are not part of the
 * common UI surface.
 */
data class SessionBriefing(
    val date: LocalDate,
    val title: String,
    val workoutType: WorkoutType,
    val durationMin: Int?,
    val targets: PrescriptionTargets,
    val rationale: String,
    val confidence: DataConfidence,
    /** Persisted prescription IDs plus the stop rule (`R-SAFE-01`) for the hard-day guidance. */
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    /** Generic guidance compatible with the prescribed type and targets. */
    val howToRun: String,
    /** Conservative guidance without numbers or an unevaluated alternative workout. */
    val ifHard: String,
    /** Identifies the briefing template version; the text itself is not persisted. */
    val engineVersion: String = SessionBriefingEngine.ENGINE_VERSION,
) {
    init {
        require(title.isNotBlank()) { "Briefing title must not be blank" }
        require(rationale.isNotBlank()) { "Briefing must have a rationale" }
        require(ruleIds.isNotEmpty()) { "Briefing must cite at least one rule" }
        require(evidenceIds.isNotEmpty()) { "Briefing must cite at least one evidence" }
        require(howToRun.isNotBlank()) { "Briefing must explain how to run" }
        require(ifHard.isNotBlank()) { "Briefing must explain what to do when it is hard" }
        require(engineVersion.isNotBlank()) { "Briefing engine version must not be blank" }
    }

    val isRest: Boolean get() = workoutType == WorkoutType.REST
}
