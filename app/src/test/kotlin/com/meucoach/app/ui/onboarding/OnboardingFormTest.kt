package com.meucoach.app.ui.onboarding

import com.meucoach.app.ui.common.AVAILABLE_CITIES
import com.meucoach.app.ui.common.AVAILABLE_TIMEZONES
import com.meucoach.app.ui.common.matchesSearch
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Test
import java.time.ZoneId

class OnboardingFormTest {
    @Test
    fun `offers only valid IANA timezones`() {
        assertThat(AVAILABLE_TIMEZONES).isNotEmpty()
        AVAILABLE_TIMEZONES.forEach { option ->
            assertThatCode { ZoneId.of(option.zoneId) }.doesNotThrowAnyException()
            assertThat(option.label).isNotBlank()
        }
        assertThat(AVAILABLE_TIMEZONES.map { it.zoneId }).doesNotHaveDuplicates()
    }

    @Test
    fun `offers no duplicate or blank cities`() {
        assertThat(AVAILABLE_CITIES).isNotEmpty()
        assertThat(AVAILABLE_CITIES).doesNotHaveDuplicates()
        assertThat(AVAILABLE_CITIES).allSatisfy { assertThat(it).isNotBlank() }
    }

    @Test
    fun `searches options without accents`() {
        assertThat(matchesSearch("Brasília · Sudeste", "brasilia")).isTrue()
        assertThat(matchesSearch("São Paulo", "sao")).isTrue()
        assertThat(matchesSearch("Recife", "noronha")).isFalse()
    }
}
