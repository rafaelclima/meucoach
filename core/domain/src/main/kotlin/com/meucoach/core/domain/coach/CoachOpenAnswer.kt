package com.meucoach.core.domain.coach

import com.meucoach.core.domain.feedback.ComparisonVerdict
import com.meucoach.core.domain.feedback.SessionComparison
import com.meucoach.core.domain.feedback.SessionComparisonKind
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.ReadinessLevel
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Facts available to an open Coach answer (ADR 0063).
 *
 * Every field is already-persisted or already-decided data: the week the
 * engine generated, today's prescription (when it exists), the readiness from
 * the check-in/metrics and the closed-week rollup. Nothing here is computed by
 * the answer and nothing is invented.
 */
data class CoachOpenContext(
    val today: LocalDate,
    val plan: WeeklyPlan? = null,
    val prescription: DailyPrescription? = null,
    val execution: CoachExecution? = null,
    /** Plan session behind [execution]; carries the original title/type/duration. */
    val executedSession: PlannedSession? = null,
    /** Day authority of [executedSession] after check-in/readiness, when persisted. */
    val executionPrescription: DailyPrescription? = null,
    val readiness: ReadinessResult? = null,
    val hasCheckIn: Boolean = false,
    val sleepMinutes: Int? = null,
    val progress: CoachProgressSummary? = null,
    val lastReview: WeeklyReview? = null,
)

/**
 * What the athlete actually did, reduced to facts the Coach may narrate.
 *
 * Pace/FC are deliberately absent: the comparison carries only verdicts
 * (`WITHIN`/`ABOVE`/`BELOW`), and the exact numbers stay in the deterministic
 * card/Today. A manual completion has duration/RPE but no comparison.
 */
data class CoachExecution(
    /** Day the session was executed, so the dossier can say today vs. the last one. */
    val date: LocalDate,
    val durationMin: Int,
    val distanceKm: Double? = null,
    val rpe: Int? = null,
    val evidence: CompletionEvidence? = null,
    val comparison: SessionComparison? = null,
) {
    init {
        require(durationMin > 0) { "Executed duration must be positive" }
        require(distanceKm == null || (distanceKm.isFinite() && distanceKm >= 0.0)) {
            "Executed distance must be finite and non-negative"
        }
        require(rpe == null || rpe in 1..10) { "Executed RPE must be between 1 and 10" }
    }
}

/**
 * Context and number allow-lists for [FreeInputIntent.ASK_COACHING].
 *
 * The model may explain concepts and the plan, but every duration, distance,
 * percentage and count it writes must exist in [allowedMinutes],
 * [allowedDistancesKm], [allowedPercents] or [allowedCounts]. The allow-lists
 * cover the typed facts and every number [engineContext] prints (fixed window
 * labels and rationale numbers included), so the model is never punished for
 * echoing the dossier. Pace/FC stay out of prose entirely and are shown by the
 * deterministic reference card.
 */
