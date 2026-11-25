package com.meucoach.app.ui.coach

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CoachFallbackLabelsTest {
    @Test
    fun `free answer copy never promises a card that is not rendered`() {
        val copy = CoachFallbackLabels.freeAnswerReasonCopy("Resposta contém número fora do contexto: 4")

        assertThat(copy).doesNotContain("aqui embaixo", "fora do contexto", "4")
        assertThat(copy).contains("Desconsidera")
    }

    @Test
    fun `explain copy keeps the deterministic card reference`() {
        val copy = CoachFallbackLabels.reasonCopy("Resposta contém número fora do contexto: 4")

        assertThat(copy).contains("aqui embaixo")
        assertThat(copy).doesNotContain("fora do contexto", "4")
    }
}
