package com.meucoach.app.ui.coach

/**
 * Runner-facing copy of the deterministic fallback (P-10).
 *
 * The validator returns a technical reason; the card must never render it raw.
 * Known friendly reasons pass through, everything else becomes a short copy
 * that never names an internal code, a metric or a rule id.
 */
object CoachFallbackLabels {
    private val friendlyReasons = setOf(
        "Sem conexão com o Coach. Mostrando o resumo do motor.",
        "O Coach não respondeu. Mostrando o resumo do motor.",
    )

    fun reasonCopy(reason: String): String {
        if (reason in friendlyReasons) return reason
        val normalized = reason.lowercase()
        return when {
            "código interno" in normalized ||
                "métrica proibida" in normalized ->
                "A explicação foi descartada por citar um termo interno. Mostrando o resumo do motor."

            "pace" in normalized ->
                "A explicação tentou prescrever ritmo; só o motor prescreve. " +
                    "Mostrando o resumo do motor."

            "fc" in normalized ->
                "A explicação tentou prescrever frequência cardíaca; só o motor prescreve. " +
                    "Mostrando o resumo do motor."

            "duração não planejada" in normalized ->
                "A explicação citou uma duração fora do plano. Mostrando o resumo do motor."

            "porcentagem não calculada" in normalized ->
                "A explicação citou uma porcentagem fora dos números do motor. " +
                    "Mostrando o resumo do motor."

            else -> "A explicação não passou na revisão do motor. Mostrando o resumo do motor."
        }
    }
}
