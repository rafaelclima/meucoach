package com.meucoach.app.story

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryExportFailureStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `keeps the message until the next export clears it`() {
        val store = StoryExportFailureStore(context)

        store.save("activity-1", "Encoder sem buffer de entrada.")
        assertThat(store.messageFor("activity-1")).isEqualTo("Encoder sem buffer de entrada.")

        store.clear("activity-1")
        assertThat(store.messageFor("activity-1")).isNull()
    }

    @Test
    fun `isolates failures per activity`() {
        val store = StoryExportFailureStore(context)

        store.save("activity-1", "Falha A")

        assertThat(store.messageFor("activity-2")).isNull()
        store.clear("activity-1")
    }
}
