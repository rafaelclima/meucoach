package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadDataState
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.HeatLevel
import com.meucoach.core.model.ReadinessLevel
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.math.roundToInt

data class DailyPrescriptionInput(
    val date: LocalDate,
    val profile: AthleteProfile,
    val readiness: ReadinessResult,
    val load: LoadSnapshot? = null,
    val heatLevel: HeatLevel = HeatLevel.NONE,
    /** Today's planned session, or null when the week has no intent for today. */
    val plannedSession: PlannedSession? = null,
    val plannedSessionId: String? = null,
    val planConfidence: DataConfidence? = null,
    /**
     * Status of today's planned session. Day-level guards only apply to
     * [SessionStatus.PLANNED]; a finished day is history and is never rewritten.
     */
    val sessionStatus: SessionStatus? = plannedSession?.status,
    /**
     * Post-race recovery window (ADR 0037). It only lowers a `PLANNED` day:
     * quality/long run are downgraded and easy sessions are capped.
     */
    val recovery: RecoveryWindow? = null,
)

/**
 * The single day-level decision (ADR 0023).
 *
 * It starts from the weekly plan intent and may only keep it, reduce it or
 * downgrade it — never increase type, volume, duration or intensity. Safety
 * (illness/red readiness), readiness, load coverage and heat can all lower the
 * day; the plan itself is never rewritten here.
 */
