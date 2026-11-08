package com.meucoach.app.replan

import android.content.Context
import com.meucoach.core.domain.replan.ReplanSuggestion
import java.time.LocalDate

/**
 * Remembers the user's decision about an automatic replan suggestion.
 *
 * A declined suggestion is identified by week/version/inputs, so a new version
 * or a changed context may be proposed again. Suppression hides automatic
 * suggestions for the rest of the local day; the explicit button still works.
 * This is UI preference state, not a training decision.
 */
class ReplanSuggestionStore(context: Context, prefsName: String = PREFS_NAME) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun isSuppressed(date: LocalDate): Boolean = prefs.getString(KEY_SUPPRESSED_DATE, null) == date.toString()

    fun suppress(date: LocalDate) {
        prefs.edit().putString(KEY_SUPPRESSED_DATE, date.toString()).apply()
    }

    fun declinedSuggestion(date: LocalDate): String? = prefs.getString(declinedKey(date), null)

    fun decline(date: LocalDate, suggestion: ReplanSuggestion) {
        prefs.edit().putString(declinedKey(date), suggestionKey(suggestion)).apply()
    }

    fun clear(date: LocalDate) {
        prefs
            .edit()
            .remove(declinedKey(date))
            .remove(KEY_SUPPRESSED_DATE)
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun declinedKey(date: LocalDate): String = "$KEY_DECLINED_PREFIX$date"

    companion object {
        const val PREFS_NAME = "meucoach_replan_suggestions"
        private const val KEY_SUPPRESSED_DATE = "suppressedDate"
        private const val KEY_DECLINED_PREFIX = "declined_"

        /** Stable identity of one proposal: a new version or input set is a new proposal. */
        fun suggestionKey(suggestion: ReplanSuggestion): String =
            "${suggestion.weekStart}|${suggestion.expectedVersion}|" +
                suggestion.expectedInputsHash.take(16)
    }
}
