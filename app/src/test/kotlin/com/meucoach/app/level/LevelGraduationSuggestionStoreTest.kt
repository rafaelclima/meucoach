package com.meucoach.app.level

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.prescription.LevelGraduationEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LevelGraduationSuggestionStoreTest {
    private lateinit var store: LevelGraduationSuggestionStore
    private val today = LocalDate.of(2026, 11, 4)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = LevelGraduationSuggestionStore(context, "level-store-${System.nanoTime()}")
    }

    @Test
    fun `suppression is scoped to the day`() {
        store.suppress(today)

        assertThat(store.isSuppressed(today)).isTrue()
        assertThat(store.isSuppressed(today.plusDays(1))).isFalse()
    }

    @Test
    fun `decline is remembered by profile engine and hash`() {
        store.decline("athlete-1", LevelGraduationEngine.ENGINE_VERSION, "hash-a")

        val expected = LevelGraduationSuggestionStore.suggestionKey(
            LevelGraduationEngine.ENGINE_VERSION,
            "hash-a",
        )
        assertThat(store.declinedInputsHash("athlete-1")).isEqualTo(expected)
        assertThat(store.declinedInputsHash("athlete-2")).isNull()
    }

    @Test
    fun `a new inputs hash is a new proposal`() {
        store.decline("athlete-1", LevelGraduationEngine.ENGINE_VERSION, "hash-a")

        val same = LevelGraduationSuggestionStore.suggestionKey(
            LevelGraduationEngine.ENGINE_VERSION,
            "hash-a",
        )
        val newer = LevelGraduationSuggestionStore.suggestionKey(
            LevelGraduationEngine.ENGINE_VERSION,
            "hash-b",
        )
        assertThat(store.declinedInputsHash("athlete-1")).isEqualTo(same)
        assertThat(store.declinedInputsHash("athlete-1")).isNotEqualTo(newer)
    }

    @Test
    fun `accept clears the decline and the suppression`() {
        store.suppress(today)
        store.decline("athlete-1", LevelGraduationEngine.ENGINE_VERSION, "hash-a")

        store.clearDecline("athlete-1")
        store.clearSuppression(today)

        assertThat(store.declinedInputsHash("athlete-1")).isNull()
        assertThat(store.isSuppressed(today)).isFalse()
    }

    @Test
    fun `reset removes every preference`() {
        store.suppress(today)
        store.decline("athlete-1", LevelGraduationEngine.ENGINE_VERSION, "hash-a")

        store.reset()

        assertThat(store.isSuppressed(today)).isFalse()
        assertThat(store.declinedInputsHash("athlete-1")).isNull()
    }
}
