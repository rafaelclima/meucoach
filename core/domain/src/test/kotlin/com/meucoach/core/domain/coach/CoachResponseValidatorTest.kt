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
}