class DailyPrescriptionEngine {
    fun prescribe(input: DailyPrescriptionInput): DailyPrescription {
        val readiness = input.readiness
        val planned = input.plannedSession
        val illness = ReadinessFlag.ILLNESS_OR_INJURY in readiness.flags

        if (illness || readiness.level == ReadinessLevel.RED) {
            return rest(input, illness = illness)
        }
        if (planned == null) {
            return noPlan(input)
        }
        if (planned.type == WorkoutType.REST) {
            return plannedRest(input, planned)
        }

        val loadState = input.load?.dataState ?: LoadDataState.INSUFFICIENT
        // D5 (ADR 0039): trust for quality is objective load only; subjective
        // sRPE keeps ACWR and reduction guardrails alive but never unlocks a
        // strong session.
        val loadTrusted = input.load?.objectiveDataState == LoadDataState.KNOWN
        val subjectiveOnlyLoad = loadState == LoadDataState.KNOWN && !loadTrusted
        val acwr = input.load?.acwr
        // ACWR comes from the full series (objective + subjective): a high
        // subjective load must still trigger the reduction guardrail.
        val acwrHigh = loadState == LoadDataState.KNOWN && acwr != null && acwr > ACWR_MAX
        val acwrBlocked = loadTrusted && (acwr == null || acwr !in ACWR_MIN..ACWR_MAX)
        val shortSleep = ReadinessFlag.SHORT_SLEEP in readiness.flags
        // Missing data lowers confidence and blocks quality, but it is not bad
        // recovery: the plan duration is kept instead of being reduced.
        val absenceOnly = ReadinessFlag.INSUFFICIENT_DATA in readiness.flags && readiness.components.isEmpty()
        val negativeReadiness = readiness.level != ReadinessLevel.GREEN && !absenceOnly
        val safetyReduce = negativeReadiness || shortSleep
        // D-6 v0.1: a beginner never receives today's strong session, even when
        // the saved plan has one. Finished days bypass the guard (history).
        val beginnerGuard = planned.type == WorkoutType.QUALITY &&
            input.profile.experience == Experience.BEGINNER &&
            input.sessionStatus == SessionStatus.PLANNED
        // ADR 0037: after a completed race the day is never strong and the session
        // is capped, even when the persisted plan still has a full session.
        val recovery = input.recovery
        val recoveryActive = recovery?.isActive(input.date) == true &&
            input.sessionStatus == SessionStatus.PLANNED
        val recoveryCap = if (recoveryActive) recovery?.maxSessionMinutes(input.date) else null
        val recoveryReason = if (recoveryActive && recovery != null) {
            "recuperação após a prova (dia ${recovery.dayOfWindow(input.date)} de ${recovery.totalDays})"
        } else {
            null
        }
        val recoveryGuard = recoveryActive && planned.type == WorkoutType.QUALITY
        val qualityBlocked =
            safetyReduce || absenceOnly || !loadTrusted || acwrBlocked || acwrHigh ||
                beginnerGuard || recoveryGuard
        val safetyReasons = safetyReasons(input, shortSleep, negativeReadiness, absenceOnly)
        val qualityReasons = qualityReasons(loadState, loadTrusted, acwrHigh, acwrBlocked)
        val guardReasons = buildList {
            if (beginnerGuard) add(BEGINNER_REASON)
            if (recoveryGuard && recoveryReason != null) add(recoveryReason)
        }
        val uncertainLoad = !loadTrusted || acwrBlocked || acwrHigh

        val baseConfidence = confidence(input, loadTrusted = loadTrusted)
        val rules = baseRules(planned)
        val evidence = baseEvidence(planned)
        if (!loadTrusted) {
            rules += "R-DATA-01"
            evidence += "EV-IMPELLIZZERI-2020"
        }
        if (subjectiveOnlyLoad) {
            rules += "R-LOAD-04"
            evidence += "EV-FOSTER-2001"
        }

        return when (planned.type) {
            WorkoutType.QUALITY -> {
                if (qualityBlocked) {
                    val downgraded = reducedDuration(planned.durationMin, PrescriptionPolicy.DOWNGRADE_FACTOR)
                    val duration = if (recoveryGuard && recoveryCap != null) {
                        downgraded?.let { minOf(it, recoveryCap) }
                    } else {
                        downgraded
                    }
                    val reason = (safetyReasons + qualityReasons + guardReasons)
                        .distinct()
                        .joinToString("; ")
                    return build(
                        input = input,
                        planned = planned,
                        type = WorkoutType.EASY,
                        title = if (recoveryGuard) RECOVERY_REDUCED_TITLE else EASY_REDUCED_TITLE,
                        durationMin = duration,
                        targets = PrescriptionPolicy.targets(
                            type = WorkoutType.EASY,
                            profile = input.profile,
                            durationMin = duration,
                            heat = input.heatLevel,
                        ),
                        adjustment = PrescriptionAdjustment.DOWNGRADE,
                        rationale = "Planejado: ${planned.title} (${planned.durationMin ?: "—"} min). " +
                            "Hoje: fácil, ${duration ?: "—"} min. Motivo: $reason. " +
                            "Treino forte não foi prescrito hoje.",
                        rules = rules + "R-PRESC-02" + beginnerRules(beginnerGuard) +
                            recoveryRules(recoveryGuard) +
                            readinessRules(negativeReadiness, shortSleep) +
                            loadRules(uncertainLoad, acwrHigh),
                        evidence = evidence + readinessEvidence(shortSleep, negativeReadiness) +
                            loadEvidence(uncertainLoad, acwrHigh),
                        confidence = baseConfidence,
                    )
                }
                build(
                    input = input,
                    planned = planned,
                    type = WorkoutType.QUALITY,
                    title = planned.title,
                    durationMin = planned.durationMin,
                    targets = plannedTargets(planned, WorkoutType.QUALITY, input),
                    adjustment = PrescriptionAdjustment.KEEP,
                    rationale = "Plano mantido: ${planned.title}, ${planned.durationMin ?: "—"} min. " +
                        "Prontidão e carga permitem o treino forte de hoje.",
                    rules = rules + "R-PRESC-01",
                    evidence = evidence,
                    confidence = baseConfidence,
                )
            }

            WorkoutType.LONG -> {
                if (safetyReduce || acwrHigh || recoveryActive) {
                    val reduced = reducedDuration(planned.durationMin, PrescriptionPolicy.DOWNGRADE_FACTOR)
                    val duration = if (recoveryActive && recoveryCap != null) {
                        reduced?.let { minOf(it, recoveryCap) }
                    } else {
                        reduced
                    }
                    val reason = (
                        safetyReasons + qualityReasons +
                            listOfNotNull(recoveryReason)
                        ).distinct().joinToString("; ")
                    return build(
                        input = input,
                        planned = planned,
                        type = WorkoutType.EASY,
                        title = if (recoveryActive) RECOVERY_REDUCED_TITLE else EASY_REDUCED_TITLE,
                        durationMin = duration,
                        targets = PrescriptionPolicy.targets(
                            type = WorkoutType.EASY,
                            profile = input.profile,
                            durationMin = duration,
                            heat = input.heatLevel,
                        ),
                        adjustment = PrescriptionAdjustment.DOWNGRADE,
                        rationale = "Planejado: ${planned.title} (${planned.durationMin ?: "—"} min). " +
                            "Hoje: fácil, ${duration ?: "—"} min. Motivo: $reason. " +
                            "O longão fica para quando a recuperação permitir.",
                        rules = rules + "R-PRESC-02" + recoveryRules(recoveryActive) +
                            readinessRules(negativeReadiness, shortSleep) +
                            (if (acwrHigh) listOf("R-LOAD-03") else emptyList()),
                        evidence = evidence + readinessEvidence(shortSleep, negativeReadiness) +
                            (if (acwrHigh) listOf("EV-GABBETT-2016") else emptyList()),
                        confidence = baseConfidence,
                    )
                }
                build(
                    input = input,
                    planned = planned,
                    type = WorkoutType.LONG,
                    title = planned.title,
                    durationMin = planned.durationMin,
                    targets = plannedTargets(planned, WorkoutType.LONG, input),
                    adjustment = PrescriptionAdjustment.KEEP,
                    rationale = "Plano mantido: ${planned.title}, ${planned.durationMin ?: "—"} min, " +
                        "em ritmo fácil e conversável.",
                    rules = rules + "R-PRESC-01",
                    evidence = evidence,
                    confidence = baseConfidence,
                )
            }

            WorkoutType.EASY -> {
                val reduced = if (safetyReduce) {
                    reducedDuration(planned.durationMin, PrescriptionPolicy.REDUCE_FACTOR)
                } else {
                    planned.durationMin
                }
                val duration = if (recoveryActive && recoveryCap != null &&
                    reduced != null && reduced > recoveryCap
                ) {
                    recoveryCap
                } else {
                    reduced
                }
                val recoveryReduced = recoveryActive && duration != planned.durationMin
                if (safetyReduce || recoveryReduced) {
                    val reason = (safetyReasons + listOfNotNull(recoveryReason))
                        .distinct()
                        .joinToString("; ")
                    return build(
                        input = input,
                        planned = planned,
                        type = WorkoutType.EASY,
                        title = if (recoveryReduced) RECOVERY_REDUCED_TITLE else EASY_REDUCED_TITLE,
                        durationMin = duration,
                        targets = plannedTargets(planned, WorkoutType.EASY, input, duration),
                        adjustment = PrescriptionAdjustment.REDUCE,
                        rationale = "Planejado: ${planned.title} (${planned.durationMin ?: "—"} min). " +
                            "Hoje: fácil, ${duration ?: "—"} min. Motivo: $reason.",
                        rules = rules + "R-PRESC-02" + recoveryRules(recoveryActive) +
                            readinessRules(negativeReadiness, shortSleep),
                        evidence = evidence + readinessEvidence(shortSleep, negativeReadiness),
                        confidence = baseConfidence,
                    )
                }
                build(
                    input = input,
                    planned = planned,
                    type = WorkoutType.EASY,
                    title = planned.title,
                    durationMin = planned.durationMin,
                    targets = plannedTargets(planned, WorkoutType.EASY, input),
                    adjustment = PrescriptionAdjustment.KEEP,
                    rationale = "Plano mantido: ${planned.title}, ${planned.durationMin ?: "—"} min, " +
                        "em ritmo fácil e conversável.",
                    rules = rules + "R-PRESC-01",
                    evidence = evidence,
                    confidence = baseConfidence,
                )
            }

            WorkoutType.REST -> plannedRest(input, planned)
        }
    }

