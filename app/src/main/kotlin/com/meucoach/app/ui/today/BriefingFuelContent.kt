package com.meucoach.app.ui.today

/**
 * F4 educational content approved by ADR 0029 (gate C3-G2, copy in COMMUNICATION.md §6.10).
 *
 * This is static product copy, never engine output: it does not derive from the
 * prescription, does not alter confidence and must never contain a number, unit,
 * dose, product, electrolyte or schedule. Hydration lines stay generic because
 * every quantitative guidance in the reviewed sources (ACSM 2007, NATA 2017,
 * Thomas 2016) requires individual context the app does not have. Nutrition is a
 * referral, not advice. The race-day branch never renders these sections (FR-404).
 */
internal object BriefingFuelContent {
    const val HYDRATION_TITLE: String = "HIDRATAÇÃO"
    const val NUTRITION_TITLE: String = "ALIMENTAÇÃO"

    const val BEFORE_RUN_LABEL: String = "Antes de correr"
    const val DURING_LABEL: String = "Durante"
    const val AFTER_LABEL: String = "Depois"

    const val BEFORE_RUN: String = "Comece o treino já hidratado. Nos minutos antes, beba água com calma."
    const val DURING: String =
        "Em corridas curtas, beba quando sentir sede. Em treinos longos ou no calor, planeje pausas para beber."
    const val AFTER: String = "Nas horas depois do treino, reponha líquidos junto com a alimentação de sempre."

    /** Always visible: declares the app limit honestly and blocks a universal-advice reading. */
    const val INDIVIDUAL_NOTE: String =
        "O quanto beber varia de pessoa para pessoa — suor, clima, duração. O MeuCoach não calcula quanto você deve beber."

    /** Referral only; no dietary tip is approved by the F4 gate. */
    const val NUTRITION_REFERRAL: String =
        "O MeuCoach não prescreve dietas nem suplementos. Para um plano alimentar individual, procure um nutricionista."

    val allStrings: List<String> =
        listOf(
            HYDRATION_TITLE,
            NUTRITION_TITLE,
            BEFORE_RUN_LABEL,
            DURING_LABEL,
            AFTER_LABEL,
            BEFORE_RUN,
            DURING,
            AFTER,
            INDIVIDUAL_NOTE,
            NUTRITION_REFERRAL,
        )
}
