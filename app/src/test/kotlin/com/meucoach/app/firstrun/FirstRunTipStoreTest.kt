package com.meucoach.app.firstrun

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirstRunTipStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `marks a tip as shown and persists it`() {
        val prefsName = "first-run-tips-${System.nanoTime()}"
        val store = FirstRunTipStore(context, prefsName)

        assertThat(store.isShown("activities-empty")).isFalse()
        store.markShown("activities-empty")

        assertThat(store.isShown("activities-empty")).isTrue()
        assertThat(FirstRunTipStore(context, prefsName).isShown("activities-empty")).isTrue()
        assertThat(store.isShown("trends-empty")).isFalse()
    }
}
