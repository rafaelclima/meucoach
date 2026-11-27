package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadDataState
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Experience
import com.meucoach.core.model.ReadinessLevel
import java.time.LocalDate
import java.util.Locale

/**
 * Pure day-level quality gate (ADR 0044 §2.4, ADR 0049 §2.5, ADR 0053 §2.1).
 *
 * It answers one question — should today's planned opportunity be kept? — and
 * never looks at the readiness of the week's first day. The first failing gate
 * wins, so the output is deterministic and auditable:
 *
 * 1. illness/injury or red readiness;
 * 2. beginner guard;
 * 3. missing subjective check-in;
 * 4. short sleep;
 * 5. non-green readiness (amber: partial recovery);
 * 6. post-race recovery, race week, recovery week or taper;
 * 7. stale or insufficient objective load (ADR 0053 cold-start ramp may keep
 *    one short quality for a calibrated intermediate);
 * 8. high monotony;
 * 9. reliable strain spike;
 * 10. reliable ACWR above 1.5;
 * 11. otherwise eligible.
 *
 * [phase] and [raceWeek] are the plan context the day must respect. A taper
 * micro-dose (`STRIDES_ONLY`) is structural and is not blocked by the taper
 * itself; safety, recovery and load gates still apply.
 */
object QualityGate {
    fun evaluate(
        plannedSession: PlannedSession,
        readiness: ReadinessResult,
        load: LoadSnapshot?,
        recovery: RecoveryWindow?,
        profile: AthleteProfile,
        date: LocalDate,
        phase: MesoPhase? = null,
        raceWeek: Boolean = false,
        coldStart: ColdStartAllowance? = null,
    ): QualityEligibility {
        require(plannedSession.type == WorkoutType.QUALITY) {
            "QualityGate only evaluates a QUALITY session"
        }
        require(plannedSession.date == date) {
            "Gate date must match the planned session date"
        }
        val context = GateContext(
            plannedSession = plannedSession,
            readiness = readiness,
            load = load,
            recovery = recovery,
            profile = profile,
            date = date,
            plan = PlanDayContext(phase = phase, raceWeek = raceWeek),
            coldStart = coldStart,
        )
        val firstBlock = readinessBlock(context)
            ?: beginnerBlock(context)
            ?: subjectiveBlock(context)
            ?: sleepBlock(context)
            ?: lowReadinessBlock(context)
            ?: postRaceBlock(context)
            ?: raceWeekBlock(context)
            ?: recoveryWeekBlock(context)
            ?: taperBlock(context)
            ?: loadBlock(context)
        if (firstBlock != null) return firstBlock

        val snapshot = context.load
        if (snapshot == null || snapshot.objectiveDataState != LoadDataState.KNOWN) {
            // ADR 0053: without a KNOWN baseline there is no monotony/strain/ACWR
            // to judge. Either the cold-start ramp kept this short quality, or
            // loadBlock would have blocked above.
            return coldStartEligible()
        }
        return monotonyBlock(snapshot) ?: strainBlock(snapshot) ?: acwrBlock(snapshot) ?: eligible()
    }

    private fun readinessBlock(context: GateContext): QualityEligibility? {
        val readiness = context.readiness
        val illness = ReadinessFlag.ILLNESS_OR_INJURY in readiness.flags
        if (!illness && readiness.level != ReadinessLevel.RED) return null
        return blocked(
            reason = QualityBlockReason.ILLNESS_OR_RED_READINESS,
            detail = if (illness) {
                "sinal de doença ou lesão no check-in"
            } else {
                "prontidão baixa (${readiness.score}/100)"
            },
            ruleId = if (illness) "R-SAFE-01" else "R-READY-03",
            evidenceIds = listOf("EV-SAW-2016"),
            unlockHint = if (illness) {
                "Recupere e refaça o check-in quando estiver sem dor ou doença."
            } else {
                "Faça o check-in quando a prontidão não estiver vermelha."
            },
        )
    }

    private fun beginnerBlock(context: GateContext): QualityEligibility? {
        if (context.profile.experience != Experience.BEGINNER) return null
        return blocked(
            reason = QualityBlockReason.BEGINNER_GUARD,
            detail = "nível iniciante: o treino forte vem depois de você subir de nível",
            ruleId = "R-PRESC-04",
            evidenceIds = listOf("EV-DANIELS-2014", "EV-SEILER-2010"),
            unlockHint = "Conclua a graduação para liberar treino forte.",
        )
    }

