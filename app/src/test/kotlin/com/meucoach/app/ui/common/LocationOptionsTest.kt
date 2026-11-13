package com.meucoach.app.ui.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class LocationOptionsTest {
    @Test
    fun `a Natal runner sees the own city in the shared timezone label`() {
        // Natal shares America/Fortaleza; the label must not claim another city (U-01).
        assertThat(timezoneLabel("America/Fortaleza")).contains("Natal")
    }

    @Test
    fun `keeps the IANA id when the zone is unknown`() {
        assertThat(timezoneLabel("America/Nowhere")).isEqualTo("America/Nowhere")
    }

    @Test
    fun `every option has a readable label and accent-insensitive search`() {
        AVAILABLE_TIMEZONES.forEach { option ->
            assertThat(option.label).isNotBlank()
        }
        assertThat(matchesSearch("Brasília", "Brasilia")).isTrue()
        assertThat(matchesSearch("Natal · Fortaleza", "natal")).isTrue()
    }
}
