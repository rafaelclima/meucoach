package com.meucoach.app.ui.coach

/**
 * Trainer voice for the Coach tab (usuário 0: falar como treinador, não como planilha).
 *
 * The engine titles stay official everywhere else (Hoje, Tendências, replan
 * pickers); only the Coach chat translates them into Brazilian runner talk.
 * Unknown titles pass through untouched — the mapper never invents a workout.
 * Case-insensitive on purpose: the engine happens to send title case today.
 */
object CoachSpeechLabels {
    /** Workout name as a headline (title case, for cards). */
    fun speechTitle(title: String): String = translate(title) ?: title.trim().ifEmpty { "Treino" }

    /** Workout name mid-sentence (lowercase, for bubbles). */
    fun speechInline(title: String): String = speechTitle(title).replaceFirstChar { it.lowercase() }

    private val rules: List<Pair<Regex, String?>> = listOf(
        Regex("recupera") to null,
        Regex("surtos") to "Rodagem com estímulos",
        Regex("^fácil") to "Rodagem",
        Regex("ritmo forte") to null,
        Regex("intervalo") to "Intervalado",
        Regex("fartlek") to null,
        Regex("colina") to "Subidas",
        Regex("pace de prova") to null,
        Regex("stride") to "Acelerações curtas",
        Regex("long") to "Longão",
        Regex("descanso") to null,
    )

    private fun translate(title: String): String? {
        val normalized = title.trim().lowercase()
        if (normalized.isEmpty()) return null
        return rules.firstOrNull { (pattern, _) -> pattern.containsMatchIn(normalized) }?.second
    }
}
