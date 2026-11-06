package com.meucoach.app.firstrun

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirstRunMetricsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `records the tutorial timeline and the highest step`() {
        val store = store()

        store.markTutorialStarted()
        store.markTutorialStep(1)
        store.markTutorialStep(3)
        store.markTutorialStep(2)
        store.markTutorialCompleted()

        val json = store.toJson()
        assertThat(json.getString(FirstRunMetricsStore.KEY_TUTORIAL_STARTED))
            .isEqualTo("2026-11-05T12:00:00Z")
        assertThat(json.getString(FirstRunMetricsStore.KEY_TUTORIAL_COMPLETED))
            .isEqualTo("2026-11-05T12:00:00Z")
        assertThat(json.getInt(FirstRunMetricsStore.KEY_TUTORIAL_STEP)).isEqualTo(3)
    }

    @Test
    fun `records only the first action`() {
        val store = store()

        store.markFirstAction(FirstActionType.CHECK_IN)
        store.markFirstAction(FirstActionType.IMPORT)

        val json = store.toJson()
        assertThat(json.getString(FirstRunMetricsStore.KEY_FIRST_ACTION_TYPE))
            .isEqualTo(FirstActionType.CHECK_IN.name)
    }

    @Test
    fun `records skip and checklist events`() {
        val store = store()

        store.markTutorialSkipped()
        store.markChecklistDismissed()
        store.markChecklistCompleted()

        val json = store.toJson()
        assertThat(json.getString(FirstRunMetricsStore.KEY_TUTORIAL_SKIPPED))
            .isEqualTo("2026-11-05T12:00:00Z")
        assertThat(json.getString(FirstRunMetricsStore.KEY_CHECKLIST_DISMISSED))
            .isEqualTo("2026-11-05T12:00:00Z")
        assertThat(json.getString(FirstRunMetricsStore.KEY_CHECKLIST_COMPLETED))
            .isEqualTo("2026-11-05T12:00:00Z")
    }

    private fun store() = FirstRunMetricsStore(
        context = context,
        clock = clock,
        prefsName = "first-run-metrics-${System.nanoTime()}",
    )
}
