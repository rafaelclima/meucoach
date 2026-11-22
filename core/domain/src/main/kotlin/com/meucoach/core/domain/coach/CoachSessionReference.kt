package com.meucoach.core.domain.coach

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WorkoutType

/**
 * Deterministic training targets for the Coach answers (ADR 0057).
 *
 * Pace and heart-rate numbers only ever come from here — rendered by the UI
 * as a "Referência do motor" card, never written by the LLM. The validator
 * keeps barring every pace/bpm in prose; this card bypasses prose entirely,
 * so there is nothing to misquote, invert or invent.
 */
object CoachSessionReference {
    const val TITLE = "Referência do motor"

    /**
     * One line per decided target, preferring the persisted prescription
     * (today) over the plan intent. Empty when there is nothing numeric to
     * show (rest, RPE-less plans) — the caller renders no card then.
     */
    fun lines(session: PlannedSession, prescription: DailyPrescription?): List<String> {
        if (session.type == WorkoutType.REST) return emptyList()
        val targets = prescription?.targets ?: session.targets
        return buildList {
            targets.pace?.let { pace ->
                add("Ritmo: entre ${pace.fast.format()} e ${pace.slow.format()} por km")
            }
            targets.heartRate?.let { heartRate ->
                val ceiling = heartRate.maxBpm?.let { "até $it" } ?: "a partir de ${heartRate.minBpm}"
                add("FC: $ceiling bpm")
            }
            if (targets.pace == null && targets.heartRate == null) {
                targets.zone?.let { add("Zona $it") }
                targets.rpe?.let { add("Esforço: $it/10") }
            }
        }
    }
}
