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
    fun `medical scope fallback points to check-in and evaluation`() {
        val copy = CoachFallbackLabels.freeAnswerReasonCopy("Resposta fora do escopo do Coach")

        assertThat(copy).contains("check-in", "avaliação")
        assertThat(copy).doesNotContain("aqui embaixo", "diagnóstico certo")
    }

    @Test
    fun `explain copy keeps the deterministic card reference`() {
        val copy = CoachFallbackLabels.reasonCopy("Resposta contém número fora do contexto: 4")

        assertThat(copy).contains("aqui embaixo")
        assertThat(copy).doesNotContain("fora do contexto", "4")
    }
}
