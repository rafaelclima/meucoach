package com.meucoach.core.domain.coach

/**
 * Deterministic evolution context for ASK_PROGRESS (ADR 0057).
 *
 * Pure Kotlin/JVM: given the same rollup the Tendências tab renders, it
 * builds the only numbers the answer may cite plus the `engineContext` the
 * proxy rewrites. It never judges ("boa"/"ruim"), never projects the future
 * and never cites VDOT, readiness scores or sleep minutes — the answer stays
 * a chatty rollup in runner talk, with the numbers living in the anexo.
 */
data class ProgressAnswer(
    val engineContext: String,
    val allowedMinutes: Set<Int>,
    val allowedPercents: Set<Int>,
    /**
     * Deterministic answer shown when the model is unreachable (ADR 0057):
     * the rollup in plain words, no invention possible.
     */
    val fallback: String,
)

object CoachProgressAnswer {
    /**
     * Share of easy running the coach defends. Static training principle
     * (80/20, `SPORTS_SCIENCE.md`), not an invention: it is only ever cited
     * as the "~80%" target next to the measured share.
     */
    const val EASY_TARGET_PERCENT = 80

    fun build(summary: CoachProgressSummary): ProgressAnswer {
        val adherence = summary.adherencePercent
        val volume = summary.volumePercent
        val easy = summary.easyPercent28d
        val quadro = buildString {
            append("${summary.availableWeeks} de 4 semanas com dados: ")
            append("${summary.doneSessions} de ${summary.plannedSessions} treinos")
            if (adherence != null) append(" ($adherence%)")
            append(". ")
            append(
                "Volume: ${summary.plannedMinutes} min planejados, " +
                    "${summary.executedMinutes} min feitos",
            )
            if (volume != null) append(" ($volume%)")
            append(". ")
            if (easy != null) {
                append("Fácil: $easy% dos últimos 28 dias (alvo ~$EASY_TARGET_PERCENT%).")
            } else {
                append("Fácil: ainda sem medição nos últimos 28 dias.")
            }
        }
        return ProgressAnswer(
            engineContext =
            "TAREFA: contar a evolução das últimas 4 semanas em linguagem de rua, " +
                "sem citar pace, FC, VDOT ou números novos. Comece pelo feito, sem culpar " +
                "semana fraca, feche olhando pra frente sem prometer compensação. " +
                "Feche variando: Bora? / Fechado? / Topa? / Combinado? " +
                "QUADRO: $quadro",
            allowedMinutes = setOf(summary.plannedMinutes, summary.executedMinutes, summary.extraMinutes),
            allowedPercents = setOfNotNull(adherence, volume, easy, EASY_TARGET_PERCENT),
            fallback = buildString {
                if (summary.availableWeeks == 1) {
                    append("Última semana: ")
                } else {
                    append("Últimas ${summary.availableWeeks} semanas: ")
                }
                append("${summary.doneSessions} de ${summary.plannedSessions} treinos")
                if (adherence != null) append(" ($adherence%)")
                append(", ${summary.executedMinutes} de ${summary.plannedMinutes} min feitos")
                if (volume != null) append(" ($volume%)")
                append(".")
                if (easy != null) append(" Fácil em $easy% (alvo ~$EASY_TARGET_PERCENT%).")
            },
        )
    }
}
