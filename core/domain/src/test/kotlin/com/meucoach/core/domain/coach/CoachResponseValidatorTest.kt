package com.meucoach.core.domain.coach

import org.junit.Assert.assertTrue
import org.junit.Test

class CoachResponseValidatorTest {
    @Test
    fun `barra pace inventado`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Corra a 6:10/km"),
            allowedMinutes = setOf(30),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra fc inventada`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Mantenha 150 bpm"),
            allowedMinutes = setOf(30),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra duracao fora do plano`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Vamos fazer 90 min leves"),
            allowedMinutes = setOf(30),
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
            allowedMinutes = setOf(30),
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
            allowedMinutes = setOf(114, 129, 28),
            allowedPercents = setOf(100, 113, 46),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `barra ruleId do prompt mesmo em caixa baixa`() {
        val lower = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Regras aplicadas: r-review-01."),
            allowedMinutes = setOf(30),
            forbiddenCodes = setOf("R-REVIEW-01"),
        )
        val upper = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Regras aplicadas: R-PLAN-06."),
            allowedMinutes = setOf(30),
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
            allowedMinutes = setOf(30),
            allowedPercents = setOf(75),
            forbiddenCodes = setOf("R-REVIEW-01", "ADHERENCE_PARTIAL"),
        )
        assertTrue(result is CoachValidationResult.Valid)
    }

    @Test
    fun `barra porcentagem fora do calculado quando a checagem esta ativa`() {
        val result = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "O volume ficou em 140% do plano"),
            allowedMinutes = setOf(120),
            allowedPercents = setOf(75),
        )
        assertTrue(result is CoachValidationResult.Invalid)
    }

    @Test
    fun `aceita porcentagens calculadas e ignora a checagem sem conjunto`() {
        val valid = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Você fez 75% do volume e 78% fácil"),
            allowedMinutes = setOf(120),
            allowedPercents = setOf(75, 78),
        )
        val unchecked = CoachResponseValidator.validate(
            CoachRawResponse(title = "Semana", body = "Você fez 75% do volume"),
            allowedMinutes = setOf(120),
        )

        assertTrue(valid is CoachValidationResult.Valid)
        assertTrue(unchecked is CoachValidationResult.Valid)
    }
}
