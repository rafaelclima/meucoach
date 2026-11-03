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
    fun `formats digits as a date only for display`() {
        assertThat(DateMask.format("")).isEmpty()
        assertThat(DateMask.format("1")).isEqualTo("1")
        assertThat(DateMask.format("12")).isEqualTo("12")
        assertThat(DateMask.format("123")).isEqualTo("12/3")
        assertThat(DateMask.format("1234")).isEqualTo("12/34")
        assertThat(DateMask.format("12345")).isEqualTo("12/34/5")
        assertThat(DateMask.format("01011990")).isEqualTo("01/01/1990")
    }

    @Test
    fun `keeps only eight digits and drops separators and letters`() {
        assertThat(DateMask.digits("01/01/1990")).isEqualTo("01011990")
        assertThat(DateMask.digits("31 de fevereiro")).isEqualTo("31")
        assertThat(DateMask.digits("010119901234")).isEqualTo("01011990")
    }

    @Test
    fun `maps the caret between raw digits and the masked date`() {
        assertThat(DateMask.originalToTransformed(0)).isEqualTo(0)
        assertThat(DateMask.originalToTransformed(2)).isEqualTo(2)
        assertThat(DateMask.originalToTransformed(3)).isEqualTo(4)
        assertThat(DateMask.originalToTransformed(5)).isEqualTo(7)
        assertThat(DateMask.originalToTransformed(8)).isEqualTo(10)

        assertThat(DateMask.transformedToOriginal(0)).isEqualTo(0)
        assertThat(DateMask.transformedToOriginal(2)).isEqualTo(2)
        assertThat(DateMask.transformedToOriginal(3)).isEqualTo(2)
        assertThat(DateMask.transformedToOriginal(4)).isEqualTo(3)
        assertThat(DateMask.transformedToOriginal(7)).isEqualTo(5)
        assertThat(DateMask.transformedToOriginal(10)).isEqualTo(8)
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

    @Test
    fun `searches options without accents`() {
        assertThat(matchesSearch("Brasília · Sudeste", "brasilia")).isTrue()
        assertThat(matchesSearch("São Paulo", "sao")).isTrue()
        assertThat(matchesSearch("Recife", "noronha")).isFalse()
    }
}
