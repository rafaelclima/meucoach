package com.meucoach.app.ui.tutorial

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TutorialStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(TutorialStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `starts with the intro unseen`() {
        assertThat(TutorialStore(context).isIntroDone()).isFalse()
    }

    @Test
    fun `marks the intro as done and persists it`() {
        val store = TutorialStore(context)
        store.markIntroDone()

        assertThat(store.isIntroDone()).isTrue()
        assertThat(TutorialStore(context).isIntroDone()).isTrue()
    }

    @Test
    fun `dismisses the checklist for seven days`() {
        val now = 1_000_000L
        val store = TutorialStore(context)

        store.dismissChecklist(nowMillis = now)

        assertThat(store.isChecklistDismissed(nowMillis = now + 1)).isTrue()
        assertThat(store.isChecklistDismissed(nowMillis = now + 7 * 24 * 60 * 60 * 1000L - 1)).isTrue()
    }

    @Test
    fun `reopens the checklist after the dismissal window`() {
        val now = 1_000_000L
        val store = TutorialStore(context)

        store.dismissChecklist(nowMillis = now)

        assertThat(store.isChecklistDismissed(nowMillis = now + 7 * 24 * 60 * 60 * 1000L + 1)).isFalse()
    }
}