    private fun rest(input: DailyPrescriptionInput, illness: Boolean): DailyPrescription {
        val planned = input.plannedSession
        val plannedNote = planned?.let {
            "Planejado: ${it.title} (${it.durationMin ?: "—"} min). "
        }.orEmpty()
        return build(
            input = input,
            planned = planned,
            type = WorkoutType.REST,
            title = "Descanso",
            durationMin = null,
            targets = PrescriptionTargets(),
            adjustment = PrescriptionAdjustment.REST,
            rationale = plannedNote + if (illness) {
                "Sinal de doença ou lesão: sem treino hoje. Se persistir, procure avaliação."
            } else {
                "Prontidão baixa: o corpo pede descanso. Não reponha o treino de outra forma."
            },
            rules = (
                planned?.ruleIds.orEmpty() + if (illness) {
                    listOf("R-SAFE-01", "R-READY-03", "R-PRESC-02")
                } else {
                    listOf("R-READY-03", "R-PRESC-02")
                }
                ).distinct(),
            evidence = (planned?.evidenceIds.orEmpty() + "EV-SAW-2016").distinct(),
            confidence = DataConfidence.HIGH,
        )
    }

    private fun plannedRest(input: DailyPrescriptionInput, planned: PlannedSession): DailyPrescription = build(
        input = input,
        planned = planned,
        type = WorkoutType.REST,
        title = "Descanso",
        durationMin = null,
        targets = PrescriptionTargets(),
        adjustment = PrescriptionAdjustment.KEEP,
        rationale = "Hoje é descanso no plano. Descansar também faz parte do treino.",
        rules = (planned.ruleIds + "R-PRESC-01").distinct(),
        evidence = planned.evidenceIds,
        confidence = DataConfidence.HIGH,
    )

