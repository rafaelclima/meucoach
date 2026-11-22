package com.meucoach.app.story

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class ArtDateFormatterTest {
    @Test
    fun `formats the art date without the pt-br month dot`() {
        assertThat(ArtDateFormatter.format(LocalDate.parse("2026-10-24"))).isEqualTo("24 out 2026")
    }

    @Test
    fun `keeps single digit days without leading zero`() {
        assertThat(ArtDateFormatter.format(LocalDate.parse("2026-01-05"))).isEqualTo("5 jan 2026")
    }

    @Test
    fun `a missing date formats to null and the extra is omitted`() {
        assertThat(ArtDateFormatter.format(null)).isNull()
    }
}
