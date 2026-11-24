package com.meucoach.app.ui.coach

import com.meucoach.core.domain.coach.CoachResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CoachReferencePolicyTest {
    @Test
    fun `generic definition does not carry the engine reference`() {
        val response = CoachResponse(
            title = "Intervalado",
            body = "É correr em pedaços: acelera forte por um tempo, tira o pé, recupera e repete.",
            bullets = emptyList(),
        )

        assertThat(responseCitesSessionTargets(response)).isFalse()
    }

    @Test
    fun `answer that talks about today carries the reference`() {
        assertThat(
            responseCitesSessionTargets(
                CoachResponse(
                    title = "Hoje",
                    body = "Hoje o treino é leve: 28 min em ritmo soltinho.",
                    bullets = emptyList(),
                ),
            ),
        ).isTrue()
        assertThat(
            responseCitesSessionTargets(
                CoachResponse(
                    title = "Pace",
                    body = "Seu pace está no cartão de referência aqui embaixo.",
                    bullets = emptyList(),
                ),
            ),
        ).isTrue()
        assertThat(
            responseCitesSessionTargets(
                CoachResponse(
                    title = "Treino prescrito",
                    body = "Mantém o esforço leve.",
                    bullets = emptyList(),
                ),
            ),
        ).isTrue()
    }

    @Test
    fun `weather answer with a target word still carries the reference`() {
        assertThat(
            citesSessionTargets("Pode correr na chuva, só ajusta o ritmo."),
        ).isTrue()
    }
}
