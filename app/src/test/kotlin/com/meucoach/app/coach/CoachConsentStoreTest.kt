package com.meucoach.app.coach

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
class CoachConsentStoreTest {
    private lateinit var store: CoachConsentStore
    private lateinit var context: Context
    private lateinit var prefsName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        prefsName = "coach-consent-${System.nanoTime()}"
        store = CoachConsentStore(context, prefsName)
    }

    @Test
    fun `consent starts denied`() {
        assertThat(store.isConsented()).isFalse()
    }

    @Test
    fun `grant and revoke round-trip`() {
        store.grant()
        assertThat(store.isConsented()).isTrue()
        store.revoke()
        assertThat(store.isConsented()).isFalse()
    }

    @Test
    fun `an old summary-only consent does not cover typed questions`() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("consented", true)
            .apply()

        assertThat(store.isConsented()).isFalse()

        store.grant()
        assertThat(store.isConsented()).isTrue()
    }
}
