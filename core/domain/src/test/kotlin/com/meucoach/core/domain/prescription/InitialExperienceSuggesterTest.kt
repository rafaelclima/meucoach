package com.meucoach.core.domain.prescription

import com.meucoach.core.model.Experience
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class InitialExperienceSuggesterTest {
    @Test
    fun `beginner with valid cold-start suggests intermediate`() {
        val suggestion = InitialExperienceSuggester.suggest(Experience.BEGINNER, coldStartValid = true)

        assertThat(suggestion).isNotNull()
        assertThat(suggestion!!.to).isEqualTo(Experience.INTERMEDIATE)
        assertThat(suggestion.reason).contains("Intermediário")
    }

    @Test
    fun `beginner without cold-start gets no suggestion`() {
        assertThat(InitialExperienceSuggester.suggest(Experience.BEGINNER, coldStartValid = false)).isNull()
    }

    @Test
    fun `intermediate never gets a suggestion`() {
        assertThat(InitialExperienceSuggester.suggest(Experience.INTERMEDIATE, coldStartValid = true)).isNull()
    }

    @Test
    fun `advanced never gets a suggestion`() {
        assertThat(InitialExperienceSuggester.suggest(Experience.ADVANCED, coldStartValid = true)).isNull()
    }
}
