package com.meucoach.app.ui.onboarding

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Test
import java.time.ZoneId

class OnboardingFormTest {
    @Test
    fun `masks digits as a date while typing`() {
        assertThat(applyDateMask("")).isEmpty()
        assertThat(applyDateMask("1")).isEqualTo("1")
        assertThat(applyDateMask("12")).isEqualTo("12/")
        assertThat(applyDateMask("123")).isEqualTo("12/3")
        assertThat(applyDateMask("1234")).isEqualTo("12/34/")
        assertThat(applyDateMask("01011990")).isEqualTo("01/01/1990")
    }

    @Test
    fun `keeps an already masked date stable`() {
        assertThat(applyDateMask("01/01/1990")).isEqualTo("01/01/1990")
    }

    @Test
    fun `drops non-digits and ignores digits past the year`() {
        assertThat(applyDateMask("31 de fevereiro")).isEqualTo("31/")
        assertThat(applyDateMask("010119901234")).isEqualTo("01/01/1990")
    }

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
}
