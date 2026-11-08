package com.meucoach.app.replan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.replan.ReplanSuggestion
import com.meucoach.core.domain.replan.ReplanTrigger
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReplanSuggestionStoreTest {
    private lateinit var store: ReplanSuggestionStore
    private val today = LocalDate.of(2026, 11, 4)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = ReplanSuggestionStore(context, "replan-store-${System.nanoTime()}")
    }

    @Test
    fun `suppression is scoped to the day`() {
        store.suppress(today)

        assertThat(store.isSuppressed(today)).isTrue()
        assertThat(store.isSuppressed(today.plusDays(1))).isFalse()
    }

    @Test
    fun `declining stores the proposal identity and a new one is different`() {
        val first = suggestion(version = 1, hash = "hash-a")
        val second = suggestion(version = 1, hash = "hash-a")
        val third = suggestion(version = 2, hash = "hash-b")

        store.decline(today, first)

        assertThat(store.declinedSuggestion(today))
            .isEqualTo(ReplanSuggestionStore.suggestionKey(second))
        assertThat(store.declinedSuggestion(today))
            .isNotEqualTo(ReplanSuggestionStore.suggestionKey(third))
    }

    @Test
    fun `clear and reset remove the preference`() {
        store.suppress(today)
        store.decline(today, suggestion())

        store.clear(today)
        assertThat(store.isSuppressed(today)).isFalse()
        assertThat(store.declinedSuggestion(today)).isNull()

        store.suppress(today)
        store.reset()
        assertThat(store.isSuppressed(today)).isFalse()
    }

    private fun suggestion(version: Int = 1, hash: String = "hash-a") = ReplanSuggestion(
        weekStart = today.minusDays(2),
        trigger = ReplanTrigger.USER_REQUEST,
        missedTrainableCount = 0,
        expectedVersion = version,
        expectedInputsHash = hash,
        expectedAvailabilityHash = null,
        availabilityHash = "availability",
        rationale = "Revisão.",
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-DANIELS-2014"),
        confidence = DataConfidence.MEDIUM,
    )
}
