package com.meucoach.app.ui.coach

/**
 * Runner-facing copy of the deterministic fallback (P-10), in the coach's voice.
 *
 * The validator returns a technical reason; the card must never render it raw.
 * Known friendly reasons pass through, everything else becomes a short
 * first-person line that never names an internal code, a metric or a rule id.
 */
object CoachFallbackLabels {
    private val friendlyReasons = setOf(
        "Sem conexão aqui — te mostro o plano direto do motor.",
        "Me enrolei aqui — te mostro o plano direto.",
    )

    fun reasonCopy(reason: String): String {
        if (reason in friendlyReasons) return reason
        val normalized = reason.lowercase()
        return when {
            "código interno" in normalized ||
                "métrica proibida" in normalized ->
                "Ih, me enrolei na explicação. O plano segue o mesmo — tá aqui embaixo."

            "pace" in normalized ->
                "Quis falar de ritmo e me passei — só o motor prescreve. O plano segue aqui embaixo."

            "fc" in normalized ->
                "Quis falar de frequência cardíaca e me passei — só o motor prescreve. " +
                    "O plano segue aqui embaixo."

            "duração não planejada" in normalized ->
                "Citei uma duração que não tá no plano. Desconsidera — o plano certo tá aqui embaixo."

            "porcentagem não calculada" in normalized ->
                "Citei um percentual que não conferi no motor. Desconsidera — os números certos tão aqui embaixo."

            "número fora do contexto" in normalized ->
                "Citei um número que não confere com seus dados. Desconsidera — o que vale tá aqui embaixo."

            "longa demais" in normalized ->
                "Falei demais e me perdi. Resumindo: o plano segue o mesmo aqui embaixo."

            else -> "Me enrolei na explicação. O plano segue o mesmo — tá aqui embaixo."
        }
    }
}