    private fun subjectiveBlock(context: GateContext): QualityEligibility? {
        if (ReadinessComponent.SUBJECTIVE in context.readiness.components) return null
        return blocked(
            reason = QualityBlockReason.SUBJECTIVE_MISSING,
            detail = "você ainda não respondeu o check-in de hoje",
            ruleId = "R-READY-06",
            evidenceIds = listOf("EV-SAW-2016"),
            unlockHint = "Faça o check-in de hoje para liberar o treino forte.",
        )
    }

    private fun sleepBlock(context: GateContext): QualityEligibility? {
        if (ReadinessFlag.SHORT_SLEEP !in context.readiness.flags) return null
        return blocked(
            reason = QualityBlockReason.SHORT_SLEEP,
            detail = "sono abaixo de 6 h",
            ruleId = "R-READY-04",
            evidenceIds = listOf("EV-WALSH-2021", "EV-WATSON-2015"),
            unlockHint = "Priorize uma noite de sono de pelo menos 6 h.",
        )
    }

    /**
     * ADR 0049 §2.1/FR-406: only a green day confirms quality. Amber is a
     * partial-recovery signal, not an illness; it still lowers the day.
     */
    private fun lowReadinessBlock(context: GateContext): QualityEligibility? {
        if (context.readiness.level == ReadinessLevel.GREEN) return null
        return blocked(
            reason = QualityBlockReason.LOW_READINESS,
            detail = "prontidão moderada (${context.readiness.score}/100)",
            ruleId = "R-READY-03",
            evidenceIds = listOf("EV-SAW-2016"),
            unlockHint = "Recupere até a prontidão ficar verde e confirme no check-in.",
        )
    }

    private fun postRaceBlock(context: GateContext): QualityEligibility? {
        val window = context.recovery ?: return null
        if (!window.isActive(context.date)) return null
        return blocked(
            reason = QualityBlockReason.RECOVERY_WINDOW,
            detail = "recuperação após a prova (dia ${window.dayOfWindow(context.date)} de ${window.totalDays})",
            ruleId = "R-RACE-04",
            evidenceIds = listOf("EV-HALSON-2014"),
            unlockHint = "Aguarde o fim da recuperação pós-prova.",
        )
    }

    private fun raceWeekBlock(context: GateContext): QualityEligibility? {
        if (context.microDose || !context.plan.raceWeek) return null
        return blocked(
            reason = QualityBlockReason.RACE_WEEK,
            detail = "semana de prova: sem o treino forte principal",
            ruleId = "R-PLAN-10",
            evidenceIds = listOf("EV-BOSQUET-2007", "EV-MUJIKA-2003"),
            unlockHint = "Semana de prova: a dose pequena já é o estímulo; o treino forte volta depois.",
        )
    }

    private fun recoveryWeekBlock(context: GateContext): QualityEligibility? {
        if (context.plan.phase != MesoPhase.RECOVERY) return null
        return blocked(
            reason = QualityBlockReason.RECOVERY_WINDOW,
            detail = "semana de recuperação: sem treino forte",
            ruleId = "R-RACE-04",
            evidenceIds = listOf("EV-HALSON-2014"),
            unlockHint = "Aguarde a semana de recuperação terminar.",
        )
    }

    private fun taperBlock(context: GateContext): QualityEligibility? {
        if (context.microDose || context.plan.phase != MesoPhase.TAPER) return null
        return blocked(
            reason = QualityBlockReason.TAPER_WINDOW,
            detail = "semana de prova: só a dose pequena de sempre",
            ruleId = "R-PLAN-05",
            evidenceIds = listOf("EV-BOSQUET-2007", "EV-MUJIKA-2003"),
            unlockHint = "Mantenha a dose pequena e guarde o treino forte para depois da prova.",
        )
    }

    private fun loadBlock(context: GateContext): QualityEligibility? {
        val load = context.load ?: return if (coldStartApplies(context, reasonIfMissing = true)) {
            null
        } else {
            blocked(
                reason = QualityBlockReason.LOAD_INSUFFICIENT,
                detail = "sem dados de carga",
                ruleId = "R-DATA-01",
                evidenceIds = listOf("EV-IMPELLIZZERI-2020"),
                unlockHint = UNLOCK_LOAD,
            )
        }
        return when (load.objectiveDataState) {
            LoadDataState.STALE -> if (coldStartApplies(context, reasonIfMissing = false)) {
                null
            } else {
                blocked(
                    reason = QualityBlockReason.LOAD_STALE,
                    detail = "fonte de carga desatualizada",
                    ruleId = "R-DATA-01",
                    evidenceIds = listOf("EV-IMPELLIZZERI-2020"),
                    unlockHint = "Importe ou registre atividades recentes.",
                )
            }

            LoadDataState.INSUFFICIENT -> if (coldStartApplies(context, reasonIfMissing = false)) {
                null
            } else {
                blocked(
                    reason = QualityBlockReason.LOAD_INSUFFICIENT,
                    detail = "faltam dados de carga: ${load.objectiveDaysWithLoad} de 28 dias com FC ou ritmo",
                    ruleId = "R-DATA-01",
                    evidenceIds = listOf("EV-IMPELLIZZERI-2020"),
                    unlockHint = UNLOCK_LOAD,
                )
            }

            LoadDataState.KNOWN -> null
        }
    }

