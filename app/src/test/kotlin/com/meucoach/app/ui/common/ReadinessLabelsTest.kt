package com.meucoach.app.ui.common

import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ReadinessLabelsTest {
    @Test
    fun `uses semantic words instead of color names`() {
        assertThat(readinessLabel(ReadinessLevel.GREEN)).isEqualTo("Alta")
        assertThat(readinessLabel(ReadinessLevel.AMBER)).isEqualTo("Moderada")
        assertThat(readinessLabel(ReadinessLevel.RED)).isEqualTo("Baixa")
    }

    @Test
    fun `lowercase form fits sentences`() {
        assertThat(readinessLabelLowercase(ReadinessLevel.GREEN)).isEqualTo("alta")
        assertThat(readinessLabelLowercase(ReadinessLevel.AMBER)).isEqualTo("moderada")
        assertThat(readinessLabelLowercase(ReadinessLevel.RED)).isEqualTo("baixa")
    }
}
