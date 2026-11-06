package com.meucoach.core.data.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HealthSyncStoreTest {
    private lateinit var context: Context
    private lateinit var prefsName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefsName = "health-store-${System.nanoTime()}"
    }

    @Test
    fun `starts empty`() {
        val store = HealthSyncStore(context, prefsName)

        assertThat(store.lastSyncedDate()).isNull()
        assertThat(store.lastSyncedAt()).isNull()
        assertThat(store.lastAttemptAt()).isNull()
    }

    @Test
    fun `persists the date and the timestamps`() {
        val store = HealthSyncStore(context, prefsName)
        val at = Instant.parse("2026-11-05T12:00:00Z")
        val date = LocalDate.of(2026, 11, 5)

        store.markSynced(at, date)

        assertThat(store.lastSyncedDate()).isEqualTo(date)
        assertThat(store.lastSyncedAt()).isEqualTo(at)
        assertThat(store.lastAttemptAt()).isNull()
    }

    @Test
    fun `keeps the legacy date when the timestamp is missing`() {
        context
            .getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString("lastSyncDate", "2026-11-02")
            .apply()
        val store = HealthSyncStore(context, prefsName)

        assertThat(store.lastSyncedDate()).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(store.lastSyncedAt()).isNull()
    }

    @Test
    fun `reset clears the date, the sync time and the attempt`() {
        val store = HealthSyncStore(context, prefsName)
        val at = Instant.parse("2026-11-05T12:00:00Z")
        store.markSynced(at, LocalDate.of(2026, 11, 5))
        store.markAttempt(at.plusSeconds(60))

        store.reset()

        assertThat(store.lastSyncedDate()).isNull()
        assertThat(store.lastSyncedAt()).isNull()
        assertThat(store.lastAttemptAt()).isNull()
    }
}
