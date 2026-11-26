package com.meucoach.core.domain.coach

import org.junit.Assert.assertTrue
import org.junit.Test

class CoachResponseValidatorTest {
    private fun allowed(
        minutes: Set<Int> = emptySet(),
        percents: Set<Int> = emptySet(),
        distancesKm: Set<Double> = emptySet(),
        counts: Set<Int> = emptySet(),
        decimals: Set<Double> = emptySet(),
    ) = CoachNumberAllowList(
        minutes = minutes,
        percents = percents,
        distancesKm = distancesKm,
        counts = counts,
        decimals = decimals,
    )

    @Test
    fun `barra pace inventado`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Corra a 6:10/km"),
            allowed(minutes = setOf(30)),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra fc inventada`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Mantenha 150 bpm"),
            allowed(minutes = setOf(30)),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra duracao fora do plano`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Vamos fazer 90 min leves"),
            allowed(minutes = setOf(30)),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `aceita texto valido com duracao planejada`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(
                title = "Foco em base",
                body = "Vamos manter os 30 min leves e consistentes",
                bullets = listOf("Priorize consistência"),
            ),
            allowed(minutes = setOf(30)),
        )
        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `barra codigo de observacao na narrativa`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(
                title = "Semana 09 a 15",
                body = "46% de intensidade fácil, acima do alvo (INTENSITY_ABOVE_EASY_TARGET).",
            ),
            allowed(
                minutes = setOf(114, 129, 28),
                percents = setOf(100, 113, 46),
            ),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra ruleId do prompt mesmo em caixa baixa`() {
        val lower = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Regras aplicadas: r-review-01."),
            allowed(minutes = setOf(30)),
            forbiddenCodes = setOf("R-REVIEW-01"),
        )
        val upper = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Regras aplicadas: R-PLAN-06."),
            allowed(minutes = setOf(30)),
            forbiddenCodes = setOf("R-REVIEW-01", "R-PLAN-06"),
        )
        assertTrue(lower is CoachValidationResult.Invalid)
        assertTrue(upper is CoachValidationResult.Invalid)
    }

    @Test
    fun `aceita prosa sem codigo`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(
                title = "Semana consistente",
                body = "Você fez 75% do volume com 30 min leves por sessão. Confiança alta.",
                bullets = listOf("Priorize consistência"),
            ),
            allowed(minutes = setOf(30), percents = setOf(75)),
            forbiddenCodes = setOf("R-REVIEW-01", "ADHERENCE_PARTIAL"),
        )
        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `barra porcentagem fora do calculado`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "O volume ficou em 140% do plano"),
            allowed(minutes = setOf(120), percents = setOf(75)),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra porcentagem sem nenhum percentual calculado`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Você fez 75% do volume"),
            allowed(minutes = setOf(120)),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra distancia sem nenhuma distancia calculada`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Vamos fazer 10 km leves"),
            allowed(minutes = setOf(30)),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra contagem sem nenhuma contagem calculada`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Você fez 99 sessões"),
            allowed(minutes = setOf(30)),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `aceita numeros tipados do plano`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(
                title = "Semana",
                body = "Você fez 3 treinos, 40 min e 10 km; 80% fácil.",
            ),
            allowed(
                minutes = setOf(40),
                percents = setOf(80),
                distancesKm = setOf(10.0),
                counts = setOf(3),
            ),
        )
        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `barra decimal sem unidade nao autorizado`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Você fez 12,5 unidades"),
            allowed(minutes = setOf(30)),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `aceita decimal autorizado`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Você fez 12,5 unidades"),
            allowed(decimals = setOf(12.5)),
        )
        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `nao aceita 825 quando o fato e 82 por cento`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Você fez 82,5% fácil"),
            allowed(percents = setOf(82, 5)),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `aceita atleta e controle porque o token exige palavra inteira`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(
                title = "Semana consistente",
                body = "O atleta manteve o controle do esforço em 30 min leves.",
            ),
            allowed(minutes = setOf(30)),
        )

        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `barra a sigla atl quando ela aparece isolada`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "O atl subiu no fim de semana."),
            allowed(minutes = setOf(30)),
        )

        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `aceita porcentagens calculadas`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Você fez 75% do volume e 78% fácil"),
            allowed(minutes = setOf(120), percents = setOf(75, 78)),
        )

        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `barra resposta longa demais para o chat`() {
        val longBody = "a".repeat(CoachResponseValidator.MAX_TOTAL_CHARS)
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = longBody),
            allowed(minutes = setOf(30)),
        )

        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `aceita narrativa longa dentro do teto do chat`() {
        val body = "Você manteve os 30 min leves com boa consistência. ".repeat(20).trim()
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = body),
            allowed(minutes = setOf(30)),
        )

        assertTrue(result is CoachValidationResult.Valid)
    }
}