data class CoachOpenAnswer(
    val engineContext: String,
    val allowedMinutes: Set<Int> = emptySet(),
    val allowedPercents: Set<Int> = emptySet(),
    val allowedDistancesKm: Set<Double> = emptySet(),
    val allowedCounts: Set<Int> = emptySet(),
) {
    companion object {
        fun build(question: String, context: CoachOpenContext): CoachOpenAnswer {
            val plan = context.plan
            val prescription = context.prescription?.takeIf { it.date == context.today }
            val execution = context.execution
            val progress = context.progress
            val review = context.lastReview?.takeIf { it.status == WeeklyReviewStatus.AVAILABLE }
            val engineContext = facts(question, context)
            // The model may cite every number the engine itself wrote in the
            // dossier. The structured sets below cover the typed facts; the
            // extracted ones cover fixed labels ("últimas 4 semanas", "28
            // dias") and rationale numbers. Without this, the model echoes a
            // number it just read and the validator rejects an honest answer.
            val factNumbers = dossierNumbers(engineContext.substringAfter('\n'))
            return CoachOpenAnswer(
                engineContext = engineContext,
                allowedMinutes = allowedMinutes(context, plan, prescription, execution, progress, review) +
                    factNumbers.minutes,
                allowedPercents = allowedPercents(progress, review, execution) + factNumbers.percents,
                allowedDistancesKm = allowedDistances(plan, prescription, execution) + factNumbers.distancesKm,
                allowedCounts = allowedCounts(
                    plan = plan,
                    progress = progress,
                    review = review,
                    execution = execution,
                    readinessScore = context.readiness
                        ?.takeIf { it.components.isNotEmpty() }
                        ?.score,
                ) + factNumbers.counts,
            )
        }

        private data class DossierNumbers(
            val minutes: Set<Int> = emptySet(),
            val percents: Set<Int> = emptySet(),
            val distancesKm: Set<Double> = emptySet(),
            val counts: Set<Int> = emptySet(),
        )

        private fun dossierNumbers(facts: String): DossierNumbers = DossierNumbers(
            minutes = CoachNumberPatterns.minutes.findAll(facts)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .toSet(),
            percents = CoachNumberPatterns.percent.findAll(facts)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .toSet(),
            distancesKm = CoachNumberPatterns.distanceKm.findAll(facts)
                .mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }
                .toSet(),
            counts = CoachNumberPatterns.bareNumber.findAll(facts)
                .mapNotNull { match ->
                    val raw = match.value.replace(',', '.')
                    raw.toDoubleOrNull()?.takeIf { it == it.toInt().toDouble() }?.toInt()
                }
                .toSet(),
        )

        private fun allowedMinutes(
            context: CoachOpenContext,
            plan: WeeklyPlan?,
            prescription: DailyPrescription?,
            execution: CoachExecution?,
            progress: CoachProgressSummary?,
            review: WeeklyReview?,
        ): Set<Int> = buildSet {
            plan?.weeklyMinutes?.let(::add)
            plan?.sessions?.forEach { session -> session.durationMin?.let(::add) }
            prescription?.plannedDurationMin?.let(::add)
            prescription?.prescribedDurationMin?.let(::add)
            execution?.durationMin?.let(::add)
            context.sleepMinutes?.let(::add)
            progress?.let {
                add(it.plannedMinutes)
                add(it.executedMinutes)
                add(it.extraMinutes)
            }
            review?.volume?.let {
                it.plannedMin?.let(::add)
                it.executedMin?.let(::add)
                it.extraMin?.let(::add)
            }
        }

        private fun allowedPercents(
            progress: CoachProgressSummary?,
            review: WeeklyReview?,
            execution: CoachExecution?,
        ): Set<Int> = buildSet {
            progress?.let {
                it.adherencePercent?.let(::add)
                it.volumePercent?.let(::add)
                it.easyPercent28d?.let(::add)
            }
            review?.volume?.percentOfPlan?.let(::add)
            execution?.comparison?.durationPercentOfPlan?.let(::add)
            execution?.comparison?.distancePercentOfPlan?.let(::add)
            if (progress?.easyPercent28d != null) add(CoachProgressAnswer.EASY_TARGET_PERCENT)
        }

        private fun allowedDistances(
            plan: WeeklyPlan?,
            prescription: DailyPrescription?,
            execution: CoachExecution?,
        ): Set<Double> = buildSet {
            plan?.sessions?.forEach { session -> session.targets.distanceKm?.let(::add) }
            prescription?.targets?.distanceKm?.let(::add)
            execution?.distanceKm?.let { add(roundToOneDecimal(it)) }
        }

        private fun allowedCounts(
            plan: WeeklyPlan?,
            progress: CoachProgressSummary?,
            review: WeeklyReview?,
            execution: CoachExecution?,
            readinessScore: Int?,
        ): Set<Int> = buildSet {
            add(DAYS_IN_WEEK)
            readinessScore?.let(::add)
            execution?.rpe?.let(::add)
            plan?.let { week ->
                add(week.sessions.size)
                add(week.qualityCount)
                add(week.sessions.count { it.type == WorkoutType.EASY })
                add(week.sessions.count { it.type == WorkoutType.LONG })
                add(week.sessions.count { it.type == WorkoutType.QUALITY })
                add(week.sessions.count { it.type == WorkoutType.REST })
                add(week.sessions.count { it.status == SessionStatus.DONE })
                add(week.sessions.count { it.status == SessionStatus.CLOSED })
                add(week.sessions.count { it.status == SessionStatus.PLANNED })
            }
            progress?.let {
                add(it.availableWeeks)
                add(it.plannedSessions)
                add(it.doneSessions)
                add(it.redDays)
                add(it.shortNights)
            }
            review?.let {
                it.adherence?.let { adherence ->
                    add(adherence.planned)
                    add(adherence.done)
                    add(adherence.verified)
                    add(adherence.manual)
                    add(adherence.legacy)
                    add(adherence.skipped)
                }
                it.quality?.let { quality ->
                    add(quality.planned)
                    add(quality.kept)
                    add(quality.downgraded)
                }
                it.readiness?.let { readiness ->
                    add(readiness.measuredDays)
                    add(readiness.redDays)
                }
                it.sleep?.let { sleep ->
                    add(sleep.measuredNights)
                    add(sleep.shortNights)
                }
            }
            if (progress?.easyPercent28d != null) {
                add(CoachProgressAnswer.EASY_TARGET_PERCENT)
                add(100 - CoachProgressAnswer.EASY_TARGET_PERCENT)
            }
        }

        private fun facts(question: String, context: CoachOpenContext): String = buildString {
            val prescription = context.prescription?.takeIf { it.date == context.today }
            val review = context.lastReview?.takeIf { it.status == WeeklyReviewStatus.AVAILABLE }
            appendLine("PERGUNTA DO ATLETA: $question")
            appendLine("HOJE: ${weekday(context.today.dayOfWeek)}.")
            appendLine(
                weekFacts(
                    plan = context.plan,
                    today = context.today,
                    executedSession = context.executedSession,
                    executionPrescription = context.executionPrescription,
                ),
            )
            prescription?.let { append(prescriptionFacts(it)) }
            if (context.execution != null && context.execution.date != context.today) {
                context.executionPrescription?.let {
                    appendLine("MOTIVO DO DIA (${dayLabel(it.date, context.today)}): ${it.rationale}")
                }
            }
            context.execution?.let {
                append(
                    executionFacts(
                        execution = it,
                        today = context.today,
                        hasPrescription = context.executionPrescription != null,
                    ),
                )
            }
            context.readiness?.let { appendLine("PRONTIDÃO: ${readinessLine(it)}.") }
            context.sleepMinutes?.let { appendLine("SONO: $it min na última noite.") }
            appendLine("CHECK-IN: ${if (context.hasCheckIn) "feito" else "pendente"}.")
            context.progress?.let { appendLine("ÚLTIMAS 4 SEMANAS: ${progressLine(it)}.") }
            review?.let { appendLine("ÚLTIMA SEMANA FECHADA: ${reviewLine(it)}.") }
            append(TASK_LINE)
        }

        private fun weekFacts(
            plan: WeeklyPlan?,
            today: LocalDate,
            executedSession: PlannedSession?,
            executionPrescription: DailyPrescription?,
        ): String {
            if (plan == null) return "SEMANA: ainda sem plano gerado."
            return buildString {
                appendLine(
                    "SEMANA: ${plan.weeklyMinutes} min planejados; ${plan.sessions.size} treinos.",
                )
                plan.sessions.forEach { session ->
                    append("- ${weekday(session.date.dayOfWeek)}: ")
                    val adjusted = executionPrescription.takeIf { session.date == executedSession?.date }
                    if (adjusted != null) {
                        append(adjusted.title)
                        append(" · ${duration(adjusted.prescribedDurationMin)}")
                        append(" · ${kindLabel(adjusted.prescribedType)}")
                        append(" · ${statusLabel(session.status)}")
                        append(" · ajustado do plano: ")
                        append(session.title)
                        append(" · ${duration(session.durationMin)}")
                        append(" · ${kindLabel(session.type)}")
                    } else {
                        append(session.title)
                        append(" · ${duration(session.durationMin)}")
                        append(" · ${kindLabel(session.type)}")
                        append(" · ${statusLabel(session.status)}")
                    }
                    if (session.date == today) append(" · HOJE")
                    appendLine()
                }
                appendLine("MOTIVO DA SEMANA: ${plan.rationale}")
            }
        }

        private fun prescriptionFacts(prescription: DailyPrescription): String = buildString {
            appendLine(
                "PRESCRIÇÃO DE HOJE: ${prescription.title} · " +
                    "${duration(prescription.prescribedDurationMin)} (${prescription.adjustment.label()}).",
            )
            appendLine("MOTIVO DE HOJE: ${prescription.rationale}")
        }

        private fun executionFacts(execution: CoachExecution, today: LocalDate, hasPrescription: Boolean): String =
            buildString {
                append(executionLabel(execution.date, today))
                append(": ${execution.durationMin} min")
                execution.distanceKm?.let { append(" · ${formatDistance(it)} km") }
                execution.rpe?.let { append(" · RPE $it") }
                execution.evidence?.let { append(" · evidência ${it.label()}") }
                appendLine(".")
                val comparison = execution.comparison ?: return@buildString
                if (comparison.kind == SessionComparisonKind.FREE_RUN) {
                    appendLine("CORRIDA LIVRE: sem sessão planejada vinculada; não há alvo para comparar.")
                } else {
                    val source = if (hasPrescription) "COMPARAÇÃO COM A PRESCRIÇÃO" else "COMPARAÇÃO COM O PLANO"
                    appendLine(
                        "$source: " +
                            "duração ${verdictLabel(comparison.durationVerdict)}; " +
                            "distância ${verdictLabel(comparison.distanceVerdict)}; " +
                            "ritmo ${verdictLabel(comparison.paceVerdict)}; " +
                            "FC ${verdictLabel(comparison.heartRateVerdict)}; " +
                            "esforço ${verdictLabel(comparison.rpeVerdict)}.",
                    )
                }
            }

        /**
         * The execution in the dossier may be today's or the last completed
         * session (e.g. a rest day after a quality session). Name it
         * explicitly so the model never presents yesterday's run as today's.
         */
        private fun executionLabel(date: LocalDate, today: LocalDate): String {
            if (date == today) return "EXECUTADO HOJE"
            return "EXECUTADO NO ÚLTIMO TREINO (${dayLabel(date, today)})"
        }

        private fun dayLabel(date: LocalDate, today: LocalDate): String {
            val weekStart = today.with(DayOfWeek.MONDAY)
            val suffix = if (date.isBefore(weekStart)) " passada" else ""
            return "${weekday(date.dayOfWeek)}$suffix"
        }

        private fun verdictLabel(verdict: ComparisonVerdict?): String = when (verdict) {
            ComparisonVerdict.WITHIN -> "dentro do alvo"
            ComparisonVerdict.ABOVE -> "acima do alvo"
            ComparisonVerdict.BELOW -> "abaixo do alvo"
            null -> "sem alvo comparável"
        }

        private fun CompletionEvidence.label(): String = when (this) {
            CompletionEvidence.ACTIVITY -> "atividade importada"
            CompletionEvidence.GUIDED -> "treino guiado"
            CompletionEvidence.MANUAL -> "registro manual"
            CompletionEvidence.LEGACY_UNVERIFIED -> "registro legado"
        }

        private fun formatDistance(km: Double): String = "%.1f".format(java.util.Locale.US, km)

        private fun roundToOneDecimal(value: Double): Double = (value * 10.0).roundToInt() / 10.0

        private const val DAYS_IN_WEEK = 7

        private const val TASK_LINE =
            "TAREFA: responder à pergunta do atleta em conversa de treinador, usando SÓ os fatos acima. " +
                "Pode explicar conceitos de corrida e o plano em linguagem simples. " +
                "Nunca invente número, pace, FC, carga, volume, VDOT ou ACWR; se um número não estiver " +
                "nos fatos, não escreva. Ritmo e FC ficam no cartão do motor: não escreva pace nem bpm " +
                "no texto, use só a comparação qualitativa. Em dia executado, o alvo da avaliação é a " +
                "prescrição do dia; o plano original é só contexto — se o dia foi ajustado, explique o " +
                "plano original × a prescrição. Se a resposta não estiver nos fatos, diga o " +
                "que falta e indique Hoje, Tendências ou o check-in. Nunca prescreva treino novo, nunca " +
                "troque treino, nunca dê diagnóstico, nutrição ou musculação. " +
                "Aceite só o ajuste do motor com confirmação do atleta."

        private fun duration(minutes: Int?): String = minutes?.let { "$it min" } ?: "sem duração fixa"

        private fun weekday(day: DayOfWeek): String = when (day) {
            DayOfWeek.MONDAY -> "Segunda"
            DayOfWeek.TUESDAY -> "Terça"
            DayOfWeek.WEDNESDAY -> "Quarta"
            DayOfWeek.THURSDAY -> "Quinta"
            DayOfWeek.FRIDAY -> "Sexta"
            DayOfWeek.SATURDAY -> "Sábado"
            DayOfWeek.SUNDAY -> "Domingo"
        }

        private fun kindLabel(type: WorkoutType): String = when (type) {
            WorkoutType.REST -> "descanso"
            WorkoutType.EASY -> "fácil"
            WorkoutType.LONG -> "longo"
            WorkoutType.QUALITY -> "forte"
        }

        private fun statusLabel(status: SessionStatus): String = when (status) {
            SessionStatus.PLANNED -> "planejado"
            SessionStatus.DONE -> "feito"
            SessionStatus.CLOSED -> "fechado"
        }

        private fun readinessLine(readiness: ReadinessResult): String {
            val level = when (readiness.level) {
                ReadinessLevel.GREEN -> "verde"
                ReadinessLevel.AMBER -> "moderada"
                ReadinessLevel.RED -> "vermelha"
            }
            val flags = buildList {
                if (ReadinessFlag.ILLNESS_OR_INJURY in readiness.flags) add("sinal de doença/lesão")
                if (ReadinessFlag.SHORT_SLEEP in readiness.flags) add("sono curto")
                if (ReadinessFlag.INSUFFICIENT_DATA in readiness.flags) add("dados insuficientes")
                if (ReadinessFlag.SUBJECTIVE_MISSING in readiness.flags) add("check-in subjetivo pendente")
            }
            val score = readiness.score.takeIf { readiness.components.isNotEmpty() }
            return buildString {
                append(level)
                score?.let { append(" (score $it)") }
                if (flags.isNotEmpty()) append("; ${flags.joinToString(", ")}")
            }
        }

        private fun progressLine(progress: CoachProgressSummary): String = buildString {
            append("${progress.availableWeeks} semanas com dados; ")
            append("${progress.doneSessions} de ${progress.plannedSessions} treinos")
            progress.adherencePercent?.let { append(" ($it%)") }
            append("; ${progress.executedMinutes} de ${progress.plannedMinutes} min feitos")
            progress.volumePercent?.let { append(" ($it%)") }
            progress.easyPercent28d?.let { append("; fácil em $it% dos últimos 28 dias") }
        }

        private fun reviewLine(review: WeeklyReview): String = buildString {
            val adherence = review.adherence
            if (adherence != null) {
                append("${adherence.done} de ${adherence.planned} treinos")
            } else {
                append("sem adesão calculada")
            }
            review.volume?.percentOfPlan?.let { append("; $it% do volume planejado") }
            review.quality?.let { append("; qualidade mantida em ${it.kept} de ${it.planned}") }
        }

        private fun PrescriptionAdjustment.label(): String = when (this) {
            PrescriptionAdjustment.KEEP -> "mantido"
            PrescriptionAdjustment.REDUCE -> "reduzido"
            PrescriptionAdjustment.DOWNGRADE -> "ajustado para fácil"
            PrescriptionAdjustment.REST -> "descanso"
            PrescriptionAdjustment.NO_PLAN -> "fácil conservador"
        }
    }
}
