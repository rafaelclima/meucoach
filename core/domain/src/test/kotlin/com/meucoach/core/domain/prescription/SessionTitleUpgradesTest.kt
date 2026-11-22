package com.meucoach.core.domain.prescription

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SessionTitleUpgradesTest {
    @Test
    fun `legacy titles map to runner talk`() {
        assertThat(SessionTitleUpgrades.modernTitle("Fácil e conversável")).isEqualTo("Rodagem")
        assertThat(SessionTitleUpgrades.modernTitle("Fácil, sem pressa")).isEqualTo("Rodagem leve")
        assertThat(SessionTitleUpgrades.modernTitle("Fácil com surtos")).isEqualTo("Rodagem com estímulos")
        assertThat(SessionTitleUpgrades.modernTitle("Intervalos")).isEqualTo("Intervalado")
        assertThat(SessionTitleUpgrades.modernTitle("Intervalos curtos")).isEqualTo("Intervalado")
        assertThat(SessionTitleUpgrades.modernTitle("Colinas")).isEqualTo("Subidas")
        assertThat(SessionTitleUpgrades.modernTitle("Strides curtos")).isEqualTo("Acelerações curtas")
    }

    @Test
    fun `current titles pass through untouched`() {
        assertThat(SessionTitleUpgrades.modernTitle("Rodagem")).isEqualTo("Rodagem")
        assertThat(SessionTitleUpgrades.modernTitle("Ritmo forte")).isEqualTo("Ritmo forte")
        assertThat(SessionTitleUpgrades.modernTitle("Longão")).isEqualTo("Longão")
        assertThat(SessionTitleUpgrades.modernTitle("Descanso")).isEqualTo("Descanso")
    }
}
