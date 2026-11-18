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

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = CoachConsentStore(context, "coach-consent-${System.nanoTime()}")
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
}
