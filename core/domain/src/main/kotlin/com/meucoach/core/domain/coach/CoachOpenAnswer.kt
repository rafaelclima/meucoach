package com.meucoach.core.domain.coach

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.model.ReadinessLevel
import java.time.DayOfWeek
import java.time.LocalDate

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
    val readiness: ReadinessResult? = null,
    val hasCheckIn: Boolean = false,
    val sleepMinutes: Int? = null,
    val progress: CoachProgressSummary? = null,
    val lastReview: WeeklyReview? = null,
)

/**
 * Context and number allow-lists for [FreeInputIntent.ASK_COACHING].
 *
 * The model may explain concepts and the plan, but every duration, distance,
 * percentage and count it writes must exist in [allowedMinutes],
 * [allowedDistancesKm], [allowedPercents] or [allowedCounts]. Pace/FC stay out
 * of prose entirely and are shown by the deterministic reference card.
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
            val progress = context.progress
            val review = context.lastReview?.takeIf { it.status == WeeklyReviewStatus.AVAILABLE }
            return CoachOpenAnswer(
                engineContext = facts(
                    question = question,
                    context = context,
                    plan = plan,
                    prescription = prescription,
                    progress = progress,
                    review = review,
                ),
                allowedMinutes = allowedMinutes(context, plan, prescription, progress, review),
                allowedPercents = allowedPercents(progress, review),
                allowedDistancesKm = allowedDistances(plan, prescription),
                allowedCounts = allowedCounts(
                    plan = plan,
                    progress = progress,
                    review = review,
                    readinessScore = context.readiness
                        ?.takeIf { it.components.isNotEmpty() }
                        ?.score,
                ),
            )
        }

        private fun allowedMinutes(
            context: CoachOpenContext,
            plan: WeeklyPlan?,
            prescription: DailyPrescription?,
            progress: CoachProgressSummary?,
            review: WeeklyReview?,
        ): Set<Int> = buildSet {
            plan?.weeklyMinutes?.let(::add)
            plan?.sessions?.forEach { session -> session.durationMin?.let(::add) }
            prescription?.plannedDurationMin?.let(::add)
            prescription?.prescribedDurationMin?.let(::add)
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

        private fun allowedPercents(progress: CoachProgressSummary?, review: WeeklyReview?): Set<Int> = buildSet {
            progress?.let {
                it.adherencePercent?.let(::add)
                it.volumePercent?.let(::add)
                it.easyPercent28d?.let(::add)
            }
            review?.volume?.percentOfPlan?.let(::add)
            if (progress?.easyPercent28d != null) add(CoachProgressAnswer.EASY_TARGET_PERCENT)
        }

        private fun allowedDistances(plan: WeeklyPlan?, prescription: DailyPrescription?): Set<Double> = buildSet {
            plan?.sessions?.forEach { session -> session.targets.distanceKm?.let(::add) }
            prescription?.targets?.distanceKm?.let(::add)
        }

        private fun allowedCounts(
            plan: WeeklyPlan?,
            progress: CoachProgressSummary?,
            review: WeeklyReview?,
            readinessScore: Int?,
        ): Set<Int> = buildSet {
            add(DAYS_IN_WEEK)
            readinessScore?.let(::add)
            plan?.let { week ->
                add(week.sessions.size)
                add(week.qualityCount)
                add(week.sessions.count { it.type == WorkoutType.EASY })
                add(week.sessions.count { it.type == WorkoutType.LONG })
                add(week.sessions.count { it.type == WorkoutType.QUALITY })
                add(week.sessions.count { it.type == WorkoutType.REST })
                add(week.sessions.count { it.status == SessionStatus.DONE })
                add(week.sessions.count { it.status == SessionStatus.CLOSED })
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

        private fun facts(
            question: String,
            context: CoachOpenContext,
            plan: WeeklyPlan?,
            prescription: DailyPrescription?,
            progress: CoachProgressSummary?,
            review: WeeklyReview?,
        ): String = buildString {
            appendLine("PERGUNTA DO ATLETA: $question")
            appendLine("HOJE: ${weekday(context.today.dayOfWeek)}.")
            appendLine(weekFacts(plan, context.today))
            prescription?.let { append(prescriptionFacts(it)) }
            context.readiness?.let { appendLine("PRONTIDÃO: ${readinessLine(it)}.") }
            context.sleepMinutes?.let { appendLine("SONO: $it min na última noite.") }
            appendLine("CHECK-IN: ${if (context.hasCheckIn) "feito" else "pendente"}.")
            progress?.let { appendLine("ÚLTIMAS 4 SEMANAS: ${progressLine(it)}.") }
            review?.let { appendLine("ÚLTIMA SEMANA FECHADA: ${reviewLine(it)}.") }
            append(TASK_LINE)
        }

        private fun weekFacts(plan: WeeklyPlan?, today: LocalDate): String {
            if (plan == null) return "SEMANA: ainda sem plano gerado."
            return buildString {
                appendLine(
                    "SEMANA: ${plan.weeklyMinutes} min planejados; ${plan.sessions.size} treinos.",
                )
                plan.sessions.forEach { session ->
                    append("- ${weekday(session.date.dayOfWeek)}: ${session.title} · ")
                    append(session.durationMin?.let { "$it min" } ?: "sem duração fixa")
                    append(" · ${kindLabel(session.type)} · ${statusLabel(session.status)}")
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

        private const val DAYS_IN_WEEK = 7

        private const val TASK_LINE =
            "TAREFA: responder à pergunta do atleta em conversa de treinador, usando SÓ os fatos acima. " +
                "Pode explicar conceitos de corrida e o plano em linguagem simples. " +
                "Nunca invente número, pace, FC, carga, volume, VDOT ou ACWR; se um número não estiver " +
                "nos fatos, não escreva. Se a resposta não estiver nos fatos, diga o que falta e indique " +
                "Hoje, Tendências ou o check-in. Nunca prescreva treino novo, nunca troque treino, " +
                "nunca dê diagnóstico, nutrição ou musculação. " +
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
