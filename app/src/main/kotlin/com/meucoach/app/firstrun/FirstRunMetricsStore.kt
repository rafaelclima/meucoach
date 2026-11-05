package com.meucoach.app.firstrun

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.time.Clock

enum class FirstActionType {
    CHECK_IN,
    IMPORT,
    HEALTH,
}

/**
 * Privacy-preserving activation metrics stored on the device. Nothing is sent anywhere; the
 * timestamps are exposed in the user's own JSON export and can drive local diagnostics.
 */
class FirstRunMetricsStore(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    prefsName: String = PREFS_NAME,
) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun markTutorialStarted() {
        putOnce(KEY_TUTORIAL_STARTED, clock.instant().toString())
    }

    fun markTutorialCompleted() {
        putOnce(KEY_TUTORIAL_COMPLETED, clock.instant().toString())
    }

    fun markTutorialSkipped() {
        putOnce(KEY_TUTORIAL_SKIPPED, clock.instant().toString())
    }

    fun markTutorialStep(step: Int) {
        require(step > 0) { "Step must be positive" }
        val current = prefs.getInt(KEY_TUTORIAL_STEP, 0)
        if (step > current) {
            prefs.edit { putInt(KEY_TUTORIAL_STEP, step) }
        }
    }

    fun markFirstAction(type: FirstActionType) {
        if (prefs.contains(KEY_FIRST_ACTION_AT)) return
        prefs.edit {
            putString(KEY_FIRST_ACTION_TYPE, type.name)
            putString(KEY_FIRST_ACTION_AT, clock.instant().toString())
        }
    }

    fun markChecklistDismissed() {
        putOnce(KEY_CHECKLIST_DISMISSED, clock.instant().toString())
    }

    fun markChecklistCompleted() {
        putOnce(KEY_CHECKLIST_COMPLETED, clock.instant().toString())
    }

    fun toJson(): JSONObject = JSONObject().apply {
        putNullable(KEY_TUTORIAL_STARTED, prefs.getString(KEY_TUTORIAL_STARTED, null))
        putNullable(KEY_TUTORIAL_COMPLETED, prefs.getString(KEY_TUTORIAL_COMPLETED, null))
        putNullable(KEY_TUTORIAL_SKIPPED, prefs.getString(KEY_TUTORIAL_SKIPPED, null))
        put(KEY_TUTORIAL_STEP, prefs.getInt(KEY_TUTORIAL_STEP, 0))
        putNullable(KEY_FIRST_ACTION_TYPE, prefs.getString(KEY_FIRST_ACTION_TYPE, null))
        putNullable(KEY_FIRST_ACTION_AT, prefs.getString(KEY_FIRST_ACTION_AT, null))
        putNullable(KEY_CHECKLIST_DISMISSED, prefs.getString(KEY_CHECKLIST_DISMISSED, null))
        putNullable(KEY_CHECKLIST_COMPLETED, prefs.getString(KEY_CHECKLIST_COMPLETED, null))
    }

    private fun putOnce(key: String, value: String) {
        if (prefs.contains(key)) return
        prefs.edit { putString(key, value) }
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        put(key, value ?: JSONObject.NULL)
    }

    companion object {
        const val PREFS_NAME = "meucoach_first_run_metrics"
        const val KEY_TUTORIAL_STARTED = "tutorialStartedAt"
        const val KEY_TUTORIAL_COMPLETED = "tutorialCompletedAt"
        const val KEY_TUTORIAL_SKIPPED = "tutorialSkippedAt"
        const val KEY_TUTORIAL_STEP = "tutorialLastStep"
        const val KEY_FIRST_ACTION_TYPE = "firstActionType"
        const val KEY_FIRST_ACTION_AT = "firstActionAt"
        const val KEY_CHECKLIST_DISMISSED = "checklistDismissedAt"
        const val KEY_CHECKLIST_COMPLETED = "checklistCompletedAt"
    }
}
