package com.meucoach.core.domain.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachProgressAnswerTest {
    private fun summary() = CoachProgressSummary(
        availableWeeks = 3,
        plannedSessions = 10,
        doneSessions = 8,
        adherencePercent = 80,
        plannedMinutes = 300,
        executedMinutes = 270,
        extraMinutes = 10,
        volumePercent = 90,
        avgReadiness = null,
        redDays = 0,
        avgSleepMinutes = null,
        shortNights = 0,
        easyPercent28d = 75,
    )

    @Test
    fun `quadro cita so numeros do proprio resumo`() {
        val answer = CoachProgressAnswer.build(summary())

        assertTrue(answer.engineContext.contains("3 de 4 semanas com dados"))
        assertTrue(answer.engineContext.contains("8 de 10 treinos (80%)"))
        assertTrue(answer.engineContext.contains("300 min planejados, 270 min feitos (90%)"))
        assertTrue(answer.engineContext.contains("75% dos últimos 28 dias (alvo ~80%)"))
        assertEquals(setOf(300, 270, 10), answer.allowedMinutes)
        assertEquals(setOf(80, 90, 75), answer.allowedPercents)
    }

    @Test
    fun `sem facil medido diz que nao ha medicao`() {
        val answer = CoachProgressAnswer.build(summary().copy(easyPercent28d = null))

        assertTrue(answer.engineContext.contains("ainda sem medição"))
        assertEquals(setOf(80, 90), answer.allowedPercents)
    }
}
