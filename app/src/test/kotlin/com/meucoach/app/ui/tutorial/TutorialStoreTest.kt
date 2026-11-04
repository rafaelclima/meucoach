package com.meucoach.app.ui.tutorial

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TutorialStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

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
}