    private fun noPlan(input: DailyPrescriptionInput): DailyPrescription {
        val recommendation = WorkoutRecommender().recommend(
            PrescriptionInput(
                profile = input.profile,
                readiness = input.readiness,
                load = input.load,
                today = input.date,
                heatLevel = input.heatLevel,
            ),
        )
        return DailyPrescription(
            date = input.date,
            plannedSessionId = null,
            plannedType = null,
            plannedTitle = null,
            plannedDurationMin = null,
            prescribedType = recommendation.type,
            title = recommendation.title,
            prescribedDurationMin = recommendation.durationMin,
            targets = PrescriptionTargets(
                pace = recommendation.pace,
                heartRate = recommendation.heartRate,
                zone = if (recommendation.type == WorkoutType.EASY) "Z2" else null,
                distanceKm = recommendation.distanceKm,
                rpe = if (recommendation.pace == null && recommendation.heartRate == null) {
                    PrescriptionPolicy.EASY_RPE +
                        if (input.heatLevel == HeatLevel.HOT) PrescriptionPolicy.HEAT_RPE_STEP else 0
                } else {
                    null
                },
            ),
            adjustment = PrescriptionAdjustment.NO_PLAN,
            rationale = "Sem plano para hoje: " + recommendation.rationale +
                " O app não inventa alvos de um treino que não foi planejado.",
            ruleIds = (recommendation.ruleIds + "R-PRESC-03").distinct(),
            evidenceIds = recommendation.evidenceIds,
            confidence = DataConfidence.LOW,
            engineVersion = PrescriptionPolicy.ENGINE_VERSION,
            inputsHash = inputsHash(input),
        )
    }

    private fun build(
        input: DailyPrescriptionInput,
        planned: PlannedSession?,
        type: WorkoutType,
        title: String,
        durationMin: Int?,
        targets: PrescriptionTargets,
        adjustment: PrescriptionAdjustment,
        rationale: String,
        rules: List<String>,
        evidence: List<String>,
        confidence: DataConfidence,
    ): DailyPrescription {
        val finalRationale = rationale + heatHint(input, targets, adjustment)
        val heatRules = if (input.heatLevel == HeatLevel.HOT) {
            buildList {
                add("R-PLAN-07")
                if (input.profile.treadmillAccess) add("R-PLAN-08")
            }
        } else {
            emptyList()
        }
        val heatEvidence = if (input.heatLevel == HeatLevel.HOT) listOf("EV-ELY-2007") else emptyList()
        return DailyPrescription(
            date = input.date,
            plannedSessionId = input.plannedSessionId,
            plannedType = planned?.type,
            plannedTitle = planned?.title,
            plannedDurationMin = planned?.durationMin,
            prescribedType = type,
            title = title,
            prescribedDurationMin = durationMin,
            targets = targets,
            adjustment = adjustment,
            rationale = finalRationale,
            ruleIds = (rules + heatRules + "R-PRESC-03" + targetRules(targets)).distinct(),
            evidenceIds = (evidence + heatEvidence + targetEvidence(targets)).distinct(),
            confidence = confidence,
            engineVersion = PrescriptionPolicy.ENGINE_VERSION,
            inputsHash = inputsHash(input),
        )
    }

