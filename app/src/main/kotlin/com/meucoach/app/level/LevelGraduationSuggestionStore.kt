package com.meucoach.app.level

import android.content.Context
import java.time.LocalDate

/**
 * Lembra a decisão do usuário sobre a sugestão de graduação de nível.
 *
 * A recusa é identificada por perfil + versão do motor + hash das entradas,
 * então uma nova janela de 6 semanas (novo hash) pode sugerir de novo. A
 * supressão esconde a sugestão automática até o fim do dia local. É
 * preferência de UI, nunca decisão de treino.
 */
class LevelGraduationSuggestionStore(context: Context, prefsName: String = PREFS_NAME) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun isSuppressed(date: LocalDate): Boolean = prefs.getString(KEY_SUPPRESSED_DATE, null) == date.toString()

    fun suppress(date: LocalDate) {
        prefs.edit().putString(KEY_SUPPRESSED_DATE, date.toString()).apply()
    }

    fun declinedInputsHash(profileId: String): String? = prefs.getString(declinedKey(profileId), null)

    fun decline(profileId: String, engineVersion: String, inputsHash: String) {
        prefs.edit().putString(declinedKey(profileId), suggestionKey(engineVersion, inputsHash)).apply()
    }

    fun clearDecline(profileId: String) {
        prefs.edit().remove(declinedKey(profileId)).apply()
    }

    fun clearSuppression(date: LocalDate) {
        if (isSuppressed(date)) prefs.edit().remove(KEY_SUPPRESSED_DATE).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun declinedKey(profileId: String): String = "$KEY_DECLINED_PREFIX$profileId"

    companion object {
        const val PREFS_NAME = "meucoach_level_graduation"

        private const val KEY_SUPPRESSED_DATE = "suppressedDate"
        private const val KEY_DECLINED_PREFIX = "declined_"

        /** Identidade estável de uma proposta: novo motor ou novas entradas é nova proposta. */
        fun suggestionKey(engineVersion: String, inputsHash: String): String = "$engineVersion|$inputsHash"
    }
}
