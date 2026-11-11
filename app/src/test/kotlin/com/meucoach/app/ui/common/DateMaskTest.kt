package com.meucoach.app.ui.common

import androidx.compose.ui.text.AnnotatedString
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class DateMaskTest {
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
    fun `parses raw digits and formatted dates without shifting the day`() {
        assertThat(DateMask.parse("14032027")).isEqualTo(LocalDate.of(2027, 3, 14))
        assertThat(DateMask.parse("14/03/2027")).isEqualTo(LocalDate.of(2027, 3, 14))
        assertThat(DateMask.parse("31022027")).isNull()
        assertThat(DateMask.parse("31/02/2027")).isNull()
        assertThat(DateMask.parse("1403")).isNull()
        assertThat(DateMask.parse("data")).isNull()
    }

    @Test
    fun `formats a date for the digits-only field`() {
        assertThat(DateMask.toDigits(LocalDate.of(2027, 3, 14))).isEqualTo("14032027")
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
    fun `visual transformation keeps raw digits and maps the caret around the slashes`() {
        val result = DateVisualTransformation.filter(AnnotatedString("14032027"))

        assertThat(result.text.text).isEqualTo("14/03/2027")
        assertThat(result.offsetMapping.originalToTransformed(0)).isEqualTo(0)
        assertThat(result.offsetMapping.originalToTransformed(2)).isEqualTo(2)
        assertThat(result.offsetMapping.originalToTransformed(3)).isEqualTo(4)
        assertThat(result.offsetMapping.originalToTransformed(4)).isEqualTo(5)
        assertThat(result.offsetMapping.transformedToOriginal(3)).isEqualTo(2)
        assertThat(result.offsetMapping.transformedToOriginal(6)).isEqualTo(4)
    }

    @Test
    fun `visual transformation also accepts a pasted value with slashes`() {
        val result = DateVisualTransformation.filter(AnnotatedString("14/03/2027"))

        assertThat(result.text.text).isEqualTo("14/03/2027")
        assertThat(result.offsetMapping.originalToTransformed(10)).isEqualTo(10)
        assertThat(result.offsetMapping.transformedToOriginal(10)).isEqualTo(10)
    }
}
