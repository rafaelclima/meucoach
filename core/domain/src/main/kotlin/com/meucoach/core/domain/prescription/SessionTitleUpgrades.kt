package com.meucoach.core.domain.prescription

/**
 * One-shot display repair for plans stored before the runner-talk rename
 * (21/11/2026, usuário 0).
 *
 * Session titles are display copy and never enter a hash or fingerprint, so
 * rewriting them neither invalidates the plan nor writes history: only
 * `PLANNED` sessions are touched, `DONE`/`CLOSED`/anything else keeps the
 * title it trained with. Exact match only — an unknown title is left alone
 * instead of guessed.
 */
object SessionTitleUpgrades {
    private val legacyTitles: Map<String, String> = mapOf(
        "Fácil e conversável" to "Rodagem",
        "Fácil, sem pressa" to "Rodagem leve",
        "Fácil com surtos" to "Rodagem com estímulos",
        "Intervalos" to "Intervalado",
        "Intervalos curtos" to "Intervalado",
        "Colinas" to "Subidas",
        "Strides curtos" to "Acelerações curtas",
    )

    fun modernTitle(title: String): String = legacyTitles[title] ?: title

    fun isLegacy(title: String): Boolean = title in legacyTitles
}
