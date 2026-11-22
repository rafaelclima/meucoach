package com.meucoach.app.ui.coach

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachSpeechLabelsTest {
    @Test
    fun `facil vira rodagem`() {
        assertThat(CoachSpeechLabels.speechTitle("Fácil")).isEqualTo("Rodagem")
        assertThat(CoachSpeechLabels.speechTitle("Fácil e conversável")).isEqualTo("Rodagem")
        assertThat(CoachSpeechLabels.speechTitle("Fácil, sem pressa")).isEqualTo("Rodagem")
    }

    @Test
    fun `facil com surtos vira rodagem com estimulos`() {
        assertThat(CoachSpeechLabels.speechTitle("Fácil com surtos")).isEqualTo("Rodagem com estímulos")
    }

    @Test
    fun `nomes de corredor ficam como estao`() {
        assertThat(CoachSpeechLabels.speechTitle("Ritmo forte")).isEqualTo("Ritmo forte")
        assertThat(CoachSpeechLabels.speechTitle("Fartlek")).isEqualTo("Fartlek")
        assertThat(CoachSpeechLabels.speechTitle("Fartlek leve")).isEqualTo("Fartlek leve")
        assertThat(CoachSpeechLabels.speechTitle("Longão")).isEqualTo("Longão")
        assertThat(CoachSpeechLabels.speechTitle("Pace de prova 5k")).isEqualTo("Pace de prova 5k")
        assertThat(CoachSpeechLabels.speechTitle("Descanso")).isEqualTo("Descanso")
        assertThat(CoachSpeechLabels.speechTitle("Recuperação: fácil e curto"))
            .isEqualTo("Recuperação: fácil e curto")
    }

    @Test
    fun `intervalos e colinas e strides ganham fala brasileira`() {
        assertThat(CoachSpeechLabels.speechTitle("Intervalos")).isEqualTo("Intervalado")
        assertThat(CoachSpeechLabels.speechTitle("Intervalos curtos")).isEqualTo("Intervalado")
        assertThat(CoachSpeechLabels.speechTitle("Colinas")).isEqualTo("Subidas")
        assertThat(CoachSpeechLabels.speechTitle("Strides curtos")).isEqualTo("Acelerações curtas")
    }

    @Test
    fun `titulo desconhecido passa direto sem inventar`() {
        assertThat(CoachSpeechLabels.speechTitle("Ritmo misterioso")).isEqualTo("Ritmo misterioso")
        assertThat(CoachSpeechLabels.speechTitle("   ")).isEqualTo("Treino")
    }

    @Test
    fun `fala embutida na frase fica em minuscula`() {
        assertThat(CoachSpeechLabels.speechInline("Fácil e conversável")).isEqualTo("rodagem")
        assertThat(CoachSpeechLabels.speechInline("Ritmo forte")).isEqualTo("ritmo forte")
        assertThat(CoachSpeechLabels.speechInline("Colinas")).isEqualTo("subidas")
    }
}
