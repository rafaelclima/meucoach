package com.meucoach.core.domain.prescription

import java.time.LocalDate
import kotlin.math.abs

sealed interface SwapResult {
    data class Applied(val plan: WeeklyPlan) : SwapResult
    data class Rejected(val reason: String) : SwapResult
}

/**
 * Swaps the content of two planned sessions in the same week, keeping each
 * date and status. The resulting week must still respect the quality/long-run
 * spacing rules, so a swap can be rejected even when both sessions are planned.
 */
object SessionSwapper {
    fun swap(
        plan: WeeklyPlan,
        firstDate: LocalDate,
        secondDate: LocalDate,
        today: LocalDate,
        /** Event dates never receive an ordinary session (ADR 0030). */
        raceDates: Set<LocalDate> = emptySet(),
        /** Post-race recovery days do not enter the swap (ADR 0037). */
        recovery: RecoveryWindow? = null,
    ): SwapResult {
        if (firstDate == secondDate) return SwapResult.Rejected("Escolha outro dia.")
        if (firstDate in raceDates || secondDate in raceDates) {
            return SwapResult.Rejected("Dia de prova não entra na troca de treinos.")
        }
        if (recovery != null && (recovery.isActive(firstDate) || recovery.isActive(secondDate))) {
            return SwapResult.Rejected("Dia de recuperação após a prova não entra na troca.")
        }
        val first = plan.sessions.firstOrNull { it.date == firstDate }
            ?: return SwapResult.Rejected("Sessão de hoje não encontrada.")
        val second = plan.sessions.firstOrNull { it.date == secondDate }
            ?: return SwapResult.Rejected("Sessão escolhida não encontrada.")
        if (first.status != SessionStatus.PLANNED || second.status != SessionStatus.PLANNED) {
            return SwapResult.Rejected("Só é possível trocar sessões planejadas.")
        }
        if (firstDate.isBefore(today) || secondDate.isBefore(today)) {
            return SwapResult.Rejected("Não é possível trocar dias que já passaram.")
        }
        if (first.type == WorkoutType.REST || second.type == WorkoutType.REST) {
            return SwapResult.Rejected("Dia de descanso não entra na troca.")
        }

        val swapped = plan.sessions.map { session ->
            when (session.date) {
                firstDate -> session.copy(content = second.content)
                secondDate -> session.copy(content = first.content)
                else -> session
            }
        }
        validate(swapped)?.let { reason -> return SwapResult.Rejected(reason) }

        return SwapResult.Applied(
            plan.copy(
                sessions = swapped,
                weeklyMinutes = swapped.sumOf { it.durationMin ?: 0 },
                longRunMinutes = swapped.firstOrNull { it.type == WorkoutType.LONG }?.durationMin,
                qualityCount = swapped.count { it.type == WorkoutType.QUALITY },
            ),
        )
    }

    private fun validate(sessions: List<PlannedSession>): String? {
        val qualities = sessions.filter { it.type == WorkoutType.QUALITY }
        if (qualities.size > 2) return "O plano permite no máximo 2 treinos fortes por semana."
        val longDays = sessions.filter { it.type == WorkoutType.LONG }.map { it.date.dayOfWeek }
        qualities.forEach { quality ->
            val day = quality.date.dayOfWeek
            if (longDays.any { it == day || adjacent(it, day) }) {
                return "Treino forte não pode ficar no dia do longão nem ao lado dele."
            }
        }
        qualities.forEachIndexed { index, quality ->
            qualities.drop(index + 1).forEach { other ->
                if (adjacent(quality.date.dayOfWeek, other.date.dayOfWeek)) {
                    return "Não deixe dois treinos fortes em dias consecutivos."
                }
            }
        }
        return null
    }

    private fun adjacent(a: java.time.DayOfWeek, b: java.time.DayOfWeek): Boolean {
        val diff = abs(a.value - b.value)
        return diff == 1 || diff == 6
    }
}

private data class SessionContent(
    val type: WorkoutType,
    val title: String,
    val durationMin: Int?,
    val rationale: String,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val targets: PrescriptionTargets,
)

private val PlannedSession.content: SessionContent
    get() = SessionContent(
        type = type,
        title = title,
        durationMin = durationMin,
        rationale = rationale,
        ruleIds = ruleIds,
        evidenceIds = evidenceIds,
        targets = targets,
    )

private fun PlannedSession.copy(content: SessionContent): PlannedSession = copy(
    type = content.type,
    title = content.title,
    durationMin = content.durationMin,
    rationale = content.rationale,
    ruleIds = content.ruleIds,
    evidenceIds = content.evidenceIds,
    targets = content.targets,
)
