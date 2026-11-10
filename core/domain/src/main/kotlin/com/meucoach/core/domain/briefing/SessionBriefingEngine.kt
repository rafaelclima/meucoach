package com.meucoach.core.domain.briefing

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType

/**
 * Builds the day briefing from the persisted prescription only (ADR 0023 / C3-03).
 *
 * The engine is pure and deterministic: equal prescription and engine version
 * produce the same briefing. It never reads the current profile, never derives
 * new targets and never produces a retrospective briefing for a finished day.
 */
class SessionBriefingEngine {
    /**
     * Returns `null` for a `DONE`/`CLOSED` session: the UI shows the persisted
     * execution/history instead of recomputing the day with today's profile.
     */
    fun brief(prescription: DailyPrescription, sessionStatus: SessionStatus? = null): SessionBriefing? {
        if (sessionStatus == SessionStatus.DONE || sessionStatus == SessionStatus.CLOSED) return null

        return SessionBriefing(
            date = prescription.date,
            title = prescription.title,
            workoutType = prescription.prescribedType,
            durationMin = prescription.prescribedDurationMin,
            targets = prescription.targets,
            rationale = prescription.rationale,
            confidence = prescription.confidence,
            ruleIds = (prescription.ruleIds + STOP_RULE).distinct(),
            evidenceIds = prescription.evidenceIds.distinct(),
            howToRun = howToRun(prescription),
            ifHard = ifHard(prescription.prescribedType),
        )
    }

    private fun howToRun(prescription: DailyPrescription): String = when (prescription.prescribedType) {
        WorkoutType.REST -> restGuidance(prescription.ruleIds)

        WorkoutType.EASY, WorkoutType.LONG -> {
            val duration = prescription.prescribedDurationMin
            val base = if (duration != null) {
                "Mantenha o esforço fácil e conversável durante os $duration min prescritos."
            } else {
                "Mantenha o esforço fácil e conversável durante o treino prescrito."
            }
            listOf(base, targetGuidance(prescription.targets), "Mantenha o esforço constante do início ao fim.")
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }

        WorkoutType.QUALITY -> {
            val duration = prescription.prescribedDurationMin
            val base = if (duration != null) {
                "Faça o treino forte no esforço prescrito durante os $duration min."
            } else {
                "Faça o treino forte no esforço prescrito."
            }
            listOf(base, targetGuidance(prescription.targets), "Mantenha o esforço constante do início ao fim.")
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }
    }

    private fun targetGuidance(targets: PrescriptionTargets): String = buildList {
        targets.pace?.let { pace ->
            add("Se usar o ritmo, fique entre ${pace.fast.format()} e ${pace.slow.format()} por km.")
        }
        targets.heartRate?.let { heartRate ->
            val max = heartRate.maxBpm
            add(
                if (max != null) {
                    "Se usar a FC, fique entre ${heartRate.minBpm} e $max bpm."
                } else {
                    "Se usar a FC, mantenha a partir de ${heartRate.minBpm} bpm."
                },
            )
        }
        if (targets.pace == null && targets.heartRate == null) {
            targets.rpe?.let { rpe -> add("Use o esforço percebido $rpe/10 como guia.") }
        }
    }.joinToString(" ")

    private fun restGuidance(ruleIds: List<String>): String = if (STOP_RULE in ruleIds) {
        "Não treine hoje. O descanso é a prescrição do dia; se o sinal persistir, procure avaliação."
    } else {
        "Não há treino hoje. O descanso faz parte do plano; não reponha a sessão de outra forma."
    }

    private fun ifHard(type: WorkoutType): String = if (type == WorkoutType.REST) {
        "Se você se sentir mal, mantenha o descanso. Dor no peito, tontura ou falta de ar " +
            "anormal são sinais para procurar avaliação."
    } else {
        "Reduza o esforço ou interrompa o treino se ele deixar de ser sustentável. Não troque " +
            "por outro treino para compensar. Dor no peito, tontura ou falta de ar anormal são " +
            "sinais para parar e procurar avaliação."
    }

    companion object {
        const val ENGINE_VERSION = "session-briefing-v1"
        private const val STOP_RULE = "R-SAFE-01"
    }
}