    private fun plannedTargets(
        planned: PlannedSession,
        type: WorkoutType,
        input: DailyPrescriptionInput,
        durationMin: Int? = planned.durationMin,
    ): PrescriptionTargets {
        val base = if (planned.targets.isEmpty) {
            PrescriptionPolicy.targets(type, input.profile, durationMin, heat = HeatLevel.NONE)
        } else {
            planned.targets
        }
        return base.forDuration(durationMin).withHeat(input.heatLevel)
    }

    private fun heatHint(
        input: DailyPrescriptionInput,
        targets: PrescriptionTargets,
        adjustment: PrescriptionAdjustment,
    ): String {
        if (input.heatLevel != HeatLevel.HOT) return ""
        if (adjustment == PrescriptionAdjustment.REST) return ""
        val percent = ((PrescriptionPolicy.HEAT_PACE_FACTOR - 1.0) * 100).roundToInt()
        val guide = if (targets.pace != null) {
            "ritmo alvo $percent% mais lento, sem mudar a FC"
        } else {
            "reduza o ritmo pelo esforço, sem mudar a FC"
        }
        val treadmill = if (input.profile.treadmillAccess) " A esteira também é válida hoje." else ""
        return " Calor informado: $guide e use a FC como guia.$treadmill"
    }

    private fun confidence(input: DailyPrescriptionInput, loadTrusted: Boolean): DataConfidence {
        val planConfidence = input.planConfidence ?: DataConfidence.MEDIUM
        var confidence = capped(input.readiness.confidence, planConfidence)
        if (input.heatLevel == HeatLevel.HOT) confidence = downgrade(confidence)
        if (!loadTrusted) confidence = downgrade(confidence)
        return confidence
    }

    private fun safetyReasons(
        input: DailyPrescriptionInput,
        shortSleep: Boolean,
        negativeReadiness: Boolean,
        absenceOnly: Boolean,
    ): List<String> = buildList {
        if (shortSleep) add("sono abaixo de 6 h")
        if (negativeReadiness) add("prontidão moderada")
        if (absenceOnly) add("faltam dados de recuperação")
    }

    private fun qualityReasons(
        loadState: LoadDataState,
        loadTrusted: Boolean,
        acwrHigh: Boolean,
        acwrBlocked: Boolean,
    ): List<String> = buildList {
        when (loadState) {
            LoadDataState.STALE -> add("dados de carga desatualizados")

            LoadDataState.INSUFFICIENT -> add("faltam dados de carga das últimas 4 semanas")

            // Subjective-only load (ADR 0039): the series is known, but it is
            // perceived effort, not an objective metric.
            LoadDataState.KNOWN -> if (!loadTrusted) add("carga recente vem do esforço percebido")
        }
        if (acwrHigh) add("sua carga subiu rápido")
        if (acwrBlocked && loadState == LoadDataState.KNOWN) add("a carga recente está fora da faixa segura")
    }

    private fun readinessRules(negativeReadiness: Boolean, shortSleep: Boolean): List<String> = buildList {
        if (negativeReadiness) add("R-READY-03")
        if (shortSleep) add("R-READY-04")
    }

    private fun readinessEvidence(shortSleep: Boolean, negativeReadiness: Boolean): List<String> = buildList {
        if (negativeReadiness || shortSleep) add("EV-SAW-2016")
    }

    private fun loadRules(uncertain: Boolean, acwrHigh: Boolean): List<String> = buildList {
        if (acwrHigh) add("R-LOAD-03")
        if (uncertain && !acwrHigh) add("R-DATA-01")
    }

    private fun loadEvidence(uncertain: Boolean, acwrHigh: Boolean): List<String> = buildList {
        if (acwrHigh) add("EV-GABBETT-2016")
        if (uncertain && !acwrHigh) add("EV-IMPELLIZZERI-2020")
    }

    private fun beginnerRules(active: Boolean): List<String> = if (active) listOf("R-PRESC-04") else emptyList()

    /** ADR 0037: recovery is a product convention, never a physiological claim. */
    private fun recoveryRules(active: Boolean): List<String> = if (active) listOf("R-RACE-04") else emptyList()

