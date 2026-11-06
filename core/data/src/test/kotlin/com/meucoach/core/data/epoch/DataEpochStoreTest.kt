package com.meucoach.core.data.epoch

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
class DataEpochStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `starts empty and rotates to a durable value`() {
        val prefsName = "epoch-${System.nanoTime()}"
        val store = DataEpochStore(context, clock, prefsName)

        assertThat(store.current()).isEmpty()
        val epoch = store.rotate()

        assertThat(epoch).isNotBlank()
        assertThat(store.current()).isEqualTo(epoch)
        assertThat(DataEpochStore(context, clock, prefsName).current()).isEqualTo(epoch)
    }

    @Test
    fun `each rotation creates a new value`() {
        val store = DataEpochStore(context, clock, prefsName = "epoch-${System.nanoTime()}")

        val first = store.rotate()
        val second = store.rotate()

        assertThat(second).isNotEqualTo(first)
        assertThat(store.current()).isEqualTo(second)
    }
}