    /**
     * ADR 0053 §2.1: a calibrated intermediate may keep one short quality
     * without 8 objective days. Safety/readiness/recovery gates already passed
     * above; this only replaces the load gate. BEGINNER never qualifies.
     */
    private fun coldStartApplies(context: GateContext, reasonIfMissing: Boolean): Boolean {
        if (context.profile.experience == Experience.BEGINNER) return false
        if ((context.coldStart?.isValid() ?: false).not()) return false
        val duration = context.plannedSession.durationMin ?: return reasonIfMissing
        if (duration !in 1..ColdStartAllowance.MAX_DURATION_MIN) return false
        return true
    }

    private fun monotonyBlock(load: LoadSnapshot): QualityEligibility? {
        if (load.monotonyOk) return null
        return blocked(
            reason = QualityBlockReason.MONOTONY_HIGH,
            detail = load.monotony?.let { "semana muito repetitiva (variedade ${decimal(it)})" }
                ?: "variedade sem dados suficientes para avaliar",
            ruleId = "R-LOAD-03",
            evidenceIds = listOf("EV-FOSTER-2001"),
            unlockHint = "Varie a carga: dias leves de verdade e um estímulo principal.",
        )
    }

    private fun strainBlock(load: LoadSnapshot): QualityEligibility? {
        if (!load.strainReliable || !load.strainSpike) return null
        return blocked(
            reason = QualityBlockReason.STRAIN_SPIKE,
            detail = "carga desta semana mais que o dobro da sua base",
            ruleId = "R-LOAD-03",
            evidenceIds = listOf("EV-FOSTER-2001"),
            unlockHint = "Reduza a concentração de carga desta semana.",
        )
    }

    private fun acwrBlock(load: LoadSnapshot): QualityEligibility? {
        val acwr = load.acwr ?: return null
        if (!load.acwrReliable || acwr <= LoadSnapshot.ACWR_MAX) return null
        return blocked(
            reason = QualityBlockReason.ACWR_HIGH,
            detail = "carga dos últimos 7 dias ${decimal(acwr)}×, acima de ${decimal(LoadSnapshot.ACWR_MAX)}×",
            ruleId = "R-LOAD-03",
            evidenceIds = listOf("EV-GABBETT-2016"),
            unlockHint = "Mantenha a carga estável até voltar a 1,50× ou menos.",
        )
    }

    private fun eligible() = QualityEligibility(
        eligible = true,
        reason = null,
        detail = null,
        ruleId = RULE_ELIGIBLE,
        evidenceIds = ELIGIBLE_EVIDENCE,
        unlockHint = null,
    )

    private fun coldStartEligible() = QualityEligibility(
        eligible = true,
        reason = null,
        detail = "primeira semana depois da prova: 1 treino forte curto, sem aumentar o volume",
        ruleId = RULE_COLD_START,
        evidenceIds = ELIGIBLE_EVIDENCE,
        unlockHint = null,
    )

    private fun blocked(
        reason: QualityBlockReason,
        detail: String,
        ruleId: String,
        evidenceIds: List<String>,
        unlockHint: String,
    ) = QualityEligibility(
        eligible = false,
        reason = reason,
        detail = detail,
        ruleId = ruleId,
        evidenceIds = evidenceIds,
        unlockHint = unlockHint,
    )

    private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.2f", value).replace('.', ',')

    private class GateContext(
        val plannedSession: PlannedSession,
        val readiness: ReadinessResult,
        val load: LoadSnapshot?,
        val recovery: RecoveryWindow?,
        val profile: AthleteProfile,
        val date: LocalDate,
        val plan: PlanDayContext,
        val coldStart: ColdStartAllowance? = null,
    ) {
        val microDose: Boolean = plannedSession.qualityKind == QualityKind.STRIDES_ONLY
    }

    private data class PlanDayContext(val phase: MesoPhase?, val raceWeek: Boolean)

    private const val RULE_ELIGIBLE = "R-PER-03"
    private const val RULE_COLD_START = "R-COLD-01"
    private const val UNLOCK_LOAD = "Registre pelo menos 8 dias de carga objetiva nas últimas 4 semanas."
    private val ELIGIBLE_EVIDENCE = listOf("EV-SEILER-2010", "EV-DANIELS-2014")
}