    private fun targetRules(targets: PrescriptionTargets): List<String> = buildList {
        if (targets.pace != null || targets.heartRate != null) add("R-ZONE-01")
        if (targets.pace == null && targets.heartRate == null && targets.rpe != null) add("R-CAL-02")
    }

    private fun targetEvidence(targets: PrescriptionTargets): List<String> = buildList {
        if (targets.pace != null || targets.heartRate != null) add("EV-DANIELS-2014")
        if (targets.pace == null && targets.heartRate == null && targets.rpe != null) add("EV-FRIEL-2018")
    }

    private fun baseRules(planned: PlannedSession?): MutableList<String> =
        (planned?.ruleIds.orEmpty() + "R-ZONE-01").distinct().toMutableList()

    private fun baseEvidence(planned: PlannedSession?): MutableList<String> =
        (planned?.evidenceIds.orEmpty() + listOf("EV-SEILER-2010", "EV-DANIELS-2014"))
            .distinct()
            .toMutableList()

    private fun reducedDuration(planned: Int?, factor: Double): Int? {
        if (planned == null) return null
        val floor = PrescriptionPolicy.MIN_EASY_MINUTES
        val target = (planned * factor).roundToInt()
        return minOf(planned, maxOf(floor, target))
    }

    private fun capped(confidence: DataConfidence, cap: DataConfidence): DataConfidence {
        val rank = mapOf(DataConfidence.LOW to 0, DataConfidence.MEDIUM to 1, DataConfidence.HIGH to 2)
        return if (rank.getValue(confidence) <= rank.getValue(cap)) confidence else cap
    }

    private fun downgrade(confidence: DataConfidence): DataConfidence = when (confidence) {
        DataConfidence.HIGH -> DataConfidence.MEDIUM
        DataConfidence.MEDIUM, DataConfidence.LOW -> DataConfidence.LOW
    }

    /**
     * Deterministic fingerprint of every input that can change the decision.
     * Equal hashes mean the persisted version is still authoritative.
     */
    private fun inputsHash(input: DailyPrescriptionInput): String {
        val planned = input.plannedSession
        val canonical = buildString {
            append("engine=").append(PrescriptionPolicy.ENGINE_VERSION).append('|')
            append("date=").append(input.date).append('|')
            append("plannedId=").append(input.plannedSessionId).append('|')
            append("profile=").append(input.profile.id).append(',')
            append(input.profile.experience).append(',')
            append(input.profile.thresholdPace?.secondsPerKm).append(',')
            append(input.profile.lactateThresholdHeartRate).append(',')
            append(input.profile.treadmillAccess).append(',')
            append(input.profile.weeklyAvailability.minutesByDay.toSortedMap()).append('|')
            append("readiness=").append(input.readiness.score).append(',')
            append(input.readiness.level).append(',')
            append(input.readiness.confidence).append(',')
            append(input.readiness.flags.map { it.name }.sorted()).append('|')
            append("load=").append(input.load?.dataState).append(',')
            append(input.load?.acwr).append(',')
            append(input.load?.coverage?.daysWithLoad).append(',')
            append(input.load?.coverage?.sourceFresh).append('|')
            append("heat=").append(input.heatLevel).append('|')
            append("recovery=").append(
                input.recovery?.takeIf { it.isActive(input.date) }
                    ?.let { "${it.race.eventId},${it.dayOfWindow(input.date)}" } ?: "null",
            ).append('|')
            append("planConfidence=").append(input.planConfidence).append('|')
            append("status=").append(input.sessionStatus).append('|')
            if (planned != null) {
                append("planned=").append(planned.date).append(',')
                append(planned.type).append(',')
                append(planned.durationMin).append(',')
                append(planned.title).append(',')
                append(planned.targets).append(',')
                append(planned.ruleIds.sorted()).append(',')
                append(planned.evidenceIds.sorted())
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private companion object {
        const val ACWR_MIN = 0.8
        const val ACWR_MAX = 1.3
        const val EASY_REDUCED_TITLE = "Fácil, sem pressa"
        const val RECOVERY_REDUCED_TITLE = "Recuperação: fácil e curto"
        const val BEGINNER_REASON = "seu nível é iniciante e o treino forte fica para depois"
    }
}
