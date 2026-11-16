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
 * Pure day-level quality gate (ADR 0044 §2.4, ADR 0049 §2.5).
 *
 * It answers one question — should today's planned opportunity be kept? — and
 * never looks at the readiness of the week's first day. The first failing gate
 * wins, so the output is deterministic and auditable:
 *
 * 1. illness/injury or red readiness;
 * 2. beginner guard;
 * 3. missing subjective check-in;
 * 4. short sleep;
 * 5. post-race recovery, race week, recovery week or taper;
 * 6. stale or insufficient objective load;
 * 7. high monotony;
 * 8. reliable strain spike;
 * 9. reliable ACWR above 1.5;
 * 10. otherwise eligible.
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
        )
        val firstBlock = readinessBlock(context)
            ?: beginnerBlock(context)
            ?: subjectiveBlock(context)
            ?: sleepBlock(context)
            ?: postRaceBlock(context)
            ?: raceWeekBlock(context)
            ?: recoveryWeekBlock(context)
            ?: taperBlock(context)
            ?: loadBlock(context)
        if (firstBlock != null) return firstBlock

        val load = requireNotNull(context.load) { "loadBlock guarantees load when known" }
        return monotonyBlock(load) ?: strainBlock(load) ?: acwrBlock(load) ?: eligible()
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
                "prontidão vermelha (score ${readiness.score})"
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
            detail = "nível iniciante: o treino forte fica para depois da graduação",
            ruleId = "R-PRESC-04",
            evidenceIds = listOf("EV-DANIELS-2014", "EV-SEILER-2010"),
            unlockHint = "Conclua a graduação para liberar treino forte.",
        )
    }

    private fun subjectiveBlock(context: GateContext): QualityEligibility? {
        if (ReadinessComponent.SUBJECTIVE in context.readiness.components) return null
        return blocked(
            reason = QualityBlockReason.SUBJECTIVE_MISSING,
            detail = "check-in subjetivo de hoje ainda não foi respondido",
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
            detail = "semana de prova: sem qualidade principal",
            ruleId = "R-PLAN-10",
            evidenceIds = listOf("EV-BOSQUET-2007", "EV-MUJIKA-2003"),
            unlockHint = "Semana de prova: a micro-dose já é o estímulo; o treino forte volta depois.",
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
            detail = "fase de taper: apenas a micro-dose estrutural",
            ruleId = "R-PLAN-05",
            evidenceIds = listOf("EV-BOSQUET-2007", "EV-MUJIKA-2003"),
            unlockHint = "Mantenha a micro-dose e guarde o treino forte para depois da prova.",
        )
    }

    private fun loadBlock(context: GateContext): QualityEligibility? {
        val load = context.load ?: return blocked(
            reason = QualityBlockReason.LOAD_INSUFFICIENT,
            detail = "sem dados de carga",
            ruleId = "R-DATA-01",
            evidenceIds = listOf("EV-IMPELLIZZERI-2020"),
            unlockHint = UNLOCK_LOAD,
        )
        return when (load.objectiveDataState) {
            LoadDataState.STALE -> blocked(
                reason = QualityBlockReason.LOAD_STALE,
                detail = "fonte de carga desatualizada",
                ruleId = "R-DATA-01",
                evidenceIds = listOf("EV-IMPELLIZZERI-2020"),
                unlockHint = "Importe ou registre atividades recentes.",
            )

            LoadDataState.INSUFFICIENT -> blocked(
                reason = QualityBlockReason.LOAD_INSUFFICIENT,
                detail = "carga objetiva insuficiente: ${load.objectiveDaysWithLoad} de 28 dias",
                ruleId = "R-DATA-01",
                evidenceIds = listOf("EV-IMPELLIZZERI-2020"),
                unlockHint = UNLOCK_LOAD,
            )

            LoadDataState.KNOWN -> null
        }
    }

    private fun monotonyBlock(load: LoadSnapshot): QualityEligibility? {
        if (load.monotonyOk) return null
        return blocked(
            reason = QualityBlockReason.MONOTONY_HIGH,
            detail = load.monotony?.let { "monotonia ${decimal(it)} ≥ ${decimal(LoadSnapshot.MONOTONY_MAX)}" }
                ?: "monotonia sem dados suficientes",
            ruleId = "R-LOAD-03",
            evidenceIds = listOf("EV-FOSTER-2001"),
            unlockHint = "Varie a carga: dias leves de verdade e um estímulo principal.",
        )
    }

    private fun strainBlock(load: LoadSnapshot): QualityEligibility? {
        if (!load.strainReliable || !load.strainSpike) return null
        return blocked(
            reason = QualityBlockReason.STRAIN_SPIKE,
            detail = "strain semanal acima de 2× a média crônica",
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
            detail = "ACWR ${decimal(acwr)} > ${decimal(LoadSnapshot.ACWR_MAX)}",
            ruleId = "R-LOAD-03",
            evidenceIds = listOf("EV-GABBETT-2016"),
            unlockHint = "Mantenha a carga estável até o ACWR voltar a 1,50 ou menos.",
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
    ) {
        val microDose: Boolean = plannedSession.qualityKind == QualityKind.STRIDES_ONLY
    }

    private data class PlanDayContext(val phase: MesoPhase?, val raceWeek: Boolean)

    private const val RULE_ELIGIBLE = "R-PER-03"
    private const val UNLOCK_LOAD = "Registre pelo menos 8 dias de carga objetiva nas últimas 4 semanas."
    private val ELIGIBLE_EVIDENCE = listOf("EV-SEILER-2010", "EV-DANIELS-2014")
}
